package com.familyhub.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.familyhub.data.local.db.converters.MemberRoleConverter
import com.familyhub.data.local.entity.FamilyEventEntity
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.data.local.entity.ShoppingActivityEntity
import com.familyhub.data.local.entity.ShoppingCategoryEntity
import com.familyhub.data.local.entity.ShoppingItemEntity
import com.familyhub.data.local.entity.ShoppingListEntity
import com.familyhub.data.local.entity.RewardEntity
import com.familyhub.data.local.entity.RewardRequestEntity
import com.familyhub.data.local.entity.TaskEntity
import com.familyhub.data.local.entity.TaskHistoryEntity
import com.familyhub.data.local.entity.TaskCompletionEntity
import com.familyhub.data.local.entity.UserAchievementEntity
import com.familyhub.data.local.entity.UserScoreEntity
import com.familyhub.data.local.entity.UserWalletEntity
import com.familyhub.data.local.entity.TaskRotationEntity
import com.familyhub.data.local.entity.TaskTemplateEntity
import com.familyhub.data.local.entity.TaskCategoryEntity
import com.familyhub.data.local.entity.AppreciationEntity
import com.familyhub.data.local.entity.FamilyGoalEntity
import com.familyhub.data.local.entity.FamilyGoalStageEntity
import com.familyhub.data.local.entity.FamilyGoalStepEntity
import com.familyhub.data.local.entity.HouseRuleEntity
import com.familyhub.data.local.entity.HouseRuleBlockEntity
import com.familyhub.data.local.entity.MenuEntryEntity
import com.familyhub.data.local.entity.MenuTemplateEntity
import com.familyhub.data.local.entity.MenuTemplateEntryEntity
import com.familyhub.data.local.entity.RecipeEntity
import com.familyhub.data.local.entity.RecipeIngredientEntity
import com.familyhub.data.local.entity.RecipeStepEntity
import com.familyhub.data.local.entity.DailyAssignmentEntity
import com.familyhub.data.local.db.dao.FamilyEventDao
import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.db.dao.DataWipeDao
import com.familyhub.data.local.db.dao.ShoppingItemDao
import com.familyhub.data.local.db.dao.RewardDao
import com.familyhub.data.local.db.dao.RewardRequestDao
import com.familyhub.data.local.db.dao.TaskDao
import com.familyhub.data.local.db.dao.TaskHistoryDao
import com.familyhub.data.local.db.dao.TaskCompletionDao
import com.familyhub.data.local.db.dao.UserScoreDao
import com.familyhub.data.local.db.dao.UserAchievementDao
import com.familyhub.data.local.db.dao.UserWalletDao
import com.familyhub.data.local.db.dao.TaskRotationDao
import com.familyhub.data.local.db.dao.TaskTemplateDao
import com.familyhub.data.local.db.dao.TaskCategoryDao
import com.familyhub.data.local.db.dao.AppreciationDao
import com.familyhub.data.local.db.dao.HouseRuleDao
import com.familyhub.data.local.db.dao.FamilyGoalDao
import com.familyhub.data.local.db.dao.MenuTemplateDao
import com.familyhub.data.local.db.dao.RecipeDao
import com.familyhub.data.local.db.dao.WeeklyMenuDao
import com.familyhub.data.local.db.dao.EmergencyContactDao
import com.familyhub.data.local.db.dao.DailyAssignmentDao
import com.familyhub.data.local.db.dao.PenaltyDao
import com.familyhub.data.local.db.converters.StringListConverter
import com.familyhub.data.local.db.converters.TaskAttachmentListConverter
import com.familyhub.data.local.db.converters.TaskCommentListConverter
import com.familyhub.data.local.entity.WeeklyMenuEntity
import com.familyhub.data.local.entity.EmergencyContactEntity
import com.familyhub.data.local.entity.PenaltyEntity
import com.familyhub.data.local.entity.SyncOutboxEntity
import com.familyhub.data.local.entity.SyncTrackingControlEntity
import com.familyhub.data.local.db.dao.SyncOutboxDao

@Database(
    entities = [
        FamilyMemberEntity::class,
        TaskEntity::class,
        ShoppingItemEntity::class,
        ShoppingListEntity::class,
        ShoppingCategoryEntity::class,
        ShoppingActivityEntity::class,
        FamilyEventEntity::class,
        UserScoreEntity::class,
        TaskHistoryEntity::class,
        TaskCompletionEntity::class,
        UserAchievementEntity::class,
        RewardEntity::class,
        RewardRequestEntity::class,
        UserWalletEntity::class,
        TaskRotationEntity::class,
        HouseRuleEntity::class,
        HouseRuleBlockEntity::class,
        TaskTemplateEntity::class,
        TaskCategoryEntity::class,
        AppreciationEntity::class,
        FamilyGoalEntity::class,
        FamilyGoalStageEntity::class,
        FamilyGoalStepEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        RecipeStepEntity::class,
        WeeklyMenuEntity::class,
        MenuEntryEntity::class,
        MenuTemplateEntity::class,
        MenuTemplateEntryEntity::class,
        DailyAssignmentEntity::class,
        EmergencyContactEntity::class,
        PenaltyEntity::class,
        SyncOutboxEntity::class,
        SyncTrackingControlEntity::class,
    ],
    version = 39,
    exportSchema = true
)
@TypeConverters(MemberRoleConverter::class, StringListConverter::class, TaskAttachmentListConverter::class, TaskCommentListConverter::class)
abstract class FamilyHubDatabase : RoomDatabase() {
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun taskDao(): TaskDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun familyEventDao(): FamilyEventDao
    abstract fun userScoreDao(): UserScoreDao
    abstract fun taskHistoryDao(): TaskHistoryDao
    abstract fun taskCompletionDao(): TaskCompletionDao
    abstract fun userAchievementDao(): UserAchievementDao
    abstract fun rewardDao(): RewardDao
    abstract fun rewardRequestDao(): RewardRequestDao
    abstract fun userWalletDao(): UserWalletDao
    abstract fun taskRotationDao(): TaskRotationDao
    abstract fun houseRuleDao(): HouseRuleDao
    abstract fun taskTemplateDao(): TaskTemplateDao
    abstract fun taskCategoryDao(): TaskCategoryDao
    abstract fun appreciationDao(): AppreciationDao
    abstract fun familyGoalDao(): FamilyGoalDao
    abstract fun recipeDao(): RecipeDao
    abstract fun weeklyMenuDao(): WeeklyMenuDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun menuTemplateDao(): MenuTemplateDao
    abstract fun dailyAssignmentDao(): DailyAssignmentDao
    abstract fun penaltyDao(): PenaltyDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun dataWipeDao(): DataWipeDao
}
