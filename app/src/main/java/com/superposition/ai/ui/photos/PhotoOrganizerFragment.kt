package com.superposition.ai.ui.photos

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.superposition.ai.R
import com.superposition.ai.databinding.FragmentPhotoOrganizerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class PhotoOrganizerFragment : Fragment() {

    private var _binding: FragmentPhotoOrganizerBinding? = null
    private val binding get() = _binding!!

    private lateinit var photoAdapter: PhotoAdapter
    private val photosList = mutableListOf<PhotoInfo>()
    private val categorizedPhotos = mutableMapOf<String, MutableList<PhotoInfo>>()
    
    private val imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotoOrganizerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up RecyclerView
        photoAdapter = PhotoAdapter(photosList) { photoInfo ->
            // Handle photo click
        }

        binding.recyclerViewPhotos.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerViewPhotos.adapter = photoAdapter

        // Set up buttons
        binding.buttonScanPhotos.setOnClickListener {
            scanAndOrganizePhotos()
        }

        binding.buttonOrganize.setOnClickListener {
            organizePhotos()
        }

        binding.buttonFindDuplicates.setOnClickListener {
            findDuplicates()
        }
    }

    private fun scanAndOrganizePhotos() {
        binding.buttonScanPhotos.isEnabled = false
        binding.buttonScanPhotos.text = "Scanning..."
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
        binding.textViewStatus.text = "Scanning photos..."

        lifecycleScope.launch {
            val photos = withContext(Dispatchers.IO) {
                loadPhotosFromDevice()
            }

            binding.progressBar.max = photos.size
            binding.textViewStatus.text = "Analyzing ${photos.size} photos with AI..."

            // Analyze photos with ML Kit
            var analyzedCount = 0
            for (photo in photos) {
                withContext(Dispatchers.IO) {
                    analyzePhoto(photo)
                }
                analyzedCount++
                binding.progressBar.progress = analyzedCount
                binding.textViewStatus.text = "Analyzing $analyzedCount/${photos.size} photos..."
                delay(50) // Small delay for UI update
            }

            photosList.clear()
            photosList.addAll(photos)
            photoAdapter.notifyDataSetChanged()

            // Update categories
            updateCategoryViews()

            binding.progressBar.visibility = View.GONE
            binding.buttonScanPhotos.isEnabled = true
            binding.buttonScanPhotos.text = "🔍 Scan Photos"
            binding.buttonOrganize.visibility = View.VISIBLE
            binding.textViewStatus.text = "Found ${photos.size} photos. ${categorizedPhotos.size} categories detected."
        }
    }

    private fun loadPhotosFromDevice(): List<PhotoInfo> {
        val photos = mutableListOf<PhotoInfo>()
        val context = requireContext()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            var count = 0
            while (it.moveToNext() && count < 500) { // Limit to 500 photos for performance
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val path = it.getString(dataColumn)
                val dateAdded = it.getLong(dateColumn)
                val size = it.getLong(sizeColumn)

                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                photos.add(PhotoInfo(
                    id = id,
                    name = name,
                    path = path,
                    uri = uri,
                    dateAdded = dateAdded,
                    size = size,
                    category = "Uncategorized",
                    labels = emptyList()
                ))

                count++
            }
        }

        return photos
    }

    private suspend fun analyzePhoto(photo: PhotoInfo) {
        try {
            val imageFile = File(photo.path)
            if (!imageFile.exists()) return

            val bitmap = BitmapFactory.decodeFile(photo.path)
            val image = InputImage.fromBitmap(bitmap, 0)

            // Use ML Kit to label the image
            val labels = mutableListOf<String>()
            var confidence = 0f

            try {
                val result = suspendCancellableCoroutine<List<com.google.mlkit.vision.label.ImageLabel>> { continuation ->
                    imageLabeler.process(image)
                        .addOnSuccessListener { labels ->
                            continuation.resume(labels)
                        }
                        .addOnFailureListener { e ->
                            continuation.resumeWithException(e)
                        }
                }
                
                for (label in result) {
                    if (label.confidence > 0.5f) { // Only high confidence labels
                        labels.add(label.text)
                        confidence = label.confidence
                    }
                }
            } catch (e: Exception) {
                // ML Kit processing failed, skip
            }

            // Categorize based on labels
            val category = categorizePhoto(labels)

            photo.category = category
            photo.labels = labels
            photo.confidence = confidence

            // Add to categorized map
            if (!categorizedPhotos.containsKey(category)) {
                categorizedPhotos[category] = mutableListOf()
            }
            categorizedPhotos[category]?.add(photo)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun categorizePhoto(labels: List<String>): String {
        val labelText = labels.joinToString(" ").lowercase()

        return when {
            labelText.contains("person") || labelText.contains("people") || labelText.contains("human") -> "People"
            labelText.contains("dog") || labelText.contains("cat") || labelText.contains("pet") || labelText.contains("animal") -> "Pets & Animals"
            labelText.contains("food") || labelText.contains("meal") || labelText.contains("restaurant") -> "Food"
            labelText.contains("nature") || labelText.contains("landscape") || labelText.contains("mountain") || labelText.contains("tree") -> "Nature"
            labelText.contains("car") || labelText.contains("vehicle") || labelText.contains("automobile") -> "Vehicles"
            labelText.contains("building") || labelText.contains("architecture") || labelText.contains("city") -> "Architecture"
            labelText.contains("sport") || labelText.contains("game") || labelText.contains("play") -> "Sports"
            labelText.contains("text") || labelText.contains("document") || labelText.contains("screen") -> "Screenshots"
            else -> "Other"
        }
    }

    private fun updateCategoryViews() {
        binding.textViewPeopleCount.text = "${categorizedPhotos["People"]?.size ?: 0}"
        binding.textViewPetsCount.text = "${categorizedPhotos["Pets & Animals"]?.size ?: 0}"
        binding.textViewFoodCount.text = "${categorizedPhotos["Food"]?.size ?: 0}"
        binding.textViewNatureCount.text = "${categorizedPhotos["Nature"]?.size ?: 0}"
        binding.textViewOtherCount.text = "${categorizedPhotos["Other"]?.size ?: 0}"
    }

    private fun organizePhotos() {
        binding.buttonOrganize.isEnabled = false
        binding.buttonOrganize.text = "Organizing..."
        binding.progressBar.visibility = View.VISIBLE
        binding.textViewStatus.text = "Organizing photos into folders..."

        lifecycleScope.launch {
            var organizedCount = 0

            withContext(Dispatchers.IO) {
                for ((category, photos) in categorizedPhotos) {
                    if (category == "Uncategorized" || category == "Other") continue

                    val categoryFolder = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "SuperpositionAI/$category"
                    )
                    categoryFolder.mkdirs()

                    for (photo in photos) {
                        try {
                            val sourceFile = File(photo.path)
                            if (sourceFile.exists()) {
                                val destFile = File(categoryFolder, photo.name)
                                sourceFile.copyTo(destFile, overwrite = false)
                                organizedCount++
                            }
                        } catch (e: Exception) {
                            // Skip if file already exists or can't be copied
                        }
                    }
                }
            }

            Toast.makeText(
                requireContext(),
                "Organized $organizedCount photos into folders",
                Toast.LENGTH_LONG
            ).show()

            binding.buttonOrganize.text = "Organize Photos"
            binding.buttonOrganize.isEnabled = true
            binding.progressBar.visibility = View.GONE
            binding.textViewStatus.text = "Organization complete!"
        }
    }

    private fun findDuplicates() {
        binding.buttonFindDuplicates.isEnabled = false
        binding.buttonFindDuplicates.text = "Finding duplicates..."
        binding.progressBar.visibility = View.VISIBLE
        binding.textViewStatus.text = "Scanning for duplicate photos..."

        lifecycleScope.launch {
            val duplicates = withContext(Dispatchers.IO) {
                findDuplicatePhotos()
            }

            Toast.makeText(
                requireContext(),
                "Found ${duplicates.size} duplicate photos",
                Toast.LENGTH_LONG
            ).show()

            binding.buttonFindDuplicates.text = "Find Duplicates"
            binding.buttonFindDuplicates.isEnabled = true
            binding.progressBar.visibility = View.GONE
            binding.textViewStatus.text = "Found ${duplicates.size} duplicates"
        }
    }

    private suspend fun findDuplicatePhotos(): List<Pair<PhotoInfo, PhotoInfo>> {
        val duplicates = mutableListOf<Pair<PhotoInfo, PhotoInfo>>()
        val hashMap = mutableMapOf<String, PhotoInfo>()

        for (photo in photosList) {
            try {
                val hash = calculateFileHash(File(photo.path))
                if (hashMap.containsKey(hash)) {
                    duplicates.add(Pair(hashMap[hash]!!, photo))
                } else {
                    hashMap[hash] = photo
                }
            } catch (e: Exception) {
                // Skip if can't read file
            }
        }

        return duplicates
    }

    private fun calculateFileHash(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        imageLabeler.close()
        _binding = null
    }

    data class PhotoInfo(
        val id: Long,
        val name: String,
        val path: String,
        val uri: Uri,
        val dateAdded: Long,
        val size: Long,
        var category: String,
        var labels: List<String>,
        var confidence: Float = 0f
    )
}


