package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Folder
import com.example.data.MediaItem
import com.example.ui.VaultViewModel
import com.example.ui.theme.glassmorphic
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotosTabScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark by viewModel.isDarkMode.collectAsState()

    // Sub-states
    val folders by viewModel.photoFolders.collectAsState()
    val allPhotos by viewModel.activePhotos.collectAsState()
    val activeFolder by viewModel.activePhotoFolder.collectAsState()

    // Sheets/Dialogs toggles
    var showAddBottomSheet by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameFolderDialog by remember { mutableStateOf(value = false) }
    var showDeleteFolderConfirm by remember { mutableStateOf(value = false) }
    
    var folderTargetForAction by remember { mutableStateOf<Folder?>(null) }
    
    var showMovePhotoDialog by remember { mutableStateOf(false) }
    var photoTargetForMove by remember { mutableStateOf<MediaItem?>(null) }

    // Multi-select context bar state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedPhotoIds = remember { mutableStateListOf<Long>() }

    // Image viewer state
    var activeViewerPhotoIndex by remember { mutableStateOf<Int?>(null) }

    // Camera URI holder
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    // --- Action Launchers ---
    // Gallery Photo Picker (Multiple)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importSelectedMedia(uris, "photo", activeFolder?.id)
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val file = tempCameraFile
            if (file != null) {
                viewModel.saveCapturedPhoto(file, activeFolder?.id)
            }
        } else {
            // Cleanup temp file
            tempCameraFile?.delete()
        }
    }

    // Set up camera capture
    val launchCamera = {
        try {
            val tempFile = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
            val authority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, tempFile)
            tempCameraFile = tempFile
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            viewModel.showToast.tryEmit("Camera open error")
        }
    }

    // Safe photo list within current context (filtered by folder if open)
    val displayPhotos = remember(allPhotos, activeFolder) {
        if (activeFolder == null) {
            allPhotos
        } else {
            allPhotos.filter { it.folderId == activeFolder?.id }
        }
    }

    // Exit folder view with back handler
    if (activeFolder != null && activeViewerPhotoIndex == null) {
        BackHandler {
            viewModel.activePhotoFolder.value = null
        }
    }

    if (activeViewerPhotoIndex != null) {
        BackHandler {
            activeViewerPhotoIndex = null
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
                            IconButton(onClick = { viewModel.activePhotoFolder.value = null }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (isDark) Color.White else Color.Black
                                )
                            }
                        }
                        Text(
                            text = activeFolder?.name ?: "Photos",
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

                // Batch Selection or Action buttons
                if (isMultiSelectMode) {
                    Row {
                        IconButton(onClick = {
                            // Unhide batch
                            val itemsToUnhide = displayPhotos.filter { selectedPhotoIds.contains(it.id) }
                            coroutineScope.launch {
                                itemsToUnhide.forEach { viewModel.unhideMediaItem(it) }
                                selectedPhotoIds.clear()
                                isMultiSelectMode = false
                            }
                        }) {
                            Icon(Icons.Filled.Unarchive, "Unhide Selected", tint = if (isDark) Color(0xFF30D158) else Color(0xFF007AFF))
                        }
                        IconButton(onClick = {
                            // Trash batch
                            val itemsToTrash = displayPhotos.filter { selectedPhotoIds.contains(it.id) }
                            itemsToTrash.forEach { viewModel.trashMediaItem(it) }
                            selectedPhotoIds.clear()
                            isMultiSelectMode = false
                        }) {
                            Icon(Icons.Filled.Delete, "Delete Selected", tint = Color(0xFFFF453A))
                        }
                        IconButton(onClick = {
                            isMultiSelectMode = false
                            selectedPhotoIds.clear()
                        }) {
                            Icon(Icons.Filled.Close, "Cancel", tint = if (isDark) Color.White else Color.Black)
                        }
                    }
                }
            }

            // Scrollable Grid View
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Show Folders section ONLY when we are in root view
                if (activeFolder == null) {
                    item(span = { GridItemSpan(3) }) {
                        Text(
                            text = "Folders",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            color = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    if (folders.isEmpty()) {
                        item(span = { GridItemSpan(3) }) {
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
                        // 2 column grid for folders
                        item(span = { GridItemSpan(3) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    for (i in folders.indices step 2) {
                                        FolderGridCard(
                                            folder = folders[i],
                                            viewModel = viewModel,
                                            isDark = isDark,
                                            onOpen = { viewModel.activePhotoFolder.value = folders[i] },
                                            onLongPress = {
                                                folderTargetForAction = folders[i]
                                                showRenameFolderDialog = true
                                            },
                                            onDeleteRequest = {
                                                folderTargetForAction = folders[i]
                                                showDeleteFolderConfirm = true
                                            }
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    for (i in 1 until folders.size step 2) {
                                        FolderGridCard(
                                            folder = folders[i],
                                            viewModel = viewModel,
                                            isDark = isDark,
                                            onOpen = { viewModel.activePhotoFolder.value = folders[i] },
                                            onLongPress = {
                                                folderTargetForAction = folders[i]
                                                showRenameFolderDialog = true
                                            },
                                            onDeleteRequest = {
                                                folderTargetForAction = folders[i]
                                                showDeleteFolderConfirm = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(3) }) {
                        Text(
                            text = "All Photos",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            color = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                        )
                    }
                }

                // Empty state for Photos
                if (displayPhotos.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = "No photos",
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
                    itemsIndexed(displayPhotos) { index, item ->
                        val isSelected = selectedPhotoIds.contains(item.id)
                        
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onLongClick = {
                                        viewModel.performHapticFeedback()
                                        if (!isMultiSelectMode) {
                                            isMultiSelectMode = true
                                            selectedPhotoIds.add(item.id)
                                        }
                                    },
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            viewModel.performHapticFeedback()
                                            if (isSelected) {
                                                selectedPhotoIds.remove(item.id)
                                                if (selectedPhotoIds.isEmpty()) {
                                                    isMultiSelectMode = false
                                                }
                                            } else {
                                                selectedPhotoIds.add(item.id)
                                            }
                                        } else {
                                            activeViewerPhotoIndex = index
                                        }
                                    }
                                )
                                .testTag("photo_item_${item.id}")
                        ) {
                            // Thumbnail Async Image with Coil
                            AsyncImage(
                                model = item.storedPath,
                                contentDescription = item.fileName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Select Badge overlay
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
                        }
                    }
                }
            }
        }

        // --- Custom Floating Action Button with Rotate Animation ---
        var isFabRotated by remember { mutableStateOf(false) }
        val fabRotationAngle by animateFloatAsState(
            targetValue = if (isFabRotated) 45f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "fab_rotation"
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
                modifier = Modifier.testTag("photo_fab")
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

        // Bottom Sheet / Action Sheet for Adding Photo Actions
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
                        text = "Add to Vault",
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
                        headlineContent = { Text("Import from Gallery", color = if (isDark) Color.White else Color.Black) },
                        leadingContent = { Icon(Icons.Filled.PhotoLibrary, "Gallery", tint = if (isDark) Color(0xFF30D158) else Color(0xFF30D158)) },
                        modifier = Modifier.clickable {
                            showAddBottomSheet = false
                            isFabRotated = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    ListItem(
                        headlineContent = { Text("Take Photo", color = if (isDark) Color.White else Color.Black) },
                        leadingContent = { Icon(Icons.Filled.CameraAlt, "Camera", tint = Color(0xFFFF453A)) },
                        modifier = Modifier.clickable {
                            showAddBottomSheet = false
                            isFabRotated = false
                            launchCamera()
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
                title = { Text("Create New Folder") },
                text = {
                    OutlinedTextField(
                        value = folderNameInput,
                        onValueChange = { folderNameInput = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("folder_name_input")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.createFolder(folderNameInput, "photo")
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

        // Folder Operations dialogs
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
                        
                        // Option to unhide entire folder
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    val items = allPhotos.filter { it.folderId == folderTargetForAction?.id }
                                    items.forEach { viewModel.unhideMediaItem(it) }
                                    viewModel.deleteFolder(folderTargetForAction!!)
                                    showRenameFolderDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Unarchive, "Unhide", tint = if (isDark) Color(0xFF30D158) else Color(0xFF007AFF))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unhide Entire Folder Contents")
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

        // Delete Folder Confirmation
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

        // Move Photo to Folder Dialog
        if (showMovePhotoDialog && photoTargetForMove != null) {
            AlertDialog(
                onDismissRequest = { showMovePhotoDialog = false },
                title = { Text("Move to Folder") },
                text = {
                    LazyColumn {
                        item {
                            ListItem(
                                headlineContent = { Text("Root Vault / Unassigned") },
                                leadingContent = { Icon(Icons.Filled.Folder, "Unassigned") },
                                modifier = Modifier.clickable {
                                    viewModel.moveMediaToFolder(photoTargetForMove!!, null)
                                    showMovePhotoDialog = false
                                }
                            )
                        }
                        items(folders) { f ->
                            ListItem(
                                headlineContent = { Text(f.name) },
                                leadingContent = { Icon(Icons.Filled.Folder, f.name) },
                                modifier = Modifier.clickable {
                                    viewModel.moveMediaToFolder(photoTargetForMove!!, f.id)
                                    showMovePhotoDialog = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showMovePhotoDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Fullscreen Swipeable Picture Viewer
        if (activeViewerPhotoIndex != null) {
            val initialIndex = activeViewerPhotoIndex!!
            val pagerState = rememberPagerState(
                initialPage = initialIndex,
                pageCount = { displayPhotos.size }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Main swipeable horizontal pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val mediaItem = displayPhotos[page]
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Pinch to zoom container logic
                        // We can use standard AsyncImage loaded with coil which offers premium performance
                        AsyncImage(
                            model = mediaItem.storedPath,
                            contentDescription = mediaItem.fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // Top Toolbar overlay
                val activeItem = displayPhotos.getOrNull(pagerState.currentPage)
                if (activeItem != null) {
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
                            onClick = { activeViewerPhotoIndex = null },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                        }

                        Text(
                            text = activeItem.fileName,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.width(48.dp)) // Equal spacing balance
                    }

                    // Bottom Action overlay bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Share (one-time)
                        IconButton(onClick = {
                            viewModel.shareMediaItem(activeItem) { publicUri ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, publicUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Image"))
                            }
                        }) {
                            Icon(Icons.Filled.Share, "Share", tint = Color.White)
                        }

                        // Move to Folder
                        IconButton(onClick = {
                            photoTargetForMove = activeItem
                            showMovePhotoDialog = true
                        }) {
                            Icon(Icons.Filled.FolderOpen, "Move Folder", tint = Color.White)
                        }

                        // Unhide / Export
                        IconButton(onClick = {
                            viewModel.unhideMediaItem(activeItem)
                            // Dismiss viewer if list empty or adjust index
                            if (displayPhotos.size <= 1) {
                                activeViewerPhotoIndex = null
                            }
                        }) {
                            Icon(Icons.Filled.Unarchive, "Unhide", tint = Color.White)
                        }

                        // Delete to trash
                        IconButton(onClick = {
                            viewModel.trashMediaItem(activeItem)
                            if (displayPhotos.size <= 1) {
                                activeViewerPhotoIndex = null
                            }
                        }) {
                            Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFFF453A))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderGridCard(
    folder: Folder,
    viewModel: VaultViewModel,
    isDark: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val itemsFlow = remember(folder.id) { viewModel.getMediaItemsInFolder(folder.id, "photo") }
    val items by itemsFlow.collectAsState(initial = emptyList())
    val thumbnail = items.firstOrNull()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress
            )
            .testTag("folder_card_${folder.id}"),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassmorphic(darkMode = isDark, cornerRadius = 20.dp, elevation = 6.dp)
                .padding(12.dp)
        ) {
            // Folder Image Thumbnail (Uses first photo or placeholder icon)
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
                        model = thumbnail.storedPath,
                        contentDescription = "Folder thumbnail",
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

            // Folder Name
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (isDark) Color.White else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Items Count
            Text(
                text = "${items.size} items",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93)
            )
        }
    }
}
