package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class VaultRepository(private val context: Context) {
    private val database = VaultDatabase.getDatabase(context)
    private val folderDao = database.folderDao()
    private val mediaItemDao = database.mediaItemDao()

    private val vaultDir = File(context.filesDir, "vault").apply {
        if (!exists()) mkdirs()
    }
    private val thumbnailDir = File(context.cacheDir, "thumbnails").apply {
        if (!exists()) mkdirs()
    }
    private val tempShareDir = File(context.cacheDir, "temp_share").apply {
        if (!exists()) mkdirs()
    }

    private val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)

    // --- Passcode Management (SHA-256 + Salt) ---
    fun isPasscodeSet(): Boolean {
        return prefs.contains("passcode_hash")
    }

    fun savePasscode(passcode: String): Boolean {
        val salt = UUID.randomUUID().toString()
        val hash = hashPasscode(passcode, salt)
        return prefs.edit()
            .putString("passcode_salt", salt)
            .putString("passcode_hash", hash)
            .commit()
    }

    fun verifyPasscode(passcode: String): Boolean {
        val salt = prefs.getString("passcode_salt", null) ?: return false
        val savedHash = prefs.getString("passcode_hash", null) ?: return false
        return hashPasscode(passcode, salt) == savedHash
    }

    fun resetPasscode() {
        prefs.edit()
            .remove("passcode_salt")
            .remove("passcode_hash")
            .apply()
    }

    private fun hashPasscode(passcode: String, salt: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val text = passcode + salt
            val hashBytes = digest.digest(text.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    // --- Folder Operations ---
    fun getFoldersByType(type: String): Flow<List<Folder>> = folderDao.getFoldersByType(type)
    suspend fun getFolderById(id: Long): Folder? = folderDao.getFolderById(id)
    suspend fun insertFolder(folder: Folder): Long = folderDao.insertFolder(folder)
    suspend fun updateFolder(folder: Folder) = folderDao.updateFolder(folder)
    suspend fun deleteFolder(folder: Folder) = folderDao.deleteFolder(folder)

    // --- MediaItem Operations ---
    fun getAllActiveMedia(): Flow<List<MediaItem>> = mediaItemDao.getAllActiveMedia()
    fun getActiveMediaByType(type: String): Flow<List<MediaItem>> = mediaItemDao.getActiveMediaByType(type)
    fun getMediaByFolder(folderId: Long): Flow<List<MediaItem>> = mediaItemDao.getMediaByFolder(folderId)
    fun getTrashedItems(): Flow<List<MediaItem>> = mediaItemDao.getTrashedItems()
    fun getMediaCountByFolder(folderId: Long): Flow<Int> = mediaItemDao.getMediaCountByFolder(folderId)
    fun getFirstItemInFolder(folderId: Long): Flow<MediaItem?> = mediaItemDao.getFirstItemInFolder(folderId)

    suspend fun insertMediaItem(mediaItem: MediaItem): Long = mediaItemDao.insertMediaItem(mediaItem)
    suspend fun updateMediaItem(mediaItem: MediaItem) = mediaItemDao.updateMediaItem(mediaItem)
    
    suspend fun deleteMediaItem(mediaItem: MediaItem) {
        withContext(Dispatchers.IO) {
            try {
                // Delete physical files
                val file = File(mediaItem.storedPath)
                if (file.exists()) file.delete()
                val thumbFile = File(mediaItem.thumbnailPath)
                if (thumbFile.exists() && mediaItem.thumbnailPath != mediaItem.storedPath) {
                    thumbFile.delete()
                }
            } catch (e: Exception) {
                Log.e("VaultRepository", "Error deleting physical file", e)
            }
            mediaItemDao.deleteMediaItem(mediaItem)
        }
    }

    suspend fun emptyTrash() {
        withContext(Dispatchers.IO) {
            try {
                val trashed = mediaItemDao.getTrashedItems().first()
                for (item in trashed) {
                    val file = File(item.storedPath)
                    if (file.exists()) file.delete()
                    val thumbFile = File(item.thumbnailPath)
                    if (thumbFile.exists() && item.thumbnailPath != item.storedPath) {
                        thumbFile.delete()
                    }
                }
                mediaItemDao.emptyTrash()
            } catch (e: Exception) {
                Log.e("VaultRepository", "Error emptying trash", e)
            }
        }
    }

    suspend fun cleanExpiredTrash() {
        withContext(Dispatchers.IO) {
            try {
                val trashed = mediaItemDao.getTrashedItems().first()
                val now = System.currentTimeMillis()
                for (item in trashed) {
                    if (item.trashExpiryDate != null && now >= item.trashExpiryDate) {
                        val file = File(item.storedPath)
                        if (file.exists()) file.delete()
                        val thumbFile = File(item.thumbnailPath)
                        if (thumbFile.exists() && item.thumbnailPath != item.storedPath) {
                            thumbFile.delete()
                        }
                        mediaItemDao.deleteMediaItem(item)
                    }
                }
            } catch (e: Exception) {
                Log.e("VaultRepository", "Error cleaning expired trash", e)
            }
        }
    }

    // --- Import Media (Gallery to Vault) ---
    suspend fun importMedia(uri: Uri, type: String, folderId: Long?): MediaItem? = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            val contentResolver = context.contentResolver
            
            // Get original metadata
            var originalName = "imported_${System.currentTimeMillis()}"
            var size = 0L
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        originalName = it.getString(nameIndex)
                    }
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        size = it.getLong(sizeIndex)
                    }
                }
            }

            inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
            
            // Hashed/secure filename in vault
            val secureId = UUID.randomUUID().toString()
            val fileExtension = getFileExtension(originalName)
            val secureFile = File(vaultDir, secureId + (if (fileExtension.isNotEmpty()) ".$fileExtension" else ""))
            
            // Copy file to vault
            FileOutputStream(secureFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }

            if (size == 0L) {
                size = secureFile.length()
            }

            // Thumbnail extraction and duration check
            var duration = 0L
            var thumbnailPath = secureFile.absolutePath

            if (type == "video") {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(secureFile.absolutePath)
                    
                    // Duration
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    duration = durationStr?.toLongOrNull() ?: 0L

                    // Extract Frame
                    val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (bitmap != null) {
                        val thumbFile = File(thumbnailDir, "thumb_$secureId.jpg")
                        FileOutputStream(thumbFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        thumbnailPath = thumbFile.absolutePath
                    }
                } catch (e: Exception) {
                    Log.e("VaultRepository", "Error extracting video thumbnail", e)
                } finally {
                    try {
                        retriever.release()
                    } catch (ex: Exception) {
                        // ignore
                    }
                }
            }

            val newItem = MediaItem(
                folderId = folderId,
                fileName = originalName,
                storedPath = secureFile.absolutePath,
                thumbnailPath = thumbnailPath,
                type = type,
                size = size,
                duration = duration,
                addedAt = System.currentTimeMillis(),
                isInTrash = false
            )

            val id = mediaItemDao.insertMediaItem(newItem)
            return@withContext newItem.copy(id = id)
        } catch (e: Exception) {
            Log.e("VaultRepository", "Error importing media", e)
            null
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    // --- Save Take Photo / Camera Captured File ---
    suspend fun saveCameraPhoto(tempFile: File, folderId: Long?): MediaItem? = withContext(Dispatchers.IO) {
        try {
            val secureId = UUID.randomUUID().toString()
            val secureFile = File(vaultDir, "$secureId.jpg")
            
            // Move temp file to secure vault
            if (tempFile.renameTo(secureFile)) {
                val size = secureFile.length()
                val originalName = "camera_${System.currentTimeMillis()}.jpg"

                val newItem = MediaItem(
                    folderId = folderId,
                    fileName = originalName,
                    storedPath = secureFile.absolutePath,
                    thumbnailPath = secureFile.absolutePath,
                    type = "photo",
                    size = size,
                    duration = 0L,
                    addedAt = System.currentTimeMillis(),
                    isInTrash = false
                )
                val id = mediaItemDao.insertMediaItem(newItem)
                return@withContext newItem.copy(id = id)
            }
            null
        } catch (e: Exception) {
            Log.e("VaultRepository", "Error saving camera photo", e)
            null
        }
    }

    // --- Export / Unhide Media ---
    suspend fun unhideMediaItem(mediaItem: MediaItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(mediaItem.storedPath)
            if (!srcFile.exists()) return@withContext false

            val folderName = "SimpleCalculator"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, mediaItem.fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (mediaItem.type == "video") "video/mp4" else "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, if (mediaItem.type == "video") "${Environment.DIRECTORY_MOVIES}/$folderName" else "${Environment.DIRECTORY_PICTURES}/$folderName")
                }
                
                val uri = resolver.insert(
                    if (mediaItem.type == "video") MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return@withContext false

                resolver.openOutputStream(uri)?.use { outStream ->
                    srcFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
            } else {
                val publicDir = if (mediaItem.type == "video") {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), folderName)
                } else {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folderName)
                }
                if (!publicDir.exists()) publicDir.mkdirs()
                
                val destFile = File(publicDir, mediaItem.fileName)
                srcFile.inputStream().use { inStream ->
                    destFile.outputStream().use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                // Scan media scanner
                android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
            }

            // Success, delete from vault
            deleteMediaItem(mediaItem)
            return@withContext true
        } catch (e: Exception) {
            Log.e("VaultRepository", "Error unhiding item", e)
            false
        }
    }

    // --- One-Time Sharing ---
    suspend fun createTempShareFile(mediaItem: MediaItem): File? = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(mediaItem.storedPath)
            if (!srcFile.exists()) return@withContext null

            val tempFile = File(tempShareDir, "share_${System.currentTimeMillis()}_${mediaItem.fileName}")
            srcFile.inputStream().use { inStream ->
                tempFile.outputStream().use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
            return@withContext tempFile
        } catch (e: Exception) {
            Log.e("VaultRepository", "Error creating temp share file", e)
            null
        }
    }

    fun deleteTempShareFile(file: File) {
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("VaultRepository", "Error deleting temp share file", e)
        }
    }

    private fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot != -1) fileName.substring(lastDot + 1) else ""
    }
}
