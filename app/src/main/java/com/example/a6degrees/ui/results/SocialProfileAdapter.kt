package com.example.a6degrees.ui.results

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.a6degrees.databinding.ItemSocialProfileBinding
import com.example.a6degrees.domain.model.SocialProfile

class SocialProfileAdapter : ListAdapter<SocialProfile, SocialProfileAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSocialProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSocialProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(profile: SocialProfile) {
            binding.platform.text = profile.platform
            binding.username.text = profile.username.ifBlank { profile.url ?: "" }
            binding.root.setOnClickListener {
                profile.url?.let { url ->
                    binding.root.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SocialProfile>() {
            override fun areItemsTheSame(a: SocialProfile, b: SocialProfile) = a.url == b.url
            override fun areContentsTheSame(a: SocialProfile, b: SocialProfile) = a == b
        }
    }
}
