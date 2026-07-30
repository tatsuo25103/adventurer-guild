package com.example.adventurerguild.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.example.adventurerguild.MainActivity
import com.example.adventurerguild.R
import com.example.adventurerguild.ui.AppLanguage
import com.example.adventurerguild.ui.AppLanguageStore
import com.example.adventurerguild.ui.text
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class QuestStatusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        QuestWidgetUpdater.update(context, manager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetConfigStore.clear(context, it) }
        super.onDeleted(context, appWidgetIds)
    }
}

object QuestWidgetUpdater {
    const val EXTRA_OPEN_QUEST_ID = "com.example.adventurerguild.extra.OPEN_QUEST_ID"
    const val EXTRA_WIDGET_GUILD_ID = "com.example.adventurerguild.extra.WIDGET_GUILD_ID"
    const val EXTRA_WIDGET_MODE = "com.example.adventurerguild.extra.WIDGET_MODE"

    fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, QuestStatusWidgetProvider::class.java))
        update(context, manager, ids)
    }

    fun update(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val source = WidgetDataSource.load(context)
        appWidgetIds.forEach { widgetId ->
            val target = WidgetConfigStore.load(context, widgetId)
                ?.takeIf { source.canUseTarget(it) }
                ?: source.defaultTarget()?.also { WidgetConfigStore.save(context, widgetId, it) }
            val state = source.buildState(target)
            manager.updateAppWidget(
                widgetId,
                buildRemoteViews(context, widgetId, target, state, source.language)
            )
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_quest_list)
        }
    }

    fun updateAppWidget(context: Context, appWidgetId: Int) {
        update(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
    }

    private fun buildRemoteViews(
        context: Context,
        appWidgetId: Int,
        target: WidgetTarget?,
        state: WidgetQuestState,
        language: AppLanguage
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_quest_status)
        views.setTextViewText(R.id.widget_title, state.title)
        views.setTextViewText(R.id.widget_summary, state.summary)
        views.setTextViewText(
            R.id.widget_empty,
            state.questItems.firstOrNull { it.questId.isBlank() }?.line
                ?: language.text("目前沒有任務", "No quests", "Keine Aufgaben", "クエストなし")
        )

        val serviceIntent = Intent(context, QuestStatusWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("adventurerguild://widget/list/$appWidgetId")
        }
        views.setRemoteAdapter(R.id.widget_quest_list, serviceIntent)
        views.setEmptyView(R.id.widget_quest_list, R.id.widget_empty)
        views.setPendingIntentTemplate(
            R.id.widget_quest_list,
            pendingIntentTemplate(context, appWidgetId, target)
        )

        val openAppIntent = pendingIntentForQuest(context, appWidgetId, target, null, 0)
        views.setOnClickPendingIntent(R.id.widget_title, openAppIntent)
        views.setOnClickPendingIntent(R.id.widget_summary, openAppIntent)
        views.setOnClickPendingIntent(R.id.widget_empty, openAppIntent)
        return views
    }

    private fun pendingIntentTemplate(
        context: Context,
        widgetId: Int,
        target: WidgetTarget?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_QUEST
            data = Uri.parse("adventurerguild://widget/$widgetId/list/${target?.guildId ?: "none"}/${target?.mode?.name ?: "none"}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            target?.let {
                putExtra(EXTRA_WIDGET_GUILD_ID, it.guildId)
                putExtra(EXTRA_WIDGET_MODE, it.mode.name)
            }
        }
        return PendingIntent.getActivity(
            context,
            ("$widgetId:${target?.guildId}:${target?.mode}:list").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun pendingIntentForQuest(
        context: Context,
        widgetId: Int,
        target: WidgetTarget?,
        questId: String?,
        row: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = if (questId.isNullOrBlank()) ACTION_OPEN_APP else ACTION_OPEN_QUEST
            data = Uri.parse("adventurerguild://widget/$widgetId/${questId ?: "home"}/$row/${target?.guildId ?: "none"}/${target?.mode?.name ?: "none"}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            questId?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_OPEN_QUEST_ID, it) }
            target?.let {
                putExtra(EXTRA_WIDGET_GUILD_ID, it.guildId)
                putExtra(EXTRA_WIDGET_MODE, it.mode.name)
            }
        }
        return PendingIntent.getActivity(
            context,
            ("$widgetId:${target?.guildId}:${target?.mode}:${questId ?: "home"}:$row").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val ACTION_OPEN_APP = "com.example.adventurerguild.widget.OPEN_APP"
    private const val ACTION_OPEN_QUEST = "com.example.adventurerguild.widget.OPEN_QUEST"
}

enum class WidgetMode { ADVENTURER, MANAGER }

data class WidgetTarget(
    val guildId: String,
    val mode: WidgetMode
)

data class WidgetTargetChoice(
    val target: WidgetTarget,
    val label: String,
    val subtitle: String
)

object WidgetConfigStore {
    private const val PREFS = "quest_status_widget_prefs"
    private const val KEY_GUILD = "guild_id_"
    private const val KEY_MODE = "mode_"

    fun save(context: Context, appWidgetId: Int, target: WidgetTarget) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GUILD + appWidgetId, target.guildId)
            .putString(KEY_MODE + appWidgetId, target.mode.name)
            .apply()
    }

    fun load(context: Context, appWidgetId: Int): WidgetTarget? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val guildId = prefs.getString(KEY_GUILD + appWidgetId, null)?.takeIf { it.isNotBlank() } ?: return null
        val mode = prefs.getString(KEY_MODE + appWidgetId, null)
            ?.let { runCatching { WidgetMode.valueOf(it) }.getOrNull() }
            ?: return null
        return WidgetTarget(guildId, mode)
    }

    fun clear(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_GUILD + appWidgetId)
            .remove(KEY_MODE + appWidgetId)
            .apply()
    }
}

