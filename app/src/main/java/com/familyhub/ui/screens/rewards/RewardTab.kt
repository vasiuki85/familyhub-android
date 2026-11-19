package com.familyhub.ui.screens.rewards

import androidx.annotation.StringRes
import com.familyhub.R

enum class RewardTab(@StringRes val titleRes: Int) {
    CATALOG(R.string.rewards_tab_catalog),
    MY_REQUESTS(R.string.rewards_tab_my_requests),
    RECEIVED(R.string.rewards_tab_received),
    PENDING_REVIEW(R.string.rewards_tab_pending_review),
}
