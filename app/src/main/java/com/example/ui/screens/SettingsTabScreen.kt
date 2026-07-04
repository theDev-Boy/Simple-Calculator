package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.MediaItem
import com.example.ui.VaultViewModel
import com.example.ui.theme.glassmorphic
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabScreen(
    viewModel: VaultViewModel,
    onLockRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark by viewModel.isDarkMode.collectAsState()
    val trashedItems by viewModel.trashedItems.collectAsState()
    
    // Dialog toggles
    var showChangePasscodeDialog by remember { mutableStateOf(false) }
    var showResetPasscodeDialog by remember { mutableStateOf(false) }
    var showUnhideAllFirstConfirm by remember { mutableStateOf(false) }
    var showUnhideAllSecondConfirm by remember { mutableStateOf(false) }
    var showTrashViewerDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }

    // Export progress & summary from VM
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportSummary by viewModel.exportSummary.collectAsState()

    // Pulse animation for destructive unhide-all button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isDark) Color.White else Color.Black
                )
                Text(
                    text = "Configuration & Safety",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93)
                )
            }

            // Quick Lock Action
            IconButton(
                onClick = onLockRequest,
                modifier = Modifier.background(if (isDark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6), CircleShape)
            ) {
                Icon(Icons.Filled.Lock, "Lock Vault", tint = if (isDark) Color.White else Color.Black)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // SECTION 1: APPEARANCE
            item {
                SettingsSectionHeader("Appearance")
                SettingsGroupCard(isDark = isDark) {
                    SettingsToggleRow(
                        title = "Dark Mode",
                        subtitle = "Use dark theme",
                        icon = Icons.Filled.DarkMode,
                        iconTint = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF),
                        checked = isDark,
                        onCheckedChange = { viewModel.toggleDarkMode() },
                        isDark = isDark
                    )
                }
            }

            // SECTION 2: SECURITY
            item {
                SettingsSectionHeader("Security")
                SettingsGroupCard(isDark = isDark) {
                    Column {
                        SettingsClickableRow(
                            title = "Change Passcode",
                            subtitle = "Update vault passcode",
                            icon = Icons.Filled.Lock,
                            iconTint = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF),
                            onClick = { showChangePasscodeDialog = true },
                            isDark = isDark
                        )
                        Divider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x1F000000), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                        SettingsClickableRow(
                            title = "Reset Passcode",
                            subtitle = "Re-enter passcode setup state",
                            icon = Icons.Filled.RestartAlt,
                            iconTint = Color(0xFFFF453A),
                            onClick = { showResetPasscodeDialog = true },
                            isDark = isDark
                        )
                    }
                }
            }

            // SECTION 3: VAULT MANAGEMENT
            item {
                SettingsSectionHeader("Vault Management")
                SettingsGroupCard(isDark = isDark) {
                    Column {
                        // Recently Deleted Row
                        SettingsClickableRow(
                            title = "Recently Deleted",
                            subtitle = "${trashedItems.size} items in trash bin",
                            icon = Icons.Filled.DeleteOutline,
                            iconTint = Color(0xFF30D158),
                            onClick = { showTrashViewerDialog = true },
                            isDark = isDark
                        )
                        Divider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x1F000000), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                        
                        // Unhide All row (Pulsing destructive button)
                        SettingsClickableRow(
                            title = "Unhide All Content",
                            subtitle = "Restore all media back to phone gallery",
                            icon = Icons.Filled.Unarchive,
                            iconTint = Color(0xFFFF453A),
                            onClick = { showUnhideAllFirstConfirm = true },
                            modifier = Modifier.graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            },
                            isDark = isDark
                        )
                    }
                }
            }

            // SECTION 4: ABOUT
            item {
                SettingsSectionHeader("About")
                SettingsGroupCard(isDark = isDark) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // App logo image with subtle lock badge overlay
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1C1C1E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_app_icon),
                                contentDescription = "App Logo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF30D158)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Lock",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Simple Calculator",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            color = if (isDark) Color.White else Color.Black
                        )
                        Text(
                            text = "Version 1.0.0",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
                                )
                            ) {
                                Icon(Icons.Filled.Star, "Rate")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rate App")
                            }

                            OutlinedButton(
                                onClick = { showPrivacyPolicyDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isDark) Color.White else Color.Black
                                )
                            ) {
                                Icon(Icons.Filled.PrivacyTip, "Privacy")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Privacy Policy")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Disguised as a calculator for your privacy",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93)
                        )
                    }
                }
            }
        }
    }

    // --- DIALOGS IMPLEMENTATION ---

    // Change Passcode
    if (showChangePasscodeDialog) {
        var currentP by remember { mutableStateOf("") }
        var newP by remember { mutableStateOf("") }
        var confirmP by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePasscodeDialog = false },
            title = { Text("Change Passcode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentP,
                        onValueChange = { currentP = it },
                        label = { Text("Current Passcode") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("change_current_pass")
                    )
                    OutlinedTextField(
                        value = newP,
                        onValueChange = { newP = it },
                        label = { Text("New Passcode") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("change_new_pass")
                    )
                    OutlinedTextField(
                        value = confirmP,
                        onValueChange = { confirmP = it },
                        label = { Text("Confirm New Passcode") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("change_confirm_pass")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newP == confirmP) {
                            val success = viewModel.changePasscode(currentP, newP)
                            if (success) showChangePasscodeDialog = false
                        } else {
                            viewModel.showToast.tryEmit("New passcodes do not match!")
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasscodeDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Reset Passcode
    if (showResetPasscodeDialog) {
        var currentP by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showResetPasscodeDialog = false },
            title = { Text("Reset Passcode") },
            text = {
                Column {
                    Text("Warning: This will not delete your hidden files. You will be sent back to the setup screen to configure a new passcode.", color = if (isDark) Color.White else Color.Black)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = currentP,
                        onValueChange = { currentP = it },
                        label = { Text("Current Passcode") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("reset_pass_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = viewModel.resetPasscodeFromSettings(currentP)
                        if (success) {
                            showResetPasscodeDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A), contentColor = Color.White)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPasscodeDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Unhide All Dialog 1
    if (showUnhideAllFirstConfirm) {
        AlertDialog(
            onDismissRequest = { showUnhideAllFirstConfirm = false },
            title = { Text("Unhide All Content?") },
            text = { Text("This will move ALL your hidden photos and videos back to your public gallery. This action cannot be undone.", color = if (isDark) Color.White else Color.Black) },
            confirmButton = {
                Button(
                    onClick = {
                        showUnhideAllFirstConfirm = false
                        showUnhideAllSecondConfirm = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A))
                ) {
                    Text("Proceed")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnhideAllFirstConfirm = false }) {
                    Text("Cancel")
                }
            },
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Unhide All Dialog 2 (Double confirmation required)
    if (showUnhideAllSecondConfirm) {
        AlertDialog(
            onDismissRequest = { showUnhideAllSecondConfirm = false },
            title = { Text("Are you absolutely sure?") },
            text = { Text("Double confirmation: Do you want to export all private vault items back to Pictures and Movies?", color = if (isDark) Color.White else Color.Black) },
            confirmButton = {
                Button(
                    onClick = {
                        showUnhideAllSecondConfirm = false
                        viewModel.unhideAllContent()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A))
                ) {
                    Text("Unhide Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnhideAllSecondConfirm = false }) {
                    Text("Cancel")
                }
            },
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Export progress dialog overlay
    if (exportProgress != null) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Exporting Content...") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(progress = { exportProgress!! }, color = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Please wait, moving files...", color = if (isDark) Color.White else Color.Black)
                }
            },
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Export summary dialog
    if (exportSummary != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearExportSummary() },
            title = { Text("Export Complete") },
            text = { Text(exportSummary!!, color = if (isDark) Color.White else Color.Black) },
            confirmButton = {
                Button(onClick = { viewModel.clearExportSummary() }) {
                    Text("OK")
                }
            },
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Recently Deleted Trash Viewer Dialog
    if (showTrashViewerDialog) {
        AlertDialog(
            onDismissRequest = { showTrashViewerDialog = false },
            title = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Recently Deleted")
                    if (trashedItems.isNotEmpty()) {
                        TextButton(onClick = { viewModel.emptyTrash() }) {
                            Text("Empty", color = Color(0xFFFF453A))
                        }
                    }
                }
            },
            text = {
                if (trashedItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        Text("No items in Trash", color = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(trashedItems) { item ->
                            val daysRemaining = remember(item.trashExpiryDate) {
                                val expiry = item.trashExpiryDate ?: 0L
                                val diff = expiry - System.currentTimeMillis()
                                val days = (diff / (24 * 60 * 60 * 1000)).coerceAtLeast(0L)
                                days
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDark) Color(0x0FFFFFFF) else Color(0x0A000000))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail
                                AsyncImage(
                                    model = if (item.type == "video") item.thumbnailPath else item.storedPath,
                                    contentDescription = "Trash item",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.fileName,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                                        color = if (isDark) Color.White else Color.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$daysRemaining days left",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFFF453A)
                                    )
                                }

                                Row {
                                    IconButton(onClick = { viewModel.restoreMediaItem(item) }) {
                                        Icon(Icons.Filled.Restore, "Restore", tint = if (isDark) Color(0xFF30D158) else Color(0xFF007AFF))
                                    }
                                    IconButton(onClick = { viewModel.deleteMediaPermanently(item) }) {
                                        Icon(Icons.Filled.Delete, "Delete Permanently", tint = Color(0xFFFF453A))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showTrashViewerDialog = false }) {
                    Text("Done")
                }
            },
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Privacy Policy dialog
    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = { Text("Privacy Policy") },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    item {
                        Text(
                            text = """
                                Simple Calculator Vault respects your absolute privacy.
                                
                                1. Personal Data:
                                The app does not collect, track, upload, or transmit any of your personal images, videos, passwords, or usage telemetry. Everything is stored strictly locally on your physical device.
                                
                                2. Storage & Files:
                                Your private photos and videos are hashed and stored in the application's private, sandbox database. Other apps, system galleries, and file explorers cannot access, view, or parse these vault files.
                                
                                3. Hashed Passcode:
                                Your secret calculator passcode is secured using salt and irreversible SHA-256 local encryption hashes. There is no remote backup cloud of this passcode.
                                
                                4. Offline First:
                                The application operates 100% offline. No internet permission is declared in the manifest, preventing any possibility of data leakage.
                                
                                Privacy matters. Enjoy a truly safe digital vault disguised as a simple calculator.
                            """.trimIndent(),
                            color = if (isDark) Color.White else Color.Black,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacyPolicyDialog = false }) {
                    Text("I Understand")
                }
            },
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

// --- Visual Helpers ---
@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        ),
        color = Color(0xFF8E8E93),
        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
    )
}

@Composable
fun SettingsGroupCard(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassmorphic(darkMode = isDark, cornerRadius = 20.dp, elevation = 4.dp)
                .padding(4.dp)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = if (isDark) Color.White else Color.Black)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93))
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = if (isDark) Color(0xFF30D158) else Color(0xFF30D158)
            )
        )
    }
}

@Composable
fun SettingsClickableRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = if (isDark) Color.White else Color.Black)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93))
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Navigate",
            tint = if (isDark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)
        )
    }
}

