package com.example.adventurerguild.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.adventurerguild.R

class QuestStatusWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return QuestStatusRemoteViewsFactory(applicationContext, widgetId)
    }
}

private class QuestStatusRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {
    private var items: List<WidgetQuestItem> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val source = WidgetDataSource.load(context)
        val target = WidgetConfigStore.load(context, appWidgetId)
            ?.takeIf { source.canUseTarget(it) }
            ?: source.defaultTarget()
        items = source.buildState(target).questItems
            .filter { it.questId.isNotBlank() }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val item = items.getOrNull(position) ?: return null
        return RemoteViews(context.packageName, R.layout.widget_quest_status_item).apply {
            setTextViewText(R.id.widget_quest_item_text, item.line)
            setTextColor(R.id.widget_quest_item_text, item.color)
            setOnClickFillInIntent(
                R.id.widget_quest_item_text,
                Intent().apply {
                    putExtra(QuestWidgetUpdater.EXTRA_OPEN_QUEST_ID, item.questId)
                }
            )
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.questId?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
