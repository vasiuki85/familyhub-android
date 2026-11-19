package com.familyhub.ui.screens.goals

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.FamilyGoalsRepository
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.domain.model.FamilyGoal
import com.familyhub.domain.model.FamilyGoalDetail
import com.familyhub.domain.model.FamilyGoalStage
import com.familyhub.domain.model.FamilyGoalStatus
import com.familyhub.domain.model.FamilyGoalStep
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.utils.IdGenerator
import com.familyhub.domain.utils.displayLabel
import com.familyhub.util.TimeUtils
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class FamilyGoalFilter(val statuses: Set<FamilyGoalStatus>?) {
    ACTIVE(setOf(FamilyGoalStatus.ACTIVE)),
    COMPLETED(setOf(FamilyGoalStatus.COMPLETED)),
    ARCHIVED(setOf(FamilyGoalStatus.ARCHIVED)),
    ALL(null)
}

data class FamilyGoalListItem(
    val goal: FamilyGoal,
    val stages: List<FamilyGoalStage>,
    val creatorName: String,
    val deadlineLabel: String?,
    val progressPercent: Float,
    val completedSteps: Int,
    val totalSteps: Int,
    val remainingSteps: Int,
    val nextStepTitle: String?,
)

data class FamilyGoalsUiState(
    val isLoading: Boolean = true,
    val goals: List<FamilyGoalListItem> = emptyList(),
    val filter: FamilyGoalFilter = FamilyGoalFilter.ACTIVE,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val currentUserId: String = "",
    val currentUserRole: MemberRole = MemberRole.PENDING,
) {
    val filteredGoals: List<FamilyGoalListItem>
        get() = goals.filter { item ->
            val statuses = filter.statuses ?: FamilyGoalStatus.values().toSet()
            item.goal.status in statuses
        }

    val canCreateGoals: Boolean
        get() = currentUserRole != MemberRole.PENDING

    fun canManageGoal(goal: FamilyGoal): Boolean =
        currentUserRole != MemberRole.PENDING &&
            (
                currentUserRole == MemberRole.ADMIN ||
                    currentUserRole == MemberRole.PARENT ||
                    goal.createdByUserId == currentUserId
                )

    fun findGoal(goalId: String): FamilyGoalListItem? =
        goals.find { it.goal.id == goalId }
}

