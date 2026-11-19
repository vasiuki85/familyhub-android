package com.familyhub.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.usecase.TaskCompletionFeedback

@Composable
fun TaskCompletionFeedbackCard(
    feedback: TaskCompletionFeedback,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = feedback.summary,
                style = MaterialTheme.typography.bodyLarge
            )

            if (feedback.achievements.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.task_completion_feedback_achievements_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp)
                )

                feedback.achievements.forEach { achievement ->
                    Text(
                        text = stringResource(
                            R.string.task_completion_feedback_achievement_item,
                            achievement.title
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = achievement.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.common_close))
            }
        }
    }
}
