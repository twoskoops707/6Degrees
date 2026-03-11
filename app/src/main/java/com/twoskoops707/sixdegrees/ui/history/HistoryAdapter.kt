package com.twoskoops707.sixdegrees.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.twoskoops707.sixdegrees.data.local.entity.OsintReportEntity
import com.twoskoops707.sixdegrees.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter(
    private val onItemClick: (OsintReportEntity) -> Unit
) : ListAdapter<OsintReportEntity, HistoryAdapter.ViewHolder>(DIFF) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

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
            val fields = report.searchQuery.split("|").mapNotNull {
                val p = it.split("=", limit = 2)
                if (p.size == 2) p[0].trim() to p[1].trim() else null
            }.toMap()

            val searchType = try {
                val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
                moshi.adapter<Map<String, String>>(type).fromJson(report.companiesJson)
                    ?.get("search_type") ?: inferType(fields, report.searchQuery)
            } catch (_: Exception) { inferType(fields, report.searchQuery) }

            val displayName = when {
                fields["name"]?.isNotBlank() == true -> fields["name"]!!
                fields["email"]?.isNotBlank() == true -> fields["email"]!!
                fields["phone"]?.isNotBlank() == true -> fields["phone"]!!
                fields["username"]?.isNotBlank() == true -> "@${fields["username"]}"
                fields.isEmpty() -> report.searchQuery
                else -> fields.values.firstOrNull() ?: report.searchQuery
            }
            binding.searchQuery.text = displayName

            val details = mutableListOf<String>()
            if (fields["name"]?.isNotBlank() == true) {
                fields["phone"]?.takeIf { it.isNotBlank() }?.let { details.add("☎ $it") }
                fields["email"]?.takeIf { it.isNotBlank() }?.let { details.add("✉ $it") }
                fields["username"]?.takeIf { it.isNotBlank() }?.let { details.add("@ $it") }
            }
            val city = fields["city"] ?: ""
            val state = fields["state"] ?: ""
            val loc = listOf(city, state).filter { it.isNotBlank() }.joinToString(", ")
            if (loc.isNotBlank()) details.add("📍 $loc")
            fields["dob"]?.takeIf { it.isNotBlank() }?.let { details.add("DOB: $it") }

            if (details.isNotEmpty()) {
                binding.searchSubtitle.text = details.joinToString("  ·  ")
                binding.searchSubtitle.visibility = View.VISIBLE
            } else {
                binding.searchSubtitle.visibility = View.GONE
            }

            binding.confidence.text = searchType.uppercase()
            binding.searchDate.text = dateFormat.format(report.generatedAt)
            binding.root.setOnClickListener { onItemClick(report) }
        }

        private fun inferType(fields: Map<String, String>, raw: String): String {
            return when {
                fields.containsKey("name") -> "person"
                fields.containsKey("email") -> "email"
                fields.containsKey("phone") -> "phone"
                fields.containsKey("username") -> "username"
                raw.contains("@") -> "email"
                raw.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) -> "ip"
                raw.contains(".") -> "domain"
                else -> "search"
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<OsintReportEntity>() {
            override fun areItemsTheSame(a: OsintReportEntity, b: OsintReportEntity) = a.id == b.id
            override fun areContentsTheSame(a: OsintReportEntity, b: OsintReportEntity) = a == b
        }
    }
}
