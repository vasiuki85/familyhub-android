package com.familyhub.data.sync

/** Canonical logical identifier shared by Room, merge, and Firestore upload. */
internal fun achievementLogicalId(userId: String, type: String): String = "${userId}_${type}"
