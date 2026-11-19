package com.familyhub.ui.components

import androidx.compose.ui.focus.FocusRequester

/**
 * Runs [validators] in order, assigns the first error to each field, and invokes [onValid] when all pass.
 * Returns the first [FocusRequester] that should receive focus after a failed submit.
 */
internal fun runFormValidation(
    validators: List<Pair<() -> String?, (String?) -> Unit>>,
    focusRequesters: List<FocusRequester> = emptyList(),
    onValid: () -> Unit,
): Boolean {
    var firstErrorIndex: Int? = null
    validators.forEachIndexed { index, (validate, assignError) ->
        val error = validate()
        assignError(error)
        if (error != null && firstErrorIndex == null) {
            firstErrorIndex = index
        }
    }
    val failedIndex = firstErrorIndex
    if (failedIndex != null) {
        focusRequesters.getOrNull(failedIndex)?.let { requester ->
            runCatching { requester.requestFocus() }
        }
        return false
    }
    onValid()
    return true
}
