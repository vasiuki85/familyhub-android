package com.familyhub.ui.screens.rewards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardProgressUiMapperTest {
    @Test
    fun negativeBalance_countsFullDistanceToCost() {
        val model = RewardProgressUiMapper.map(
            currentBalance = -10,
            rewardCost = 20,
            isActive = true,
        )

        assertEquals(0f, model.progress, 0f)
        assertEquals(30, model.remainingPoints)
        assertEquals(0, model.progressBalance)
        assertFalse(model.canRequest)
    }

    @Test
    fun zeroBalance_returnsFullRemainingPoints() {
        val model = RewardProgressUiMapper.map(
            currentBalance = 0,
            rewardCost = 20,
            isActive = true,
        )

        assertEquals(0f, model.progress, 0f)
        assertEquals(20, model.remainingPoints)
        assertFalse(model.canRequest)
    }

    @Test
    fun partialProgress_keepsBarClampedButRemainingUsesTrueBalance() {
        val model = RewardProgressUiMapper.map(
            currentBalance = 44,
            rewardCost = 50,
            isActive = true,
        )

        assertEquals(0.88f, model.progress, 0.001f)
        assertEquals(6, model.remainingPoints)
        assertEquals(44, model.progressBalance)
        assertFalse(model.canRequest)
    }

    @Test
    fun exactlyAffordable_allowsActiveRewardRequest() {
        val model = RewardProgressUiMapper.map(
            currentBalance = 50,
            rewardCost = 50,
            isActive = true,
        )

        assertEquals(1f, model.progress, 0f)
        assertEquals(0, model.remainingPoints)
        assertTrue(model.canRequest)
    }

    @Test
    fun moreThanAffordable_capsProgressBarAtOne() {
        val model = RewardProgressUiMapper.map(
            currentBalance = 80,
            rewardCost = 50,
            isActive = true,
        )

        assertEquals(1f, model.progress, 0f)
        assertEquals(0, model.remainingPoints)
        assertTrue(model.canRequest)
    }

    @Test
    fun zeroCostReward_isImmediatelyRequestable() {
        val model = RewardProgressUiMapper.map(
            currentBalance = -5,
            rewardCost = 0,
            isActive = true,
        )

        assertEquals(1f, model.progress, 0f)
        assertEquals(0, model.remainingPoints)
        assertTrue(model.canRequest)
    }

    @Test
    fun inactiveReward_neverAllowsRequest() {
        assertFalse(
            RewardProgressUiMapper.map(
                currentBalance = 100,
                rewardCost = 50,
                isActive = false,
            ).canRequest,
        )
    }
}