data class WidgetDataSource(
    val currentUid: String?,
    val currentGuildId: String?,
    val currentUser: JSONObject?,
    val users: List<JSONObject>,
    val guilds: List<JSONObject>,
    val quests: List<JSONObject>,
    val submissions: List<JSONObject>,
    val language: AppLanguage
) {
    fun choices(): List<WidgetTargetChoice> {
        val user = currentUser ?: return emptyList()
        return guilds.flatMap { guild ->
            val guildId = guild.optString("id")
            buildList {
                if (user.isAdventurerOf(guild)) {
                    add(
                        WidgetTargetChoice(
                            target = WidgetTarget(guildId, WidgetMode.ADVENTURER),
                            label = guild.optString("name").ifBlank {
                                language.text("未命名公會", "Unnamed guild", "Unbenannte Gilde", "名称未設定ギルド")
                            },
                            subtitle = language.text(
                                "冒險者任務",
                                "Adventurer quests",
                                "Abenteureraufgaben",
                                "冒険者クエスト"
                            )
                        )
                    )
                }
                if (user.isManagerOf(guild)) {
                    add(
                        WidgetTargetChoice(
                            target = WidgetTarget(guildId, WidgetMode.MANAGER),
                            label = guild.optString("name").ifBlank {
                                language.text("未命名公會", "Unnamed guild", "Unbenannte Gilde", "名称未設定ギルド")
                            },
                            subtitle = language.text(
                                "管理方待辦",
                                "Guild staff tasks",
                                "Verwaltungsaufgaben",
                                "管理者の対応事項"
                            )
                        )
                    )
                }
            }
        }.sortedWith(compareBy<WidgetTargetChoice> { it.label }.thenBy { it.subtitle })
    }

    fun canUseTarget(target: WidgetTarget): Boolean {
        val user = currentUser ?: return false
        val guild = guilds.firstOrNull { it.optString("id") == target.guildId } ?: return false
        return when (target.mode) {
            WidgetMode.ADVENTURER -> user.isAdventurerOf(guild)
            WidgetMode.MANAGER -> user.isManagerOf(guild)
        }
    }

    fun defaultTarget(): WidgetTarget? {
        val current = currentGuildId?.let { guildId ->
            choices().firstOrNull { it.target.guildId == guildId }?.target
        }
        return current ?: choices().firstOrNull()?.target
    }

    internal fun buildState(target: WidgetTarget?): WidgetQuestState {
        val user = currentUser
        if (user == null) {
            return WidgetQuestState(
                language.text("冒險者公會任務", "Adventurer Guild Quests", "Aufgaben der Abenteurergilde", "冒険者ギルドのクエスト"),
                language.text("尚未登入", "Not signed in", "Nicht angemeldet", "未ログイン"),
                listOf(
                    WidgetQuestItem(
                        "",
                        language.text("點擊開啟 App 登入", "Tap to sign in", "Zum Anmelden tippen", "タップしてログイン"),
                        COLOR_INK
                    )
                )
            )
        }
        if (target == null) {
            return WidgetQuestState(
                language.text("冒險者公會任務", "Adventurer Guild Quests", "Aufgaben der Abenteurergilde", "冒険者ギルドのクエスト"),
                language.text("尚未選擇公會", "No guild selected", "Keine Gilde ausgewählt", "ギルド未選択"),
                listOf(
                    WidgetQuestItem(
                        "",
                        language.text(
                            "長按小工具後重新設定",
                            "Long-press the widget to configure it",
                            "Widget lange drücken, um es einzurichten",
                            "ウィジェットを長押しして設定してください"
                        ),
                        COLOR_INK
                    )
                )
            )
        }
        val guild = guilds.firstOrNull { it.optString("id") == target.guildId }
            ?: return WidgetQuestState("冒險者公會任務", "找不到小工具指定公會", listOf(WidgetQuestItem("", "重新設定小工具", COLOR_INK)))
        return when (target.mode) {
            WidgetMode.ADVENTURER -> buildAdventurerState(user, guild)
            WidgetMode.MANAGER -> buildManagerState(user, guild)
        }
    }

    private fun buildAdventurerState(user: JSONObject, guild: JSONObject): WidgetQuestState {
        val now = System.currentTimeMillis()
        val guildId = guild.optString("id")
        val userId = user.optString("uid")
        val activeQuests = quests
            .filter { it.optString("guildId") == guildId }
            .filter { it.isPublished() }
            .filter { it.isAnnounced(now) }
            .filter { it.isVisibleToAdventurer(guild, now) || it.isWithinSubmissionDeadline(now) }
            .filter { it.canBeSeenBy(userId) }
            .filter { it.adventurerState(user, submissions, now) in setOf(WidgetAdventurerQuestState.IN_PROGRESS, WidgetAdventurerQuestState.SUBMITTED, WidgetAdventurerQuestState.REVISION) }
            .sortedWith(widgetQuestOrder(now))

        val pendingCount = activeQuests.count { it.adventurerState(user, submissions, now) == WidgetAdventurerQuestState.SUBMITTED }
        val typeSummary = activeQuests.typeSummary(language)
        val items = activeQuests.mapNotNull { quest ->
            val state = quest.adventurerState(user, submissions, now) ?: return@mapNotNull null
            WidgetQuestItem(
                questId = quest.optString("id"),
                line = "${quest.dueLabel(now, language)}[${state.localizedLabel(language)}] ${quest.optString("type").toQuestTypeLabel(language)} · ${quest.optString("title")}",
                color = quest.dueColor(now)
            )
        }
        return WidgetQuestState(
            title = language.text(
                "${guild.optString("name").ifBlank { "冒險者公會" }} 任務",
                "${guild.optString("name").ifBlank { "Adventurer Guild" }} Quests",
                "${guild.optString("name").ifBlank { "Abenteurergilde" }} Aufgaben",
                "${guild.optString("name").ifBlank { "冒険者ギルド" }} クエスト"
            ),
            summary = language.text(
                "未完成 ${activeQuests.size} · 待審 $pendingCount · $typeSummary",
                "Active ${activeQuests.size} · Review $pendingCount · $typeSummary",
                "Aktiv ${activeQuests.size} · Prüfung $pendingCount · $typeSummary",
                "未完了 ${activeQuests.size} · 審査待ち $pendingCount · $typeSummary"
            ),
            questItems = items.ifEmpty {
                listOf(
                    WidgetQuestItem(
                        "",
                        language.text("目前沒有手上任務", "No active quests", "Keine aktiven Aufgaben", "進行中のクエストはありません"),
                        COLOR_INK
                    )
                )
            }
        )
    }

    private fun buildManagerState(user: JSONObject, guild: JSONObject): WidgetQuestState {
        val now = System.currentTimeMillis()
        val guildId = guild.optString("id")
        val userId = user.optString("uid")
        val guildQuests = quests.filter { it.optString("guildId") == guildId }
        val reviewableQuestIds = guildQuests
            .filter { user.canReviewQuest(guild, it) }
            .map { it.optString("id") }
            .toSet()
        val pending = submissions
            .filter { it.optString("status") == "SUBMITTED" && it.optString("questId") in reviewableQuestIds }
            .sortedBy { it.optLong("submittedAtMillis", 0L) }
        val publishedCount = guildQuests.count { it.isPublished() && it.isAnnounced(now) }
        val scheduledCount = guildQuests.count { it.isPublished() && !it.isAnnounced(now) }
        val draftCount = guildQuests.count { it.optString("status") == "DRAFT" }
        val questById = guildQuests.associateBy { it.optString("id") }
        val items = pending.map { submission ->
            val quest = questById[submission.optString("questId")]
            val assignedReviewers = quest?.optJSONArray("assignedReviewerIds").stringList()
            WidgetQuestItem(
                questId = submission.optString("questId"),
                line = "[${language.text("待審", "Review", "Prüfung", "審査待ち")}] ${
                    submission.optString("userName").ifBlank {
                        language.text("成員", "Member", "Mitglied", "メンバー")
                    }
                } · ${quest?.optString("title") ?: submission.optString("questTitle")}",
                color = if (assignedReviewers.isNotEmpty() && userId !in assignedReviewers) COLOR_SOON else COLOR_INK
            )
        }
        return WidgetQuestState(
            title = language.text(
                "${guild.optString("name").ifBlank { "公會" }} 管理",
                "${guild.optString("name").ifBlank { "Guild" }} Management",
                "${guild.optString("name").ifBlank { "Gilde" }} Verwaltung",
                "${guild.optString("name").ifBlank { "ギルド" }} 管理"
            ),
            summary = language.text(
                "待審 ${pending.size} · 已上架 $publishedCount · 待上架 $draftCount · 未公告 $scheduledCount",
                "Review ${pending.size} · Published $publishedCount · Drafts $draftCount · Scheduled $scheduledCount",
                "Prüfung ${pending.size} · Veröffentlicht $publishedCount · Entwürfe $draftCount · Geplant $scheduledCount",
                "審査待ち ${pending.size} · 公開中 $publishedCount · 下書き $draftCount · 公開予定 $scheduledCount"
            ),
            questItems = items.ifEmpty {
                listOf(
                    WidgetQuestItem(
                        "",
                        language.text(
                            "目前沒有可審核回報",
                            "No submissions to review",
                            "Keine Einreichungen zur Prüfung",
                            "審査可能な報告はありません"
                        ),
                        COLOR_INK
                    )
                )
            }
        )
    }

    companion object {
        fun load(context: Context): WidgetDataSource {
            val language = AppLanguageStore.load(context)
            val file = File(context.filesDir, "offline_guild_state.json")
            if (!file.exists()) {
                return WidgetDataSource(null, null, null, emptyList(), emptyList(), emptyList(), emptyList(), language)
            }
            return runCatching {
                val root = JSONObject(file.readText())
                val users = root.optJSONArray("users").asObjects()
                val currentUid = root.optString("currentUid").takeIf { it.isNotBlank() }
                WidgetDataSource(
                    currentUid = currentUid,
                    currentGuildId = root.optString("currentGuildId").takeIf { it.isNotBlank() },
                    currentUser = users.firstOrNull { it.optString("uid") == currentUid },
                    users = users,
                    guilds = root.optJSONArray("guilds").asObjects(),
                    quests = root.optJSONArray("quests").asObjects(),
                    submissions = root.optJSONArray("submissions").asObjects(),
                    language = language
                )
            }.getOrDefault(
                WidgetDataSource(null, null, null, emptyList(), emptyList(), emptyList(), emptyList(), language)
            )
        }
    }
}

