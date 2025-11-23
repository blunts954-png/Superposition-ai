package com.superposition.ai.ui.apps

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.superposition.ai.R
import com.superposition.ai.databinding.FragmentAppsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    private lateinit var appsAdapter: AppsAdapter
    private val appsList = mutableListOf<AppInfo>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up RecyclerView
        appsAdapter = AppsAdapter(
            appsList,
            onUninstallClick = { appInfo ->
                uninstallApp(appInfo)
            }
        )

        binding.recyclerViewApps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewApps.adapter = appsAdapter

        // Load apps
        loadApps()
    }

    private fun loadApps() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                getInstalledApps()
            }

            appsList.clear()
            appsList.addAll(apps)
            appsAdapter.notifyDataSetChanged()

            binding.progressBar.visibility = View.GONE
            binding.textViewEmpty.visibility = if (appsList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun getInstalledApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
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

                        // Note: Getting actual app size requires special permissions
                        // For now, we'll just show 0 (size calculation would need PackageManager.getPackageSizeInfo)
                        val appSize = 0L

                        apps.add(AppInfo(appName, packageName, icon, appSize))
                    } catch (e: Exception) {
                        // Skip apps that can't be loaded
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return apps.sortedBy { it.name }
    }

    private fun uninstallApp(appInfo: AppInfo) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${appInfo.packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Cannot uninstall ${appInfo.name}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable,
        val size: Long
    )
}

