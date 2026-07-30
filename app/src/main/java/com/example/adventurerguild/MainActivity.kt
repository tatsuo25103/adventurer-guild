package com.example.adventurerguild

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.adventurerguild.ui.AdventurerGuildApp
import com.example.adventurerguild.viewmodel.OfflineGuildViewModel
import com.example.adventurerguild.widget.QuestWidgetUpdater
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {
    private var openQuestIdState: MutableState<String?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyWidgetTargetToOfflineState(intent)
        QuestWidgetUpdater.update(applicationContext)
        setContent {
            val openQuestId = remember { mutableStateOf(intent.getStringExtra(QuestWidgetUpdater.EXTRA_OPEN_QUEST_ID)) }
            openQuestIdState = openQuestId
            val viewModel = remember { OfflineGuildViewModel(applicationContext) }
            AdventurerGuildApp(
                viewModel,
                requestedQuestId = openQuestId.value,
                onQuestRequestConsumed = { openQuestId.value = null }
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyWidgetTargetToOfflineState(intent)) {
            recreate()
            return
        }
        openQuestIdState?.value = intent.getStringExtra(QuestWidgetUpdater.EXTRA_OPEN_QUEST_ID)
    }

    private fun applyWidgetTargetToOfflineState(intent: android.content.Intent?): Boolean {
        val guildId = intent?.getStringExtra(QuestWidgetUpdater.EXTRA_WIDGET_GUILD_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return false
        val widgetMode = intent.getStringExtra(QuestWidgetUpdater.EXTRA_WIDGET_MODE)
        val targetRole = when (widgetMode) {
            "MANAGER" -> "GUILD_ADMIN"
            "ADVENTURER" -> "ADVENTURER"
            else -> return false
        }
        val file = File(filesDir, "offline_guild_state.json")
        if (!file.exists()) return false
        return runCatching {
            val root = JSONObject(file.readText())
            val currentUid = root.optString("currentUid").takeIf { it.isNotBlank() } ?: return@runCatching false
            val guilds = root.optJSONArray("guilds")
            val guild = guilds.asObjects().firstOrNull { it.optString("id") == guildId } ?: return@runCatching false
            val users = root.optJSONArray("users") ?: return@runCatching false
            var changed = false
            for (index in 0 until users.length()) {
                val user = users.optJSONObject(index) ?: continue
                if (user.optString("uid") != currentUid) continue
                val canUseTarget = when (targetRole) {
                    "GUILD_ADMIN" -> guild.optString("ownerUid") == currentUid || guildId in user.optJSONArray("managedGuildIds").stringList()
                    else -> guildId in user.optJSONArray("joinedGuildIds").stringList() &&
                        guild.optString("ownerUid") != currentUid &&
                        guildId !in user.optJSONArray("managedGuildIds").stringList()
                }
                if (!canUseTarget) return@runCatching false
                if (user.optString("role") != targetRole) {
                    user.put("role", targetRole)
                    changed = true
                }
                if (user.optString("guildId") != guildId) {
                    user.put("guildId", guildId)
                    changed = true
                }
                if (root.optString("currentGuildId") != guildId) {
                    root.put("currentGuildId", guildId)
                    changed = true
                }
                users.put(index, user)
                break
            }
            if (changed) file.writeText(root.toString(2))
            changed
        }.getOrDefault(false)
    }
}

@androidx.compose.runtime.Composable
private fun MissingFirebaseConfigScreen() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF3E7CE))
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E9))) {
                Column(Modifier.padding(18.dp)) {
                    Text("冒險者公會", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("App 已成功啟動，但尚未設定 Firebase。")
                    Spacer(Modifier.height(10.dp))
                    Text("請將 google-services.json 放到 app/google-services.json，並在 gradle.properties 填入 GOOGLE_WEB_CLIENT_ID 後重新編譯。")
                }
            }
        }
    }
}

private fun JSONArray?.asObjects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.stringList(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { value -> value.isNotBlank() } }
