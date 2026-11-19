package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportPenalty
import com.familyhub.domain.utils.PenaltyValidationException
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncMergerPenaltyValidationTest {

    private val merger = SyncMerger()

    @Test
    fun mergeSnapshots_rejectsZeroAmountLocal() {
        val local = ExportData(penalties = listOf(penalty(amount = 0)))
        val remote = ExportData()

        assertThrows(PenaltyValidationException::class.java) {
            merger.mergeSnapshots(local, remote, "child", isMemberWriteAllowed = true)
        }
    }

    @Test
    fun mergeSnapshots_rejectsPositiveAmountRemote() {
        val local = ExportData()
        val remote = ExportData(penalties = listOf(penalty(amount = 5)))

        assertThrows(PenaltyValidationException::class.java) {
            merger.mergeSnapshots(local, remote, "child", isMemberWriteAllowed = true)
        }
    }

    @Test
    fun mergeSnapshots_rejectsActiveWithCanceledAt() {
        val local = ExportData(
            penalties = listOf(penalty(canceled = false, canceledAt = 1L))
        )

        assertThrows(PenaltyValidationException::class.java) {
            merger.mergeSnapshots(local, remote = null, "child", isMemberWriteAllowed = true)
        }
    }

    @Test
    fun mergeSnapshots_rejectsIntMinValueAmount() {
        val local = ExportData(penalties = listOf(penalty(amount = Int.MIN_VALUE)))

        assertThrows(PenaltyValidationException::class.java) {
            merger.mergeSnapshots(local, remote = null, "child", isMemberWriteAllowed = true)
        }
    }

    private fun penalty(
        amount: Int = -10,
        canceled: Boolean = false,
        canceledAt: Long? = null
    ) = ExportPenalty(
        id = "penalty-1",
        userId = "child",
        issuedByUserId = "parent",
        amount = amount,
        createdAt = 1L,
        canceled = canceled,
        canceledAt = canceledAt
    )
}
