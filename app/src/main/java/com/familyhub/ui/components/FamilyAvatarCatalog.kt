package com.familyhub.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.utils.BundledFamilyAvatar

data class FamilyAvatarOption(
    val token: String,
    @DrawableRes val drawableRes: Int,
    @StringRes val descriptionRes: Int,
)

object FamilyAvatarCatalog {
    val options = listOf(
        FamilyAvatarOption(BundledFamilyAvatar.tokens[0], R.drawable.fh_family_avatar_01, R.string.family_avatar_01),
        FamilyAvatarOption(BundledFamilyAvatar.tokens[1], R.drawable.fh_family_avatar_02, R.string.family_avatar_02),
        FamilyAvatarOption(BundledFamilyAvatar.tokens[2], R.drawable.fh_family_avatar_03, R.string.family_avatar_03),
        FamilyAvatarOption(BundledFamilyAvatar.tokens[3], R.drawable.fh_family_avatar_04, R.string.family_avatar_04),
        FamilyAvatarOption(BundledFamilyAvatar.tokens[4], R.drawable.fh_family_avatar_05, R.string.family_avatar_05),
        FamilyAvatarOption(BundledFamilyAvatar.tokens[5], R.drawable.fh_family_avatar_06, R.string.family_avatar_06),
        FamilyAvatarOption(BundledFamilyAvatar.tokens[6], R.drawable.fh_family_avatar_07, R.string.family_avatar_07),
        FamilyAvatarOption(BundledFamilyAvatar.tokens[7], R.drawable.fh_family_avatar_08, R.string.family_avatar_08),
    )

    @DrawableRes
    fun drawableFor(token: String?): Int? = options.firstOrNull { it.token == token }?.drawableRes
}

@Composable
fun FamilyAvatarPicker(
    selectedToken: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.family_avatar_picker_title),
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(FamilyAvatarCatalog.options, key = FamilyAvatarOption::token) { option ->
                val description = stringResource(option.descriptionRes)
                Image(
                    painter = painterResource(option.drawableRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (selectedToken == option.token) 3.dp else 1.dp,
                            color = if (selectedToken == option.token) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .selectable(
                            selected = selectedToken == option.token,
                            role = Role.RadioButton,
                            onClick = { onSelected(option.token) },
                        )
                        .semantics { contentDescription = description },
                )
            }
        }
    }
}
