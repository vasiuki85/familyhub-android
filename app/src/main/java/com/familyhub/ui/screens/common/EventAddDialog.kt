package com.familyhub.ui.screens.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import com.familyhub.R
import com.familyhub.domain.model.FamilyEvent
import com.familyhub.domain.model.EventRecurrence
import com.familyhub.ui.components.FhDialogFormBody
import com.familyhub.ui.components.runFormValidation
import com.familyhub.ui.components.fhDialogAction
import com.familyhub.ui.theme.FhTheme
import com.familyhub.util.TimeUtils
import com.familyhub.ui.common.toTitleRes
import java.util.Calendar
import android.app.TimePickerDialog
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventAddDialog(
    event: FamilyEvent? = null, // null creates an event; non-null edits it
    initialDate: LocalDate? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, Int, EventRecurrence, Int?) -> Unit
) {
    var title by remember { mutableStateOf(event?.title ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    val zoneId = remember { ZoneId.systemDefault() }
    val initialDateTime = remember(event?.startDateTime) {
        event?.startDateTime ?: System.currentTimeMillis()
    }
    val initialPickerDateMillis = remember(event?.startDateTime, initialDate, initialDateTime, zoneId) {
        EventDateTimePolicy.initialPickerDateMillis(
            eventStartDateTime = event?.startDateTime,
            requestedDate = initialDate,
            nowMillis = initialDateTime,
            zoneId = zoneId,
        )
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialPickerDateMillis
    )
    val initialCalendar = remember(initialDateTime) {
        Calendar.getInstance().apply { timeInMillis = initialDateTime }
    }
    var selectedHour by remember { mutableStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(initialCalendar.get(Calendar.MINUTE)) }
    var durationText by remember { mutableStateOf(event?.durationMinutes?.toString() ?: "60") }
    var recurrence by remember { mutableStateOf(event?.recurrence ?: EventRecurrence.NONE) }
    var recurrenceExpanded by remember { mutableStateOf(false) }
    var reminderExpanded by remember { mutableStateOf(false) }
    var reminderMinutes by remember { mutableStateOf(event?.reminderMinutesBefore) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var durationError by remember { mutableStateOf<String?>(null) }
    val validationTitleEmpty = stringResource(R.string.validation_event_title_empty)
    val validationDurationInvalid = stringResource(R.string.validation_event_duration_invalid)
    val titleFocusRequester = remember { FocusRequester() }
    val durationFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val reminderOptions = listOf<Int?>(null, 5, 15, 60, 24 * 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(FhTheme.radius.card),
        title = {
            Text(
                text = if (event == null) {
                    stringResource(R.string.event_dialog_add_title)
                } else {
                    stringResource(R.string.event_dialog_edit_title)
                }
            )
        },
        text = {
            FhDialogFormBody {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = null
                    },
                    label = { Text(stringResource(R.string.event_dialog_name_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester),
                    singleLine = true,
                    isError = titleError != null,
                    supportingText = titleError?.let { error -> { Text(error) } },
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .fhDialogAction(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                    )
                    val selectedDate = datePickerState.selectedDateMillis
                    val dateText = selectedDate?.let { selectedDateMillis ->
                        EventDateTimePolicy.pickerDate(selectedDateMillis).format(
                            DateTimeFormatter
                                .ofLocalizedDate(FormatStyle.MEDIUM)
                                .withLocale(Locale.getDefault())
                        )
                    } ?: stringResource(R.string.event_dialog_pick_date)
                    Text(
                        text = dateText,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                selectedHour = hour
                                selectedMinute = minute
                            },
                            selectedHour,
                            selectedMinute,
                            true
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .fhDialogAction(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                    )
                    val selectedDateMillis =
                        datePickerState.selectedDateMillis ?: initialPickerDateMillis
                    val selectedDateTime = EventDateTimePolicy.combinePickerDateAndTime(
                        selectedDateMillis = selectedDateMillis,
                        hour = selectedHour,
                        minute = selectedMinute,
                        zoneId = zoneId,
                    )
                    Text(
                        text = stringResource(
                            R.string.event_dialog_time_value,
                            TimeUtils.formatTime(selectedDateTime)
                        ),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                OutlinedTextField(
                    value = durationText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } || input.isEmpty()) {
                            durationText = input
                            durationError = null
                        }
                    },
                    label = { Text(stringResource(R.string.event_dialog_duration_label)) },
                    placeholder = { Text(stringResource(R.string.event_dialog_duration_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(durationFocusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    isError = durationError != null,
                    supportingText = durationError?.let { error -> { Text(error) } },
                )

                ExposedDropdownMenuBox(
                    expanded = recurrenceExpanded,
                    onExpandedChange = { recurrenceExpanded = it }
                ) {
                    OutlinedTextField(
                        value = stringResource(recurrence.toTitleRes()),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.event_dialog_recurrence_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recurrenceExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = recurrenceExpanded,
                        onDismissRequest = { recurrenceExpanded = false }
                    ) {
                        EventRecurrence.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.toTitleRes())) },
                                onClick = {
                                    recurrence = option
                                    recurrenceExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = reminderExpanded,
                    onExpandedChange = { reminderExpanded = it }
                ) {
                    OutlinedTextField(
                        value = reminderMinutes.toReminderText(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.event_dialog_reminder_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reminderExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = reminderExpanded,
                        onDismissRequest = { reminderExpanded = false }
                    ) {
                        reminderOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.toReminderText()) },
                                onClick = {
                                    reminderMinutes = option
                                    reminderExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    runFormValidation(
                        validators = listOf(
                            {
                                if (title.isBlank()) validationTitleEmpty else null
                            } to { titleError = it },
                            {
                                val duration = durationText.toIntOrNull()?.takeIf { value -> value > 0 }
                                if (duration == null) validationDurationInvalid else null
                            } to { durationError = it },
                        ),
                        focusRequesters = listOf(titleFocusRequester, durationFocusRequester),
                    ) {
                        val selectedDate =
                            datePickerState.selectedDateMillis ?: initialPickerDateMillis
                        val selectedDateTime = EventDateTimePolicy.combinePickerDateAndTime(
                            selectedDateMillis = selectedDate,
                            hour = selectedHour,
                            minute = selectedMinute,
                            zoneId = zoneId,
                        )
                        val duration = durationText.toIntOrNull() ?: return@runFormValidation
                        onConfirm(title.trim(), selectedDateTime, duration, recurrence, reminderMinutes)
                    }
                },
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(
                    text = if (event == null) {
                        stringResource(R.string.event_dialog_create)
                    } else {
                        stringResource(R.string.event_dialog_save)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.fhDialogAction(),
                ) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.fhDialogAction(),
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            shape = RoundedCornerShape(FhTheme.radius.card),
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun Int?.toReminderText(): String {
    return when (this) {
        null -> stringResource(R.string.event_dialog_reminder_none)
        5 -> stringResource(R.string.event_dialog_reminder_minutes, 5)
        15 -> stringResource(R.string.event_dialog_reminder_minutes, 15)
        60 -> stringResource(R.string.event_dialog_reminder_hour)
        24 * 60 -> stringResource(R.string.event_dialog_reminder_day)
        else -> stringResource(R.string.event_dialog_reminder_minutes, this)
    }
}