@HiltViewModel
class FamilyGoalsViewModel @Inject constructor(
    private val goalsRepository: FamilyGoalsRepository,
    private val memberRepository: FamilyMemberRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val idGenerator: IdGenerator,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(FamilyGoalsUiState())
    val state: StateFlow<FamilyGoalsUiState> = _state.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            try {
                combine(
                    goalsRepository.getAllGoalDetails(),
                    memberRepository.getAllMembers(),
                    appSettingsDataStore.settingsFlow
                ) { details, members, settings ->
                    val memberMap = members.associateBy(FamilyMember::id)
                    val currentMember = memberMap[settings.currentUserId]
                    FamilyGoalsUiState(
                        isLoading = false,
                        goals = details.map { it.toListItem(memberMap, context) },
                        filter = _state.value.filter,
                        errorMessage = null,
                        successMessage = _state.value.successMessage,
                        currentUserId = settings.currentUserId,
                        currentUserRole = currentMember?.role ?: MemberRole.PENDING,
                    )
                }.collect { newState ->
                    _state.value = newState
                }
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        error,
                        UserFacingErrorContext.Goals,
                        tag = "FamilyGoalsViewModel",
                    ),
                )
            }
        }
    }

    fun setFilter(filter: FamilyGoalFilter) {
        _state.value = _state.value.copy(filter = filter)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun clearSuccess() {
        _state.value = _state.value.copy(successMessage = null)
    }

    fun createGoal(
        title: String,
        description: String,
        deadline: Long?,
        stageInputs: List<GoalStageInput>,
        isPersonal: Boolean
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            emitError(context.getString(R.string.goals_error_title_required))
            return
        }

        val currentUserId = _state.value.currentUserId
        if (currentUserId.isBlank()) {
            emitError(context.getString(R.string.error_no_current_user))
            return
        }

        viewModelScope.launch {
            val now = TimeUtils.now()
            val goalId = idGenerator.newId()
            val stages = stageInputs.toDomain(goalId)
            val goal = FamilyGoal(
                id = goalId,
                title = trimmedTitle,
                description = description.trim().ifBlank { null },
                targetPoints = 0,
                currentPoints = 0,
                deadlineAt = deadline,
                status = FamilyGoalStatus.ACTIVE,
                createdByUserId = currentUserId,
                createdAt = now,
                updatedAt = now,
                isPersonal = isPersonal
            )

            runCatching { goalsRepository.upsert(goal, stages) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        successMessage = context.getString(R.string.goals_message_created, goal.title)
                    )
                }
                .onFailure { error ->
                    emitError(
                        UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Goals,
                            tag = "FamilyGoalsViewModel",
                        ),
                    )
                }
        }
    }

    fun updateGoal(
        item: FamilyGoalListItem,
        title: String,
        description: String,
        deadline: Long?,
        stageInputs: List<GoalStageInput>,
        isPersonal: Boolean
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            emitError(context.getString(R.string.goals_error_title_required))
            return
        }

        viewModelScope.launch {
            val now = TimeUtils.now()
            val stages = stageInputs.toDomain(item.goal.id)
            val updatedGoal = item.goal.copy(
                title = trimmedTitle,
                description = description.trim().ifBlank { null },
                deadlineAt = deadline,
                updatedAt = now,
                isPersonal = isPersonal
            )

            runCatching { goalsRepository.upsert(updatedGoal, stages) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        successMessage = context.getString(R.string.goals_message_updated, updatedGoal.title)
                    )
                }
                .onFailure { error ->
                    emitError(
                        UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Goals,
                            tag = "FamilyGoalsViewModel",
                        ),
                    )
                }
        }
    }

    fun toggleStep(goalId: String, stepId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            runCatching { goalsRepository.toggleStep(goalId, stepId, isCompleted) }
                .onFailure { error ->
                    emitError(
                        UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Goals,
                            tag = "FamilyGoalsViewModel",
                        ),
                    )
                }
        }
    }

    fun addProgress(item: FamilyGoalListItem, additionalSteps: Int) {
        if (additionalSteps <= 0) {
            emitError(context.getString(R.string.goals_error_progress_invalid))
            return
        }

        viewModelScope.launch {
            if (item.totalSteps > 0) {
                val applied = runCatching {
                    goalsRepository.completeNextSteps(item.goal.id, additionalSteps)
                }.getOrElse { error ->
                    emitError(
                        UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Goals,
                            tag = "FamilyGoalsViewModel",
                        ),
                    )
                    return@launch
                }

                if (applied > 0) {
                    val completedAll = applied >= item.remainingSteps
                    val messageRes = if (completedAll) {
                        R.string.goals_message_completed
                    } else {
                        R.string.goals_message_progress_added
                    }
                    _state.value = _state.value.copy(
                        successMessage = context.getString(messageRes, item.goal.title)
                    )
                    return@launch
                }
            }

            val newPoints = item.goal.currentPoints + additionalSteps
            val cappedPoints = newPoints.coerceAtMost(item.goal.targetPoints)
            val newStatus = when {
                item.goal.status == FamilyGoalStatus.ARCHIVED -> FamilyGoalStatus.ARCHIVED
                cappedPoints >= item.goal.targetPoints && item.goal.targetPoints > 0 -> FamilyGoalStatus.COMPLETED
                else -> FamilyGoalStatus.ACTIVE
            }
            val updatedGoal = item.goal.copy(
                currentPoints = cappedPoints,
                status = newStatus,
                updatedAt = TimeUtils.now()
            )

            runCatching { goalsRepository.upsert(updatedGoal) }
                .onSuccess {
                    val messageRes = if (updatedGoal.status == FamilyGoalStatus.COMPLETED) {
                        R.string.goals_message_completed
                    } else {
                        R.string.goals_message_progress_added
                    }
                    _state.value = _state.value.copy(
                        successMessage = context.getString(messageRes, updatedGoal.title)
                    )
                }
                .onFailure { error ->
                    emitError(
                        UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Goals,
                            tag = "FamilyGoalsViewModel",
                        ),
                    )
                }
        }
    }

    fun markCompleted(item: FamilyGoalListItem) {
        viewModelScope.launch {
            if (item.totalSteps > 0) {
                runCatching { goalsRepository.markAllStepsCompleted(item.goal.id) }
                    .onSuccess {
                        _state.value = _state.value.copy(
                            successMessage = context.getString(R.string.goals_message_completed, item.goal.title)
                        )
                    }
                    .onFailure { error ->
                        emitError(
                            UserFacingErrorMapper.message(
                                context,
                                error,
                                UserFacingErrorContext.Goals,
                                tag = "FamilyGoalsViewModel",
                            ),
                        )
                    }
                return@launch
            }

            val updatedGoal = item.goal.copy(
                currentPoints = item.goal.targetPoints,
                status = FamilyGoalStatus.COMPLETED,
                updatedAt = TimeUtils.now()
            )
            runCatching { goalsRepository.upsert(updatedGoal) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        successMessage = context.getString(R.string.goals_message_completed, updatedGoal.title)
                    )
                }
                .onFailure { error ->
                    emitError(
                        UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Goals,
                            tag = "FamilyGoalsViewModel",
                        ),
                    )
                }
        }
    }

    fun reopen(goal: FamilyGoal) {
        viewModelScope.launch {
            runCatching { goalsRepository.updateStatus(goal, FamilyGoalStatus.ACTIVE, TimeUtils.now()) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        successMessage = context.getString(R.string.goals_message_reopened, goal.title)
                    )
                }
                .onFailure { error ->
                    emitError(
                        UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Goals,
                            tag = "FamilyGoalsViewModel",
                        ),
                    )
                }
        }
    }

    fun archive(goal: FamilyGoal) {
        viewModelScope.launch {
            runCatching { goalsRepository.updateStatus(goal, FamilyGoalStatus.ARCHIVED, TimeUtils.now()) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        successMessage = context.getString(R.string.goals_message_archived, goal.title)
                    )
                }
                .onFailure { error ->
                    emitError(
                        UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Goals,
                            tag = "FamilyGoalsViewModel",
                        ),
                    )
                }
        }
    }

    fun delete(goal: FamilyGoal) {
        viewModelScope.launch {
            runCatching { goalsRepository.delete(goal) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        successMessage = context.getString(R.string.goals_message_deleted, goal.title)
                    )
                }
                .onFailure { error ->
                    emitError(
                        UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Goals,
                            tag = "FamilyGoalsViewModel",
                        ),
                    )
                }
        }
    }

    private fun emitError(message: String) {
        _state.value = _state.value.copy(errorMessage = message)
    }

    private fun List<GoalStageInput>.toDomain(goalId: String): List<FamilyGoalStage> =
        mapIndexedNotNull { index, stage ->
            val trimmedTitle = stage.title.trim()
            val stageId = stage.id ?: idGenerator.newId()
            val normalizedSteps = stage.steps.mapIndexedNotNull { stepIndex, step ->
                val stepTitle = step.title.trim()
                if (stepTitle.isBlank()) {
                    null
                } else {
                    FamilyGoalStep(
                        id = step.id ?: idGenerator.newId(),
                        goalId = goalId,
                        stageId = stageId,
                        title = stepTitle,
                        isCompleted = step.isCompleted,
                        position = stepIndex
                    )
                }
            }
            if (trimmedTitle.isBlank() && normalizedSteps.isEmpty()) {
                null
            } else {
                val resolvedTitle = trimmedTitle.ifBlank {
                    context.getString(R.string.goals_stage_default_title, index + 1)
                }
                FamilyGoalStage(
                    id = stageId,
                    goalId = goalId,
                    title = resolvedTitle,
                    description = stage.description?.trim().takeUnless { it.isNullOrBlank() },
                    position = index,
                    steps = normalizedSteps
                )
            }
        }

    }