internal data class WidgetQuestState(
    val title: String,
    val summary: String,
    val questItems: List<WidgetQuestItem>
)

internal data class WidgetQuestItem(
    val questId: String,
    val line: String,
    val color: Int
)

private enum class WidgetAdventurerQuestState(val label: String) {
    IN_PROGRESS("未完成"),
    SUBMITTED("待審"),
    REVISION("補件")
}

private fun WidgetAdventurerQuestState.localizedLabel(language: AppLanguage): String = when (this) {
    WidgetAdventurerQuestState.IN_PROGRESS ->
        language.text("未完成", "Active", "Aktiv", "未完了")
    WidgetAdventurerQuestState.SUBMITTED ->
        language.text("待審", "Review", "Prüfung", "審査待ち")
    WidgetAdventurerQuestState.REVISION ->
        language.text("補件", "Revision", "Nachbesserung", "修正必要")
}

private const val COLOR_INK = 0xFF241A12.toInt()
private const val COLOR_SOON = 0xFF8A5A00.toInt()
private const val COLOR_URGENT = 0xFF803A3A.toInt()

private fun widgetQuestOrder(now: Long): Comparator<JSONObject> =
    compareBy<JSONObject> { it.duePriority(now) }
        .thenByDescending { it.optBoolean("pinned", false) }
        .thenBy { it.optInt("sortOrder", 0) }
        .thenBy { it.optString("title") }

