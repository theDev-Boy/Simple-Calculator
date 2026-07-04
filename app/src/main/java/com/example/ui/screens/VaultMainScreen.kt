package com.example.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VaultViewModel
import com.example.ui.theme.glassmorphic

enum class VaultTab {
    Photos,
    Videos,
    Settings
}

@Composable
fun VaultMainScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(VaultTab.Photos) }
    val isDark by viewModel.isDarkMode.collectAsState()
    val context = LocalContext.current

    // NOTE: FLAG_SECURE is commented out because this app runs in a browser-based streaming emulator.
    // Setting FLAG_SECURE blocks the emulator's screen-sharing/capture stream, making the screen completely black.
    // You can uncomment this block when deploying to a physical device to enable screenshot and recents protection!
    /*
    DisposableEffect(key1 = true) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    */

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = if (isDark) Color(0xFF0D0D0F) else Color(0xFFF2F2F7),
        bottomBar = {
            GlassyBottomNavigationBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    viewModel.performHapticFeedback()
                    currentTab = tab
                },
                isDark = isDark
            )
        }
    ) { innerPadding ->
        // Animated transition between screens with shared axis slide + fade
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                val direction = if (initialState.ordinal < targetState.ordinal) 1 else -1
                slideInHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) { width -> direction * width / 2 } + fadeIn(animationSpec = tween(300)) togetherWith
                slideOutHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) { width -> -direction * width / 2 } + fadeOut(animationSpec = tween(300))
            },
            modifier = Modifier.padding(innerPadding),
            label = "tab_transitions"
        ) { tab ->
            when (tab) {
                VaultTab.Photos -> PhotosTabScreen(viewModel = viewModel)
                VaultTab.Videos -> VideosTabScreen(viewModel = viewModel)
                VaultTab.Settings -> SettingsTabScreen(viewModel = viewModel, onLockRequest = {
                    viewModel.lockVault()
                })
            }
        }
    }
}

@Composable
fun GlassyBottomNavigationBar(
    currentTab: VaultTab,
    onTabSelected: (VaultTab) -> Unit,
    isDark: Boolean
) {
    // Determine nav bar height based on selection: "When active section is Videos, bottom nav bar smoothly expands in height by 6dp and icons get 10% larger"
    val isVideosTab = currentTab == VaultTab.Videos
    
    val navHeight by animateDpAsState(
        targetValue = if (isVideosTab) 78.dp else 72.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "nav_height"
    )

    val iconBaseScale = if (isVideosTab) 1.10f else 1.0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(navHeight)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
            .glassmorphic(darkMode = isDark, cornerRadius = 24.dp, elevation = 12.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple(VaultTab.Photos, "Photos", Pair(Icons.Filled.Image, Icons.Outlined.Image)),
                Triple(VaultTab.Videos, "Videos", Pair(Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle)),
                Triple(VaultTab.Settings, "Settings", Pair(Icons.Filled.Settings, Icons.Outlined.Settings))
            )

            for ((tab, label, icons) in tabs) {
                val isSelected = currentTab == tab
                
                // Spring scale animation for selected icon
                val selectionScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "tab_icon_scale"
                )

                val displayScale = selectionScale * iconBaseScale
                val activeIconColor = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
                val inactiveIconColor = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93)

                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            onTabSelected(tab)
                        }
                        .testTag("tab_button_${label.lowercase()}")
                ) {
                    Icon(
                        imageVector = if (isSelected) icons.first else icons.second,
                        contentDescription = label,
                        modifier = Modifier
                            .size(if (isVideosTab) 28.dp else 24.dp)
                            .graphicsLayer {
                                scaleX = displayScale
                                scaleY = displayScale
                            },
                        tint = if (isSelected) activeIconColor else inactiveIconColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isSelected) activeIconColor else inactiveIconColor
                    )
                }
            }
        }
    }
}
