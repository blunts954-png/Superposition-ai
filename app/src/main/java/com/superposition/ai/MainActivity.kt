package com.superposition.ai

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.superposition.ai.ui.dashboard.DashboardFragment
import com.superposition.ai.ui.cleaner.CleanerFragment
import com.superposition.ai.ui.apps.AppsFragment
import com.superposition.ai.ui.storage.StorageFragment
import com.superposition.ai.ui.settings.SettingsFragment
import com.superposition.ai.ui.battery.BatteryFragment
import com.superposition.ai.ui.photos.PhotoOrganizerFragment
import com.superposition.ai.ui.security.SecurityFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 100
        private const val REQUEST_USAGE_STATS = 101
        private const val REQUEST_MANAGE_STORAGE = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_navigation)

        // Check and request permissions
        checkPermissions()

        // Set up bottom navigation
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    loadFragment(DashboardFragment())
                    true
                }
                R.id.nav_cleaner -> {
                    loadFragment(CleanerFragment())
                    true
                }
                R.id.nav_apps -> {
                    loadFragment(AppsFragment())
                    true
                }
                R.id.nav_storage -> {
                    loadFragment(StorageFragment())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }
    }

    fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    
    fun navigateToCleaner() {
        bottomNav.selectedItemId = R.id.nav_cleaner
    }
    
    fun navigateToBattery() {
        loadFragment(BatteryFragment())
    }
    
    fun navigateToPhotoOrganizer() {
        loadFragment(PhotoOrganizerFragment())
    }
    
    fun navigateToSecurity() {
        loadFragment(SecurityFragment())
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        // Storage permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // Request permissions
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_STORAGE_PERMISSION)
        }

        // Usage stats permission
        checkUsageStatsPermission()
    }

    private fun checkUsageStatsPermission() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }

        if (mode != AppOpsManager.MODE_ALLOWED) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivityForResult(intent, REQUEST_USAGE_STATS)
        }
    }
}
