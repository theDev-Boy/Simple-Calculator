package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PasscodeSetupState
import com.example.ui.VaultViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CalculatorScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val expression by viewModel.calculatorExpression.collectAsState()
    val result by viewModel.calculatorResult.collectAsState()
    val message by viewModel.displayMessage.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()

    // Shake animation state for passcode mismatch
    val shakeOffset = remember { Animatable(0f) }
    
    LaunchedEffect(key1 = true) {
        viewModel.showShakeTrigger.collectLatest {
            // Haptic vibration is triggered in ViewModel, here we animate the shake
            shakeOffset.animateTo(
                targetValue = 20f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
            )
            shakeOffset.animateTo(
                targetValue = -20f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
            )
            shakeOffset.animateTo(
                targetValue = 10f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
            )
            shakeOffset.animateTo(
                targetValue = -10f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
            )
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    val backgroundColor = if (isDark) Color(0xFF0D0D0F) else Color(0xFFF2F2F7)
    val displayTextColor = if (isDark) Color.White else Color.Black
    val messageTextColor = if (isDark) Color(0xFF98989D) else Color(0xFF8E8E93)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App header (Disguised subtitle)
        Text(
            text = "Simple Calculator",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp
            ),
            color = messageTextColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = TextAlign.Center
        )

        // Calculator display area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = shakeOffset.value.dp)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Status/Instruction Message for passcode sequence
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp
                ),
                color = if (message.contains("mismatch") || message.contains("must be")) Color(0xFFFF453A) else messageTextColor,
                modifier = Modifier.padding(bottom = 12.dp, end = 8.dp),
                textAlign = TextAlign.End
            )

            // Current equation or entered numbers
            Text(
                text = expression.ifEmpty { "0" },
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = if (expression.length > 10) 36.sp else 48.sp,
                    fontWeight = FontWeight.Light
                ),
                color = displayTextColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .testTag("calculator_display")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Result display (for calculations)
            if (result.isNotEmpty()) {
                Text(
                    text = "= $result",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = if (isDark) Color(0xFF30D158) else Color(0xFF007AFF),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Calculator keypad layout
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val buttons = listOf(
                listOf("AC", "(", ")", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("C", "0", ".", "=")
            )

            for (row in buttons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (btn in row) {
                        CalculatorButton(
                            text = btn,
                            onClick = { viewModel.onCalculatorButtonPress(btn) },
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            isDark = isDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "button_scale"
    )

    // Colors mapping to authentic calculators (iOS inspired)
    val buttonColor = when (text) {
        "AC", "C", "(", ")" -> if (isDark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)
        "÷", "×", "-", "+", "=" -> if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
        else -> if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
    }

    val textColor = when (text) {
        "AC", "C", "(", ")" -> if (isDark) Color.White else Color.Black
        "÷", "×", "-", "+", "=" -> Color.White
        else -> if (isDark) Color.White else Color.Black
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(buttonColor)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current
            ) {
                onClick()
            }
            .testTag("calc_btn_$text")
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = if (text.length > 1) 20.sp else 26.sp,
                fontWeight = FontWeight.Medium
            ),
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}
