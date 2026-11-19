package com.familyhub.domain.model

data class Task(
    val id: String,                 // UUID
    val title: String,              // "Вынести мусор"
    val description: String?,       // "контейнер у дороги"
    val createdByUserId: String,    // кто назначил
    val assignedToUserId: String,   // кто должен сделать
    val dueDate: Long?,             // дедлайн (millis UTC) или null
    val dueDateHasTime: Boolean = false,
    val reminderMode: TaskReminderMode = TaskReminderMode.NONE,
    val reminderMinutesOfDay: Int? = null, // 0..1439 when AT_TIME
    val repeatRule: RepeatRule,     // NONE / DAILY / WEEKLY
    val status: TaskStatus,         // TODO / DONE
    val createdAt: Long,            // millis UTC
    val orderIndex: Int = 0,        // порядок сортировки (для drag & drop)
    val isPrivate: Boolean = false,  // приватная задача (видна только создателю и исполнителю)
    val taskType: TaskType = TaskType.ROUTINE, // тип задачи влияет на начисление баллов
    val priority: TaskPriority = TaskPriority.MEDIUM, // приоритет задачи
    val categoryId: String? = null,           // категория задачи для группировки и фильтрации
    val rewardPoints: Int? = null,
    val attachments: List<TaskAttachment> = emptyList(), // вложения (файлы, фото)
    val comments: List<TaskComment> = emptyList(), // комментарии к задаче
    val requiresApproval: Boolean = false, // родитель должен подтвердить выполнение
    val completionCycle: Long = 0L
)