private fun FamilyGoalDetail.toListItem(
    memberMap: Map<String, FamilyMember>,
    context: Context
): FamilyGoalListItem {
    val totalSteps = totalSteps
    val completedSteps = completedSteps
    val progress = when {
        totalSteps > 0 -> completedSteps.toFloat() / totalSteps.toFloat()
        goal.targetPoints > 0 -> goal.progress
        else -> 0f
    }
    val remaining = when {
        totalSteps > 0 -> (totalSteps - completedSteps).coerceAtLeast(0)
        goal.targetPoints > 0 -> (goal.targetPoints - goal.currentPoints).coerceAtLeast(0)
        else -> 0
    }
    val deadlineLabel = goal.deadlineAt?.let(TimeUtils::formatDate)
    val nextStepTitle = stages
        .sortedBy(FamilyGoalStage::position)
        .flatMap { stage -> stage.steps.sortedBy(FamilyGoalStep::position) }
        .firstOrNull { !it.isCompleted }
        ?.title
    return FamilyGoalListItem(
        goal = goal,
        stages = stages,
        creatorName = memberMap[goal.createdByUserId]?.displayLabel()
            ?: memberMap.values.firstOrNull { it.id == goal.createdByUserId }?.displayLabel()
            ?: context.getString(R.string.goal_creator_unknown),
        deadlineLabel = deadlineLabel,
        progressPercent = progress,
        completedSteps = if (totalSteps > 0) completedSteps else goal.currentPoints,
        totalSteps = if (totalSteps > 0) totalSteps else goal.targetPoints,
        remainingSteps = remaining,
        nextStepTitle = nextStepTitle,
    )
}
