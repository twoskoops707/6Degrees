package com.example.a6degrees.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.a6degrees.databinding.FragmentApiSettingsBinding

class ApiSettingsFragment : Fragment() {

    private var _binding: FragmentApiSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApiSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupSaveButton()
        setupGuideButtons()
        loadSavedApiKeys()
    }

    private fun setupToolbar() {
        binding.settingsToolbar.setNavigationOnClickListener {
            // Navigate back to main settings
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupSaveButton() {
        binding.saveApiKeysButton.setOnClickListener {
            saveApiKeys()
        }
    }

    private fun setupGuideButtons() {
        binding.viewPdlGuideButton.setOnClickListener {
            // TODO: Navigate to People Data Labs guide
        }

        binding.viewClearbitGuideButton.setOnClickListener {
            // TODO: Navigate to Clearbit guide
        }

        binding.viewPiplGuideButton.setOnClickListener {
            // TODO: Navigate to Pipl guide
        }

        binding.viewBuiltwithGuideButton.setOnClickListener {
            // TODO: Navigate to BuiltWith guide
        }

        binding.viewHunterGuideButton.setOnClickListener {
            // TODO: Navigate to Hunter.io guide
        }

        binding.viewHibpGuideButton.setOnClickListener {
            // TODO: Navigate to HaveIBeenPwned guide
        }
    }

    private fun loadSavedApiKeys() {
        // TODO: Load saved API keys from secure storage
        // For now, we'll just leave the fields empty
    }

    private fun saveApiKeys() {
        // TODO: Save API keys to secure storage
        // Show success message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}