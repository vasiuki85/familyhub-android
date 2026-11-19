package com.familyhub.ui.screens.report

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.domain.model.WeeklyFamilyReport
import com.familyhub.domain.usecase.GenerateWeeklyReportUseCase
import com.familyhub.domain.utils.WeekUtils
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class WeeklyReportUiState(
    val isLoading: Boolean = false,
    val report: WeeklyFamilyReport? = null,
    val selectedWeekStart: Long,
    val canNavigateForward: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class WeeklyReportViewModel @Inject constructor(
    private val generateWeeklyReportUseCase: GenerateWeeklyReportUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val currentWeekStart = WeekUtils.getCurrentWeekStartMillis()
    private val millisInWeek = TimeUnit.DAYS.toMillis(7)

    private val selectedWeekStart = MutableStateFlow(currentWeekStart)

    private val _state = MutableStateFlow(
        WeeklyReportUiState(
            isLoading = true,
            report = null,
            selectedWeekStart = currentWeekStart,
            canNavigateForward = false,
            errorMessage = null
        )
    )
    val state: StateFlow<WeeklyReportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            selectedWeekStart.collectLatest { weekStart ->
                loadReport(weekStart)
            }
        }
    }

    fun showPreviousWeek() {
        val newWeek = selectedWeekStart.value - millisInWeek
        selectedWeekStart.value = newWeek
    }

    fun showNextWeek() {
        val currentSelection = selectedWeekStart.value
        if (currentSelection < currentWeekStart) {
            val newWeek = (currentSelection + millisInWeek).coerceAtMost(currentWeekStart)
            selectedWeekStart.value = newWeek
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadReport(selectedWeekStart.value)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private suspend fun loadReport(weekStart: Long) {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null, selectedWeekStart = weekStart)
        try {
            val report = generateWeeklyReportUseCase(weekStart)
            _state.value = WeeklyReportUiState(
                isLoading = false,
                report = report,
                selectedWeekStart = weekStart,
                canNavigateForward = weekStart < currentWeekStart,
                errorMessage = null
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                errorMessage = UserFacingErrorMapper.message(
                    context,
                    e,
                    UserFacingErrorContext.LoadData,
                    tag = "WeeklyReportViewModel",
                ),
                canNavigateForward = weekStart < currentWeekStart,
                selectedWeekStart = weekStart
            )
        }
    }
}
