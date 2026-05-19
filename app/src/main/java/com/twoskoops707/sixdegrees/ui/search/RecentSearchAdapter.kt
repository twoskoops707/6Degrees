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
            val fields = report.searchQuery.split("|").mapNotNull {
                val p = it.split("=", limit = 2)
                if (p.size == 2) p[0].trim() to p[1].trim() else null
            }.toMap()
            val displayName = when {
                fields["name"]?.isNotBlank() == true -> fields["name"]!!
                fields["subject"]?.isNotBlank() == true -> fields["subject"]!!
                fields["email"]?.isNotBlank() == true -> fields["email"]!!
                fields["phone"]?.isNotBlank() == true -> fields["phone"]!!
                fields["username"]?.isNotBlank() == true -> "@${fields["username"]}"
                fields.isEmpty() -> report.searchQuery
                else -> fields.values.firstOrNull() ?: report.searchQuery
            }
            binding.searchQuery.text = displayName
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
