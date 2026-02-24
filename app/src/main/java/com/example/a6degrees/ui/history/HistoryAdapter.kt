package com.example.a6degrees.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.a6degrees.data.local.entity.OsintReportEntity
import com.example.a6degrees.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter(
    private val onItemClick: (OsintReportEntity) -> Unit
) : ListAdapter<OsintReportEntity, HistoryAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US)

        fun bind(report: OsintReportEntity) {
            binding.searchQuery.text = report.searchQuery
            binding.searchDate.text = dateFormat.format(report.generatedAt)
            binding.confidence.text = "Confidence: ${(report.confidenceScore * 100).toInt()}%"
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
