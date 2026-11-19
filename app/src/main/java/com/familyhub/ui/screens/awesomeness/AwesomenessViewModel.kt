package com.familyhub.ui.screens.awesomeness

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.repository.AwesomenessRepository
import com.familyhub.domain.model.AwesomenessEntry
import com.familyhub.domain.utils.IdGenerator
import com.familyhub.util.TimeUtils
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class AwesomenessUiState(
    val isLoading: Boolean = true,
    val entries: List<AwesomenessEntry> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class AwesomenessViewModel @Inject constructor(
    private val repository: AwesomenessRepository,
    private val idGenerator: IdGenerator,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(AwesomenessUiState())
    val state: StateFlow<AwesomenessUiState> = _state.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            repository.getAllEntries().catch { error ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        error,
                        UserFacingErrorContext.LoadData,
                        tag = "AwesomenessViewModel",
                    ),
                )
            }.collect { entries ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    entries = entries,
                    errorMessage = null
                )
            }
        }
    }

    fun addEntry(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            _state.value = _state.value.copy(
                errorMessage = context.getString(R.string.awesomeness_error_empty_text)
            )
            return
        }

        viewModelScope.launch {
            val entry = AwesomenessEntry(
                id = idGenerator.newId(),
                text = trimmedText,
                createdAt = TimeUtils.now()
            )

            runCatching { repository.addEntry(entry) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Awesomeness,
                            tag = "AwesomenessViewModel",
                        ),
                    )
                }
        }
    }

    fun updateEntry(entry: AwesomenessEntry, text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            _state.value = _state.value.copy(
                errorMessage = context.getString(R.string.awesomeness_error_empty_text)
            )
            return
        }

        viewModelScope.launch {
            val updatedEntry = entry.copy(text = trimmedText)
            runCatching { repository.updateEntry(updatedEntry) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Awesomeness,
                            tag = "AwesomenessViewModel",
                        ),
                    )
                }
        }
    }

    fun deleteEntry(entry: AwesomenessEntry) {
        viewModelScope.launch {
            runCatching { repository.deleteEntry(entry) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Awesomeness,
                            tag = "AwesomenessViewModel",
                        ),
                    )
                }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}