private fun JSONObject.adventurerState(
    user: JSONObject,
    submissions: List<JSONObject>,
    now: Long
): WidgetAdventurerQuestState? {
    val type = optString("type")
    val userId = user.optString("uid")
    if (user.optString("rank").rankOrdinal() < optString("minRank").rankOrdinal()) return null
    if (type == "MAIN_QUEST" && missingPrerequisiteQuestIds(userId, submissions).isNotEmpty()) return null
    if (type == "PROMOTION_QUEST" && !user.canStartPromotionTrial()) return null
    val mandatory = type.isStrictCycleQuestType()
    val repeatable = type == "REPEATABLE_QUEST"
    val formation = type == "FORMATION_QUEST"
    val latest = submissions
        .filter { it.optString("questId") == optString("id") && it.optString("userId") == userId }
        .filter { !mandatory || it.optLong("submittedAtMillis", 0L) in currentCycleWindow(now) }
        .maxByOrNull { it.optLong("submittedAtMillis", 0L) }
    return when (latest?.optString("status")) {
        "SUBMITTED" -> WidgetAdventurerQuestState.SUBMITTED
        "NEEDS_REVISION", "REJECTED" -> WidgetAdventurerQuestState.REVISION
        "APPROVED" -> null
        else -> when {
            mandatory -> WidgetAdventurerQuestState.IN_PROGRESS
            formation && assignedFormationSlotIds(userId).isNotEmpty() -> WidgetAdventurerQuestState.IN_PROGRESS
            !repeatable && optString("id") in user.optJSONArray("acceptedQuestIds").stringList() -> WidgetAdventurerQuestState.IN_PROGRESS
            repeatable && !isRepeatLimitReached(userId, submissions, now) -> WidgetAdventurerQuestState.IN_PROGRESS
            else -> null
        }
    }
}

