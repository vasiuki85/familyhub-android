package com.familyhub.ui.screens.tasks

import com.familyhub.ui.components.FhCheckboxRow
import com.familyhub.ui.components.FhIcons
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.familyhub.ui.theme.components.FHButton
import com.familyhub.ui.theme.components.FHTonalButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.documentfile.provider.DocumentFile
import com.familyhub.R
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskAttachment
import com.familyhub.domain.model.TaskRotationConfig
import com.familyhub.domain.model.TaskRotationFrequency
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskComment
import com.familyhub.domain.model.TaskType
import com.familyhub.domain.model.TaskReminderMode
import com.familyhub.domain.model.TaskTemplate
import com.familyhub.domain.model.TaskCategory
import com.familyhub.domain.utils.TaskReminderSchedule
import com.familyhub.domain.utils.displayLabel
import com.familyhub.util.TimeUtils
import java.util.Calendar
import java.util.UUID
import timber.log.Timber

internal const val TASK_EDITOR_CANCEL_TEST_TAG = "task_editor_cancel_action"
internal const val TASK_EDITOR_CREATE_TEST_TAG = "task_editor_create_action"
internal const val TASK_EDITOR_FOOTER_TEST_TAG = "task_editor_footer"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorDialog(
    members: List<FamilyMember>,
    templates: List<TaskTemplate>,
    categories: List<TaskCategory>,
    availableCategoryColors: List<Long>,
    currentUserId: String,
    currentUserRole: MemberRole,
    task: Task? = null, // Если null — создание, иначе — редактирование
    rotationConfig: TaskRotationConfig? = null,
    onDismiss: () -> Unit,
    onConfirm: (TaskEditorSaveData) -> Unit,
    onSaveTemplate: (String, String?, RepeatRule, String?, Boolean, TaskType, TaskPriority, String?) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onCreateCategory: (String, Long) -> String
) {
    val context = LocalContext.current
    val membersMap = remember(members) { members.associateBy { it.id } }
    val capabilities = remember(currentUserRole) {
        TaskRoleCapabilityPolicy.resolve(currentUserRole)
    }

    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var selectedRepeatRule by remember { mutableStateOf(task?.repeatRule ?: RepeatRule.NONE) }
    var selectedMemberId by remember {
        mutableStateOf(
            task?.assignedToUserId
                ?: TaskRoleCapabilityPolicy.resolveCreateAssignee(
                    role = currentUserRole,
                    currentUserId = currentUserId,
                    requestedAssigneeId = members.firstOrNull()?.id.orEmpty(),
                ).orEmpty(),
        )
    }
    var isPrivate by remember { mutableStateOf(task?.isPrivate ?: false) }
    var selectedTaskType by remember { mutableStateOf(task?.taskType ?: TaskType.ROUTINE) }
    var selectedPriority by remember { mutableStateOf(task?.priority ?: TaskPriority.MEDIUM) }
    var selectedDueDate by remember { mutableStateOf(task?.dueDate) }
    var dueDateHasTime by remember { mutableStateOf(task?.dueDateHasTime ?: false) }
    var reminderMode by remember { mutableStateOf(task?.reminderMode ?: TaskReminderMode.NONE) }
    var reminderMinutesOfDay by remember {
        mutableStateOf(task?.reminderMinutesOfDay ?: TaskReminderSchedule.DEFAULT_REMINDER_MINUTES_OF_DAY)
    }
    var reminderExpanded by remember { mutableStateOf(false) }
    var rewardPointsInput by rememberSaveable { mutableStateOf(task?.rewardPoints?.toString() ?: "") }
    val defaultCategoryColor = remember(availableCategoryColors) {
        availableCategoryColors.firstOrNull() ?: 0xFF2196F3.toLong()
    }
    var selectedCategoryId by remember { mutableStateOf(task?.categoryId) }
    var newCategoryName by remember { mutableStateOf("") }
    var categoryNameError by remember { mutableStateOf<String?>(null) }
    var newCategoryColor by remember { mutableStateOf(defaultCategoryColor) }
    var showCategorySheet by rememberSaveable { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(task?.attachments ?: emptyList()) }
    var comments by remember { mutableStateOf(task?.comments ?: emptyList()) }
    var commentDraft by remember { mutableStateOf("") }
    var commentError by remember { mutableStateOf<String?>(null) }
    var showAdditional by rememberSaveable(task?.id) {
        mutableStateOf(
            task?.description?.isNotBlank() == true ||
                task?.attachments?.isNotEmpty() == true ||
                task?.comments?.isNotEmpty() == true
        )
    }

    var templateExpanded by remember { mutableStateOf(false) }
    var selectedTemplateId by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDueDate?.let { TimeUtils.getStartOfDay(it) },
    )

    var memberExpanded by remember { mutableStateOf(false) }
    var rotationFrequencyExpanded by remember { mutableStateOf(false) }
    var rotationShowDatePicker by remember { mutableStateOf(false) }

    var selectedRotationMembers by remember {
        mutableStateOf(rotationConfig?.memberIds?.toSet() ?: emptySet())
    }
    var rotationEnabled by remember { mutableStateOf(rotationConfig != null) }
    var rotationFrequency by remember { mutableStateOf(rotationConfig?.frequency ?: TaskRotationFrequency.WEEKLY) }
    val initialRotationStart = rotationConfig?.startDateTime ?: TimeUtils.now()
    val rotationDatePickerState = rememberDatePickerState(initialSelectedDateMillis = initialRotationStart)
    val rotationInitialCalendar = remember(initialRotationStart) {
        Calendar.getInstance().apply { timeInMillis = initialRotationStart }
    }
    var rotationHour by remember { mutableStateOf(rotationInitialCalendar.get(Calendar.HOUR_OF_DAY)) }
    var rotationMinute by remember { mutableStateOf(rotationInitialCalendar.get(Calendar.MINUTE)) }
    var rotationError by remember { mutableStateOf<String?>(null) }
    var submitLocked by remember { mutableStateOf(false) }

    LaunchedEffect(task?.id, capabilities) {
        selectedTemplateId = null
        selectedCategoryId = task?.categoryId
        attachments = task?.attachments ?: emptyList()
        comments = task?.comments ?: emptyList()
        commentDraft = ""
        commentError = null
        rewardPointsInput = task?.rewardPoints?.toString() ?: ""
        selectedDueDate = task?.dueDate
        dueDateHasTime = task?.dueDateHasTime ?: false
        reminderMode = task?.reminderMode ?: TaskReminderMode.NONE
        reminderMinutesOfDay = task?.reminderMinutesOfDay
            ?: TaskReminderSchedule.DEFAULT_REMINDER_MINUTES_OF_DAY
        if (!capabilities.canChooseAssignee) {
            selectedMemberId = task?.assignedToUserId ?: currentUserId
        }
        if (!capabilities.canManageTaskConfiguration) {
            rotationEnabled = false
            rotationShowDatePicker = false
            showCategorySheet = false
        }
        if (!capabilities.canChooseAssignee) memberExpanded = false
    }

    LaunchedEffect(templates) {
        selectedTemplateId?.let { id ->
            if (templates.none { it.id == id }) {
                selectedTemplateId = null
            }
        }
    }

    LaunchedEffect(availableCategoryColors) {
        if (availableCategoryColors.isNotEmpty() && newCategoryColor !in availableCategoryColors) {
            newCategoryColor = availableCategoryColors.first()
        }
    }

    LaunchedEffect(categories) {
        if (selectedCategoryId != null && categories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = null
        }
    }

    val selectedTemplate = templates.firstOrNull { it.id == selectedTemplateId }

    // Validation states
    var titleError by remember { mutableStateOf<String?>(null) }
    var descriptionError by remember { mutableStateOf<String?>(null) }
    var memberError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(
        task?.id,
        members,
        currentUserId,
        capabilities.canChooseAssignee,
    ) {
        val memberIds = members.asSequence().map { it.id }.toSet()
        selectedMemberId = when {
            !capabilities.canChooseAssignee -> task?.assignedToUserId ?: currentUserId
            selectedMemberId in memberIds -> selectedMemberId
            task?.assignedToUserId?.let(memberIds::contains) == true ->
                task.assignedToUserId
            currentUserId in memberIds -> currentUserId
            else -> members.firstOrNull()?.id.orEmpty()
        }
        if (selectedMemberId.isNotBlank()) {
            memberError = null
        }
    }

    // String resources for validation
    val validationTitleEmpty = stringResource(R.string.validation_title_empty)
    val validationTitleTooLong = stringResource(R.string.validation_title_too_long)
    val validationDescriptionTooLong = stringResource(R.string.validation_description_too_long)
    val validationMemberNotSelected = stringResource(R.string.validation_member_not_selected)

    val attachmentOpenTitle = stringResource(R.string.task_attachment_open_title)
    val attachmentOpenError = stringResource(R.string.task_attachment_open_error)

    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val resolver = context.contentResolver
            runCatching {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.onFailure { Timber.w(it, "TaskEditorDialog: failed to persist attachment uri") }
            val displayName = DocumentFile.fromSingleUri(context, uri)?.name
                ?: context.getString(R.string.task_attachment_unknown_name)
            val mimeType = resolver.getType(uri)
            val newAttachment = TaskAttachment(
                id = UUID.randomUUID().toString(),
                name = displayName,
                uri = uri.toString(),
                mimeType = mimeType,
                addedAt = System.currentTimeMillis()
            )
            attachments = attachments + newAttachment
        }
    }

    fun openAttachment(attachment: TaskAttachment) {
        val uri = Uri.parse(attachment.uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, attachmentOpenTitle)
        runCatching {
            context.startActivity(chooser)
        }.onFailure {
            Timber.w(it, "TaskEditorDialog: failed to open attachment")
            Toast.makeText(context, attachmentOpenError, Toast.LENGTH_LONG).show()
        }
    }

    // Validation functions
    fun validateTitle(newTitle: String): String? {
        return when {
            newTitle.isBlank() -> validationTitleEmpty
            newTitle.length > 100 -> validationTitleTooLong
            else -> null
        }
    }

    fun validateDescription(newDescription: String): String? {
        return if (newDescription.length > 500) {
            validationDescriptionTooLong
        } else null
    }

    fun validateMember(): String? {
        return if (selectedMemberId.isBlank()) {
            validationMemberNotSelected
        } else null
    }

    val submitTask = {
        if (!submitLocked) {
        titleError = validateTitle(title)
        descriptionError = validateDescription(description)
        memberError = validateMember()

        if (titleError == null && descriptionError == null && memberError == null) {
            val rotationData = if (capabilities.canManageTaskConfiguration && rotationEnabled) {
                val normalizedMembers = selectedRotationMembers.toMutableSet().apply {
                    if (selectedMemberId.isNotBlank()) add(selectedMemberId)
                }
                if (normalizedMembers.size < 2) {
                    rotationError = context.getString(R.string.task_rotation_error_members)
                    null
                } else {
                    val selectedDate = rotationDatePickerState.selectedDateMillis
                    if (selectedDate == null) {
                        rotationError = context.getString(R.string.task_rotation_error_start)
                        null
                    } else {
                        val calendar = Calendar.getInstance().apply {
                            timeInMillis = selectedDate
                            set(Calendar.HOUR_OF_DAY, rotationHour)
                            set(Calendar.MINUTE, rotationMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        rotationError = null
                        TaskRotationEditorData(
                            enabled = true,
                            memberIds = normalizedMembers.toList(),
                            frequency = rotationFrequency,
                            startDateTime = calendar.timeInMillis
                        )
                    }
                }
            } else {
                rotationError = null
                null
            }

            val rewardPoints = rewardPointsInput.toIntOrNull()
            if (rotationError == null) {
                submitLocked = true
                onConfirm(
                    TaskEditorSaveData(
                        title = title.trim(),
                        description = description.trim().ifBlank { null },
                        dueDate = selectedDueDate,
                        dueDateHasTime = dueDateHasTime,
                        reminderMode = reminderMode,
                        reminderMinutesOfDay = reminderMinutesOfDay.takeIf {
                            reminderMode == TaskReminderMode.AT_TIME
                        },
                        repeatRule = selectedRepeatRule,
                        assignedToUserId = selectedMemberId,
                        isPrivate = isPrivate,
                        taskType = selectedTaskType,
                        priority = selectedPriority,
                        rewardPoints = rewardPoints,
                        categoryId = selectedCategoryId,
                        attachments = attachments,
                        comments = comments,
                        rotation = rotationData,
                    )
                )
            }
        }
        }
    }

    BackHandler(onBack = onDismiss)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            if (task == null) {
                                stringResource(R.string.tasks_create)
                            } else {
                                stringResource(R.string.tasks_edit)
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = FhIcons.Close,
                                contentDescription = stringResource(R.string.tasks_editor_close_cd),
                            )
                        }
                    },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.tasks_editor_essentials_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            titleError = validateTitle(it)
                        },
                        label = { Text(stringResource(R.string.task_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = titleError != null,
                        supportingText = titleError?.let { { Text(it) } },
                    )

                if (members.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = memberExpanded,
                        onExpandedChange = {
                            memberExpanded = capabilities.canChooseAssignee && it
                        },
                    ) {
                        OutlinedTextField(
                            value = members.firstOrNull { it.id == selectedMemberId }?.displayLabel().orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.task_assign_to)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            trailingIcon = if (capabilities.canChooseAssignee) {
                                {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = memberExpanded,
                                    )
                                }
                            } else {
                                null
                            },
                            isError = memberError != null,
                            supportingText = memberError?.let { { Text(it) } },
                        )
                        DropdownMenu(
                            expanded = capabilities.canChooseAssignee && memberExpanded,
                            onDismissRequest = { memberExpanded = false },
                        ) {
                            members.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text(member.displayLabel()) },
                                    onClick = {
                                        selectedMemberId = member.id
                                        memberExpanded = false
                                        memberError = validateMember()
                                        if (rotationEnabled) {
                                            selectedRotationMembers = selectedRotationMembers + member.id
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                val dueDateLabel = selectedDueDate?.let { millis ->
                    if (dueDateHasTime) {
                        TimeUtils.formatDateTime(millis)
                    } else {
                        TimeUtils.formatDate(millis)
                    }
                } ?: stringResource(R.string.task_due_not_set)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = FhIcons.CalendarToday,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(
                                text = stringResource(R.string.task_due_date),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = dueDateLabel,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    if (selectedDueDate != null) {
                        IconButton(onClick = {
                            selectedDueDate = null
                            val (hasTime, mode, minutes) = TaskReminderSchedule.normalizeAfterClearingDate()
                            dueDateHasTime = hasTime
                            reminderMode = mode
                            reminderMinutesOfDay = minutes
                                ?: TaskReminderSchedule.DEFAULT_REMINDER_MINUTES_OF_DAY
                        }) {
                            Icon(
                                imageVector = FhIcons.Close,
                                contentDescription = stringResource(R.string.task_due_clear_cd),
                            )
                        }
                    }
                }

                if (selectedDueDate != null) {
                    val dueTimeLabel = if (dueDateHasTime) {
                        TimeUtils.formatTime(selectedDueDate!!)
                    } else {
                        stringResource(R.string.task_due_time_not_set)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val (hour, minute) = if (dueDateHasTime) {
                                    TaskReminderSchedule.dueTimeParts(selectedDueDate!!)
                                } else {
                                    8 to 0
                                }
                                TimePickerDialog(
                                    context,
                                    { _, pickedHour, pickedMinute ->
                                        selectedDueDate = TaskReminderSchedule.mergeDueTime(
                                            selectedDueDate!!,
                                            pickedHour,
                                            pickedMinute,
                                        )
                                        dueDateHasTime = true
                                    },
                                    hour,
                                    minute,
                                    true,
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = FhIcons.AccessTime,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(
                                    text = stringResource(R.string.task_due_time_label),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    text = dueTimeLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                        if (dueDateHasTime) {
                            IconButton(onClick = {
                                selectedDueDate = TimeUtils.getStartOfDay(selectedDueDate!!)
                                dueDateHasTime = false
                                val (mode, minutes) = TaskReminderSchedule.normalizeAfterClearingTime(
                                    reminderMode,
                                    reminderMinutesOfDay,
                                )
                                reminderMode = mode
                                reminderMinutesOfDay = minutes
                                    ?: TaskReminderSchedule.DEFAULT_REMINDER_MINUTES_OF_DAY
                            }) {
                                Icon(
                                    imageVector = FhIcons.Close,
                                    contentDescription = stringResource(R.string.task_due_time_clear_cd),
                                )
                            }
                        }
                    }

                    val reminderOptions = TaskReminderMode.entries
                    val reminderLabel = when (reminderMode) {
                        TaskReminderMode.NONE -> stringResource(R.string.task_reminder_none)
                        TaskReminderMode.AT_DUE -> stringResource(R.string.task_reminder_at_due)
                        TaskReminderMode.ONE_HOUR_BEFORE ->
                            stringResource(R.string.task_reminder_one_hour_before)
                        TaskReminderMode.AT_TIME -> {
                            val minutes = reminderMinutesOfDay
                                ?: TaskReminderSchedule.DEFAULT_REMINDER_MINUTES_OF_DAY
                            val previewMillis = TimeUtils.getStartOfDay(selectedDueDate!!) +
                                minutes * 60_000L
                            stringResource(
                                R.string.task_reminder_at_time,
                                TimeUtils.formatTime(previewMillis),
                            )
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = reminderExpanded,
                        onExpandedChange = { reminderExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = reminderLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.task_reminder_label)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = reminderExpanded)
                            },
                        )
                        DropdownMenu(
                            expanded = reminderExpanded,
                            onDismissRequest = { reminderExpanded = false },
                        ) {
                            reminderOptions.forEach { option ->
                                val enabled = when (option) {
                                    TaskReminderMode.AT_DUE,
                                    TaskReminderMode.ONE_HOUR_BEFORE,
                                    -> dueDateHasTime
                                    else -> true
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (option) {
                                                TaskReminderMode.NONE ->
                                                    stringResource(R.string.task_reminder_none)
                                                TaskReminderMode.AT_DUE ->
                                                    stringResource(R.string.task_reminder_at_due)
                                                TaskReminderMode.ONE_HOUR_BEFORE ->
                                                    stringResource(R.string.task_reminder_one_hour_before)
                                                TaskReminderMode.AT_TIME ->
                                                    stringResource(R.string.task_reminder_at_time_option)
                                            },
                                        )
                                    },
                                    onClick = {
                                        if (!enabled) return@DropdownMenuItem
                                        reminderMode = option
                                        reminderExpanded = false
                                        if (option == TaskReminderMode.AT_TIME &&
                                            reminderMinutesOfDay == null
                                        ) {
                                            reminderMinutesOfDay =
                                                TaskReminderSchedule.DEFAULT_REMINDER_MINUTES_OF_DAY
                                        }
                                    },
                                    enabled = enabled,
                                )
                            }
                        }
                    }

                    if (reminderMode == TaskReminderMode.AT_TIME) {
                        val reminderMinutes = reminderMinutesOfDay
                            ?: TaskReminderSchedule.DEFAULT_REMINDER_MINUTES_OF_DAY
                        val reminderHour = reminderMinutes / 60
                        val reminderMinute = reminderMinutes % 60
                        FHTonalButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, pickedHour, pickedMinute ->
                                        reminderMinutesOfDay = pickedHour * 60 + pickedMinute
                                    },
                                    reminderHour,
                                    reminderMinute,
                                    true,
                                ).show()
                            },
                        ) {
                            Icon(
                                imageVector = FhIcons.AccessTime,
                                contentDescription = stringResource(R.string.task_reminder_time_pick_cd),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.task_reminder_set_time))
                        }
                    }
                }

                if (currentUserRole == MemberRole.PARENT || currentUserRole == MemberRole.ADMIN) {
                    OutlinedTextField(
                        value = rewardPointsInput,
                        onValueChange = { newValue ->
                            rewardPointsInput = newValue.filterIndexed { index, char ->
                                (char == '-' && index == 0) || char.isDigit()
                            }
                        },
                        label = { Text(stringResource(R.string.task_reward_points_label)) },
                        placeholder = { Text(stringResource(R.string.task_reward_points_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(
                    text = stringResource(R.string.task_category_section_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { selectedCategoryId = null },
                        label = { Text(stringResource(R.string.task_category_none)) },
                    )
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = { selectedCategoryId = category.id },
                            label = { Text(category.name) },
                            leadingIcon = {
                                Spacer(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(category.color)),
                                )
                            },
                        )
                    }
                    if (capabilities.canManageTaskConfiguration) {
                        FHTonalButton(onClick = { showCategorySheet = true }) {
                            Text(stringResource(R.string.tasks_v2_create_category_action))
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.tasks_v2_templates_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExposedDropdownMenuBox(
                    expanded = templateExpanded,
                    onExpandedChange = { templateExpanded = it }
                ) {
                    val templateLabel = selectedTemplate?.title
                        ?: stringResource(R.string.task_template_none)
                    OutlinedTextField(
                        value = templateLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.task_template_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateExpanded)
                        }
                    )
                    DropdownMenu(
                        expanded = templateExpanded,
                        onDismissRequest = { templateExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.task_template_none)) },
                            onClick = {
                                selectedTemplateId = null
                                templateExpanded = false
                            }
                        )
                        templates.forEach { template ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(template.title)
                                        template.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedTemplateId = template.id
                                    templateExpanded = false
                                    title = template.title
                                    description = template.description ?: ""
                                    selectedRepeatRule = template.repeatRule
                                    selectedTaskType = template.taskType
                                    selectedPriority = template.priority
                                    if (currentUserRole != MemberRole.CHILD || task == null) {
                                        isPrivate = template.isPrivate
                                    }
                                    selectedCategoryId = template.categoryId
                                    attachments = emptyList()
                                    template.defaultAssigneeId?.let { defaultId ->
                                        if (capabilities.canChooseAssignee &&
                                            members.any { it.id == defaultId }
                                        ) {
                                            selectedMemberId = defaultId
                                        }
                                    }
                                    selectedDueDate = null
                                    dueDateHasTime = false
                                    reminderMode = TaskReminderMode.NONE
                                    reminderMinutesOfDay =
                                        TaskReminderSchedule.DEFAULT_REMINDER_MINUTES_OF_DAY
                                }
                            )
                        }
                    }
                }

                if (capabilities.canManageTaskConfiguration) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FHTonalButton(
                            onClick = {
                                val currentTitleError = validateTitle(title)
                                titleError = currentTitleError
                                if (currentTitleError == null) {
                                    onSaveTemplate(
                                        title,
                                        description.takeIf { it.isNotBlank() },
                                        selectedRepeatRule,
                                        selectedMemberId.takeIf { it.isNotBlank() },
                                        isPrivate,
                                        selectedTaskType,
                                        selectedPriority,
                                        selectedCategoryId
                                    )
                                }
                            },
                        ) {
                            Icon(
                                imageVector = FhIcons.Save,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.task_template_save_action))
                        }
                    }

                    if (selectedTemplate != null) {
                        FHTonalButton(
                            onClick = {
                                selectedTemplateId?.let { id ->
                                    onDeleteTemplate(id)
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(
                                imageVector = FhIcons.Delete,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.task_template_delete_action))
                        }
                    }
                }
                }

                FHTonalButton(
                    onClick = { showAdditional = !showAdditional },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = if (showAdditional) {
                            FhIcons.ExpandLess
                        } else {
                            FhIcons.ExpandMore
                        },
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.tasks_editor_additional))
                }

                AnimatedVisibility(visible = showAdditional) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        descriptionError = validateDescription(it)
                    },
                    label = { Text(stringResource(R.string.task_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = descriptionError?.let { { Text(it) } }
                )

                Text(
                    text = stringResource(R.string.task_attachments_section_title),
                    style = MaterialTheme.typography.titleMedium
                )

                if (attachments.isEmpty()) {
                    Text(
                        text = stringResource(R.string.task_attachments_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        attachments.forEach { attachment ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { openAttachment(attachment) }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = FhIcons.AttachFile,
                                    contentDescription = stringResource(R.string.task_attachment_icon_cd),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = attachment.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    attachment.mimeType?.let { mime ->
                                        Text(
                                            text = stringResource(R.string.task_attachment_type_label, mime),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    attachments = attachments.filterNot { it.id == attachment.id }
                                }) {
                                    Icon(
                                        imageVector = FhIcons.Delete,
                                        contentDescription = stringResource(R.string.task_attachment_remove_cd),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                FHTonalButton(onClick = { attachmentPickerLauncher.launch(arrayOf("*/*")) }) {
                    Icon(
                        imageVector = FhIcons.AttachFile,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.task_attachment_add_button))
                }

                Text(
                    text = stringResource(R.string.task_comments_section_title),
                    style = MaterialTheme.typography.titleMedium
                )

                if (comments.isEmpty()) {
                    Text(
                        text = stringResource(R.string.task_comments_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        comments.sortedBy { it.createdAt }.forEach { comment ->
                            val authorName = membersMap[comment.authorId]?.displayLabel()
                                ?: stringResource(R.string.task_comment_unknown_author)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = FhIcons.Chat,
                                    contentDescription = stringResource(R.string.task_comment_icon_cd),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.task_comment_author_message_format,
                                            authorName,
                                            comment.message
                                        ),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.task_comment_timestamp,
                                            TimeUtils.formatDateTime(comment.createdAt)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    comments = comments.filterNot { it.id == comment.id }
                                }) {
                                    Icon(
                                        imageVector = FhIcons.Delete,
                                        contentDescription = stringResource(R.string.task_comment_remove_cd),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                val canAddComments = currentUserId.isNotBlank()
                if (!canAddComments) {
                    Text(
                        text = stringResource(R.string.task_comment_unavailable_no_user),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                OutlinedTextField(
                    value = commentDraft,
                    onValueChange = {
                        commentDraft = it
                        if (commentError != null && it.isNotBlank()) {
                            commentError = null
                        }
                    },
                    label = { Text(stringResource(R.string.task_comment_input_label)) },
                    placeholder = { Text(stringResource(R.string.task_comment_input_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = commentError != null,
                    supportingText = commentError?.let { errorText -> { Text(errorText) } }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FHTonalButton(
                        onClick = {
                            if (commentDraft.isBlank()) {
                                commentError = context.getString(R.string.task_comment_validation_empty)
                                return@FHTonalButton
                            }
                            if (!canAddComments) {
                                commentError = context.getString(R.string.task_comment_unavailable_no_user)
                                return@FHTonalButton
                            }
                            val newComment = TaskComment(
                                id = UUID.randomUUID().toString(),
                                authorId = currentUserId,
                                message = commentDraft.trim(),
                                createdAt = System.currentTimeMillis()
                            )
                            comments = comments + newComment
                            commentDraft = ""
                            commentError = null
                        },
                        enabled = canAddComments
                    ) {
                        Icon(
                            imageVector = FhIcons.Chat,
                            contentDescription = stringResource(R.string.task_comment_add_cd)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.task_comment_add_button))
                    }
                }
                    }
                }

                Text(
                    text = stringResource(R.string.tasks_v2_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.task_type_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TaskType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedTaskType == type,
                            onClick = { selectedTaskType = type },
                            label = { Text(stringResource(type.titleRes)) },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.task_priority_label),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TaskPriority.values().forEach { priority ->
                        val indicatorColor = when (priority) {
                            TaskPriority.HIGH -> MaterialTheme.colorScheme.error
                            TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary
                            TaskPriority.LOW -> MaterialTheme.colorScheme.tertiary
                        }
                        FilterChip(
                            selected = selectedPriority == priority,
                            onClick = { selectedPriority = priority },
                            label = { Text(stringResource(priority.titleRes)) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(indicatorColor)
                                )
                            }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.task_repeat),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RepeatRule.entries.forEach { rule ->
                        val label = when (rule) {
                            RepeatRule.NONE -> stringResource(R.string.task_repeat_none)
                            RepeatRule.DAILY -> stringResource(R.string.task_repeat_daily)
                            RepeatRule.WEEKLY -> stringResource(R.string.task_repeat_weekly)
                        }
                        FilterChip(
                            selected = selectedRepeatRule == rule,
                            onClick = { selectedRepeatRule = rule },
                            label = { Text(label) },
                        )
                    }
                }

                if (members.isNotEmpty()) {
                    FhCheckboxRow(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        label = stringResource(R.string.task_private_hint),
                        enabled = currentUserRole == MemberRole.ADMIN ||
                            currentUserRole == MemberRole.PARENT ||
                            (currentUserRole == MemberRole.CHILD && task == null),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    if (capabilities.canManageTaskConfiguration) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = rotationEnabled,
                                role = Role.Switch,
                                onValueChange = {
                                    rotationEnabled = it
                                    if (!it) {
                                        rotationError = null
                                    } else if (selectedRotationMembers.isEmpty() && selectedMemberId.isNotBlank()) {
                                        selectedRotationMembers = setOf(selectedMemberId)
                                    }
                                },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = rotationEnabled,
                            onCheckedChange = null,
                        )
                        Text(
                            text = stringResource(R.string.task_rotation_enable_label),
                            modifier = Modifier.padding(start = 8.dp),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (rotationEnabled) {
                        Text(
                            text = stringResource(R.string.task_rotation_members_label),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                        members.forEach { member ->
                            FhCheckboxRow(
                                checked = selectedRotationMembers.contains(member.id),
                                onCheckedChange = { checked ->
                                    selectedRotationMembers = if (checked) {
                                        selectedRotationMembers + member.id
                                    } else {
                                        selectedRotationMembers - member.id
                                    }
                                },
                                label = member.displayLabel(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp),
                            )
                        }

                        ExposedDropdownMenuBox(
                            expanded = rotationFrequencyExpanded,
                            onExpandedChange = { rotationFrequencyExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = stringResource(rotationFrequency.titleRes),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.task_rotation_frequency_label)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                supportingText = {
                                    Text(stringResource(rotationFrequency.descriptionRes))
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rotationFrequencyExpanded) }
                            )
                            DropdownMenu(
                                expanded = rotationFrequencyExpanded,
                                onDismissRequest = { rotationFrequencyExpanded = false }
                            ) {
                                TaskRotationFrequency.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.titleRes)) },
                                        onClick = {
                                            rotationFrequency = option
                                            rotationFrequencyExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        val rotationDateMillis = rotationDatePickerState.selectedDateMillis ?: initialRotationStart
                        val rotationPreviewMillis = Calendar.getInstance().apply {
                            timeInMillis = rotationDateMillis
                            set(Calendar.HOUR_OF_DAY, rotationHour)
                            set(Calendar.MINUTE, rotationMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FHTonalButton(onClick = { rotationShowDatePicker = true }) {
                                Text(stringResource(R.string.task_rotation_pick_date))
                            }
                            FHTonalButton(
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            rotationHour = hour
                                            rotationMinute = minute
                                        },
                                        rotationHour,
                                        rotationMinute,
                                        true
                                    ).show()
                                }
                            ) {
                                Text(stringResource(R.string.task_rotation_pick_time))
                            }
                        }

                        Text(
                            text = stringResource(
                                R.string.task_rotation_start_preview,
                                TimeUtils.formatDateTime(rotationPreviewMillis)
                            ),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (rotationError != null) {
                            rotationError?.let { errorText ->
                                Text(
                                    text = errorText,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    }
                }
            }
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag(TASK_EDITOR_FOOTER_TEST_TAG),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FHTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag(TASK_EDITOR_CANCEL_TEST_TAG),
                    ) {
                        Text(stringResource(R.string.task_cancel))
                    }
                    FHButton(
                        onClick = submitTask,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag(TASK_EDITOR_CREATE_TEST_TAG),
                    ) {
                        Text(
                            if (task == null) {
                                stringResource(R.string.task_create)
                            } else {
                                stringResource(R.string.task_edit)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCategorySheet && capabilities.canManageTaskConfiguration) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.tasks_v2_create_category_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = {
                        newCategoryName = it
                        categoryNameError = null
                    },
                    label = { Text(stringResource(R.string.task_category_name_label)) },
                    placeholder = { Text(stringResource(R.string.task_category_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = categoryNameError != null,
                    supportingText = categoryNameError?.let { { Text(it) } },
                    singleLine = true,
                )
                if (availableCategoryColors.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.task_category_color_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        availableCategoryColors.forEachIndexed { index, colorValue ->
                            val isSelected = newCategoryColor == colorValue
                            val colorDescription = stringResource(
                                R.string.task_category_color_option_cd,
                                index + 1,
                                availableCategoryColors.size,
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.RadioButton,
                                        onClick = { newCategoryColor = colorValue },
                                    )
                                    .semantics {
                                        contentDescription = colorDescription
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorValue))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            shape = CircleShape,
                                        ),
                                )
                            }
                        }
                    }
                }
                FHButton(
                    onClick = {
                        val trimmed = newCategoryName.trim()
                        val error = when {
                            trimmed.isEmpty() -> context.getString(R.string.task_category_error_name_required)
                            categories.any { it.name.equals(trimmed, ignoreCase = true) } ->
                                context.getString(R.string.task_category_error_name_duplicate)
                            else -> null
                        }
                        categoryNameError = error
                        if (error == null) {
                            val newId = onCreateCategory(trimmed, newCategoryColor)
                            if (newId.isNotEmpty()) {
                                selectedCategoryId = newId
                                newCategoryName = ""
                                showCategorySheet = false
                            }
                        }
                    },
                    enabled = newCategoryName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.task_category_add_button))
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                FHTonalButton(onClick = {
                    val selected = datePickerState.selectedDateMillis?.let(TimeUtils::getStartOfDay)
                    val hadDueDate = selectedDueDate != null
                    selectedDueDate = selected
                    if (selected != null) {
                        dueDateHasTime = false
                        if (!hadDueDate && task == null && reminderMode == TaskReminderMode.NONE) {
                            val (mode, minutes) = TaskReminderSchedule.defaultReminderForNewDueDate()
                            reminderMode = mode
                            reminderMinutesOfDay = minutes
                        }
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                FHTonalButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (rotationShowDatePicker && capabilities.canManageTaskConfiguration) {
        DatePickerDialog(
            onDismissRequest = { rotationShowDatePicker = false },
            confirmButton = {
                FHTonalButton(onClick = { rotationShowDatePicker = false }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                FHTonalButton(onClick = { rotationShowDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = rotationDatePickerState)
        }
    }
}
