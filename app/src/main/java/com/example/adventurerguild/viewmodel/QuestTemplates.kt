package com.example.adventurerguild.viewmodel

import android.content.Context
import com.example.adventurerguild.model.AdventurerRank
import com.example.adventurerguild.model.Quest
import com.example.adventurerguild.model.QuestDifficulty
import com.example.adventurerguild.model.QuestStatus
import com.example.adventurerguild.model.QuestTemplate
import com.example.adventurerguild.model.QuestType
import com.example.adventurerguild.model.normalizedTimingPolicy

fun childDailyQuestTemplates(guildId: String, adminUid: String): List<Quest> {
    val weekdays = listOf(1, 2, 3, 4, 5, 6, 7)
    fun daily(title: String, description: String, gp: Long = 10, exp: Long = 10, tags: List<String> = listOf("生活習慣")) = Quest(
        guildId = guildId,
        title = title,
        description = description,
        type = QuestType.DAILY_QUEST,
        status = QuestStatus.PUBLISHED,
        gpReward = gp,
        expReward = exp,
        createdBy = adminUid,
        activeWeekdays = weekdays,
        difficulty = QuestDifficulty.EASY,
        tags = tags,
        minRank = AdventurerRank.F
    )

    return listOf(
        daily("早上喝水 1000cc", "今天早上到中午前喝完 1000cc 水，可以用文字或照片回報，也可以空白交由公會認定。", gp = 15, exp = 15, tags = listOf("健康", "喝水")),
        daily("回家先洗手", "回家後先用肥皂洗手，完成後回報。", tags = listOf("衛生", "生活習慣")),
        daily("聯絡簿簽名確認", "確認聯絡簿已給家長簽名，或由家長/公會管理員認定完成。", gp = 12, exp = 10, tags = listOf("學校", "責任")),
        daily("睡前刷牙", "睡前完成刷牙，養成每天照顧牙齒的習慣。", tags = listOf("衛生", "睡前")),
        daily("整理明天書包", "把明天要帶的課本、作業、文具放進書包。", gp = 12, exp = 12, tags = listOf("學校", "整理")),
        daily("閱讀 10 分鐘", "閱讀任何喜歡的書 10 分鐘，可以回報書名或空白讓公會認定。", gp = 15, exp = 20, tags = listOf("閱讀", "學習")),
        daily("收玩具與桌面", "把玩具或桌面整理到指定位置。", gp = 10, exp = 12, tags = listOf("整理", "生活習慣")),
        daily("運動伸展 5 分鐘", "做簡單伸展、跳繩、散步或其他安全運動 5 分鐘。", gp = 15, exp = 15, tags = listOf("健康", "運動")),
        daily("對家人說謝謝", "今天主動對家人說一次謝謝，練習表達感謝。", gp = 10, exp = 10, tags = listOf("禮貌", "情緒")),
        daily("準時上床準備睡覺", "在約定時間前完成睡前準備並上床。", gp = 15, exp = 15, tags = listOf("睡眠", "生活習慣"))
    )
}

fun loadBundledQuestTemplates(context: Context, guildId: String): List<QuestTemplate> =
    runCatching {
        context.assets.list("quest_templates")
            .orEmpty()
            .filter { it.endsWith(".csv", ignoreCase = true) }
            .flatMap { fileName ->
                val text = context.assets.open("quest_templates/$fileName")
                    .bufferedReader()
                    .use { it.readText() }
                parseQuestTemplateCsv(text, fileName, guildId)
            }
    }.getOrDefault(emptyList())

private fun parseQuestTemplateCsv(text: String, sourceFile: String, guildId: String): List<QuestTemplate> {
    val rows = text.lineSequence()
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        .map(::parseCsvLine)
        .toList()
    if (rows.isEmpty()) return emptyList()
    val headers = rows.first().map { it.trim() }
    return rows.drop(1).mapNotNull { values ->
        val row = headers.zip(values + List((headers.size - values.size).coerceAtLeast(0)) { "" }).toMap()
        val title = row["title"].orEmpty().trim()
        if (title.isBlank()) return@mapNotNull null
        val templateName = row["template_name"].orEmpty().ifBlank { title }
        val type = enumOrDefault(row["type"], QuestType.DAILY_QUEST)
        val quest = Quest(
            guildId = guildId,
            title = title,
            description = row["description"].orEmpty(),
            type = type,
            status = QuestStatus.DRAFT,
            gpReward = row["gp_reward"].toLongOrDefault(10),
            expReward = row["exp_reward"].toLongOrDefault(10),
            targetCount = row["target_count"].toLongOrDefault(1),
            hasTimeLimit = row["has_time_limit"].toBooleanLoose(),
            penaltyGp = row["penalty_gp"].toLongOrDefault(0),
            penaltyExp = row["penalty_exp"].toLongOrDefault(0),
            activeWeekdays = row["active_weekdays"].toIntList(),
            difficulty = enumOrDefault(row["difficulty"], QuestDifficulty.NORMAL),
            tags = row["tags"].toStringList(),
            minRank = enumOrDefault(row["min_rank"], AdventurerRank.F),
            bonusGp = row["bonus_gp"].toLongOrDefault(0),
            bonusExp = row["bonus_exp"].toLongOrDefault(0),
            gracePeriodDays = row["grace_period_days"].toIntOrDefault(0),
            submissionDeadlineDays = row["submission_deadline_days"].toIntOrDefault(0),
            weeklyRefreshWeekday = row["weekly_refresh_weekday"].toIntOrNullIn(1, 7),
            monthlyRefreshDay = row["monthly_refresh_day"].toIntOrNullIn(1, 31),
            pinned = row["pinned"].toBooleanLoose()
        ).normalizedTimingPolicy()
        QuestTemplate(
            id = "asset:$sourceFile:$templateName",
            guildId = guildId,
            name = templateName,
            quest = quest,
            createdBy = "bundled-csv"
        )
    }
}

private fun parseCsvLine(line: String): List<String> {
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val char = line[i]
        when {
            char == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                cell.append('"')
                i++
            }
            char == '"' -> inQuotes = !inQuotes
            char == ',' && !inQuotes -> {
                cells += cell.toString()
                cell.clear()
            }
            else -> cell.append(char)
        }
        i++
    }
    cells += cell.toString()
    return cells
}

private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T =
    raw?.trim()?.takeIf { it.isNotBlank() }?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

private fun String?.toLongOrDefault(default: Long): Long = this?.trim()?.toLongOrNull() ?: default

private fun String?.toIntOrDefault(default: Int): Int = this?.trim()?.toIntOrNull() ?: default

private fun String?.toIntOrNullIn(min: Int, max: Int): Int? = this?.trim()?.toIntOrNull()?.coerceIn(min, max)

private fun String?.toBooleanLoose(): Boolean =
    this?.trim()?.lowercase() in setOf("true", "1", "yes", "y")

private fun String?.toStringList(): List<String> =
    this.orEmpty().split(";", "；", "、").map { it.trim() }.filter { it.isNotBlank() }.distinct()

private fun String?.toIntList(): List<Int> =
    toStringList().mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.distinct()
