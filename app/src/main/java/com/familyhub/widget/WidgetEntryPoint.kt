package com.familyhub.widget

import android.content.Context
import com.familyhub.FamilyHubApplication
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.TaskRepository
import com.familyhub.data.repository.WeeklyMenuRepository
import com.familyhub.data.repository.RecipeRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun taskRepository(): TaskRepository
    fun memberRepository(): FamilyMemberRepository
    fun appSettingsDataStore(): AppSettingsDataStore
    fun weeklyMenuRepository(): WeeklyMenuRepository
    fun recipeRepository(): RecipeRepository
}

fun getWidgetEntryPoint(context: Context): WidgetEntryPoint {
    val application = context.applicationContext as? FamilyHubApplication
        ?: throw IllegalStateException("Application context must be FamilyHubApplication")
    return EntryPointAccessors.fromApplication(application, WidgetEntryPoint::class.java)
}
