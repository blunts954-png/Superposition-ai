package com.superposition.ai.ui.storage

import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.superposition.ai.R
import com.superposition.ai.databinding.FragmentStorageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StorageFragment : Fragment() {

    private var _binding: FragmentStorageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStorageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Load storage information
        loadStorageInfo()
    }

    private fun loadStorageInfo() {
        lifecycleScope.launch {
            val storageInfo = withContext(Dispatchers.IO) {
                calculateStorageInfo()
            }
            
            // Update UI
            binding.textViewTotalStorage.text = formatSize(storageInfo.totalStorage)
            binding.textViewUsedStorage.text = formatSize(storageInfo.usedStorage)
            binding.textViewFreeStorage.text = formatSize(storageInfo.freeStorage)
            
            val usedPercent = if (storageInfo.totalStorage > 0) {
                ((storageInfo.usedStorage.toFloat() / storageInfo.totalStorage) * 100).toInt()
            } else {
                0
            }
            binding.progressBarStorage.progress = usedPercent
            
            binding.textViewAppsSize.text = formatSize(storageInfo.appsSize)
            binding.textViewImagesSize.text = formatSize(storageInfo.imagesSize)
            binding.textViewVideosSize.text = formatSize(storageInfo.videosSize)
            binding.textViewAudioSize.text = formatSize(storageInfo.audioSize)
            binding.textViewDocumentsSize.text = formatSize(storageInfo.documentsSize)
            binding.textViewOtherSize.text = formatSize(storageInfo.otherSize)
        }
    }

    private fun calculateStorageInfo(): StorageInfo {
        // Get total storage
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val totalStorage = stat.blockCountLong * stat.blockSizeLong
        val availableStorage = stat.availableBlocksLong * stat.blockSizeLong
        val usedStorage = totalStorage - availableStorage

        // Calculate breakdown
        val externalStorage = Environment.getExternalStorageDirectory()
        
        val imagesSize = getDirectorySize(File(externalStorage, Environment.DIRECTORY_PICTURES))
        val videosSize = getDirectorySize(File(externalStorage, Environment.DIRECTORY_MOVIES))
        val audioSize = getDirectorySize(File(externalStorage, Environment.DIRECTORY_MUSIC))
        val documentsSize = getDirectorySize(File(externalStorage, Environment.DIRECTORY_DOCUMENTS))
        
        // Apps size (approximate - would need special permissions for exact)
        val appsSize = try {
            val context = requireContext()
            val packageManager = context.packageManager
            var totalAppsSize = 0L
            val packages = packageManager.getInstalledPackages(0)
            // Note: Getting actual app size requires PackageManager.getPackageSizeInfo
            // which is deprecated. This is an approximation.
            totalAppsSize = usedStorage / 4 // Rough estimate: apps take ~25% of storage
            totalAppsSize
        } catch (e: Exception) {
            0L
        }
        
        val otherSize = usedStorage - imagesSize - videosSize - audioSize - documentsSize - appsSize
        
        return StorageInfo(
            totalStorage = totalStorage,
            usedStorage = usedStorage,
            freeStorage = availableStorage,
            appsSize = appsSize,
            imagesSize = imagesSize,
            videosSize = videosSize,
            audioSize = audioSize,
            documentsSize = documentsSize,
            otherSize = maxOf(0, otherSize)
        )
    }

    private fun getDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles()
            if (files != null) {
                for (file in files) {
                    size += if (file.isDirectory) {
                        getDirectorySize(file)
                    } else {
                        file.length()
                    }
                }
            }
        }
        return size
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000f)
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f)
            bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000f)
            else -> "$bytes B"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class StorageInfo(
        val totalStorage: Long,
        val usedStorage: Long,
        val freeStorage: Long,
        val appsSize: Long,
        val imagesSize: Long,
        val videosSize: Long,
        val audioSize: Long,
        val documentsSize: Long,
        val otherSize: Long
    )
}
