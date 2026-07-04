package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.VaultViewModel
import com.example.ui.screens.CalculatorScreen
import com.example.ui.screens.VaultMainScreen
import com.example.ui.theme.SimpleCalculatorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Configure immersive full-screen mode right at creation
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

        setContent {
            val viewModel: VaultViewModel = viewModel()
            val isDark by viewModel.isDarkMode.collectAsState()
            val isUnlocked by viewModel.isUnlocked.collectAsState()
            val context = LocalContext.current

            // Toast feedback collectors
            LaunchedEffect(key1 = true) {
                viewModel.showToast.collectLatest { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }

            SimpleCalculatorTheme(darkTheme = isDark) {
                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(key1 = true) {
                    delay(1000) // 1 second launcher splash feel
                    showSplash = false
                }

                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(200),
                    label = "splash_fade"
                ) { isSplashActive ->
                    if (isSplashActive) {
                        SplashScreen(isDark = isDark)
                    } else {
                        // Main Application screen routing based on authentication
                        Crossfade(
                            targetState = isUnlocked,
                            animationSpec = tween(300),
                            label = "screen_auth_switch"
                        ) { unlocked ->
                            if (unlocked) {
                                VaultMainScreen(viewModel = viewModel)
                            } else {
                                CalculatorScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(isDark: Boolean) {
    val backgroundColor = if (isDark) Color(0xFF0D0D0F) else Color(0xFFF2F2F7)
    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDark) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardColor),
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
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0xFF30D158)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Lock",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Simple Calculator",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = textColor
            )
        }
    }
}