private fun JSONObject.canReviewQuest(guild: JSONObject, quest: JSONObject): Boolean {
    val uid = optString("uid")
    if (!isManagerOf(guild)) return false
    if (guild.optString("ownerUid") == uid) return true
    val assignedReviewers = quest.optJSONArray("assignedReviewerIds").stringList()
    if (assignedReviewers.isNotEmpty()) return uid in assignedReviewers
    val permission = if (quest.optString("proofMode") == "IN_PERSON") "REVIEW_NEARBY_SUBMISSIONS" else "REVIEW_QUESTS"
    return hasGuildPermission(guild, permission)
}

private fun JSONObject.hasGuildPermission(guild: JSONObject, permission: String): Boolean {
    if (guild.optString("ownerUid") == optString("uid")) return true
    val roleTitle = optJSONObject("guildRoles")?.optString(guild.optString("id")).orEmpty()
    val permissions = guild.optJSONObject("rolePermissions")
        ?.optJSONArray(roleTitle)
        .stringList()
    return permission in permissions
}

private fun JSONObject.isAdventurerOf(guild: JSONObject): Boolean {
    val guildId = guild.optString("id")
    return guildId in optJSONArray("joinedGuildIds").stringList() && !isManagerOf(guild)
}

private fun JSONObject.isManagerOf(guild: JSONObject): Boolean {
    val guildId = guild.optString("id")
    return guild.optString("ownerUid") == optString("uid") || guildId in optJSONArray("managedGuildIds").stringList()
}

