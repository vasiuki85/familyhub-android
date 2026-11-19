package com.familyhub.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.familyhub.R
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.WeeklyMenu
import com.familyhub.domain.model.MenuEntry
import com.familyhub.domain.model.Recipe
import com.familyhub.domain.utils.WeekUtils
import com.familyhub.util.TimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
private const val PDF_WIDTH = 1240

enum class MenuShareFormat {
    PDF,
    IMAGE
}

@Singleton
class MealPlanShareService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dayTitleFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
    private val weekFileFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    fun createShareIntent(
        menu: WeeklyMenu,
        recipes: Map<String, Recipe>,
        members: Map<String, FamilyMember>,
        format: MenuShareFormat
    ): Intent {
        require(menu.entries.isNotEmpty()) { "Menu is empty" }
        val file = when (format) {
            MenuShareFormat.PDF -> generatePdf(menu, recipes, members)
            MenuShareFormat.IMAGE -> generateImage(menu, recipes, members)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = when (format) {
            MenuShareFormat.PDF -> "application/pdf"
            MenuShareFormat.IMAGE -> "image/png"
        }
        val summaryText = buildShareSummary(menu, recipes, members)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.menu_share_subject))
            putExtra(Intent.EXTRA_TEXT, summaryText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, context.getString(R.string.menu_share_title))
    }

    private fun generatePdf(
        menu: WeeklyMenu,
        recipes: Map<String, Recipe>,
        members: Map<String, FamilyMember>
    ): File {
        val width = PDF_WIDTH
        val height = renderMenu(null, width, menu, recipes, members).roundToInt().coerceAtLeast(1)
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
        val page = document.startPage(pageInfo)
        renderMenu(page.canvas, width, menu, recipes, members)
        document.finishPage(page)

        val file = createTargetFile(menu, "pdf")
        FileOutputStream(file).use { output ->
            document.writeTo(output)
        }
        document.close()
        return file
    }

    private fun generateImage(
        menu: WeeklyMenu,
        recipes: Map<String, Recipe>,
        members: Map<String, FamilyMember>
    ): File {
        val width = 1080
        val height = renderMenu(null, width, menu, recipes, members).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        renderMenu(canvas, width, menu, recipes, members)
        val file = createTargetFile(menu, "png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
        return file
    }

    private fun renderMenu(
        canvas: Canvas?,
        width: Int,
        menu: WeeklyMenu,
        recipes: Map<String, Recipe>,
        members: Map<String, FamilyMember>
    ): Float {
        val paddingHorizontal = 80f
        val paddingVertical = 80f
        val sectionSpacing = 48f
        val mealSpacing = 28f
        val contentWidth = (width - paddingHorizontal * 2).roundToInt().coerceAtLeast(1)

        val titlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 32f
        }
        val dayPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val mealPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 32f
        }

        canvas?.drawColor(Color.WHITE)

        var y = paddingVertical
        val startDateText = TimeUtils.formatDate(menu.startDateMillis)
        val endDateText = TimeUtils.formatDate(TimeUtils.addDays(menu.startDateMillis, 6))
        val title = context.getString(R.string.menu_share_header, startDateText, endDateText)
        val titleLayout = buildStaticLayout(title, titlePaint, contentWidth)
        canvas?.let {
            it.save()
            it.translate(paddingHorizontal, y)
            titleLayout.draw(it)
            it.restore()
        }
        y += titleLayout.height + 16f

        val subtitle = context.getString(R.string.menu_share_subtitle, menu.entries.size)
        val subtitleLayout = buildStaticLayout(subtitle, subtitlePaint, contentWidth)
        canvas?.let {
            it.save()
            it.translate(paddingHorizontal, y)
            subtitleLayout.draw(it)
            it.restore()
        }
        y += subtitleLayout.height + sectionSpacing

        val todayStart = TimeUtils.getStartOfDay(TimeUtils.now())
        val currentWeekStart = WeekUtils.getWeekStartMillis(todayStart)
        val todayOffset = if (menu.startDateMillis == currentWeekStart) {
            ((todayStart - currentWeekStart) / DAY_MILLIS).toInt()
        } else {
            null
        }

        for (dayOffset in 0 until 7) {
            val dayMillis = WeekUtils.dayStart(menu.startDateMillis, dayOffset)
            val dayLabelBase = dayTitleFormat.format(Date(dayMillis)).replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            }
            val dayLabel = buildString {
                if (todayOffset == dayOffset) {
                    append(context.getString(R.string.menu_share_today_prefix))
                }
                append(dayLabelBase)
                if (todayOffset == dayOffset) {
                    append(context.getString(R.string.menu_share_today_suffix))
                }
            }
            val dayLayout = buildStaticLayout(dayLabel, dayPaint, contentWidth)
            canvas?.let {
                it.save()
                it.translate(paddingHorizontal, y)
                dayLayout.draw(it)
                it.restore()
            }
            y += dayLayout.height + 12f

            val entriesForDay = menu.entries.filter { it.dayOfWeek == dayOffset }
            if (entriesForDay.isEmpty()) {
                val emptyText = context.getString(R.string.menu_share_no_meal)
                val emptyLayout = buildStaticLayout(emptyText, mealPaint, contentWidth)
                canvas?.let {
                    it.save()
                    it.translate(paddingHorizontal + 32f, y)
                    emptyLayout.draw(it)
                    it.restore()
                }
                y += emptyLayout.height + mealSpacing
            } else {
                for (mealType in MealType.values()) {
                    val entry = entriesForDay.firstOrNull { it.mealType == mealType }
                    val line = buildMealLine(entry, mealType, recipes, members)
                    val mealLayout = buildStaticLayout(line, mealPaint, contentWidth)
                    canvas?.let {
                        it.save()
                        it.translate(paddingHorizontal + 32f, y)
                        mealLayout.draw(it)
                        it.restore()
                    }
                    y += mealLayout.height + mealSpacing
                }
            }

            y += sectionSpacing / 2
        }

        return y + paddingVertical
    }

    private fun buildMealLine(
        entry: MenuEntry?,
        mealType: MealType,
        recipes: Map<String, Recipe>,
        members: Map<String, FamilyMember>
    ): String {
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
        }?.takeIf { it.isNullOrBlank().not() } ?: context.getString(R.string.menu_share_no_meal)
        val servings = entry?.servings?.takeIf { it > 0 }?.let {
            context.getString(R.string.menu_servings_format, it)
        }
        val assigned = entry?.assignedMemberId?.let { id -> members[id]?.displayName }
        val assignedSuffix = assigned?.let { name ->
            context.getString(R.string.menu_share_assigned_format, name)
        }
        return buildString {
            append(mealLabel)
            append(": ")
            append(dish)
            if (!servings.isNullOrBlank()) {
                append(" — ")
                append(servings)
            }
            if (!assignedSuffix.isNullOrBlank()) {
                append(" ")
                append(assignedSuffix)
            }
        }
    }

    private fun buildShareSummary(
        menu: WeeklyMenu,
        recipes: Map<String, Recipe>,
        members: Map<String, FamilyMember>
    ): String {
        val startDateText = TimeUtils.formatDate(menu.startDateMillis)
        val endDateText = TimeUtils.formatDate(TimeUtils.addDays(menu.startDateMillis, 6))
        val builder = StringBuilder()
        builder.appendLine(context.getString(R.string.menu_share_summary_title, startDateText, endDateText))
        builder.appendLine()
        for (dayOffset in 0 until 7) {
            val dayMillis = WeekUtils.dayStart(menu.startDateMillis, dayOffset)
            val dayLabel = dayTitleFormat.format(Date(dayMillis)).replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            }
            builder.appendLine(dayLabel)
            for (mealType in MealType.values()) {
                val entry = menu.entries.firstOrNull { it.dayOfWeek == dayOffset && it.mealType == mealType }
                val line = buildMealLine(entry, mealType, recipes, members)
                builder.append("• ")
                builder.appendLine(line)
            }
            builder.appendLine()
        }
        return builder.toString()
    }

    private fun buildStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.15f)
            .build()
    }

    private fun createTargetFile(menu: WeeklyMenu, extension: String): File {
        val dir = File(context.cacheDir, "menu_exports").apply { mkdirs() }
        dir.listFiles()?.filter { it.extension.equals(extension, ignoreCase = true) }?.forEach { it.delete() }
        val fileName = "menu_${weekFileFormat.format(Date(menu.startDateMillis))}.$extension"
        return File(dir, fileName)
    }
}
