package com.familyhub.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.data.export.ExportFormat
import com.familyhub.data.export.PlaintextExportPendingRequest
import com.familyhub.ui.components.fhDialogAction
import com.familyhub.ui.theme.FhTheme

@Composable
fun PlaintextExportWarningDialog(
    request: PlaintextExportPendingRequest,
    isInProgress: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val fileTypeA11y = when (request) {
        is PlaintextExportPendingRequest.Format -> when (request.format) {
            ExportFormat.CSV -> stringResource(R.string.plaintext_export_a11y_file_type_csv)
            ExportFormat.JSON -> stringResource(R.string.plaintext_export_a11y_file_type_json)
        }
        PlaintextExportPendingRequest.Backup -> stringResource(R.string.plaintext_export_a11y_file_type_backup)
    }
    val warningA11y = stringResource(R.string.plaintext_export_a11y_warning, fileTypeA11y)

    AlertDialog(
        onDismissRequest = {
            if (!isInProgress) {
                onCancel()
            }
        },
        shape = RoundedCornerShape(FhTheme.radius.card),
        title = {
            Text(text = stringResource(R.string.plaintext_export_warning_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = warningA11y }
            ) {
                Text(
                    text = stringResource(R.string.plaintext_export_warning_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = fileTypeA11y,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isInProgress,
                modifier = Modifier.fhDialogAction(),
            ) {
                if (isInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text(stringResource(R.string.plaintext_export_warning_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isInProgress,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.plaintext_export_warning_cancel))
            }
        },
    )
}
