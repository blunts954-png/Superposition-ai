package com.superposition.ai.ui.security

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.superposition.ai.R
import com.superposition.ai.databinding.FragmentSecurityBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecurityFragment : Fragment() {

    private var _binding: FragmentSecurityBinding? = null
    private val binding get() = _binding!!

    private lateinit var securityAppsAdapter: SecurityAppsAdapter
    private val securityAppsList = mutableListOf<SecurityAppInfo>()

    // Dangerous permissions that indicate potential security risks
    private val dangerousPermissions = listOf(
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.WRITE_CONTACTS,
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.WRITE_CALENDAR
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecurityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up RecyclerView
        securityAppsAdapter = SecurityAppsAdapter(
            securityAppsList,
            onAppClick = { appInfo ->
                showAppDetails(appInfo)
            }
        )

        binding.recyclerViewSecurityApps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewSecurityApps.adapter = securityAppsAdapter

        // Set up scan button
        binding.buttonScanSecurity.setOnClickListener {
            scanSecurity()
        }

        // Load security info
        loadSecurityInfo()
    }

    private fun loadSecurityInfo() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val securityInfo = withContext(Dispatchers.IO) {
                calculateSecurityScore()
            }

            // Update security score
            binding.textViewSecurityScore.text = "${securityInfo.score}/100"
            binding.progressBarSecurity.progress = securityInfo.score

            // Update status
            binding.textViewSecurityStatus.text = when {
                securityInfo.score >= 80 -> "Secure"
                securityInfo.score >= 60 -> "Moderate Risk"
                else -> "High Risk"
            }

            binding.textViewSecurityStatus.setTextColor(
                when {
                    securityInfo.score >= 80 -> requireContext().getColor(R.color.accent_green)
                    securityInfo.score >= 60 -> requireContext().getColor(R.color.accent_orange)
                    else -> requireContext().getColor(R.color.accent_red)
                }
            )

            // Load risky apps
            val apps = withContext(Dispatchers.IO) {
                getRiskyApps()
            }

            securityAppsList.clear()
            securityAppsList.addAll(apps)
            securityAppsAdapter.notifyDataSetChanged()

            binding.progressBar.visibility = View.GONE
            binding.textViewEmpty.visibility = if (securityAppsList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun scanSecurity() {
        binding.buttonScanSecurity.isEnabled = false
        binding.buttonScanSecurity.text = "Scanning..."
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                getRiskyApps()
            }

            securityAppsList.clear()
            securityAppsList.addAll(apps)
            securityAppsAdapter.notifyDataSetChanged()

            val securityInfo = calculateSecurityScore()
            binding.textViewSecurityScore.text = "${securityInfo.score}/100"
            binding.progressBarSecurity.progress = securityInfo.score

            Toast.makeText(
                requireContext(),
                "Scan complete. Found ${apps.size} apps with security concerns",
                Toast.LENGTH_LONG
            ).show()

            binding.buttonScanSecurity.isEnabled = true
            binding.buttonScanSecurity.text = "🔒 Scan Security"
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun calculateSecurityScore(): SecurityInfo {
        val packageManager = requireContext().packageManager
        val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        
        var totalRisk = 0
        var appCount = 0

        for (packageInfo in packages) {
            val appInfo = packageInfo.applicationInfo
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                appCount++
                val riskLevel = calculateAppRisk(packageInfo)
                totalRisk += riskLevel
            }
        }

        val avgRisk = if (appCount > 0) totalRisk / appCount else 0
        val score = 100 - (avgRisk * 2).coerceIn(0, 100)

        return SecurityInfo(score)
    }

    private fun calculateAppRisk(packageInfo: android.content.pm.PackageInfo): Int {
        var riskLevel = 0
        val requestedPermissions = packageInfo.requestedPermissions ?: return 0

        for (permission in requestedPermissions) {
            if (dangerousPermissions.contains(permission)) {
                riskLevel += 10
            }
        }

        // Check for suspicious package names
        if (packageInfo.packageName.contains("hack") ||
            packageInfo.packageName.contains("crack") ||
            packageInfo.packageName.contains("virus")) {
            riskLevel += 50
        }

        return riskLevel.coerceIn(0, 50)
    }

    private fun getRiskyApps(): List<SecurityAppInfo> {
        val apps = mutableListOf<SecurityAppInfo>()
        val packageManager = requireContext().packageManager

        try {
            val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (packageInfo in packages) {
                val appInfo = packageInfo.applicationInfo

                // Only check user-installed apps
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                    val riskLevel = calculateAppRisk(packageInfo)
                    
                    if (riskLevel > 20) { // Only show apps with significant risk
                        try {
                            val appName = packageManager.getApplicationLabel(appInfo).toString()
                            val icon = packageManager.getApplicationIcon(appInfo)
                            val packageName = packageInfo.packageName
                            
                            val riskyPermissions = getRiskyPermissions(packageInfo)
                            val riskDescription = getRiskDescription(riskLevel, riskyPermissions)

                            apps.add(SecurityAppInfo(
                                name = appName,
                                packageName = packageName,
                                icon = icon,
                                riskLevel = riskLevel,
                                riskyPermissions = riskyPermissions,
                                riskDescription = riskDescription
                            ))
                        } catch (e: Exception) {
                            // Skip apps that can't be loaded
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return apps.sortedByDescending { it.riskLevel }
    }

    private fun getRiskyPermissions(packageInfo: android.content.pm.PackageInfo): List<String> {
        val risky = mutableListOf<String>()
        val requestedPermissions = packageInfo.requestedPermissions ?: return risky

        for (permission in requestedPermissions) {
            if (dangerousPermissions.contains(permission)) {
                risky.add(permission.substringAfterLast("."))
            }
        }

        return risky
    }

    private fun getRiskDescription(riskLevel: Int, permissions: List<String>): String {
        return when {
            riskLevel >= 40 -> "High Risk: Multiple dangerous permissions"
            riskLevel >= 30 -> "Moderate Risk: Several sensitive permissions"
            riskLevel >= 20 -> "Low Risk: Some permissions requested"
            else -> "Minimal Risk"
        }
    }

    private fun showAppDetails(appInfo: SecurityAppInfo) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${appInfo.packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Cannot open app details",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class SecurityInfo(
        val score: Int
    )

    data class SecurityAppInfo(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable,
        val riskLevel: Int,
        val riskyPermissions: List<String>,
        val riskDescription: String
    )
}

