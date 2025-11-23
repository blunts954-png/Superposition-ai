package com.superposition.ai.ui.photos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.superposition.ai.R

class PhotoAdapter(
    private val photos: List<PhotoOrganizerFragment.PhotoInfo>,
    private val onPhotoClick: (PhotoOrganizerFragment.PhotoInfo) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageViewPhoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        
        Glide.with(holder.itemView.context)
            .load(photo.uri)
            .thumbnail(0.1f)
            .centerCrop()
            .into(holder.imageView)
        
        holder.itemView.setOnClickListener {
            onPhotoClick(photo)
        }
    }

    override fun getItemCount() = photos.size
}