private fun JSONObject.isPublished(): Boolean =
    optString("status") == "PUBLISHED" || optString("status") == "AVAILABLE"

private fun JSONObject.isAnnounced(now: Long): Boolean =
    optLong("announcedAtMillis", 0L) <= 0L || optLong("announcedAtMillis", 0L) <= now

private fun JSONObject.isOpenForAccept(now: Long): Boolean =
    optLong("acceptStartsAtMillis", 0L) <= 0L || optLong("acceptStartsAtMillis", 0L) <= now

private fun JSONObject.isVisibleToAdventurer(guild: JSONObject, now: Long): Boolean {
    if (!isPublished()) return false
    if (!isAnnounced(now)) return false
    if (!isOpenForAccept(now)) return false
    if (guild.optBoolean("vacationEnabled", false) && optString("type").isStrictCycleQuestType()) return false
    val type = optString("type")
    if (type == "DAILY_QUEST") {
        val active = optJSONArray("activeWeekdays").intList()
        val today = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = now }
            .get(Calendar.DAY_OF_WEEK)
            .toIsoWeekday()
        if (active.isNotEmpty() && today !in active) return false
    }
    if (optBoolean("hasTimeLimit", false)) {
        val start = optLong("startsAtMillis", 0L)
        val end = optLong("endsAtMillis", 0L)
        if (start > 0L && now < start) return false
        if (end > 0L && now > end) return false
    }
    return type != "GUILD_RAID"
}

private fun JSONObject.isWithinSubmissionDeadline(now: Long): Boolean {
    val end = optLong("endsAtMillis", 0L)
    val deadlineDays = optInt("submissionDeadlineDays", 0)
    return end > 0L && deadlineDays > 0 && now <= end + TimeUnit.DAYS.toMillis(deadlineDays.toLong())
}

private fun JSONObject.canBeSeenBy(userId: String): Boolean {
    val assigned = optJSONArray("assignedAdventurerIds").stringList()
    return assigned.isEmpty() || userId in assigned
}

private fun JSONObject.assignedFormationSlotIds(userId: String): List<String> =
    optJSONArray("formationAssignments").asObjects()
        .filter { it.optString("userId") == userId }
        .map { it.optString("slotId") }

