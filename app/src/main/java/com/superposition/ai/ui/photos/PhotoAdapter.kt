package com.superposition.ai.ui.photos

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.superposition.ai.R

class PhotoAdapter(
    private val photos: List<PhotoOrganizerFragment.PhotoInfo>,
    private val onPhotoClick: (PhotoOrganizerFragment.PhotoInfo) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageViewPhoto)
        val categoryBadge: TextView? = itemView.findViewById(R.id.textViewCategory)
        val selectionOverlay: View? = itemView.findViewById(R.id.viewSelectionOverlay)
        val faceCountBadge: TextView? = itemView.findViewById(R.id.textViewFaceCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]

        // Load image with Glide (optimized for performance)
        Glide.with(holder.itemView.context)
            .load(photo.uri)
            .thumbnail(0.1f) // Show low-res thumbnail quickly
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .placeholder(R.color.colorSurface)
            .into(holder.imageView)

        // Show category badge if available
        holder.categoryBadge?.let { badge ->
            if (photo.category != "Uncategorized" && photo.category != "Other") {
                badge.visibility = View.VISIBLE
                badge.text = getCategoryEmoji(photo.category)
                badge.setBackgroundColor(getCategoryColor(photo.category))
            } else {
                badge.visibility = View.GONE
            }
        }

        // Show face count badge if has faces
        holder.faceCountBadge?.let { badge ->
            if (photo.hasFaces && photo.faceCount > 0) {
                badge.visibility = View.VISIBLE
                badge.text = "👤${if (photo.faceCount > 1) photo.faceCount.toString() else ""}"
            } else {
                badge.visibility = View.GONE
            }
        }

        // Selection overlay
        holder.selectionOverlay?.visibility = if (photo.isSelected) View.VISIBLE else View.GONE

        // Click handler
        holder.itemView.setOnClickListener {
            onPhotoClick(photo)
        }

        // Long click for selection (optional enhancement)
        holder.itemView.setOnLongClickListener {
            photo.isSelected = !photo.isSelected
            notifyItemChanged(position)
            true
        }
    }

    override fun getItemCount() = photos.size

    private fun getCategoryEmoji(category: String): String {
        return when (category) {
            "Selfies" -> "🤳"
            "People", "Group Photos" -> "👥"
            "Pets & Animals" -> "🐾"
            "Food" -> "🍔"
            "Nature" -> "🌲"
            "Travel" -> "✈️"
            "Vehicles" -> "🚗"
            "Sports" -> "⚽"
            "Events" -> "🎉"
            "Screenshots" -> "📱"
            "Documents" -> "📄"
            "Shopping" -> "🛍️"
            "Videos" -> "🎥"
            else -> "📸"
        }
    }

    private fun getCategoryColor(category: String): Int {
        return when (category) {
            "Selfies" -> Color.parseColor("#E91E63") // Pink
            "People", "Group Photos" -> Color.parseColor("#2196F3") // Blue
            "Pets & Animals" -> Color.parseColor("#FF9800") // Orange
            "Food" -> Color.parseColor("#FF5722") // Deep Orange
            "Nature" -> Color.parseColor("#4CAF50") // Green
            "Travel" -> Color.parseColor("#00BCD4") // Cyan
            "Vehicles" -> Color.parseColor("#607D8B") // Blue Grey
            "Sports" -> Color.parseColor("#9C27B0") // Purple
            "Events" -> Color.parseColor("#FFEB3B") // Yellow
            "Screenshots" -> Color.parseColor("#795548") // Brown
            "Documents" -> Color.parseColor("#3F51B5") // Indigo
            "Shopping" -> Color.parseColor("#E91E63") // Pink
            "Videos" -> Color.parseColor("#F44336") // Red
            else -> Color.parseColor("#9E9E9E") // Grey
        }
    }
}
