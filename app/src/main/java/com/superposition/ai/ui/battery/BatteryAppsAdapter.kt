package com.superposition.ai.ui.battery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.superposition.ai.R

class BatteryAppsAdapter(
    private val apps: List<BatteryFragment.BatteryAppInfo>,
    private val onOptimizeClick: (BatteryFragment.BatteryAppInfo) -> Unit
) : RecyclerView.Adapter<BatteryAppsAdapter.BatteryAppViewHolder>() {

    class BatteryAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconImageView: ImageView = itemView.findViewById(R.id.imageViewAppIcon)
        val nameTextView: TextView = itemView.findViewById(R.id.textViewAppName)
        val usageTextView: TextView = itemView.findViewById(R.id.textViewBatteryUsage)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBarBatteryUsage)
        val optimizeButton: TextView = itemView.findViewById(R.id.buttonOptimize)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BatteryAppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_battery_app, parent, false)
        return BatteryAppViewHolder(view)
    }

    override fun onBindViewHolder(holder: BatteryAppViewHolder, position: Int) {
        val app = apps[position]
        
        holder.iconImageView.setImageDrawable(app.icon)
        holder.nameTextView.text = app.name
        holder.usageTextView.text = "${app.batteryUsage}%"
        holder.progressBar.progress = app.batteryUsage
        
        holder.optimizeButton.setOnClickListener {
            onOptimizeClick(app)
        }
    }

    override fun getItemCount() = apps.size
}

