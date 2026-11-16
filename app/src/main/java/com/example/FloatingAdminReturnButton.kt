package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Exact 5 User Application routes where an authorized Admin in Preview Mode sees the
 * Floating Admin Return Icon (`Home`, `Control`, `AI`, `Progress`, `Profile`).
 */
val USER_APP_PREVIEW_ROUTES: Set<String> = setOf(
    "dashboard/{username}",
    "hand_control",
    "ai_assistant",
    "reports",
    "profile/{username}"
)

/**
 * Floating shield / Admin Panel return button visible exclusively to verified,
 * active administrators inspecting the NeuroGrip User Application in Admin Preview Mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingAdminReturnButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.85f, animationSpec = tween(150)),
        modifier = modifier
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip {
                    Text(
                        text = "Return to Admin Panel",
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            state = rememberTooltipState()
        ) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                shadowElevation = 8.dp,
                tonalElevation = 6.dp,
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, Color(0xFF93C5FD).copy(alpha = 0.9f)),
                modifier = Modifier
                    .sizeIn(minWidth = 52.dp, minHeight = 52.dp)
                    .size(52.dp)
                    .semantics { contentDescription = "Return to Admin Panel" }
                    .testTag("floating_admin_return_button")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0F172A),
                                    Color(0xFF1E3A8A)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(25.dp)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(8.dp)
                            .background(Color(0xFF22C55E), CircleShape)
                    )
                }
            }
        }
    }
}
