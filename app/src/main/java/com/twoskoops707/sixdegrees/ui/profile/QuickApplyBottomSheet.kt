package com.twoskoops707.sixdegrees.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.twoskoops707.sixdegrees.R
import com.twoskoops707.sixdegrees.data.UserProfile
import com.twoskoops707.sixdegrees.databinding.BottomSheetQuickApplyBinding
import com.twoskoops707.sixdegrees.databinding.ItemQuickApplyFieldBinding

class QuickApplyBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetQuickApplyBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetQuickApplyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val apiName = arguments?.getString(ARG_API_NAME) ?: ""
        val signupUrl = arguments?.getString(ARG_SIGNUP_URL) ?: ""
        val profile = UserProfile(
            firstName = arguments?.getString("firstName") ?: "",
            lastName = arguments?.getString("lastName") ?: "",
            email = arguments?.getString("email") ?: "",
            phone = arguments?.getString("phone") ?: "",
            address = arguments?.getString("address") ?: "",
            city = arguments?.getString("city") ?: "",
            state = arguments?.getString("state") ?: "",
            zip = arguments?.getString("zip") ?: "",
            country = arguments?.getString("country") ?: "US",
            company = arguments?.getString("company") ?: "",
            website = arguments?.getString("website") ?: "",
            ccNumber = arguments?.getString("ccNumber") ?: "",
            ccExpiry = arguments?.getString("ccExpiry") ?: "",
            ccCvv = arguments?.getString("ccCvv") ?: "",
            ccName = arguments?.getString("ccName") ?: ""
        )

        binding.tvSheetTitle.text = getString(R.string.quick_apply_title, apiName)

        val fields = profile.fieldsForApi(apiName)
        binding.rvFields.adapter = FieldAdapter(fields) { label, value ->
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(requireContext(), getString(R.string.copied_toast, value), Toast.LENGTH_SHORT).show()
        }

        if (signupUrl.isNotBlank()) {
            binding.btnOpenSignup.visibility = View.VISIBLE
            binding.btnOpenSignup.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(signupUrl)))
            }
        } else {
            binding.btnOpenSignup.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class FieldAdapter(
        private val fields: List<Pair<String, String>>,
        private val onCopy: (String, String) -> Unit
    ) : RecyclerView.Adapter<FieldAdapter.VH>() {

        inner class VH(val b: ItemQuickApplyFieldBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemQuickApplyFieldBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (label, value) = fields[position]
            holder.b.tvFieldLabel.text = label
            holder.b.tvFieldValue.text = value
            holder.b.root.setOnClickListener { onCopy(label, value) }
        }

        override fun getItemCount() = fields.size
    }

    companion object {
        const val TAG = "QuickApplySheet"
        private const val ARG_API_NAME = "api_name"
        private const val ARG_SIGNUP_URL = "signup_url"

        fun show(
            fragment: androidx.fragment.app.Fragment,
            apiName: String,
            signupUrl: String,
            profile: UserProfile
        ) {
            QuickApplyBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_API_NAME, apiName)
                    putString(ARG_SIGNUP_URL, signupUrl)
                    putString("firstName", profile.firstName)
                    putString("lastName", profile.lastName)
                    putString("email", profile.email)
                    putString("phone", profile.phone)
                    putString("address", profile.address)
                    putString("city", profile.city)
                    putString("state", profile.state)
                    putString("zip", profile.zip)
                    putString("country", profile.country)
                    putString("company", profile.company)
                    putString("website", profile.website)
                    putString("ccNumber", profile.ccNumber)
                    putString("ccExpiry", profile.ccExpiry)
                    putString("ccCvv", profile.ccCvv)
                    putString("ccName", profile.ccName)
                }
            }.show(fragment.childFragmentManager, TAG)
        }
    }
}
