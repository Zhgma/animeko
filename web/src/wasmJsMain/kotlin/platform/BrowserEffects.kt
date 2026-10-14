package me.him188.ani.app.ui.foundation.effects
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
fun Modifier.blurEffect(radius: Dp, edgeTreatment: BlurredEdgeTreatment) = blur(radius, edgeTreatment)
