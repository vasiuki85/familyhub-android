package com.familyhub.data.work

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDeadlineCheckWorkerSourceContractTest {
  private val source: String by lazy {
    File("src/main/java/com/familyhub/data/work/TaskDeadlineCheckWorker.kt")
      .readText(Charsets.UTF_8)
  }

  @Test
  fun worker_filtersByAssigneeUsingPolicy() {
    assertTrue(source.contains("TaskDeadlineNotificationPolicy.shouldNotify"))
    assertTrue(source.contains("AppSettingsDataStore"))
    assertTrue(source.contains("currentUserId"))
  }
}
