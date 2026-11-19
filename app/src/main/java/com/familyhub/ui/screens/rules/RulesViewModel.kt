package com.familyhub.ui.screens.rules

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.HouseRulesRepository
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.HouseRule
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

data class HouseRuleListItem(
    val rule: HouseRule,
    val authorName: String
)

data class HouseRulesUiState(
    val isLoading: Boolean = true,
    val rules: List<HouseRuleListItem> = emptyList(),
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val currentUserId: String = "",
    val currentUserRole: MemberRole = MemberRole.CHILD
) {
    val filteredRules: List<HouseRuleListItem>
        get() = if (searchQuery.isBlank()) {
            rules
        } else {
            val query = searchQuery.trim().lowercase()
            rules.filter { item ->
                item.rule.title.lowercase().contains(query) ||
                    (item.rule.description?.lowercase()?.contains(query) == true) ||
                    (item.rule.category?.lowercase()?.contains(query) == true)
            }
        }

    val groupedRules: Map<String?, List<HouseRuleListItem>>
        get() = filteredRules.groupBy { it.rule.category }
}

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val houseRulesRepository: HouseRulesRepository,
    private val memberRepository: FamilyMemberRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val idGenerator: IdGenerator,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(HouseRulesUiState())
    val state: StateFlow<HouseRulesUiState> = _state.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            try {
                combine(
                    houseRulesRepository.getAllRules(),
                    memberRepository.getAllMembers(),
                    appSettingsDataStore.settingsFlow
                ) { rules, members, settings ->
                    val memberMap = members.associateBy(FamilyMember::id)
                    val currentMember = members.find { it.id == settings.currentUserId }
                    val currentUserRole = currentMember?.role ?: MemberRole.CHILD
                    HouseRulesUiState(
                        isLoading = false,
                        rules = rules.map { rule ->
                            HouseRuleListItem(
                                rule = rule,
                                authorName = memberMap[rule.createdByUserId]?.displayLabel()
                                    ?: context.getString(R.string.rules_author_unknown)
                            )
                        },
                        searchQuery = _state.value.searchQuery,
                        errorMessage = null,
                        currentUserId = settings.currentUserId,
                        currentUserRole = currentUserRole
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
                        UserFacingErrorContext.RulesLoad,
                        tag = "RulesViewModel",
                    ),
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun createRule(title: String, description: String, category: String?) {
        val trimmedTitle = title.trim()
        val trimmedDescription = description.trim()
        val trimmedCategory = category?.trim()?.ifBlank { null }
        
        if (trimmedTitle.isBlank()) {
            _state.value = _state.value.copy(
                errorMessage = context.getString(R.string.error_rules_title_required)
            )
            return
        }

        val currentUserId = _state.value.currentUserId
        if (currentUserId.isBlank()) {
            _state.value = _state.value.copy(
                errorMessage = context.getString(R.string.error_no_current_user)
            )
            return
        }

        viewModelScope.launch {
            val now = TimeUtils.now()
            val rule = HouseRule(
                id = idGenerator.newId(),
                title = trimmedTitle,
                description = trimmedDescription.ifBlank { null },
                createdByUserId = currentUserId,
                createdAt = now,
                updatedAt = now,
                category = trimmedCategory
            )

            runCatching { houseRulesRepository.upsert(rule) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.RulesCreate,
                            tag = "RulesViewModel",
                        ),
                    )
                }
        }
    }

    fun updateRule(rule: HouseRule, title: String, description: String, category: String?) {
        val trimmedTitle = title.trim()
        val trimmedDescription = description.trim()
        val trimmedCategory = category?.trim()?.ifBlank { null }
        
        if (trimmedTitle.isBlank()) {
            _state.value = _state.value.copy(
                errorMessage = context.getString(R.string.error_rules_title_required)
            )
            return
        }

        viewModelScope.launch {
            val updatedRule = rule.copy(
                title = trimmedTitle,
                description = trimmedDescription.ifBlank { null },
                updatedAt = TimeUtils.now(),
                category = trimmedCategory
            )

            runCatching { houseRulesRepository.upsert(updatedRule) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.RulesUpdate,
                            tag = "RulesViewModel",
                        ),
                    )
                }
        }
    }

    fun deleteRule(rule: HouseRule) {
        viewModelScope.launch {
            runCatching { houseRulesRepository.delete(rule) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.RulesDelete,
                            tag = "RulesViewModel",
                        ),
                    )
                }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
