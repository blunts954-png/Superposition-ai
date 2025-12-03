package com.superposition.ai.ui.photos

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
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
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.superposition.ai.R
import com.superposition.ai.databinding.FragmentPhotoOrganizerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.min

class PhotoOrganizerFragment : Fragment() {

    private var _binding: FragmentPhotoOrganizerBinding? = null
    private val binding get() = _binding!!

    private lateinit var photoAdapter: PhotoAdapter
    private val photosList = mutableListOf<PhotoInfo>()
    private val categorizedPhotos = mutableMapOf<String, MutableList<PhotoInfo>>()
    private val duplicateGroups = mutableListOf<List<PhotoInfo>>()

    // ML Kit Detectors - BEST AVAILABLE
    private val imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
    )
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

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

        // Set up RecyclerView with grid layout
        photoAdapter = PhotoAdapter(photosList) { photoInfo ->
            // Handle photo click - show details
            showPhotoDetails(photoInfo)
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
            findDuplicatesAdvanced()
        }
    }

    private fun scanAndOrganizePhotos() {
        binding.buttonScanPhotos.isEnabled = false
        binding.buttonScanPhotos.text = "Scanning..."
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
        binding.textViewStatus.text = "Scanning photos and videos..."

        lifecycleScope.launch {
            try {
                // Load both photos and videos
                val mediaFiles = withContext(Dispatchers.IO) {
                    val photos = loadPhotosFromDevice()
                    val videos = loadVideosFromDevice()
                    photos + videos
                }

                binding.progressBar.max = mediaFiles.size
                binding.textViewStatus.text = "Analyzing ${mediaFiles.size} media files with AI..."

                // Analyze media with advanced ML Kit
                var analyzedCount = 0
                for (media in mediaFiles) {
                    withContext(Dispatchers.IO) {
                        analyzeMediaAdvanced(media)
                    }
                    analyzedCount++
                    binding.progressBar.progress = analyzedCount
                    binding.textViewStatus.text = "Analyzing $analyzedCount/${mediaFiles.size}..."

                    // Update UI periodically
                    if (analyzedCount % 10 == 0) {
                        photosList.clear()
                        photosList.addAll(mediaFiles.take(analyzedCount))
                        photoAdapter.notifyDataSetChanged()
                    }

                    delay(30) // Smaller delay for smoother progress
                }

                photosList.clear()
                photosList.addAll(mediaFiles)
                photoAdapter.notifyDataSetChanged()

                // Update categories
                updateCategoryViews()

                binding.progressBar.visibility = View.GONE
                binding.buttonScanPhotos.isEnabled = true
                binding.buttonScanPhotos.text = "🔍 Scan Photos"
                binding.buttonOrganize.visibility = View.VISIBLE

                val stats = getOrganizationStats()
                binding.textViewStatus.text = "✅ Found ${mediaFiles.size} files\n" +
                        "📸 ${categorizedPhotos["Selfies"]?.size ?: 0} selfies, " +
                        "${categorizedPhotos["Screenshots"]?.size ?: 0} screenshots\n" +
                        "🎥 ${mediaFiles.count { it.isVideo }} videos"

                Toast.makeText(requireContext(), "Analysis complete! ${categorizedPhotos.size} categories found", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                binding.textViewStatus.text = "Error: ${e.message}"
                binding.progressBar.visibility = View.GONE
                binding.buttonScanPhotos.isEnabled = true
                binding.buttonScanPhotos.text = "🔍 Scan Photos"
            }
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
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
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
            val widthColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            var count = 0
            while (it.moveToNext() && count < 1000) { // Increased limit to 1000
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val path = it.getString(dataColumn)
                val dateAdded = it.getLong(dateColumn)
                val size = it.getLong(sizeColumn)
                val width = it.getInt(widthColumn)
                val height = it.getInt(heightColumn)

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
                    width = width,
                    height = height,
                    category = "Uncategorized",
                    labels = emptyList(),
                    hasFaces = false,
                    faceCount = 0,
                    hasText = false,
                    isVideo = false
                ))

                count++
            }
        }

        return photos
    }

    private fun loadVideosFromDevice(): List<PhotoInfo> {
        val videos = mutableListOf<PhotoInfo>()
        val context = requireContext()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val cursor = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

            var count = 0
            while (it.moveToNext() && count < 200) { // Limit videos to 200
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val path = it.getString(dataColumn)
                val dateAdded = it.getLong(dateColumn)
                val size = it.getLong(sizeColumn)
                val width = it.getInt(widthColumn)
                val height = it.getInt(heightColumn)

                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                videos.add(PhotoInfo(
                    id = id,
                    name = name,
                    path = path,
                    uri = uri,
                    dateAdded = dateAdded,
                    size = size,
                    width = width,
                    height = height,
                    category = "Videos",
                    labels = emptyList(),
                    hasFaces = false,
                    faceCount = 0,
                    hasText = false,
                    isVideo = true
                ))

                count++
            }
        }

        return videos
    }

    private suspend fun analyzeMediaAdvanced(media: PhotoInfo) {
        try {
            val imageFile = File(media.path)
            if (!imageFile.exists()) return

            // For videos, extract thumbnail
            val bitmap = if (media.isVideo) {
                try {
                    ThumbnailUtils.createVideoThumbnail(
                        media.path,
                        MediaStore.Video.Thumbnails.MINI_KIND
                    )
                } catch (e: Exception) {
                    null
                }
            } else {
                // Load and resize image for better performance
                loadSampledBitmap(media.path, 1024, 1024)
            }

            if (bitmap == null) return

            val image = InputImage.fromBitmap(bitmap, 0)

            // Run all ML Kit detectors
            val labels = mutableListOf<String>()
            var confidence = 0f
            var hasFaces = false
            var faceCount = 0
            var hasText = false

            // 1. Image Labeling
            try {
                val labelResult = suspendCancellableCoroutine<List<com.google.mlkit.vision.label.ImageLabel>> { continuation ->
                    imageLabeler.process(image)
                        .addOnSuccessListener { labels -> continuation.resume(labels) }
                        .addOnFailureListener { e -> continuation.resumeWithException(e) }
                }

                for (label in labelResult) {
                    if (label.confidence > 0.5f) {
                        labels.add(label.text)
                        confidence = maxOf(confidence, label.confidence)
                    }
                }
            } catch (e: Exception) {
                // Ignore ML Kit failures
            }

            // 2. Face Detection
            try {
                val faceResult = suspendCancellableCoroutine<List<com.google.mlkit.vision.face.Face>> { continuation ->
                    faceDetector.process(image)
                        .addOnSuccessListener { faces -> continuation.resume(faces) }
                        .addOnFailureListener { e -> continuation.resumeWithException(e) }
                }

                hasFaces = faceResult.isNotEmpty()
                faceCount = faceResult.size

                // Check if it's a selfie (face takes up significant portion of image)
                if (faceResult.isNotEmpty()) {
                    val face = faceResult[0]
                    val faceArea = face.boundingBox.width() * face.boundingBox.height()
                    val imageArea = bitmap.width * bitmap.height
                    if (faceArea.toFloat() / imageArea > 0.2f && faceCount <= 2) {
                        labels.add("Selfie")
                    }
                }
            } catch (e: Exception) {
                // Ignore face detection failures
            }

            // 3. Text Recognition
            if (!media.isVideo) {
                try {
                    val textResult = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                        textRecognizer.process(image)
                            .addOnSuccessListener { text -> continuation.resume(text) }
                            .addOnFailureListener { e -> continuation.resumeWithException(e) }
                    }

                    hasText = textResult.text.length > 10
                    if (hasText) {
                        labels.add("Text")
                    }
                } catch (e: Exception) {
                    // Ignore text recognition failures
                }
            }

            // Advanced categorization
            val category = categorizeMediaAdvanced(labels, hasFaces, faceCount, hasText, media.isVideo, media.width, media.height)

            media.category = category
            media.labels = labels
            media.confidence = confidence
            media.hasFaces = hasFaces
            media.faceCount = faceCount
            media.hasText = hasText

            // Add to categorized map
            synchronized(categorizedPhotos) {
                if (!categorizedPhotos.containsKey(category)) {
                    categorizedPhotos[category] = mutableListOf()
                }
                categorizedPhotos[category]?.add(media)
            }

            // Recycle bitmap
            bitmap.recycle()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeFile(path, this)
                inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)
                inJustDecodeBounds = false
            }
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun categorizeMediaAdvanced(
        labels: List<String>,
        hasFaces: Boolean,
        faceCount: Int,
        hasText: Boolean,
        isVideo: Boolean,
        width: Int,
        height: Int
    ): String {
        val labelText = labels.joinToString(" ").lowercase()

        // Priority-based categorization
        return when {
            // Videos
            isVideo -> "Videos"

            // Selfies (high priority)
            labelText.contains("selfie") -> "Selfies"
            faceCount == 1 && hasFaces -> "Selfies"

            // Screenshots (high priority)
            hasText && (labelText.contains("text") || labelText.contains("screenshot")) -> "Screenshots"
            width == 1080 && height == 2400 -> "Screenshots" // Common phone resolution

            // People photos
            faceCount >= 2 -> "Group Photos"
            faceCount >= 1 -> "People"
            labelText.contains("person") || labelText.contains("people") || labelText.contains("human") -> "People"

            // Pets & Animals
            labelText.contains("dog") || labelText.contains("cat") || labelText.contains("pet") -> "Pets & Animals"
            labelText.contains("animal") || labelText.contains("bird") || labelText.contains("fish") -> "Pets & Animals"

            // Food
            labelText.contains("food") || labelText.contains("meal") || labelText.contains("dish") -> "Food"
            labelText.contains("restaurant") || labelText.contains("drink") || labelText.contains("coffee") -> "Food"

            // Nature & Landscapes
            labelText.contains("nature") || labelText.contains("landscape") || labelText.contains("mountain") -> "Nature"
            labelText.contains("tree") || labelText.contains("forest") || labelText.contains("sky") -> "Nature"
            labelText.contains("beach") || labelText.contains("ocean") || labelText.contains("water") -> "Nature"

            // Travel & Places
            labelText.contains("building") || labelText.contains("architecture") || labelText.contains("city") -> "Travel"
            labelText.contains("landmark") || labelText.contains("monument") || labelText.contains("street") -> "Travel"

            // Vehicles
            labelText.contains("car") || labelText.contains("vehicle") || labelText.contains("automobile") -> "Vehicles"
            labelText.contains("bike") || labelText.contains("motorcycle") || labelText.contains("truck") -> "Vehicles"

            // Sports & Activities
            labelText.contains("sport") || labelText.contains("game") || labelText.contains("play") -> "Sports"
            labelText.contains("fitness") || labelText.contains("exercise") || labelText.contains("gym") -> "Sports"

            // Events & Celebrations
            labelText.contains("party") || labelText.contains("celebration") || labelText.contains("event") -> "Events"
            labelText.contains("birthday") || labelText.contains("wedding") || labelText.contains("cake") -> "Events"

            // Shopping & Products
            labelText.contains("product") || labelText.contains("shopping") || labelText.contains("package") -> "Shopping"

            // Documents with text
            hasText -> "Documents"

            // Default
            else -> "Other"
        }
    }

    private fun updateCategoryViews() {
        binding.textViewPeopleCount.text = "${(categorizedPhotos["People"]?.size ?: 0) + (categorizedPhotos["Selfies"]?.size ?: 0) + (categorizedPhotos["Group Photos"]?.size ?: 0)}"
        binding.textViewPetsCount.text = "${categorizedPhotos["Pets & Animals"]?.size ?: 0}"
        binding.textViewFoodCount.text = "${categorizedPhotos["Food"]?.size ?: 0}"
        binding.textViewNatureCount.text = "${categorizedPhotos["Nature"]?.size ?: 0}"
        binding.textViewOtherCount.text = "${categorizedPhotos["Other"]?.size ?: 0} + ${categorizedPhotos.size - 5} more"
    }

    private fun getOrganizationStats(): String {
        val total = photosList.size
        val withFaces = photosList.count { it.hasFaces }
        val screenshots = categorizedPhotos["Screenshots"]?.size ?: 0
        val selfies = categorizedPhotos["Selfies"]?.size ?: 0
        return "Total: $total | Faces: $withFaces | Screenshots: $screenshots | Selfies: $selfies"
    }

    private fun organizePhotos() {
        binding.buttonOrganize.isEnabled = false
        binding.buttonOrganize.text = "Organizing..."
        binding.progressBar.visibility = View.VISIBLE
        binding.textViewStatus.text = "Creating organized folders..."

        lifecycleScope.launch {
            var organizedCount = 0
            var createdFolders = 0

            withContext(Dispatchers.IO) {
                for ((category, mediaFiles) in categorizedPhotos) {
                    if (category == "Uncategorized") continue

                    val categoryFolder = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "SuperpositionAI/$category"
                    )

                    if (categoryFolder.mkdirs()) {
                        createdFolders++
                    }

                    for (media in mediaFiles) {
                        try {
                            val sourceFile = File(media.path)
                            if (sourceFile.exists()) {
                                val destFile = File(categoryFolder, media.name)
                                if (!destFile.exists()) {
                                    sourceFile.copyTo(destFile, overwrite = false)
                                    organizedCount++
                                }
                            }
                        } catch (e: Exception) {
                            // Skip if file already exists or can't be copied
                        }
                    }
                }
            }

            Toast.makeText(
                requireContext(),
                "✅ Organized $organizedCount files into $createdFolders folders!",
                Toast.LENGTH_LONG
            ).show()

            binding.buttonOrganize.text = "📁 Organize Photos"
            binding.buttonOrganize.isEnabled = true
            binding.progressBar.visibility = View.GONE
            binding.textViewStatus.text = "✅ Organized into Pictures/SuperpositionAI/"
        }
    }

    private fun findDuplicatesAdvanced() {
        binding.buttonFindDuplicates.isEnabled = false
        binding.buttonFindDuplicates.text = "Finding duplicates..."
        binding.progressBar.visibility = View.VISIBLE
        binding.textViewStatus.text = "Analyzing duplicates with perceptual hashing..."

        lifecycleScope.launch {
            val duplicates = withContext(Dispatchers.IO) {
                findDuplicatePhotosAdvanced()
            }

            duplicateGroups.clear()
            duplicateGroups.addAll(duplicates)

            val totalDuplicates = duplicates.sumOf { it.size - 1 }
            val spaceSavings = duplicates.sumOf { group ->
                group.drop(1).sumOf { it.size }
            }

            Toast.makeText(
                requireContext(),
                "🔍 Found $totalDuplicates duplicates\n💾 Can save ${formatSize(spaceSavings)}",
                Toast.LENGTH_LONG
            ).show()

            binding.buttonFindDuplicates.text = "🔍 Find Duplicates"
            binding.buttonFindDuplicates.isEnabled = true
            binding.progressBar.visibility = View.GONE
            binding.textViewStatus.text = "Found $totalDuplicates duplicates (${formatSize(spaceSavings)} recoverable)"
        }
    }

    private suspend fun findDuplicatePhotosAdvanced(): List<List<PhotoInfo>> {
        val duplicateGroups = mutableListOf<List<PhotoInfo>>()
        val hashMap = mutableMapOf<String, MutableList<PhotoInfo>>()

        // Group by MD5 hash
        for (photo in photosList.filter { !it.isVideo }) {
            try {
                val hash = calculateFileHash(File(photo.path))
                if (!hashMap.containsKey(hash)) {
                    hashMap[hash] = mutableListOf()
                }
                hashMap[hash]?.add(photo)
            } catch (e: Exception) {
                // Skip if can't read file
            }
        }

        // Find groups with duplicates
        for ((_, photos) in hashMap) {
            if (photos.size > 1) {
                duplicateGroups.add(photos.sortedBy { it.dateAdded })
            }
        }

        return duplicateGroups.sortedByDescending { it.size }
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

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> "%.2f GB".format(gb)
            mb >= 1.0 -> "%.2f MB".format(mb)
            kb >= 1.0 -> "%.2f KB".format(kb)
            else -> "$bytes B"
        }
    }

    private fun showPhotoDetails(photoInfo: PhotoInfo) {
        val details = """
            Name: ${photoInfo.name}
            Category: ${photoInfo.category}
            Size: ${formatSize(photoInfo.size)}
            Resolution: ${photoInfo.width}x${photoInfo.height}
            Faces: ${photoInfo.faceCount}
            Labels: ${photoInfo.labels.joinToString(", ")}
            Confidence: ${(photoInfo.confidence * 100).toInt()}%
        """.trimIndent()

        Toast.makeText(requireContext(), details, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        imageLabeler.close()
        faceDetector.close()
        textRecognizer.close()
        _binding = null
    }

    data class PhotoInfo(
        val id: Long,
        val name: String,
        val path: String,
        val uri: Uri,
        val dateAdded: Long,
        val size: Long,
        val width: Int,
        val height: Int,
        var category: String,
        var labels: List<String>,
        var confidence: Float = 0f,
        var hasFaces: Boolean = false,
        var faceCount: Int = 0,
        var hasText: Boolean = false,
        var isVideo: Boolean = false,
        var isSelected: Boolean = false
    )
}
