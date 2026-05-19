package com.twoskoops707.sixdegrees.ui.results

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.twoskoops707.sixdegrees.databinding.ItemEmploymentBinding
import com.twoskoops707.sixdegrees.domain.model.Employment

class EmploymentAdapter : ListAdapter<Employment, EmploymentAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEmploymentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemEmploymentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(job: Employment) {
            binding.jobTitle.text = job.jobTitle.ifBlank { "Unknown Title" }
            binding.companyName.text = job.companyName.ifBlank { "Unknown Company" }
            val period = buildString {
                if (!job.startDate.isNullOrBlank()) append(job.startDate)
                if (!job.startDate.isNullOrBlank() || !job.endDate.isNullOrBlank()) append(" – ")
                append(if (job.isCurrent) "Present" else job.endDate ?: "")
            }
            binding.period.text = period
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Employment>() {
            override fun areItemsTheSame(a: Employment, b: Employment) =
                a.companyName == b.companyName && a.jobTitle == b.jobTitle
            override fun areContentsTheSame(a: Employment, b: Employment) = a == b
        }
    }
}
