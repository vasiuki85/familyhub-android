package com.familyhub.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.ui.theme.components.FHTonalButton

@Composable
fun FamilyOnboardingCard(
    model: FamilyOnboardingUiModel,
    onCreateFamily: () -> Unit,
    onJoinFamily: () -> Unit,
    onContinueLocally: () -> Unit,
    onConnectFamily: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!model.visible || model.titleResId == null || model.messageResId == null) {
        return
    }

    val bannerDescription = model.contentDescriptionResId?.let { stringResource(it) }
        ?: stringResource(model.titleResId)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = bannerDescription },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(model.titleResId),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(model.messageResId),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (model.compact) {
                FHTonalButton(onClick = onConnectFamily, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_family_onboarding_connect))
                }
            } else {
                FHTonalButton(onClick = onCreateFamily, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_family_onboarding_create))
                }
                FHTonalButton(onClick = onJoinFamily, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_family_onboarding_join))
                }
                FHTonalButton(onClick = onContinueLocally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_family_onboarding_continue_local))
                }
            }
        }
    }
}
