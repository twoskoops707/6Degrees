package com.twoskoops707.sixdegrees.ui.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.databinding.FragmentWizardBinding

class WizardFragment : Fragment() {

    private var _binding: FragmentWizardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWizardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.tilePerson.setOnClickListener { navigateToSearch("person") }
        binding.tileEmail.setOnClickListener { navigateToSearch("email") }
        binding.tilePhone.setOnClickListener { navigateToSearch("phone") }
        binding.tileUsername.setOnClickListener { navigateToSearch("username") }
        binding.tileIp.setOnClickListener { navigateToSearch("ip") }
        binding.tileCompany.setOnClickListener { navigateToSearch("company") }
        binding.tileImage.setOnClickListener { navigateToSearch("image") }
        binding.tileDorkBuilder.setOnClickListener {
            findNavController().navigate(R.id.action_wizard_to_dork_builder)
        }
        binding.tileSetup.setOnClickListener {
            findNavController().navigate(R.id.action_wizard_to_api_settings)
        }
    }

    private fun navigateToSearch(type: String) {
        val bundle = Bundle().apply { putString("searchType", type) }
        findNavController().navigate(R.id.action_wizard_to_search, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
