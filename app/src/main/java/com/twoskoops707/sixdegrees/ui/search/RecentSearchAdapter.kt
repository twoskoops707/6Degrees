package com.twoskoops707.sixdegrees.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.twoskoops707.sixdegrees.data.local.entity.OsintReportEntity
import com.twoskoops707.sixdegrees.databinding.ItemRecentSearchBinding
import java.text.SimpleDateFormat
import java.util.Locale

class RecentSearchAdapter(
    private val onItemClick: (OsintReportEntity) -> Unit
) : ListAdapter<OsintReportEntity, RecentSearchAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecentSearchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

        fun bind(report: OsintReportEntity) {
            binding.searchQuery.text = report.searchQuery
            binding.searchDate.text = dateFormat.format(report.generatedAt)
            binding.root.setOnClickListener { onItemClick(report) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<OsintReportEntity>() {
            override fun areItemsTheSame(a: OsintReportEntity, b: OsintReportEntity) = a.id == b.id
            override fun areContentsTheSame(a: OsintReportEntity, b: OsintReportEntity) = a == b
        }
    }
}
