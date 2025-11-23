package com.superposition.ai.ui.security

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.superposition.ai.R

class SecurityAppsAdapter(
    private val apps: List<SecurityFragment.SecurityAppInfo>,
    private val onAppClick: (SecurityFragment.SecurityAppInfo) -> Unit
) : RecyclerView.Adapter<SecurityAppsAdapter.SecurityAppViewHolder>() {

    class SecurityAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconImageView: ImageView = itemView.findViewById(R.id.imageViewAppIcon)
        val nameTextView: TextView = itemView.findViewById(R.id.textViewAppName)
        val riskTextView: TextView = itemView.findViewById(R.id.textViewRiskLevel)
        val descriptionTextView: TextView = itemView.findViewById(R.id.textViewRiskDescription)
        val permissionsTextView: TextView = itemView.findViewById(R.id.textViewPermissions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SecurityAppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_security_app, parent, false)
        return SecurityAppViewHolder(view)
    }

    override fun onBindViewHolder(holder: SecurityAppViewHolder, position: Int) {
        val app = apps[position]
        
        holder.iconImageView.setImageDrawable(app.icon)
        holder.nameTextView.text = app.name
        holder.riskTextView.text = "Risk: ${app.riskLevel}%"
        holder.descriptionTextView.text = app.riskDescription
        
        val permissionsText = if (app.riskyPermissions.isNotEmpty()) {
            "Permissions: ${app.riskyPermissions.take(3).joinToString(", ")}"
        } else {
            "No risky permissions"
        }
        holder.permissionsTextView.text = permissionsText
        
        holder.itemView.setOnClickListener {
            onAppClick(app)
        }
    }

    override fun getItemCount() = apps.size
}

