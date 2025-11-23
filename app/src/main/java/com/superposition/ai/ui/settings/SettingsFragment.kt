package com.superposition.ai.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.superposition.ai.R
import com.superposition.ai.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set app version
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(
                requireContext().packageName,
                0
            )
            binding.textViewVersion.text = packageInfo.versionName
        } catch (e: Exception) {
            binding.textViewVersion.text = "1.0.0"
        }

        // Set up trial status (placeholder - would come from SharedPreferences or billing)
        binding.textViewTrialStatus.text = "7 days left in trial"

        // Upgrade button
        binding.buttonUpgrade.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Premium upgrade coming soon!",
                Toast.LENGTH_SHORT
            ).show()
            // TODO: Implement Google Play Billing
        }

        // Auto clean switch
        binding.switchAutoClean.setOnCheckedChangeListener { _, isChecked ->
            // TODO: Save to SharedPreferences
            Toast.makeText(
                requireContext(),
                if (isChecked) "Auto clean enabled" else "Auto clean disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Notifications switch
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            // TODO: Save to SharedPreferences
            Toast.makeText(
                requireContext(),
                if (isChecked) "Notifications enabled" else "Notifications disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Privacy policy
        binding.cardPrivacyPolicy.setOnClickListener {
            openPrivacyPolicy()
        }
    }

    private fun openPrivacyPolicy() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://superposition.ai/privacy")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Cannot open privacy policy",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

