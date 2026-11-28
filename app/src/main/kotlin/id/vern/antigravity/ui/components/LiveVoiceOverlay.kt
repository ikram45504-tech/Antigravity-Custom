package id.vern.antigravity.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.vern.antigravity.R
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityGreen
import id.vern.antigravity.ui.theme.AntigravityRed
import id.vern.antigravity.voice.GeminiLiveConnectionState
import id.vern.antigravity.voice.GeminiLiveUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVoiceOverlay(
    state: GeminiLiveUiState,
    micAmplitude: Float,
    speakerAmplitude: Float,
    onToggleMute: () -> Unit,
    onEndSession: () -> Unit
) {
    if (!state.active) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isSpeaking = state.connectionState == GeminiLiveConnectionState.SPEAKING
    val rawAmp = if (isSpeaking) speakerAmplitude else micAmplitude
    val smoothAmp by animateFloatAsState(
        targetValue = rawAmp.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "liveOrbAmp"
    )

    val primaryAccent = when (state.connectionState) {
        GeminiLiveConnectionState.SPEAKING -> AntigravityGreen
        GeminiLiveConnectionState.ERROR -> AntigravityRed
        else -> AntigravityBlue
    }

    ModalBottomSheet(
        onDismissRequest = onEndSession,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_live_sparkle),
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.statusMessage.ifBlank { "Live" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (!state.activeToolName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AntigravityBlue.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = AntigravityBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Running ${state.activeToolName}...",
                            fontSize = 11.5.sp,
                            color = AntigravityBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Audio-reactive Live orb
            Box(
                modifier = Modifier.size(170.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(170.dp)) {
                    val baseR = size.minDimension * 0.28f
                    val outerR = baseR * (1.25f + smoothAmp * 0.75f)
                    val midR = baseR * (1.08f + smoothAmp * 0.45f)

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = 0.26f),
                                Color.Transparent
                            )
                        ),
                        radius = outerR
                    )
                    drawCircle(
                        color = primaryAccent.copy(alpha = 0.22f),
                        radius = midR
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = 0.9f),
                                primaryAccent.copy(alpha = 0.55f)
                            )
                        ),
                        radius = baseR
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_live_sparkle),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.userTranscript.isNotBlank()) {
                    Text(
                        text = state.userTranscript,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (state.modelTranscript.isNotBlank()) {
                    Text(
                        text = state.modelTranscript,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isMuted) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            CircleShape
                        )
                        .clickable { onToggleMute() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (state.isMuted) "Unmute" else "Mute",
                        tint = if (state.isMuted) AntigravityRed else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AntigravityRed.copy(alpha = 0.18f))
                        .border(1.dp, AntigravityRed.copy(alpha = 0.45f), CircleShape)
                        .clickable { onEndSession() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "End Live",
                        tint = AntigravityRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
