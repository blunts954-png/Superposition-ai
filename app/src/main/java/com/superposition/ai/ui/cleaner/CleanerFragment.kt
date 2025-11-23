package com.superposition.ai.ui.cleaner

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.superposition.ai.R
import com.superposition.ai.databinding.FragmentCleanerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CleanerFragment : Fragment() {

    private var _binding: FragmentCleanerBinding? = null
    private val binding get() = _binding!!

    private var cacheSize: Long = 0
    private var tempSize: Long = 0
    private var whatsappSize: Long = 0
    private var downloadsSize: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCleanerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up scan button
        binding.btnScan.setOnClickListener {
            scanForJunk()
        }

        // Set up clean button
        binding.btnClean.setOnClickListener {
            cleanSelectedJunk()
        }
    }

    private fun scanForJunk() {
        binding.btnScan.isEnabled = false
        binding.btnScan.text = "Scanning..."
        binding.cardScanResult.visibility = View.GONE
        binding.progressScan.progress = 0
        binding.tvScanStatus.text = "Starting scan..."

        lifecycleScope.launch {
            val totalSize = withContext(Dispatchers.IO) {
                scanCacheFiles()
                binding.progressScan.progress = 25
                binding.tvScanStatus.text = "Scanning cache files..."

                delay(300)
                scanTempFiles()
                binding.progressScan.progress = 50
                binding.tvScanStatus.text = "Scanning temp files..."

                delay(300)
                scanWhatsAppFiles()
                binding.progressScan.progress = 75
                binding.tvScanStatus.text = "Scanning WhatsApp files..."

                delay(300)
                scanOldDownloads()
                binding.progressScan.progress = 100
                binding.tvScanStatus.text = "Scan complete!"

                cacheSize + tempSize + whatsappSize + downloadsSize
            }

            // Update UI
            binding.tvJunkSize.text = formatSize(totalSize)
            binding.tvCacheSize.text = formatSize(cacheSize)
            binding.tvTempSize.text = formatSize(tempSize)
            binding.tvWhatsappSize.text = formatSize(whatsappSize)
            binding.tvDownloadsSize.text = formatSize(downloadsSize)

            binding.cardScanResult.visibility = View.VISIBLE
            binding.btnClean.visibility = View.VISIBLE
            binding.btnScan.isEnabled = true
            binding.btnScan.text = "🔍 SCAN FOR JUNK"
        }
    }

    private fun scanCacheFiles(): Long {
        cacheSize = 0
        try {
            val context = requireContext()
            val cacheDir = context.cacheDir
            cacheSize += getDirectorySize(cacheDir)

            val externalCacheDir = context.externalCacheDir
            if (externalCacheDir != null) {
                cacheSize += getDirectorySize(externalCacheDir)
            }

            // Scan app cache directories
            val packageManager = context.packageManager
            val packages = packageManager.getInstalledPackages(0)
            for (packageInfo in packages) {
                try {
                    val appCacheDir = File(context.cacheDir.parent, packageInfo.packageName)
                    if (appCacheDir.exists()) {
                        cacheSize += getDirectorySize(appCacheDir)
                    }
                } catch (e: Exception) {
                    // Ignore individual app errors
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cacheSize
    }

    private fun scanTempFiles(): Long {
        tempSize = 0
        try {
            val tempDirs = listOf(
                File(Environment.getExternalStorageDirectory(), "temp"),
                File(Environment.getExternalStorageDirectory(), ".tmp"),
                File(requireContext().filesDir, "temp")
            )

            for (dir in tempDirs) {
                if (dir.exists()) {
                    tempSize += getDirectorySize(dir)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tempSize
    }

    private fun scanWhatsAppFiles(): Long {
        whatsappSize = 0
        try {
            val whatsappDirs = listOf(
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/.Sent"),
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/.Statuses"),
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/Thumbnails")
            )

            for (dir in whatsappDirs) {
                if (dir.exists()) {
                    whatsappSize += getDirectorySize(dir)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return whatsappSize
    }

    private fun scanOldDownloads(): Long {
        downloadsSize = 0
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir.exists()) {
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                downloadsSize = getDirectorySizeOlderThan(downloadsDir, thirtyDaysAgo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return downloadsSize
    }

    private fun cleanSelectedJunk() {
        binding.btnClean.isEnabled = false
        binding.btnClean.text = "Cleaning..."
        binding.tvScanStatus.text = "Cleaning selected files..."

        lifecycleScope.launch {
            var cleanedSize = 0L

            withContext(Dispatchers.IO) {
                if (binding.cbCache.isChecked) {
                    cleanedSize += cleanCacheFiles()
                    delay(200)
                }

                if (binding.cbTemp.isChecked) {
                    cleanedSize += cleanTempFiles()
                    delay(200)
                }

                if (binding.cbWhatsapp.isChecked) {
                    cleanedSize += cleanWhatsAppFiles()
                    delay(200)
                }

                if (binding.cbDownloads.isChecked) {
                    cleanedSize += cleanOldDownloads()
                    delay(200)
                }
            }

            Toast.makeText(
                context,
                "Cleaned ${formatSize(cleanedSize)}",
                Toast.LENGTH_LONG
            ).show()

            // Reset scan results
            cacheSize = 0
            tempSize = 0
            whatsappSize = 0
            downloadsSize = 0

            binding.tvJunkSize.text = "0 MB"
            binding.tvCacheSize.text = "0 MB"
            binding.tvTempSize.text = "0 MB"
            binding.tvWhatsappSize.text = "0 MB"
            binding.tvDownloadsSize.text = "0 MB"

            binding.cardScanResult.visibility = View.GONE
            binding.btnClean.visibility = View.GONE
            binding.btnClean.isEnabled = true
            binding.btnClean.text = "🧹 CLEAN ALL"
            binding.progressScan.progress = 0
            binding.tvScanStatus.text = "Ready to scan"
        }
    }

    private fun cleanCacheFiles(): Long {
        var cleaned = 0L
        try {
            val context = requireContext()
            cleaned += deleteDirectory(context.cacheDir)
            context.externalCacheDir?.let {
                cleaned += deleteDirectory(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cleaned
    }

    private fun cleanTempFiles(): Long {
        var cleaned = 0L
        try {
            val tempDirs = listOf(
                File(Environment.getExternalStorageDirectory(), "temp"),
                File(Environment.getExternalStorageDirectory(), ".tmp"),
                File(requireContext().filesDir, "temp")
            )

            for (dir in tempDirs) {
                if (dir.exists()) {
                    cleaned += deleteDirectory(dir)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cleaned
    }

    private fun cleanWhatsAppFiles(): Long {
        var cleaned = 0L
        try {
            val whatsappDirs = listOf(
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/.Sent"),
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/.Statuses"),
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/Thumbnails")
            )

            for (dir in whatsappDirs) {
                if (dir.exists()) {
                    cleaned += deleteDirectory(dir)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cleaned
    }

    private fun cleanOldDownloads(): Long {
        var cleaned = 0L
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir.exists()) {
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                cleaned = deleteFilesOlderThan(downloadsDir, thirtyDaysAgo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cleaned
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

    private fun getDirectorySizeOlderThan(directory: File, timestamp: Long): Long {
        var size = 0L
        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.lastModified() < timestamp) {
                        size += if (file.isDirectory) {
                            getDirectorySizeOlderThan(file, timestamp)
                        } else {
                            file.length()
                        }
                    }
                }
            }
        }
        return size
    }

    private fun deleteDirectory(directory: File): Long {
        var deleted = 0L
        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles()
            if (files != null) {
                for (file in files) {
                    deleted += if (file.isDirectory) {
                        deleteDirectory(file)
                    } else {
                        val size = file.length()
                        if (file.delete()) size else 0L
                    }
                }
            }
        }
        return deleted
    }

    private fun deleteFilesOlderThan(directory: File, timestamp: Long): Long {
        var deleted = 0L
        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.lastModified() < timestamp) {
                        deleted += if (file.isDirectory) {
                            deleteFilesOlderThan(file, timestamp)
                        } else {
                            val size = file.length()
                            if (file.delete()) size else 0L
                        }
                    }
                }
            }
        }
        return deleted
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
}

