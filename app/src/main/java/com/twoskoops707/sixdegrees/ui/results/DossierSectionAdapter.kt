package com.twoskoops707.sixdegrees.ui.results

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.twoskoops707.sixdegrees.databinding.FragmentDossierSectionBinding

class DossierSectionAdapter(
    private val sections: List<Pair<String, List<Pair<String, String>>>>,
    private val populateSection: (LinearLayout, List<Pair<String, String>>) -> Unit
) : RecyclerView.Adapter<DossierSectionAdapter.SectionViewHolder>() {

    class SectionViewHolder(val binding: FragmentDossierSectionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding = FragmentDossierSectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        val container = holder.binding.dossierSectionContainer
        container.removeAllViews()
        populateSection(container, sections[position].second)
    }

    override fun getItemCount(): Int = sections.size
}
