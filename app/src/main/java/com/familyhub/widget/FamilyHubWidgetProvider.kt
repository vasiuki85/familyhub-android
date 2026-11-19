package com.familyhub.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.familyhub.FamilyHubApplication
import com.familyhub.R
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.utils.filterByVisibility
import com.familyhub.domain.utils.WeekUtils
import com.familyhub.domain.model.MealType
import com.familyhub.ui.MainActivity
import com.familyhub.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FamilyHubWidgetProvider : AppWidgetProvider() {

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, FamilyHubWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            updateWidgets(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        widgetScope.launch {
            try {
                // Получаем зависимости через Hilt EntryPoint
                val entryPoint = getWidgetEntryPoint(context)
                val taskRepository = entryPoint.taskRepository()
                val memberRepository = entryPoint.memberRepository()
                val appSettingsDataStore = entryPoint.appSettingsDataStore()
                val weeklyMenuRepository = entryPoint.weeklyMenuRepository()
                val recipeRepository = entryPoint.recipeRepository()

                val settings = appSettingsDataStore.settingsFlow.first()
                val currentUserId = settings.currentUserId

                if (currentUserId.isBlank()) {
                    // Если пользователь не выбран, показываем пустой виджет
                    appWidgetIds.forEach { appWidgetId ->
                        val views = RemoteViews(context.packageName, R.layout.widget_layout)
                        views.setTextViewText(R.id.widget_tasks_empty, context.getString(R.string.widget_select_user))
                        views.setViewVisibility(R.id.widget_tasks_empty, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_tasks_list, View.GONE)
                        views.setViewVisibility(R.id.widget_meals_section, View.GONE)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                    return@launch
                }

                val allTasks = taskRepository.getAllTasks().first()
                val members = memberRepository.getAllMembers().first()
                val membersById = members.associateBy { it.id }
                val recipes = recipeRepository.observeRecipes().first().associateBy { it.id }

                // Получаем задачи на сегодня для текущего пользователя
                val today = TimeUtils.getStartOfDay(TimeUtils.now())
                val tomorrow = today + java.util.concurrent.TimeUnit.DAYS.toMillis(1)

                val myTasksToday = allTasks
                    .filterByVisibility(currentUserId)
                    .filter { 
                        it.assignedToUserId == currentUserId && 
                        it.status == TaskStatus.TODO &&
                        (it.dueDate == null || (it.dueDate!! >= today && it.dueDate!! < tomorrow))
                    }
                    .take(5)

                val weekStart = WeekUtils.getCurrentWeekStartMillis()
                val menu = weeklyMenuRepository.getMenu(weekStart)
                val dayOffset = ((today - weekStart) / java.util.concurrent.TimeUnit.DAYS.toMillis(1)).toInt()
                val mealsForToday = if (menu != null && dayOffset in 0..6) {
                    menu.entries.filter { it.dayOfWeek == dayOffset }
                } else {
                    emptyList()
                }
                val dayLabel = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(today))

                appWidgetIds.forEach { appWidgetId ->
                    val views = RemoteViews(context.packageName, R.layout.widget_layout)

                    // Настройка намерения для открытия приложения
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)

                    views.setTextViewText(R.id.widget_tasks_title, context.getString(R.string.widget_tasks_title))

                    if (myTasksToday.isEmpty()) {
                        views.setViewVisibility(R.id.widget_tasks_empty, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_tasks_list, android.view.View.GONE)
                        views.setTextViewText(R.id.widget_tasks_empty, context.getString(R.string.widget_tasks_empty))
                    } else {
                        views.setViewVisibility(R.id.widget_tasks_empty, View.GONE)
                        views.setViewVisibility(R.id.widget_tasks_list, android.view.View.VISIBLE)

                        // Создаем список задач
                        val tasksText = myTasksToday.joinToString("\n") { task ->
                            "• ${task.title}"
                        }

                        // Используем TextView для отображения списка задач
                        views.setTextViewText(R.id.widget_tasks_list, tasksText)
                    }

                    views.setViewVisibility(R.id.widget_meals_section, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_meals_title,
                        context.getString(R.string.widget_menu_title_format, dayLabel)
                    )

                    if (mealsForToday.isEmpty()) {
                        views.setViewVisibility(R.id.widget_menu_list, View.GONE)
                        views.setViewVisibility(R.id.widget_menu_empty, View.VISIBLE)
                        views.setTextViewText(R.id.widget_menu_empty, context.getString(R.string.widget_menu_empty))
                    } else {
                        val mealLines = MealType.values().map { mealType ->
                            val entry = mealsForToday.firstOrNull { it.mealType == mealType }
                            val mealLabel = context.getString(
                                when (mealType) {
                                    MealType.BREAKFAST -> R.string.meal_breakfast
                                    MealType.LUNCH -> R.string.meal_lunch
                                    MealType.DINNER -> R.string.meal_dinner
                                    MealType.SNACK -> R.string.meal_snack
                                }
                            )
                            val dish = entry?.let {
                                it.recipeId?.let { id -> recipes[id]?.title } ?: it.customDish
                            }?.takeIf { !it.isNullOrBlank() } ?: context.getString(R.string.menu_share_no_meal)
                            val servings = entry?.servings?.takeIf { it > 0 }?.let {
                                context.getString(R.string.menu_servings_format, it)
                            }
                            val assigned = entry?.assignedMemberId?.let { membersById[it]?.displayName }
                            val base = context.getString(R.string.widget_menu_line_format, mealLabel, dish)
                            buildString {
                                append("• ")
                                append(base)
                                if (!servings.isNullOrBlank()) {
                                    append(" — ")
                                    append(servings)
                                }
                                if (!assigned.isNullOrBlank()) {
                                    append(" (")
                                    append(context.getString(R.string.menu_share_assigned_short, assigned))
                                    append(")")
                                }
                            }
                        }
                        views.setViewVisibility(R.id.widget_menu_empty, View.GONE)
                        views.setViewVisibility(R.id.widget_menu_list, View.VISIBLE)
                        views.setTextViewText(R.id.widget_menu_list, mealLines.joinToString("\n"))
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                // В случае ошибки показываем сообщение об ошибке
                appWidgetIds.forEach { appWidgetId ->
                    val views = RemoteViews(context.packageName, R.layout.widget_layout)
                    views.setViewVisibility(R.id.widget_tasks_empty, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_tasks_list, android.view.View.GONE)
                    views.setTextViewText(R.id.widget_tasks_empty, context.getString(R.string.widget_error_loading))
                    views.setViewVisibility(R.id.widget_meals_section, View.GONE)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }

    companion object {
        fun updateWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, FamilyHubWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, FamilyHubWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
