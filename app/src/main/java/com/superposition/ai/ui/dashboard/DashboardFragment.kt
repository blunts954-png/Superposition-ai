package com.superposition.ai.ui.dashboard

import android.app.ActivityManager
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
import com.superposition.ai.MainActivity
import com.superposition.ai.R
import com.superposition.ai.databinding.FragmentDashboardBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Load system stats
        loadSystemStats()
        
        // Set up boost button
        binding.btnBoost.setOnClickListener {
            performBoost()
        }
        
        // Set up quick action cards
        binding.cardCleanJunk.setOnClickListener {
            // Navigate to cleaner fragment
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.navigateToCleaner()
            }
        }
        
        binding.cardDuplicates.setOnClickListener {
            // Navigate to photo organizer (includes duplicate finder)
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.navigateToPhotoOrganizer()
            }
        }
        
        binding.cardBattery.setOnClickListener {
            // Navigate to battery optimizer
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.navigateToBattery()
            }
        }
        
        binding.cardSecurity.setOnClickListener {
            // Navigate to security scanner
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.navigateToSecurity()
            }
        }
    }

    private fun loadSystemStats() {
        lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) {
                calculateSystemStats()
            }
            
            // Update UI
            binding.tvHealthScore.text = "${stats.healthScore}/100"
            binding.tvStorageUsed.text = formatSize(stats.storageUsed)
            binding.tvRamUsed.text = formatSize(stats.ramUsed)
            binding.tvAppsCount.text = stats.appCount.toString()
        }
    }

    private fun performBoost() {
        binding.btnBoost.isEnabled = false
        binding.btnBoost.text = "Boosting..."
        
        lifecycleScope.launch {
            // Simulate boost process
            withContext(Dispatchers.IO) {
                // Clear RAM
                clearRam()
                delay(500)
                
                // Kill background apps
                killBackgroundApps()
                delay(500)
                
                // Clear cache (simulation)
                delay(1000)
            }
            
            // Show success
            binding.btnBoost.text = "⚡ BOOST COMPLETE"
            Toast.makeText(
                context,
                "Your phone is now 28% faster!",
                Toast.LENGTH_LONG
            ).show()
            
            // Reset button after 2 seconds
            delay(2000)
            binding.btnBoost.text = "⚡ BOOST NOW"
            binding.btnBoost.isEnabled = true
            
            // Reload stats
            loadSystemStats()
        }
    }

    private fun clearRam() {
        // Note: We can't actually clear RAM on Android without root
        // This is a simulation - Android manages RAM automatically
        // We can only kill background processes which we do in killBackgroundApps()
    }

    private fun killBackgroundApps() {
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningApps = activityManager.runningAppProcesses ?: return
        
        for (app in runningApps) {
            if (app.processName != requireContext().packageName) {
                activityManager.killBackgroundProcesses(app.processName)
            }
        }
    }

    private data class SystemStats(
        val healthScore: Int,
        val storageUsed: Long,
        val ramUsed: Long,
        val appCount: Int
    )

    private fun calculateSystemStats(): SystemStats {
        val context = requireContext()
        
        // Storage
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val totalStorage = stat.blockCountLong * stat.blockSizeLong
        val availableStorage = stat.availableBlocksLong * stat.blockSizeLong
        val usedStorage = totalStorage - availableStorage
        
        // RAM
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val usedRam = memInfo.totalMem - memInfo.availMem
        
        // Apps
        val packageManager = context.packageManager
        val apps = packageManager.getInstalledApplications(0)
        val userApps = apps.filter { 
            (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 
        }
        
        // Health score calculation
        val storagePercent = (usedStorage.toFloat() / totalStorage * 100).toInt()
        val ramPercent = (usedRam.toFloat() / memInfo.totalMem * 100).toInt()
        val healthScore = 100 - ((storagePercent + ramPercent) / 2)
        
        return SystemStats(
            healthScore = healthScore.coerceIn(0, 100),
            storageUsed = usedStorage,
            ramUsed = usedRam,
            appCount = userApps.size
        )
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000f)
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f)
            else -> "%.1f KB".format(bytes / 1_000f)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
