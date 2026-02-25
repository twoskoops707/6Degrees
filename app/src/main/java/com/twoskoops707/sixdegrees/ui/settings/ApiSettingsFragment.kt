package com.twoskoops707.sixdegrees.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.data.ApiKeyManager
import com.twoskoops707.sixdegrees.data.UserProfileManager
import com.twoskoops707.sixdegrees.databinding.FragmentApiSettingsBinding
import com.twoskoops707.sixdegrees.ui.profile.QuickApplyBottomSheet

class ApiSettingsFragment : Fragment() {

    private var _binding: FragmentApiSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var profileManager: UserProfileManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentApiSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        apiKeyManager = ApiKeyManager(requireContext())
        profileManager = UserProfileManager(requireContext())

        binding.settingsToolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_api_settings_to_profile)
        }

        loadSavedApiKeys()
        binding.saveApiKeysButton.setOnClickListener { saveApiKeys() }

        setupQuickApplyButtons()
    }

    private fun setupQuickApplyButtons() {
        data class ApiEntry(val apiName: String, val signupUrl: String)

        val entries = mapOf(
            binding.btnQuickApplyHibp to ApiEntry("HaveIBeenPwned", "https://haveibeenpwned.com/API/Key"),
            binding.btnQuickApplyHunter to ApiEntry("Hunter.io", "https://hunter.io/users/sign_up"),
            binding.btnQuickApplyPdl to ApiEntry("People Data Labs", "https://www.peopledatalabs.com/signup"),
            binding.btnQuickApplyNumverify to ApiEntry("Numverify", "https://numverify.com/product"),
            binding.btnQuickApplyShodan to ApiEntry("Shodan", "https://account.shodan.io/register"),
            binding.btnQuickApplyPipl to ApiEntry("Pipl", "https://pipl.com/api/")
        )

        entries.forEach { (button, entry) ->
            button.setOnClickListener {
                val profile = profileManager.load()
                if (!profileManager.hasProfile()) {
                    Toast.makeText(requireContext(), "Set up your profile first — tap Edit Profile", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                QuickApplyBottomSheet.show(this, entry.apiName, entry.signupUrl, profile)
            }
        }
    }

    private fun loadSavedApiKeys() {
        binding.hibpApiKeyInput.setText(apiKeyManager.hibpKey)
        binding.hunterApiKeyInput.setText(apiKeyManager.hunterKey)
        binding.pdlApiKeyInput.setText(apiKeyManager.pdlKey)
        binding.numverifyApiKeyInput.setText(apiKeyManager.numverifyKey)
        binding.shodanApiKeyInput.setText(apiKeyManager.shodanKey)
        binding.piplApiKeyInput.setText(apiKeyManager.piplKey)
    }

    private fun saveApiKeys() {
        apiKeyManager.hibpKey = binding.hibpApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.hunterKey = binding.hunterApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.pdlKey = binding.pdlApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.numverifyKey = binding.numverifyApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.shodanKey = binding.shodanApiKeyInput.text?.toString()?.trim() ?: ""
        apiKeyManager.piplKey = binding.piplApiKeyInput.text?.toString()?.trim() ?: ""
        Toast.makeText(requireContext(), "API keys saved", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