private fun JSONObject.isRepeatLimitReached(
    userId: String,
    submissions: List<JSONObject>,
    now: Long
): Boolean {
    if (optString("type") != "REPEATABLE_QUEST") return false
    val limitType = optString("repeatLimitType")
    val limitCount = optInt("repeatLimitCount", 0)
    if (limitType == "NONE" || limitCount <= 0) return false
    val window = repeatLimitWindow(now)
    val used = submissions.count {
        it.optString("questId") == optString("id") &&
            it.optString("userId") == userId &&
            it.optString("status") !in setOf("REJECTED", "NEEDS_REVISION") &&
            (window == null || it.optLong("submittedAtMillis", 0L) in window)
    }
    return used >= limitCount
}

private fun JSONObject.missingPrerequisiteQuestIds(userId: String, submissions: List<JSONObject>): List<String> {
    val required = optJSONArray("prerequisiteQuestIds").stringList()
    if (required.isEmpty()) return emptyList()
    val completed = submissions
        .filter { it.optString("userId") == userId && it.optString("status") == "APPROVED" }
        .map { it.optString("questId") }
        .toSet()
    return required.filterNot { it in completed }
}

private fun JSONObject.currentCycleWindow(now: Long): LongRange {
    if (!optString("type").isStrictCycleQuestType()) return 0L..Long.MAX_VALUE
    val today = Calendar.getInstance(Locale.getDefault()).apply {
        timeInMillis = now
        startOfDay()
    }
    return when (optString("type")) {
        "DAILY_QUEST" -> {
            val start = today.timeInMillis
            start..(start + TimeUnit.DAYS.toMillis(1) - 1)
        }
        "WEEKLY_QUEST" -> {
            val refreshWeekday = optInt("weeklyRefreshWeekday", 1).takeIf { it in 1..7 } ?: 1
            val todayWeekday = today.get(Calendar.DAY_OF_WEEK).toIsoWeekday()
            val daysSinceRefresh = (todayWeekday - refreshWeekday + 7) % 7
            val start = today.timeInMillis - TimeUnit.DAYS.toMillis(daysSinceRefresh.toLong())
            start..(start + TimeUnit.DAYS.toMillis(7) - 1)
        }
        "MONTHLY_QUEST" -> {
            val refreshDay = optInt("monthlyRefreshDay", 1).coerceIn(1, 31)
            val startCalendar = Calendar.getInstance(Locale.getDefault()).apply {
                timeInMillis = today.timeInMillis
                set(Calendar.DAY_OF_MONTH, minOf(refreshDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
                startOfDay()
                if (timeInMillis > today.timeInMillis) {
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, minOf(refreshDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
                    startOfDay()
                }
            }
            val endCalendar = Calendar.getInstance(Locale.getDefault()).apply {
                timeInMillis = startCalendar.timeInMillis
                add(Calendar.MONTH, 1)
                set(Calendar.DAY_OF_MONTH, minOf(refreshDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
                startOfDay()
            }
            startCalendar.timeInMillis..(endCalendar.timeInMillis - 1)
        }
        else -> 0L..Long.MAX_VALUE
    }
}

private fun JSONObject.repeatLimitWindow(now: Long): LongRange? {
    val today = Calendar.getInstance(Locale.getDefault()).apply {
        timeInMillis = now
        startOfDay()
    }
    return when (optString("repeatLimitType")) {
        "TOTAL" -> null
        "DAILY" -> {
            val start = today.timeInMillis
            start..(start + TimeUnit.DAYS.toMillis(1) - 1)
        }
        "WEEKLY" -> {
            val weekday = today.get(Calendar.DAY_OF_WEEK).toIsoWeekday()
            val start = today.timeInMillis - TimeUnit.DAYS.toMillis(((weekday - 1 + 7) % 7).toLong())
            start..(start + TimeUnit.DAYS.toMillis(7) - 1)
        }
        "MONTHLY" -> {
            val startCalendar = Calendar.getInstance(Locale.getDefault()).apply {
                timeInMillis = today.timeInMillis
                set(Calendar.DAY_OF_MONTH, 1)
                startOfDay()
            }
            val endCalendar = Calendar.getInstance(Locale.getDefault()).apply {
                timeInMillis = startCalendar.timeInMillis
                add(Calendar.MONTH, 1)
            }
            startCalendar.timeInMillis..(endCalendar.timeInMillis - 1)
        }
        else -> null
    }
}

private fun JSONObject.duePriority(now: Long): Int =
    when {
        optLong("endsAtMillis", 0L) <= 0L -> 2
        optLong("endsAtMillis") - now <= TimeUnit.DAYS.toMillis(1) -> 0
        optLong("endsAtMillis") - now <= TimeUnit.DAYS.toMillis(3) -> 1
        else -> 2
    }

private fun JSONObject.dueColor(now: Long): Int =
    when (duePriority(now)) {
        0 -> COLOR_URGENT
        1 -> COLOR_SOON
        else -> COLOR_INK
    }

private fun JSONObject.dueLabel(now: Long, language: AppLanguage): String {
    val end = optLong("endsAtMillis", 0L)
    if (end <= 0L) return ""
    val diff = end - now
    return when {
        diff <= TimeUnit.DAYS.toMillis(1) ->
            language.text("[快到期]", "[Due soon]", "[Bald fällig]", "[期限間近]")
        diff <= TimeUnit.DAYS.toMillis(3) ->
            language.text("[即將到期]", "[Upcoming]", "[Demnächst]", "[まもなく期限]")
        else -> ""
    }
}

private fun List<JSONObject>.typeSummary(language: AppLanguage): String =
    groupingBy { it.optString("type").toQuestTypeLabel(language) }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(3)
        .joinToString(" · ") { "${it.key}${it.value}" }
        .ifBlank {
            language.text("目前沒有任務", "No quests", "Keine Aufgaben", "クエストなし")
        }

private fun String.isStrictCycleQuestType(): Boolean =
    this == "DAILY_QUEST" || this == "WEEKLY_QUEST" || this == "MONTHLY_QUEST"

private fun String.toQuestTypeLabel(language: AppLanguage): String =
    when (this) {
        "DAILY_QUEST" -> language.text("每日", "Daily", "Täglich", "デイリー")
        "WEEKLY_QUEST" -> language.text("每週", "Weekly", "Wöchentlich", "週間")
        "MONTHLY_QUEST" -> language.text("月度", "Monthly", "Monatlich", "月間")
        "REPEATABLE_QUEST" -> language.text("常駐", "Repeatable", "Wiederholbar", "常設")
        "LIMITED_EVENT_QUEST" -> language.text("限時", "Limited", "Befristet", "期間限定")
        "MAIN_QUEST" -> language.text("命運", "Main", "Haupt", "メイン")
        "PROMOTION_QUEST" -> language.text("晉階", "Promotion", "Aufstieg", "昇格")
        "FORMATION_QUEST" -> language.text("戰團", "Formation", "Formation", "編成")
        else -> language.text("任務", "Quest", "Aufgabe", "クエスト")
    }

private fun String.rankOrdinal(): Int =
    when (this) {
        "E" -> 1
        "D" -> 2
        "C" -> 3
        "B" -> 4
        "A" -> 5
        "S" -> 6
        else -> 0
    }

private fun JSONObject.canStartPromotionTrial(): Boolean {
    val nextMinExp = when (optString("rank")) {
        "F" -> 300L
        "E" -> 900L
        "D" -> 2_000L
        "C" -> 4_500L
        "B" -> 8_000L
        "A" -> 15_000L
        else -> Long.MAX_VALUE
    }
    return optLong("exp", 0L) >= nextMinExp
}

private fun Int.toIsoWeekday(): Int =
    if (this == Calendar.SUNDAY) 7 else this - 1

private fun Calendar.startOfDay(): Calendar = apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private fun JSONArray?.asObjects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { index -> optJSONObject(index) }

private fun JSONArray?.stringList(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }

private fun JSONArray?.intList(): List<Int> =
    if (this == null) emptyList() else (0 until length()).map { index -> optInt(index) }
