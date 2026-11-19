package com.familyhub.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

private const val PEER_TAB_DURATION_MS = 180
private const val DETAIL_DURATION_MS = 200
private const val PEER_TAB_RISE_FRACTION = 0.018f

private val peerTabTween = tween<Float>(PEER_TAB_DURATION_MS)
private val peerTabSlideTween = tween<IntOffset>(PEER_TAB_DURATION_MS)
private val detailSlideTween = tween<IntOffset>(DETAIL_DURATION_MS)
private val detailFadeTween = tween<Float>(DETAIL_DURATION_MS)

val PeerTabEnterTransition: EnterTransition =
    fadeIn(peerTabTween) +
        slideInVertically(
            initialOffsetY = { fullHeight ->
                (fullHeight * PEER_TAB_RISE_FRACTION).toInt().coerceIn(12, 48)
            },
            animationSpec = peerTabSlideTween,
        )

val PeerTabExitTransition: ExitTransition = fadeOut(peerTabTween)

val DetailEnterTransition: EnterTransition =
    slideInHorizontally(initialOffsetX = { it }, animationSpec = detailSlideTween) +
        fadeIn(detailFadeTween)

val DetailExitTransition: ExitTransition = fadeOut(detailFadeTween)

val DetailPopEnterTransition: EnterTransition = fadeIn(detailFadeTween)

val DetailPopExitTransition: ExitTransition =
    slideOutHorizontally(targetOffsetX = { it }, animationSpec = detailSlideTween) +
        fadeOut(detailFadeTween)
