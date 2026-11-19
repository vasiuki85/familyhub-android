package com.familyhub.data.repository

import com.familyhub.data.importer.DataImportService
import com.familyhub.data.restore.EncryptedRestoreCoordinator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuMutationBypassContractTest {

  @Test
  fun importAndRestorePathsDoNotInjectMenuMutationCoordinator() {
    val importFieldTypes = DataImportService::class.java.declaredFields.map { it.type.name }
    val restoreFieldTypes = EncryptedRestoreCoordinator::class.java.declaredFields.map { it.type.name }

    assertFalse(importFieldTypes.any { it.endsWith("MenuMutationCoordinator") })
    assertFalse(restoreFieldTypes.any { it.endsWith("MenuMutationCoordinator") })
    assertTrue(restoreFieldTypes.any { it.endsWith("DataImportService") })
  }
}
