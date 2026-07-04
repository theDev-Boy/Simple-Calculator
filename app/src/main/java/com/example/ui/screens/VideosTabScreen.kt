package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.Folder
import com.example.data.MediaItem
import com.example.ui.VaultViewModel
import com.example.ui.theme.glassmorphic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideosTabScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark by viewModel.isDarkMode.collectAsState()

    val folders by viewModel.videoFolders.collectAsState()
    val allVideos by viewModel.activeVideos.collectAsState()
    val activeFolder by viewModel.activeVideoFolder.collectAsState()

    var showAddBottomSheet by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameFolderDialog by remember { mutableStateOf(false) }
    var showDeleteFolderConfirm by remember { mutableStateOf(false) }
    var folderTargetForAction by remember { mutableStateOf<Folder?>(null) }

    var showMoveVideoDialog by remember { mutableStateOf(false) }
    var videoTargetForMove by remember { mutableStateOf<MediaItem?>(null) }

    var selectedVideoForPlayback by remember { mutableStateOf<MediaItem?>(null) }

    // Multi-select context bar state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedVideoIds = remember { mutableStateListOf<Long>() }

    // Video import launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importSelectedMedia(uris, "video", activeFolder?.id)
        }
    }

    val displayVideos = remember(allVideos, activeFolder) {
        if (activeFolder == null) {
            allVideos
        } else {
            allVideos.filter { it.folderId == activeFolder?.id }
        }
    }

    if (activeFolder != null && selectedVideoForPlayback == null) {
        BackHandler {
            viewModel.activeVideoFolder.value = null
        }
    }

    if (selectedVideoForPlayback != null) {
        BackHandler {
            selectedVideoForPlayback = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activeFolder != null) {
                            IconButton(onClick = { viewModel.activeVideoFolder.value = null }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (isDark) Color.White else Color.Black
                                )
                            }
                        }
                        Text(
                            text = activeFolder?.name ?: "Videos",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isDark) Color.White else Color.Black
                        )
                    }
                    if (activeFolder == null) {
                        Text(
                            text = "Secure Vault",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93)
                        )
                    }
                }

                if (isMultiSelectMode) {
                    Row {
                        IconButton(onClick = {
                            val itemsToUnhide = displayVideos.filter { selectedVideoIds.contains(it.id) }
                            coroutineScope.launch {
                                itemsToUnhide.forEach { viewModel.unhideMediaItem(it) }
                                selectedVideoIds.clear()
                                isMultiSelectMode = false
                            }
                        }) {
                            Icon(Icons.Filled.Unarchive, "Unhide Selected", tint = if (isDark) Color(0xFF30D158) else Color(0xFF007AFF))
                        }
                        IconButton(onClick = {
                            val itemsToTrash = displayVideos.filter { selectedVideoIds.contains(it.id) }
                            itemsToTrash.forEach { viewModel.trashMediaItem(it) }
                            selectedVideoIds.clear()
                            isMultiSelectMode = false
                        }) {
                            Icon(Icons.Filled.Delete, "Delete Selected", tint = Color(0xFFFF453A))
                        }
                        IconButton(onClick = {
                            isMultiSelectMode = false
                            selectedVideoIds.clear()
                        }) {
                            Icon(Icons.Filled.Close, "Cancel", tint = if (isDark) Color.White else Color.Black)
                        }
                    }
                }
            }

            // Grid View
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeFolder == null) {
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            text = "Folders",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            color = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    if (folders.isEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isDark) Color(0x0FFFFFFF) else Color(0x1F000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No custom folders yet. Create one!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93)
                                )
                            }
                        }
                    } else {
                        items(folders) { folder ->
                            VideoFolderGridCard(
                                folder = folder,
                                viewModel = viewModel,
                                isDark = isDark,
                                onOpen = { viewModel.activeVideoFolder.value = folder },
                                onLongPress = {
                                    folderTargetForAction = folder
                                    showRenameFolderDialog = true
                                }
                            )
                        }
                    }

                    item(span = { GridItemSpan(2) }) {
                        Text(
                            text = "All Videos",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            color = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                        )
                    }
                }

                if (displayVideos.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VideoLibrary,
                                contentDescription = "No videos",
                                modifier = Modifier.size(56.dp),
                                tint = if (isDark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Empty Vault Screen",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93)
                            )
                        }
                    }
                } else {
                    items(displayVideos) { item ->
                        val isSelected = selectedVideoIds.contains(item.id)
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onLongClick = {
                                        viewModel.performHapticFeedback()
                                        if (!isMultiSelectMode) {
                                            isMultiSelectMode = true
                                            selectedVideoIds.add(item.id)
                                        }
                                    },
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            viewModel.performHapticFeedback()
                                            if (isSelected) {
                                                selectedVideoIds.remove(item.id)
                                                if (selectedVideoIds.isEmpty()) {
                                                    isMultiSelectMode = false
                                                }
                                            } else {
                                                selectedVideoIds.add(item.id)
                                            }
                                        } else {
                                            selectedVideoForPlayback = item
                                        }
                                    }
                                )
                                .testTag("video_item_${item.id}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.5f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                            ) {
                                // Thumbnail Async Image
                                AsyncImage(
                                    model = item.thumbnailPath,
                                    contentDescription = item.fileName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Duration overlay Badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.70f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = formatDuration(item.duration),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }

                                // Selection overlay
                                if (isMultiSelectMode) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(if (isSelected) Color.Black.copy(alpha = 0.4f) else Color.Transparent)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) (if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF))
                                                else Color.White.copy(alpha = 0.6f)
                                            )
                                            .border(2.dp, Color.White, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                // Play icon badge
                                if (!isMultiSelectMode) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Video Name and Info
                            Text(
                                text = item.fileName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                color = if (isDark) Color.White else Color.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Text(
                                text = formatSize(item.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // FAB for importing videos
        var isFabRotated by remember { mutableStateOf(false) }
        val fabRotationAngle by animateFloatAsState(
            targetValue = if (isFabRotated) 45f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "video_fab_rotation"
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 86.dp, end = 16.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    isFabRotated = !isFabRotated
                    showAddBottomSheet = true
                },
                containerColor = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("video_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add Content",
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            rotationZ = fabRotationAngle
                        }
                )
            }
        }

        // Add Video Action Sheet
        if (showAddBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showAddBottomSheet = false
                    isFabRotated = false
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = if (isDark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Add Video",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isDark) Color.White else Color.Black,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    ListItem(
                        headlineContent = { Text("Create New Folder", color = if (isDark) Color.White else Color.Black) },
                        leadingContent = { Icon(Icons.Filled.CreateNewFolder, "Folder", tint = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)) },
                        modifier = Modifier.clickable {
                            showAddBottomSheet = false
                            isFabRotated = false
                            showCreateFolderDialog = true
                        }
                    )

                    ListItem(
                        headlineContent = { Text("Import Videos", color = if (isDark) Color.White else Color.Black) },
                        leadingContent = { Icon(Icons.Filled.VideoLibrary, "Videos", tint = if (isDark) Color(0xFF30D158) else Color(0xFF30D158)) },
                        modifier = Modifier.clickable {
                            showAddBottomSheet = false
                            isFabRotated = false
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    )
                }
            }
        }

        // Create Folder Dialog
        if (showCreateFolderDialog) {
            var folderNameInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateFolderDialog = false },
                title = { Text("Create Video Folder") },
                text = {
                    OutlinedTextField(
                        value = folderNameInput,
                        onValueChange = { folderNameInput = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.createFolder(folderNameInput, "video")
                            showCreateFolderDialog = false
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateFolderDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Folder Operations Action dialog
        if (showRenameFolderDialog && folderTargetForAction != null) {
            var renameInput by remember { mutableStateOf(folderTargetForAction?.name ?: "") }
            AlertDialog(
                onDismissRequest = { showRenameFolderDialog = false },
                title = { Text("Folder Actions") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = renameInput,
                            onValueChange = { renameInput = it },
                            label = { Text("Rename Folder") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    val items = allVideos.filter { it.folderId == folderTargetForAction?.id }
                                    items.forEach { viewModel.unhideMediaItem(it) }
                                    viewModel.deleteFolder(folderTargetForAction!!)
                                    showRenameFolderDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Unarchive, "Unhide", tint = if (isDark) Color(0xFF30D158) else Color(0xFF007AFF))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unhide All Folder Videos")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.renameFolder(folderTargetForAction!!, renameInput)
                            showRenameFolderDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showRenameFolderDialog = false
                            showDeleteFolderConfirm = true
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF453A))
                    ) {
                        Text("Delete Folder")
                    }
                },
                containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Delete Video Folder Confirm
        if (showDeleteFolderConfirm && folderTargetForAction != null) {
            AlertDialog(
                onDismissRequest = { showDeleteFolderConfirm = false },
                title = { Text("Delete Folder") },
                text = { Text("Delete folder and ALL its contents? This cannot be undone.", color = if (isDark) Color.White else Color.Black) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteFolder(folderTargetForAction!!)
                            showDeleteFolderConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A), contentColor = Color.White)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteFolderConfirm = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Move Video Dialog
        if (showMoveVideoDialog && videoTargetForMove != null) {
            AlertDialog(
                onDismissRequest = { showMoveVideoDialog = false },
                title = { Text("Move Video") },
                text = {
                    LazyColumn {
                        item {
                            ListItem(
                                headlineContent = { Text("Root Vault / Unassigned") },
                                leadingContent = { Icon(Icons.Filled.Folder, "Unassigned") },
                                modifier = Modifier.clickable {
                                    viewModel.moveMediaToFolder(videoTargetForMove!!, null)
                                    showMoveVideoDialog = false
                                }
                            )
                        }
                        items(folders) { f ->
                            ListItem(
                                headlineContent = { Text(f.name) },
                                leadingContent = { Icon(Icons.Filled.Folder, f.name) },
                                modifier = Modifier.clickable {
                                    viewModel.moveMediaToFolder(videoTargetForMove!!, f.id)
                                    showMoveVideoDialog = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showMoveVideoDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Fullscreen Advanced Video Player Component Overlay
        if (selectedVideoForPlayback != null) {
            FullscreenVideoPlayer(
                mediaItem = selectedVideoForPlayback!!,
                viewModel = viewModel,
                onDismiss = { selectedVideoForPlayback = null },
                onMoveRequest = { item ->
                    videoTargetForMove = item
                    showMoveVideoDialog = true
                },
                isDark = isDark
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoFolderGridCard(
    folder: Folder,
    viewModel: VaultViewModel,
    isDark: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val itemsFlow = remember(folder.id) { viewModel.getMediaItemsInFolder(folder.id, "video") }
    val items by itemsFlow.collectAsState(initial = emptyList())
    val thumbnail = items.firstOrNull()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress
            ),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassmorphic(darkMode = isDark, cornerRadius = 20.dp, elevation = 6.dp)
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    AsyncImage(
                        model = thumbnail.thumbnailPath,
                        contentDescription = "Folder video thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "Empty Folder",
                        modifier = Modifier.size(48.dp),
                        tint = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (isDark) Color.White else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${items.size} videos",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93)
            )
        }
    }
}

// --- Video Player implementation with ExoPlayer + Gestures ---
@Composable
fun FullscreenVideoPlayer(
    mediaItem: MediaItem,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
    onMoveRequest: (MediaItem) -> Unit,
    isDark: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Screen brightness & volume handlers
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val window = remember { (context as? Activity)?.window }

    var volumeValue by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
    }
    var brightnessValue by remember {
        mutableFloatStateOf(window?.attributes?.screenBrightness ?: 0.5f)
    }

    if (brightnessValue < 0) brightnessValue = 0.5f // system default fix

    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    // ExoPlayer State Setup
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItemSource = androidx.media3.common.MediaItem.fromUri(Uri.fromFile(File(mediaItem.storedPath)))
            setMediaItem(mediaItemSource)
            prepare()
            playWhenReady = true
        }
    }

    // Playback and lock state
    var isPlaying by remember { mutableStateOf(true) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // Auto-update seek slider position
    LaunchedEffect(exoPlayer) {
        while (true) {
            playbackPosition = exoPlayer.currentPosition
            totalDuration = exoPlayer.duration
            if (totalDuration < 0) totalDuration = 0
            isPlaying = exoPlayer.isPlaying
            delay(500)
        }
    }

    DisposableEffect(key1 = true) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isScreenLocked) {
                // Drag Gestures for:
                // Swipe Left side vertical: Brightness
                // Swipe Right side vertical: Volume
                // Horizontal Swipe: Scrubbing Seek
                if (!isScreenLocked) {
                    detectDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            showBrightnessOverlay = false
                            showVolumeOverlay = false
                        },
                        onDragCancel = {
                            showBrightnessOverlay = false
                            showVolumeOverlay = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width
                            val height = size.height

                            val isLeftSide = change.position.x < (width / 2)

                            if (Math.abs(dragAmount.y) > Math.abs(dragAmount.x)) {
                                // Vertical drag
                                val delta = -dragAmount.y / height
                                if (isLeftSide) {
                                    // Brightness
                                    brightnessValue = (brightnessValue + delta).coerceIn(0f, 1f)
                                    showBrightnessOverlay = true
                                    coroutineScope.launch {
                                        window?.let { w ->
                                            val lp = w.attributes
                                            lp.screenBrightness = brightnessValue
                                            w.attributes = lp
                                        }
                                    }
                                } else {
                                    // Volume
                                    volumeValue = (volumeValue + delta).coerceIn(0f, 1f)
                                    showVolumeOverlay = true
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val targetVol = (volumeValue * maxVol).toInt()
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                }
                            } else {
                                // Horizontal drag (seeking scrubbing)
                                val seekDelta = (dragAmount.x / width) * totalDuration
                                val targetPos = (exoPlayer.currentPosition + seekDelta).toLong().coerceIn(0L, totalDuration)
                                exoPlayer.seekTo(targetPos)
                                playbackPosition = targetPos
                            }
                        }
                    )
                }
            }
    ) {
        // Player Surface wrapped in AndroidView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Use our premium Custom Controller
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Lock/Unlock indicator and overlay controls
        if (!isScreenLocked) {
            // Top overlay bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }

                Text(
                    text = mediaItem.fileName,
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Share video (one-time)
                IconButton(
                    onClick = {
                        viewModel.shareMediaItem(mediaItem) { publicUri ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, publicUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Video"))
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Filled.Share, "Share", tint = Color.White)
                }
            }

            // Bottom Player controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.60f))
                    .padding(16.dp)
            ) {
                // Seek bar / duration indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(playbackPosition),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                    
                    Slider(
                        value = if (totalDuration > 0) playbackPosition.toFloat() / totalDuration else 0f,
                        onValueChange = { percent ->
                            val target = (percent * totalDuration).toLong()
                            exoPlayer.seekTo(target)
                            playbackPosition = target
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF0A84FF),
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )

                    Text(
                        text = formatDuration(totalDuration),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Playback speed and actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Playback Speed Toggle
                    TextButton(
                        onClick = {
                            playbackSpeed = when (playbackSpeed) {
                                1.0f -> 1.5f
                                1.5f -> 2.0f
                                2.0f -> 0.5f
                                else -> 1.0f
                            }
                            exoPlayer.setPlaybackSpeed(playbackSpeed)
                        }
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Seek controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                        }) {
                            Icon(Icons.Filled.Replay10, "Rewind 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                                isPlaying = !isPlaying
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.White, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(onClick = {
                            val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(totalDuration)
                            exoPlayer.seekTo(target)
                        }) {
                            Icon(Icons.Filled.Forward10, "Fast Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }

                    // More Options (Trash / Move / Unhide)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { onMoveRequest(mediaItem) }) {
                            Icon(Icons.Filled.FolderOpen, "Move", tint = Color.White)
                        }
                        IconButton(onClick = {
                            viewModel.unhideMediaItem(mediaItem)
                            onDismiss()
                        }) {
                            Icon(Icons.Filled.Unarchive, "Unhide", tint = Color.White)
                        }
                        IconButton(onClick = {
                            viewModel.trashMediaItem(mediaItem)
                            onDismiss()
                        }) {
                            Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFFF453A))
                        }
                    }
                }
            }
        }

        // Lock button ALWAYS overlayed
        IconButton(
            onClick = { isScreenLocked = !isScreenLocked },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                imageVector = if (isScreenLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = "Lock Controls",
                tint = if (isScreenLocked) Color(0xFFFF453A) else Color.White
            )
        }

        // Custom Overlay indications for brightness/volume slides
        if (showBrightnessOverlay) {
            BrightnessVolumeOverlay(
                label = "Brightness",
                value = brightnessValue,
                icon = Icons.Filled.Brightness5,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (showVolumeOverlay) {
            BrightnessVolumeOverlay(
                label = "Volume",
                value = volumeValue,
                icon = Icons.Filled.VolumeUp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun BrightnessVolumeOverlay(
    label: String,
    value: Float,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.70f)),
        modifier = modifier.size(130.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = label, color = Color.White, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { value },
                color = Color(0xFF0A84FF),
                trackColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
            )
        }
    }
}

// Helpers for formatted units
fun formatDuration(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    val hr = (ms / (1000 * 60 * 60))
    return if (hr > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hr, min, sec)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
