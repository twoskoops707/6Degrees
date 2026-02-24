package com.example.a6degrees.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.a6degrees.data.ApiKeyManager
import com.example.a6degrees.databinding.FragmentApiSettingsBinding

class ApiSettingsFragment : Fragment() {

    private var _binding: FragmentApiSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var apiKeyManager: ApiKeyManager

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
        apiKeyManager = ApiKeyManager(requireContext())
        setupToolbar()
        setupSaveButton()
        setupGuideButtons()
        loadSavedApiKeys()
    }

    private fun setupToolbar() {
        binding.settingsToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupSaveButton() {
        binding.saveApiKeysButton.setOnClickListener {
            saveApiKeys()
        }
    }

    private fun setupGuideButtons() {
        binding.viewPdlGuideButton.setOnClickListener {
            openUrl("https://www.peopledatalabs.com/signup")
        }
        binding.viewClearbitGuideButton.setOnClickListener {
            openUrl("https://clearbit.com/docs")
        }
        binding.viewPiplGuideButton.setOnClickListener {
            openUrl("https://pipl.com/api/")
        }
        binding.viewBuiltwithGuideButton.setOnClickListener {
            openUrl("https://api.builtwith.com/")
        }
        binding.viewHunterGuideButton.setOnClickListener {
            openUrl("https://hunter.io/api")
        }
        binding.viewHibpGuideButton.setOnClickListener {
            openUrl("https://haveibeenpwned.com/API/v3")
        }
    }

    private fun loadSavedApiKeys() {
        binding.pdlApiKeyInput.setText(apiKeyManager.pdlKey)
        binding.clearbitApiKeyInput.setText(apiKeyManager.clearbitKey)
        binding.piplApiKeyInput.setText(apiKeyManager.piplKey)
        binding.builtwithApiKeyInput.setText(apiKeyManager.builtWithKey)
        binding.hunterApiKeyInput.setText(apiKeyManager.hunterKey)
        binding.hibpApiKeyInput.setText(apiKeyManager.hibpKey)
    }

    private fun saveApiKeys() {
        apiKeyManager.pdlKey = binding.pdlApiKeyInput.text.toString().trim()
        apiKeyManager.clearbitKey = binding.clearbitApiKeyInput.text.toString().trim()
        apiKeyManager.piplKey = binding.piplApiKeyInput.text.toString().trim()
        apiKeyManager.builtWithKey = binding.builtwithApiKeyInput.text.toString().trim()
        apiKeyManager.hunterKey = binding.hunterApiKeyInput.text.toString().trim()
        apiKeyManager.hibpKey = binding.hibpApiKeyInput.text.toString().trim()
        Toast.makeText(requireContext(), "API keys saved", Toast.LENGTH_SHORT).show()
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
