package com.familyhub.ui.screens.home

import com.familyhub.ui.components.FhIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.components.RemoteImage
import com.familyhub.ui.theme.FhTheme

@Composable
fun UserProfileCard(
    member: FamilyMember,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    familyMembers: List<FamilyMember> = listOf(member),
    weeklyScore: Int = 0,
) {
    var showNegativeScoreInfo by remember { mutableStateOf(false) }
    val familyDescription = stringResource(
        R.string.home_redesign_family_members_cd,
        familyMembers.joinToString { it.displayLabel() },
    )
    val weeklyScoreLabel = stringResource(R.string.home_redesign_week_score, weeklyScore)
    val negativeScoreCd = stringResource(R.string.home_redesign_week_score_negative_cd)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = FhTheme.spacing.lg,
                vertical = FhTheme.spacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MemberAvatar(
                    member = member,
                    size = 48,
                )
                Spacer(modifier = Modifier.width(FhTheme.spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_redesign_greeting, member.displayLabel()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = onEditClick,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        imageVector = FhIcons.Edit,
                        contentDescription = stringResource(R.string.edit_profile),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (familyMembers.size > 1) {
                    Row(
                        modifier = Modifier.semantics { contentDescription = familyDescription },
                        horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.xs),
                    ) {
                        familyMembers.forEach { familyMember ->
                            MemberAvatar(
                                member = familyMember,
                                size = 28,
                                decorative = true,
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                val scoreModifier = if (weeklyScore < 0) {
                    Modifier
                        .clip(CircleShape)
                        .clickable { showNegativeScoreInfo = true }
                        .semantics { contentDescription = negativeScoreCd }
                } else {
                    Modifier
                }
                Surface(
                    modifier = scoreModifier,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(
                        text = weeklyScoreLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }

    if (showNegativeScoreInfo) {
        AlertDialog(
            onDismissRequest = { showNegativeScoreInfo = false },
            title = { Text(text = weeklyScoreLabel) },
            text = {
                Text(text = stringResource(R.string.home_redesign_week_score_negative_explanation))
            },
            confirmButton = {
                TextButton(onClick = { showNegativeScoreInfo = false }) {
                    Text(text = stringResource(R.string.common_ok))
                }
            },
        )
    }
}

@Composable
private fun MemberAvatar(
    member: FamilyMember,
    size: Int,
    decorative: Boolean = false,
) {
    val modifier = Modifier
        .size(size.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    val description = if (decorative) null else member.displayLabel()

    if (!member.photoUrl.isNullOrBlank()) {
        RemoteImage(
            url = member.photoUrl,
            contentDescription = description,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            val initial = member.displayLabel().trim().firstOrNull()?.uppercase()
            if (initial != null) {
                Text(
                    text = initial,
                    style = if (size >= 48) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    imageVector = FhIcons.Person,
                    contentDescription = description,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(if (size >= 48) 10.dp else 6.dp),
                )
            }
        }
    }
}
