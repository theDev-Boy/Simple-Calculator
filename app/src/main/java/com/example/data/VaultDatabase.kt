package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "photo" or "video"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "media_items",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("folderId")]
)
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long? = null,
    val fileName: String,
    val storedPath: String,
    val thumbnailPath: String,
    val type: String, // "photo" or "video"
    val size: Long,
    val duration: Long = 0L, // for videos, in ms
    val addedAt: Long = System.currentTimeMillis(),
    val isInTrash: Boolean = false,
    val trashExpiryDate: Long? = null
)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE type = :type ORDER BY createdAt DESC")
    fun getFoldersByType(type: String): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): Folder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Update
    suspend fun updateFolder(folder: Folder)

    @Delete
    suspend fun deleteFolder(folder: Folder)
}

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items WHERE isInTrash = 0 ORDER BY addedAt DESC")
    fun getAllActiveMedia(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isInTrash = 0 AND type = :type ORDER BY addedAt DESC")
    fun getActiveMediaByType(type: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isInTrash = 0 AND folderId = :folderId ORDER BY addedAt DESC")
    fun getMediaByFolder(folderId: Long): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isInTrash = 1 ORDER BY trashExpiryDate DESC")
    fun getTrashedItems(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getMediaItemById(id: Long): MediaItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItem(mediaItem: MediaItem): Long

    @Update
    suspend fun updateMediaItem(mediaItem: MediaItem)

    @Delete
    suspend fun deleteMediaItem(mediaItem: MediaItem)

    @Query("DELETE FROM media_items WHERE isInTrash = 1")
    suspend fun emptyTrash()

    @Query("SELECT COUNT(*) FROM media_items WHERE isInTrash = 0 AND folderId = :folderId")
    fun getMediaCountByFolder(folderId: Long): Flow<Int>

    @Query("SELECT * FROM media_items WHERE isInTrash = 0 AND folderId = :folderId LIMIT 1")
    fun getFirstItemInFolder(folderId: Long): Flow<MediaItem?>
}

@Database(entities = [Folder::class, MediaItem::class], version = 1, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun mediaItemDao(): MediaItemDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getDatabase(context: android.content.Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "vault_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
