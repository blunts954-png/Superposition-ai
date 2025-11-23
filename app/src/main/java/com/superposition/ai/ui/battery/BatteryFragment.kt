package com.superposition.ai.ui.battery

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.superposition.ai.MainActivity
import com.superposition.ai.R
import com.superposition.ai.databinding.FragmentBatteryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatteryFragment : Fragment() {

    private var _binding: FragmentBatteryBinding? = null
    private val binding get() = _binding!!

    private lateinit var batteryAppsAdapter: BatteryAppsAdapter
    private val batteryAppsList = mutableListOf<BatteryAppInfo>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBatteryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up RecyclerView
        batteryAppsAdapter = BatteryAppsAdapter(
            batteryAppsList,
            onOptimizeClick = { appInfo ->
                optimizeApp(appInfo)
            }
        )

        binding.recyclerViewBatteryApps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewBatteryApps.adapter = batteryAppsAdapter

        // Set up buttons
        binding.buttonOptimizeAll.setOnClickListener {
            optimizeAllApps()
        }

        binding.buttonBatterySettings.setOnClickListener {
            openBatterySettings()
        }

        // Load battery info
        loadBatteryInfo()
    }

    private fun loadBatteryInfo() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val batteryInfo = withContext(Dispatchers.IO) {
                getBatteryInfo()
            }

            // Update battery level
            binding.textViewBatteryLevel.text = "${batteryInfo.level}%"
            binding.progressBarBattery.progress = batteryInfo.level

            // Update battery status
            binding.textViewBatteryStatus.text = when {
                batteryInfo.isCharging -> "Charging"
                batteryInfo.level < 20 -> "Low Battery"
                batteryInfo.level < 50 -> "Battery OK"
                else -> "Battery Good"
            }

            // Update battery health
            binding.textViewBatteryHealth.text = batteryInfo.health

            // Update estimated time
            if (batteryInfo.isCharging) {
                binding.textViewEstimatedTime.text = "Charging..."
            } else {
                val hours = batteryInfo.level / 10 // Rough estimate
                binding.textViewEstimatedTime.text = "~$hours hours remaining"
            }

            // Load battery draining apps
            val apps = withContext(Dispatchers.IO) {
                getBatteryDrainingApps()
            }

            batteryAppsList.clear()
            batteryAppsList.addAll(apps)
            batteryAppsAdapter.notifyDataSetChanged()

            binding.progressBar.visibility = View.GONE
            binding.textViewEmpty.visibility = if (batteryAppsList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun getBatteryInfo(): BatteryInfo {
        val batteryManager = requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging

        val health = when (batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unknown Issue"
            else -> "Unknown"
        }

        return BatteryInfo(
            level = level,
            isCharging = isCharging,
            health = health
        )
    }

    private fun getBatteryDrainingApps(): List<BatteryAppInfo> {
        val apps = mutableListOf<BatteryAppInfo>()
        val packageManager = requireContext().packageManager

        try {
            val packages = packageManager.getInstalledPackages(0)
            for (packageInfo in packages) {
                val appInfo = packageInfo.applicationInfo

                // Only show user-installed apps
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                    try {
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        val icon = packageManager.getApplicationIcon(appInfo)
                        val packageName = packageInfo.packageName

                        // Simulate battery usage (in real app, would use UsageStatsManager)
                        // This is a placeholder - real implementation needs UsageStats permission
                        val batteryUsage = (0..100).random() // Simulated percentage

                        if (batteryUsage > 5) { // Only show apps using >5% battery
                            apps.add(BatteryAppInfo(appName, packageName, icon, batteryUsage))
                        }
                    } catch (e: Exception) {
                        // Skip apps that can't be loaded
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return apps.sortedByDescending { it.batteryUsage }
    }

    private fun optimizeApp(appInfo: BatteryAppInfo) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            withContext(Dispatchers.IO) {
                // Kill the app process
                val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                activityManager.killBackgroundProcesses(appInfo.packageName)
                delay(500)
            }

            Toast.makeText(
                requireContext(),
                "${appInfo.name} optimized",
                Toast.LENGTH_SHORT
            ).show()

            binding.progressBar.visibility = View.GONE
            loadBatteryInfo() // Reload
        }
    }

    private fun optimizeAllApps() {
        binding.buttonOptimizeAll.isEnabled = false
        binding.buttonOptimizeAll.text = "Optimizing..."

        lifecycleScope.launch {
            var optimizedCount = 0

            withContext(Dispatchers.IO) {
                val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                
                for (app in batteryAppsList) {
                    activityManager.killBackgroundProcesses(app.packageName)
                    optimizedCount++
                    delay(200)
                }
            }

            Toast.makeText(
                requireContext(),
                "Optimized $optimizedCount apps",
                Toast.LENGTH_LONG
            ).show()

            binding.buttonOptimizeAll.text = "Optimize All"
            binding.buttonOptimizeAll.isEnabled = true
            loadBatteryInfo() // Reload
        }
    }

    private fun openBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Cannot open battery settings",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean,
        val health: String
    )

    data class BatteryAppInfo(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable,
        val batteryUsage: Int
    )
}

