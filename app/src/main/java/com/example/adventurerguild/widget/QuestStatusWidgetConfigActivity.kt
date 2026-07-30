package com.example.adventurerguild.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.example.adventurerguild.R
import com.example.adventurerguild.ui.AppLanguageStore
import com.example.adventurerguild.ui.text

class QuestStatusWidgetConfigActivity : Activity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val source = WidgetDataSource.load(this)
        val choices = source.choices()
        if (choices.isEmpty()) {
            showEmptyState()
        } else {
            showChoices(choices)
        }
    }

    private fun showChoices(choices: List<WidgetTargetChoice>) {
        val language = AppLanguageStore.load(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(0xFFF8F1DF.toInt())
        }
        root.addView(TextView(this).apply {
            text = language.text(
                "選擇小工具顯示內容",
                "Choose widget content",
                "Widget-Inhalt auswählen",
                "ウィジェットの表示内容を選択"
            )
            textSize = 20f
            setTextColor(0xFF241A12.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = language.text(
                "每個桌面小工具可以固定顯示不同公會。",
                "Each widget can show a different guild.",
                "Jedes Widget kann eine andere Gilde anzeigen.",
                "ウィジェットごとに別のギルドを表示できます。"
            )
            textSize = 14f
            setTextColor(0xFF3F6B4C.toInt())
            setPadding(0, dp(6), 0, dp(12))
        })

        val group = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        val choiceSeparator = language.text(" · ", " · ", " · ", "・")
        val targetByViewId = mutableMapOf<Int, WidgetTarget>()
        choices.forEach { choice ->
            val viewId = View.generateViewId()
            targetByViewId[viewId] = choice.target
            group.addView(RadioButton(this).apply {
                id = viewId
                text = listOf(choice.label, choice.subtitle).joinToString(choiceSeparator)
                textSize = 16f
                setTextColor(0xFF241A12.toInt())
                setPadding(0, dp(8), 0, dp(8))
            })
        }
        targetByViewId.keys.firstOrNull()?.let(group::check)
        root.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(Button(this).apply {
            text = language.text("套用", "Apply", "Anwenden", "適用")
            setOnClickListener {
                val target = targetByViewId[group.checkedRadioButtonId] ?: return@setOnClickListener
                WidgetConfigStore.save(this@QuestStatusWidgetConfigActivity, appWidgetId, target)
                QuestWidgetUpdater.updateAppWidget(this@QuestStatusWidgetConfigActivity, appWidgetId)
                setResult(
                    RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                )
                finish()
            }
        })
        setContentView(root)
    }

    private fun showEmptyState() {
        val language = AppLanguageStore.load(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xFFF8F1DF.toInt())
        }
        root.addView(TextView(this).apply {
            text = language.text(
                "目前沒有可顯示的公會",
                "No guilds available",
                "Keine Gilden verfügbar",
                "表示できるギルドがありません"
            )
            textSize = 18f
            setTextColor(0xFF241A12.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = language.text(
                "請先進入 App 登入，並加入或管理至少一個公會。",
                "Open the app and join or manage at least one guild first.",
                "Öffne die App und tritt zuerst mindestens einer Gilde bei oder verwalte sie.",
                "先にアプリを開き、少なくとも1つのギルドに参加または管理してください。"
            )
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF3F6B4C.toInt())
            setPadding(0, dp(8), 0, dp(16))
        })
        root.addView(Button(this).apply {
            text = language.text("關閉", "Close", "Schließen", "閉じる")
            setOnClickListener { finish() }
        })
        setContentView(root)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
