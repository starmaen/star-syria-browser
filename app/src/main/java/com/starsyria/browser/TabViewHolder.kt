package com.starsyria.browser

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val titleView: TextView = itemView.findViewById(R.id.tab_title)
    private val urlView: TextView = itemView.findViewById(R.id.tab_url)

    fun bind(tab: BrowserTab, onClick: () -> Unit) {
        titleView.text = if (tab.isIncognito) "🕵 ${tab.title}" else tab.title
        urlView.text = tab.url
        itemView.setOnClickListener { onClick() }
    }
}
