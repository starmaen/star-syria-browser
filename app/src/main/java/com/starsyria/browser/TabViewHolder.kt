package com.starsyria.browser

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    // قمنا بتعديل الأسماء لتطابق ما يبحث عنه الكود لديك
    private val urlView: TextView = view.findViewById(R.id.tab_url)
    private val closeBtn: ImageButton = view.findViewById(R.id.tab_close)

    fun bind(tab: BrowserTab, onClick: () -> Unit, onClose: () -> Unit) {
        urlView.text = tab.title
        itemView.setOnClickListener { onClick() }
        closeBtn.setOnClickListener { onClose() }
    }
}
