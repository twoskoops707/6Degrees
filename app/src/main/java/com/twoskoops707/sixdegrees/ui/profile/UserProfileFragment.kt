package com.twoskoops707.sixdegrees.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.twoskoops707.sixdegrees.data.UserProfile
import com.twoskoops707.sixdegrees.data.UserProfileManager
import com.twoskoops707.sixdegrees.databinding.FragmentUserProfileBinding

class UserProfileFragment : Fragment() {

    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var profileManager: UserProfileManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        profileManager = UserProfileManager(requireContext())

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        loadProfile()

        binding.btnSaveProfile.setOnClickListener { saveProfile() }
    }

    private fun loadProfile() {
        val p = profileManager.load()
        binding.etFirstName.setText(p.firstName)
        binding.etLastName.setText(p.lastName)
        binding.etEmail.setText(p.email)
        binding.etPhone.setText(p.phone)
        binding.etAddress.setText(p.address)
        binding.etCity.setText(p.city)
        binding.etState.setText(p.state)
        binding.etZip.setText(p.zip)
        binding.etCountry.setText(p.country)
        binding.etCompany.setText(p.company)
        binding.etWebsite.setText(p.website)
        binding.etCcNumber.setText(p.ccNumber)
        binding.etCcExpiry.setText(p.ccExpiry)
        binding.etCcCvv.setText(p.ccCvv)
        binding.etCcName.setText(p.ccName)
    }

    private fun saveProfile() {
        val profile = UserProfile(
            firstName = binding.etFirstName.text?.toString()?.trim() ?: "",
            lastName = binding.etLastName.text?.toString()?.trim() ?: "",
            email = binding.etEmail.text?.toString()?.trim() ?: "",
            phone = binding.etPhone.text?.toString()?.trim() ?: "",
            address = binding.etAddress.text?.toString()?.trim() ?: "",
            city = binding.etCity.text?.toString()?.trim() ?: "",
            state = binding.etState.text?.toString()?.trim() ?: "",
            zip = binding.etZip.text?.toString()?.trim() ?: "",
            country = binding.etCountry.text?.toString()?.trim().takeIf { !it.isNullOrBlank() } ?: "US",
            company = binding.etCompany.text?.toString()?.trim() ?: "",
            website = binding.etWebsite.text?.toString()?.trim() ?: "",
            ccNumber = binding.etCcNumber.text?.toString()?.trim() ?: "",
            ccExpiry = binding.etCcExpiry.text?.toString()?.trim() ?: "",
            ccCvv = binding.etCcCvv.text?.toString()?.trim() ?: "",
            ccName = binding.etCcName.text?.toString()?.trim() ?: ""
        )
        profileManager.save(profile)
        Toast.makeText(requireContext(), getString(com.twoskoops707.sixdegrees.R.string.profile_saved_toast), Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
