package com.familyhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.utils.displayLabel

@Composable
fun FhMemberAvatar(
    member: FamilyMember,
    size: Dp,
    modifier: Modifier = Modifier,
    decorative: Boolean = false,
) {
    val avatarModifier = modifier
        .size(size)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    val description = if (decorative) null else member.displayLabel()

    if (!member.photoUrl.isNullOrBlank()) {
        RemoteImage(
            url = member.photoUrl,
            contentDescription = description,
            modifier = avatarModifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = avatarModifier,
            contentAlignment = Alignment.Center,
        ) {
            val initial = member.displayLabel().trim().firstOrNull()?.uppercaseChar()
            if (initial != null) {
                Text(
                    text = initial.toString(),
                    style = if (size >= 32.dp) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.labelSmall
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    imageVector = FhIcons.Person,
                    contentDescription = description,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(size / 4),
                )
            }
        }
    }
}
