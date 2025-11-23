package com.superposition.ai.ui.apps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.superposition.ai.R

class AppsAdapter(
    private val apps: List<AppsFragment.AppInfo>,
    private val onUninstallClick: (AppsFragment.AppInfo) -> Unit
) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconImageView: ImageView = itemView.findViewById(R.id.imageViewAppIcon)
        val nameTextView: TextView = itemView.findViewById(R.id.textViewAppName)
        val packageTextView: TextView = itemView.findViewById(R.id.textViewPackageName)
        val uninstallButton: TextView = itemView.findViewById(R.id.buttonUninstall)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        
        holder.iconImageView.setImageDrawable(app.icon)
        holder.nameTextView.text = app.name
        holder.packageTextView.text = app.packageName
        
        holder.uninstallButton.setOnClickListener {
            onUninstallClick(app)
        }
    }

    override fun getItemCount() = apps.size
}

