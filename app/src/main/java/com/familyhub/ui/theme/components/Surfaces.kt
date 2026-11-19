package com.familyhub.ui.theme.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.familyhub.ui.theme.FhTheme

@Composable
fun ThemedCard(
    modifier: Modifier = Modifier,
    tone: FhCardTone = FhCardTone.NEUTRAL,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(FhTheme.radius.card)
    val scheme = MaterialTheme.colorScheme
    val surfaceColor = tone.resolveColor(scheme.isLight()) ?: scheme.surface
    Surface(
        modifier = modifier,
        shape = shape,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        color = surfaceColor
    ) {
        Column(modifier = Modifier.padding(FhTheme.spacing.lg), content = content)
    }
}

@Composable
internal fun androidx.compose.material3.ColorScheme.isLight(): Boolean =
    this.background.luminance() > 0.5f

enum class FhCardTone {
    NEUTRAL,
    ACCENT,
    POSITIVE,
    REWARD,
    ATTENTION,
    INFO
}

internal fun FhCardTone.resolveColor(isLight: Boolean): Color? = when (this) {
    FhCardTone.ACCENT -> if (isLight) ToneAccentLight else ToneAccentDark
    FhCardTone.POSITIVE -> if (isLight) TonePositiveLight else TonePositiveDark
    FhCardTone.REWARD -> if (isLight) ToneRewardLight else ToneRewardDark
    FhCardTone.ATTENTION -> if (isLight) ToneAttentionLight else ToneAttentionDark
    FhCardTone.INFO -> if (isLight) ToneInfoLight else ToneInfoDark
    FhCardTone.NEUTRAL -> null
}

private val ToneAccentLight = Color(0xFFFFDBCF)
private val ToneAccentDark = Color(0xFF5A2A17)
private val TonePositiveLight = Color(0xFFDCE9D5)
private val TonePositiveDark = Color(0xFF263320)
private val ToneRewardLight = Color(0xFFF5E3AC)
private val ToneRewardDark = Color(0xFF4A3D14)
private val ToneAttentionLight = Color(0xFFFFDAD6)
private val ToneAttentionDark = Color(0xFF5C1A16)
private val ToneInfoLight = Color(0xFFDBE4EA)
private val ToneInfoDark = Color(0xFF223038)
