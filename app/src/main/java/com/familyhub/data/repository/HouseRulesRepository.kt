package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.dao.HouseRuleDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.HouseRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HouseRulesRepository @Inject constructor(
    private val dao: HouseRuleDao,
    private val cloudDeletionLedger: CloudDeletionLedger
) {
    fun getAllRules(): Flow<List<HouseRule>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun upsert(rule: HouseRule) {
        dao.upsert(rule.toEntity())
        cloudDeletionLedger.clearDeletion(DeletedCloudDocumentCollections.HOUSE_RULES, rule.id)
    }

    suspend fun upsertAll(rules: List<HouseRule>) {
        dao.upsertAll(rules.map { it.toEntity() })
        cloudDeletionLedger.clearDeletions(
            DeletedCloudDocumentCollections.HOUSE_RULES,
            rules.map { it.id }
        )
    }

    suspend fun delete(rule: HouseRule) {
        cloudDeletionLedger.recordDeletion(DeletedCloudDocumentCollections.HOUSE_RULES, rule.id)
        dao.deleteById(rule.id)
    }

    suspend fun clear() {
        dao.clear()
    }
}
