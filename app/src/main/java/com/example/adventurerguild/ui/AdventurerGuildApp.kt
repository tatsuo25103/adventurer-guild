package com.example.adventurerguild.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.example.adventurerguild.BuildConfig
import com.example.adventurerguild.model.*
import com.example.adventurerguild.nearby.NearbyCounterCoordinator
import com.example.adventurerguild.nearby.NearbyCounterPhase
import com.example.adventurerguild.nearby.NearbyCounterState
import com.example.adventurerguild.viewmodel.AuthDestination
import com.example.adventurerguild.viewmodel.GuildController
import com.example.adventurerguild.viewmodel.GuildPortalDestination
import com.example.adventurerguild.viewmodel.GuildUiState
import com.example.adventurerguild.widget.QuestWidgetUpdater
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

private val GuildBrown = Color(0xFF2A2118)
private val Parchment = Color(0xFFF8F1DF)
private val Brass = Color(0xFFD9A441)
private val Ink = Color(0xFF241A12)
private val Moss = Color(0xFF3F6B4C)
private val Wine = Color(0xFF803A3A)

@Composable
private fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    val language = LocalAppLanguage.current
    MaterialText(
        text = language.systemText(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

@Composable
fun AdventurerGuildApp(
    controller: GuildController,
    requestedQuestId: String? = null,
    onQuestRequestConsumed: () -> Unit = {}
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppLanguageStore.load(context)) }
    CompositionLocalProvider(LocalAppLanguage provides language) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = GuildBrown,
                secondary = Moss,
                tertiary = Brass,
                surface = Parchment,
                background = Color(0xFFF3E7CE),
                onPrimary = Color.White,
                onSurface = Ink
            ),
            shapes = Shapes(
                small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(8.dp),
                large = RoundedCornerShape(8.dp)
            )
        ) {
            Box(Modifier.fillMaxSize()) {
                if (state.user == null) {
                    AuthScreen(
                        state = state,
                        controller = controller,
                        language = language,
                        onLanguageChange = {
                            language = it
                            AppLanguageStore.save(context, it)
                            QuestWidgetUpdater.update(context)
                        }
                    )
                } else if (state.activeGuild == null) {
                    GuildPortalScreen(state, controller)
                } else {
                    GuildHome(state, controller, requestedQuestId, onQuestRequestConsumed)
                }
            }
        }
    }
}

@Composable
private fun AppVersionBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = 0.38f),
        contentColor = Color.White
    ) {
        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun LanguageMenu(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        FilledTonalButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Language, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(language.compactLabel)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AppLanguage.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.nativeName) },
                    leadingIcon = {
                        if (option == language) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onLanguageChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthScreen(
    state: GuildUiState,
    controller: GuildController,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit
) {
    val testAccountUnlockTracker = remember { HiddenTestAccountUnlockTracker() }
    var showOfflineTestAccounts by remember { mutableStateOf(false) }
    var showAccountTransfer by remember { mutableStateOf(false) }
    var transferUserId by remember { mutableStateOf("") }
    var transferCode by remember { mutableStateOf("") }
    var deviceAccountReady by remember(state.authDestination) {
        mutableStateOf(state.authDestination == AuthDestination.ROLE_SELECTION)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1F1711), Color(0xFF6D4A2E)))),
        contentAlignment = Alignment.Center
    ) {
        LanguageMenu(
            language = language,
            onLanguageChange = onLanguageChange,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                language.text("冒險者公會", "Adventurer Guild", "Abenteurergilde", "冒険者ギルド"),
                color = Brass,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.pointerInput(testAccountUnlockTracker) {
                    detectTapGestures {
                        if (testAccountUnlockTracker.registerTap(System.currentTimeMillis())) {
                            showOfflineTestAccounts = true
                        }
                    }
                }
            )
            Text(
                language.text(
                    "任務、GP 與獎勵管理",
                    "Quests, GP and rewards",
                    "Aufgaben, GP und Belohnungen",
                    "クエスト・GP・報酬管理"
                ),
                color = Parchment
            )
            if (!deviceAccountReady) {
                Button(
                    onClick = { deviceAccountReady = true },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        language.text(
                            "使用此裝置的 UUID 帳號",
                            "Use this device's UUID account",
                            "UUID-Konto dieses Geräts verwenden",
                            "この端末のUUIDアカウントを使用"
                        )
                    )
                }
            } else {
                Text(
                    language.text(
                        "裝置帳號已準備",
                        "Device account ready",
                        "Gerätekonto ist bereit",
                        "端末アカウントの準備完了"
                    ),
                    color = Parchment,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    language.text(
                        "請選擇進入方式",
                        "Choose how to enter",
                        "Zugang auswählen",
                        "入場方法を選択"
                    ),
                    color = Parchment
                )
                Button(
                    onClick = { controller.loginWithDevice(asAdmin = false) },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Explore, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        language.text(
                            "以冒險者身分進入",
                            "Enter as Adventurer",
                            "Als Abenteurer eintreten",
                            "冒険者として入る"
                        )
                    )
                }
                Button(
                    onClick = { controller.loginWithDevice(asAdmin = true) },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        language.text(
                            "以公會管理方身分進入",
                            "Enter as Guild Staff",
                            "Als Gildenverwaltung eintreten",
                            "ギルド管理者として入る"
                        )
                    )
                }
                Text(
                    language.text(
                        "此處只選擇本次入口；同一帳號可在不同公會擔任不同身分，但同一公會不可兼任。",
                        "This only selects the current entrance. One account may have different roles in different guilds, but never both roles in the same guild.",
                        "Dies wählt nur den aktuellen Zugang. Ein Konto darf in verschiedenen Gilden unterschiedliche Rollen haben, aber nie beide Rollen in derselben Gilde.",
                        "ここでは今回の入口だけを選びます。同じアカウントでギルドごとに別の役割を持てますが、同じギルドでの兼任はできません。"
                    ),
                    color = Parchment,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = { deviceAccountReady = false },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Parchment),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Parchment)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        language.text(
                            "返回帳號入口",
                            "Back to account entry",
                            "Zurück zum Kontozugang",
                            "アカウント入口に戻る"
                        )
                    )
                }
            }
            TextButton(
                onClick = { showAccountTransfer = !showAccountTransfer },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Parchment)
                Spacer(Modifier.width(8.dp))
                Text(
                    language.text(
                        "從舊手機繼承帳號",
                        "Transfer an account from an old phone",
                        "Konto vom alten Smartphone übertragen",
                        "以前のスマホからアカウントを引き継ぐ"
                    ),
                    color = Parchment
                )
            }
            if (showAccountTransfer) {
                OutlinedTextField(
                    value = transferUserId,
                    onValueChange = { transferUserId = it },
                    label = {
                        Text(language.text("帳號 UUID", "Account UUID", "Konto-UUID", "アカウントUUID"))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Parchment,
                        unfocusedTextColor = Parchment,
                        focusedBorderColor = Brass,
                        unfocusedBorderColor = Parchment.copy(alpha = 0.7f),
                        focusedLabelColor = Brass,
                        unfocusedLabelColor = Parchment
                    )
                )
                OutlinedTextField(
                    value = transferCode,
                    onValueChange = { transferCode = it },
                    label = {
                        Text(
                            language.text(
                                "一次性移機碼",
                                "One-time transfer code",
                                "Einmaliger Übertragungscode",
                                "一回限りの引き継ぎコード"
                            )
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Parchment,
                        unfocusedTextColor = Parchment,
                        focusedBorderColor = Brass,
                        unfocusedBorderColor = Parchment.copy(alpha = 0.7f),
                        focusedLabelColor = Brass,
                        unfocusedLabelColor = Parchment
                    )
                )
                Button(
                    onClick = { controller.inheritCloudAccount(transferUserId, transferCode) },
                    enabled = transferUserId.isNotBlank() && transferCode.isNotBlank() && !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        language.text(
                            "繼承此 UUID 帳號",
                            "Transfer this UUID account",
                            "Dieses UUID-Konto übertragen",
                            "このUUIDアカウントを引き継ぐ"
                        )
                    )
                }
            }
            if (!deviceAccountReady) {
                AppVersionBadge(
                    modifier = Modifier.align(Alignment.End)
                )
            }
            if (BuildConfig.ENABLE_TEST_ACCOUNTS && showOfflineTestAccounts) {
                HorizontalDivider(color = Parchment.copy(alpha = 0.35f))
                Text(
                    "離線測試帳號",
                    color = Parchment,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { controller.loginOfflineTest(asAdmin = false) },
                        enabled = !state.loading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Parchment)
                    ) {
                        Icon(Icons.Default.Explore, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("冒險者")
                    }
                    OutlinedButton(
                        onClick = { controller.loginOfflineTest(asAdmin = true) },
                        enabled = !state.loading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Parchment)
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("管理方")
                    }
                }
                Text(
                    "資料僅保存在本機，可從登出按鈕切換兩個測試身分。",
                    color = Parchment.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            ErrorText(state.error)
        }
    }
}

internal class HiddenTestAccountUnlockTracker(
    private val requiredTapCount: Int = 7,
    private val windowMillis: Long = 5_000L
) {
    private var firstTapAtMillis = 0L
    private var tapCount = 0

    fun registerTap(nowMillis: Long): Boolean {
        if (tapCount == 0 || nowMillis - firstTapAtMillis > windowMillis) {
            firstTapAtMillis = nowMillis
            tapCount = 1
        } else {
            tapCount += 1
        }

        return if (tapCount >= requiredTapCount) {
            tapCount = 0
            firstTapAtMillis = 0L
            true
        } else {
            false
        }
    }
}

private fun Throwable.googleLoginErrorMessage(): String {
    val detail = message.orEmpty()
    return when {
        detail.contains("Account reauth failed", ignoreCase = true) ||
            detail.contains("[16]") ->
            "Google 登入設定與目前 APK 簽章不一致。請由開發者在 Firebase 補登此 APK 的 SHA 憑證指紋。"
        detail.contains("No credentials available", ignoreCase = true) ->
            "裝置上沒有可用的 Google 帳號，請先在 Android 設定中加入帳號。"
        else -> detail.ifBlank { "Google 登入失敗，請稍後再試。" }
    }
}

@Composable
private fun GuildPortalScreen(state: GuildUiState, controller: GuildController) {
    val language = LocalAppLanguage.current
    var section by remember(state.portalDestination, state.user?.role) {
        mutableStateOf(
            if (state.portalDestination == GuildPortalDestination.GUILD_SELECTION) {
                if (state.user?.role == UserRole.GUILD_ADMIN) {
                    GuildPortalSection.MANAGEMENT
                } else {
                    GuildPortalSection.TAVERN
                }
            } else {
                null
            }
        )
    }
    var showNameEditor by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3E7CE))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        language.text("公會入口", "Guild Entrance", "Gildeneingang", "ギルド入口"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            language.text(
                                "歡迎，${state.user?.displayName.orEmpty()}",
                                "Welcome, ${state.user?.displayName.orEmpty()}",
                                "Willkommen, ${state.user?.displayName.orEmpty()}",
                                "ようこそ、${state.user?.displayName.orEmpty()}"
                            )
                        )
                        IconButton(onClick = { showNameEditor = true }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = language.text(
                                    "修改名稱",
                                    "Edit name",
                                    "Name bearbeiten",
                                    "名前を変更"
                                )
                            )
                        }
                    }
                    Text(
                        language.text(
                            "冒險者公會 ${state.joinedGuilds.size} 個 · 管理公會 ${state.managedGuilds.size} 個",
                            "Adventurer guilds ${state.joinedGuilds.size} · Managed guilds ${state.managedGuilds.size}",
                            "Abenteurergilden ${state.joinedGuilds.size} · Verwaltete Gilden ${state.managedGuilds.size}",
                            "冒険者ギルド ${state.joinedGuilds.size} · 管理ギルド ${state.managedGuilds.size}"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Moss
                    )
                }
                IconButton(onClick = controller::logout) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = language.text("登出", "Sign out", "Abmelden", "ログアウト")
                    )
                }
            }

            if (section == null) {
                if (state.user?.role == UserRole.GUILD_ADMIN) {
                    GuildPortalCard(
                        title = language.text(
                            "公會管理",
                            "Guild Management",
                            "Gildenverwaltung",
                            "ギルド管理"
                        ),
                        body = language.text(
                            "創建公會、選擇要管理的公會。",
                            "Create a guild or choose one to manage.",
                            "Eine Gilde erstellen oder eine zu verwaltende Gilde auswählen.",
                            "ギルドを作成するか、管理するギルドを選択します。"
                        ),
                        icon = Icons.Default.AdminPanelSettings,
                        onClick = { section = GuildPortalSection.MANAGEMENT }
                    )
                } else {
                    GuildPortalCard(
                        title = language.text(
                            "進入公會酒吧",
                            "Enter the Guild Tavern",
                            "Gildentaverne betreten",
                            "ギルド酒場に入る"
                        ),
                        body = language.text(
                            "加入公會，或選擇自己已加入的公會。",
                            "Join a guild or choose one you already belong to.",
                            "Einer Gilde beitreten oder eine bestehende Mitgliedschaft auswählen.",
                            "ギルドに参加するか、参加済みのギルドを選択します。"
                        ),
                        icon = Icons.Default.LocalBar,
                        onClick = { section = GuildPortalSection.TAVERN }
                    )
                }
                AccountTransferPanel(state, controller)
            } else {
                OutlinedButton(
                    onClick = { section = null },
                    border = BorderStroke(1.dp, GuildBrown),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GuildBrown)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        language.text(
                            "返回公會入口",
                            "Back to Guild Entrance",
                            "Zurück zum Gildeneingang",
                            "ギルド入口に戻る"
                        )
                    )
                }
                when (section) {
                    GuildPortalSection.MANAGEMENT -> GuildManagementScreen(state, controller)
                    GuildPortalSection.TAVERN -> GuildTavernScreen(state, controller)
                    null -> Unit
                }
            }
            ErrorText(state.error)
        }
    }
    if (showNameEditor) {
        DisplayNameDialog(
            currentName = state.user?.displayName.orEmpty(),
            onDismiss = { showNameEditor = false },
            onConfirm = {
                controller.updateDisplayName(it)
                showNameEditor = false
            }
        )
    }
}

@Composable
private fun DisplayNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改顯示名稱") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = it },
                label = { Text("名稱") },
                supportingText = { Text("此名稱會顯示在你加入的所有公會。") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().length in 2..40
            ) {
                Text("儲存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private enum class GuildPortalSection { MANAGEMENT, TAVERN }

@Composable
private fun GuildPortalCard(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E9)),
        border = BorderStroke(1.dp, Color(0xFFE1C98E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = Brass, modifier = Modifier.size(36.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(body)
            }
        }
    }
}

@Composable
private fun GuildManagementScreen(state: GuildUiState, controller: GuildController) {
    val language = LocalAppLanguage.current
    var guildName by remember { mutableStateOf("") }
    var staffInviteCode by remember { mutableStateOf("") }
    val staffQrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf { it.isNotBlank() }?.let { staffInviteCode = it }
    }
    val isAdmin = state.user?.role == UserRole.GUILD_ADMIN
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!isAdmin) {
            item {
                GuildCard {
                    Text(
                        language.text(
                            "管理方專用入口",
                            "Guild Staff Only",
                            "Nur für Gildenverwaltung",
                            "ギルド管理者専用"
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        language.text(
                            "冒險者不能建立或管理公會。請回到公會酒吧加入公會並接取任務。",
                            "Adventurers cannot create or manage guilds. Return to the tavern to join a guild and accept quests.",
                            "Abenteurer können keine Gilden erstellen oder verwalten. Kehre zur Taverne zurück, um einer Gilde beizutreten.",
                            "冒険者はギルドを作成・管理できません。酒場に戻ってギルドに参加してください。"
                        )
                    )
                }
            }
            return@LazyColumn
        }
        item {
            GuildCard {
                Text(
                    language.text("創建公會", "Create Guild", "Gilde erstellen", "ギルドを作成"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    guildName,
                    { guildName = it },
                    label = {
                        Text(language.text("公會名稱", "Guild name", "Gildenname", "ギルド名"))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    controller.createGuild(guildName)
                    guildName = ""
                }, enabled = guildName.isNotBlank()) {
                    Icon(Icons.Default.AddBusiness, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(language.text("創建", "Create", "Erstellen", "作成"))
                }
                if (BuildConfig.ENABLE_TEST_ACCOUNTS) {
                    FilledTonalButton(onClick = { controller.createGuild("測試公會") }) {
                        Icon(Icons.Default.Shield, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("建立測試公會")
                    }
                }
            }
        }
        item {
            Text(
                language.text(
                    "選擇管理公會",
                    "Choose a Guild to Manage",
                    "Zu verwaltende Gilde auswählen",
                    "管理するギルドを選択"
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            GuildCard {
                Text(
                    language.text(
                        "員工通道加入公會",
                        "Join through Staff Entrance",
                        "Über den Mitarbeiterzugang beitreten",
                        "職員入口から参加"
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    language.text(
                        "管理方帳號可用公會邀請碼加入管理名單；預設為見習成員，需由會長指派權限。",
                        "Guild staff can join with an invite code. New staff start as trainees until the guild master assigns permissions.",
                        "Verwaltungspersonal kann mit einem Einladungscode beitreten und beginnt ohne zugewiesene Rechte.",
                        "管理者は招待コードで参加できます。権限はギルドマスターが割り当てます。"
                    )
                )
                OutlinedTextField(
                    staffInviteCode,
                    { staffInviteCode = it },
                    label = {
                        Text(
                            language.text(
                                "公會邀請碼",
                                "Guild invite code",
                                "Gilden-Einladungscode",
                                "ギルド招待コード"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        controller.joinGuildAsManager(staffInviteCode)
                        staffInviteCode = ""
                    },
                    enabled = staffInviteCode.isNotBlank()
                ) {
                    Icon(Icons.Default.Badge, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        language.text(
                            "以管理方加入",
                            "Join as Guild Staff",
                            "Als Gildenverwaltung beitreten",
                            "管理者として参加"
                        )
                    )
                }
                OutlinedButton(
                    onClick = {
                        staffQrScanner.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("掃描公會邀請 QR Code")
                                .setBeepEnabled(false)
                                .setOrientationLocked(false)
                        )
                    }
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        language.text(
                            "掃描邀請 QR",
                            "Scan invite QR",
                            "Einladungs-QR scannen",
                            "招待QRを読み取る"
                        )
                    )
                }
            }
        }
        if (state.managedGuilds.isEmpty()) {
            item {
                Text(
                    language.text(
                        "目前沒有管理中的公會。",
                        "You do not manage any guilds yet.",
                        "Du verwaltest noch keine Gilde.",
                        "管理中のギルドはありません。"
                    )
                )
            }
        }
        items(state.managedGuilds, key = { it.id }) { guild ->
            ManagedGuildInvitationCard(guild, state, controller)
        }
    }
}

@Composable
private fun ManagedGuildInvitationCard(
    guild: Guild,
    state: GuildUiState,
    controller: GuildController,
    showEnterButton: Boolean = true
) {
    val language = LocalAppLanguage.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var confirmRotate by remember(guild.id) { mutableStateOf(false) }
    val generatedForGuild = state.generatedInviteGuildId == guild.id
    val displayedCode = if (generatedForGuild) state.generatedInviteCode ?: guild.inviteCode else guild.inviteCode
    val displayedOneTime = generatedForGuild && state.generatedInviteOneTime
    val inviteLink = guild.inviteLink(displayedCode)
    GuildCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (showEnterButton) {
                            guild.name
                        } else {
                            language.text("公會邀請", "Guild Invitation", "Gildeneinladung", "ギルド招待")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (displayedOneTime) {
                            language.text(
                                "一次性邀請碼：$displayedCode",
                                "One-time code: $displayedCode",
                                "Einmalcode: $displayedCode",
                                "一回限りコード：$displayedCode"
                            )
                        } else {
                            language.text(
                                "常用邀請碼：$displayedCode",
                                "Reusable code: $displayedCode",
                                "Dauercode: $displayedCode",
                                "通常招待コード：$displayedCode"
                            )
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                if (showEnterButton) {
                    FilledTonalButton(onClick = { controller.selectGuild(guild.id) }) {
                        Text(language.text("進入", "Enter", "Öffnen", "入る"))
                    }
                }
            }
            InviteQrCode(inviteLink)
            Text(inviteLink, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Moss)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(inviteLink)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(language.text("複製", "Copy", "Kopieren", "コピー"))
                }
                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "${guild.name} 加入連結：$inviteLink")
                        context.startActivity(Intent.createChooser(sendIntent, "分享公會加入連結"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(language.text("分享", "Share", "Teilen", "共有"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { confirmRotate = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("替換常用碼")
                }
                Button(
                    onClick = { controller.createOneTimeGuildInvite(guild.id) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("一次性 QR")
                }
            }
            if (displayedOneTime) {
                Text(
                    "此 QR 在第一個加入申請送出後失效，最長有效 24 小時。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Wine
                )
            }
        }
    }
    if (confirmRotate) {
        AlertDialog(
            onDismissRequest = { confirmRotate = false },
            title = { Text("替換常用邀請碼？") },
            text = { Text("舊的邀請碼與 QR 會立即失效，尚未送出的加入連結也無法再使用。") },
            confirmButton = {
                TextButton(onClick = {
                    controller.rotateGuildInvite(guild.id)
                    confirmRotate = false
                }) { Text("確認替換") }
            },
            dismissButton = { TextButton(onClick = { confirmRotate = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun InviteQrCode(content: String) {
    val bitmap = remember(content) {
        runCatching {
            BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 720, 720).asImageBitmap()
        }.getOrNull()
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "公會邀請 QR Code",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
        )
    }
}

private fun Guild.inviteLink(code: String = inviteCode): String =
    buildString {
        append("adventurerguild://join?code=$code&guild=$id")
        (driveInviteFileId ?: driveFolderId)
            ?.takeIf { it.isNotBlank() }
            ?.let { append("&driveFileId=$it") }
    }

@Composable
private fun GuildTavernScreen(state: GuildUiState, controller: GuildController) {
    val language = LocalAppLanguage.current
    var inviteCode by remember { mutableStateOf("") }
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf { it.isNotBlank() }?.let { inviteCode = it }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GuildCard {
                Text(
                    language.text("加入公會", "Join a Guild", "Gilde beitreten", "ギルドに参加"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    inviteCode,
                    { inviteCode = it },
                    label = {
                        Text(
                            language.text(
                                "公會邀請碼或加入連結",
                                "Guild invite code or link",
                                "Einladungscode oder Link",
                                "招待コードまたは参加リンク"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    controller.joinGuild(inviteCode)
                    inviteCode = ""
                }, enabled = inviteCode.isNotBlank()) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(language.text("加入", "Join", "Beitreten", "参加"))
                }
                OutlinedButton(
                    onClick = {
                        qrScanner.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("掃描公會邀請 QR Code")
                                .setBeepEnabled(false)
                                .setOrientationLocked(false)
                        )
                    }
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        language.text(
                            "掃描邀請 QR",
                            "Scan invite QR",
                            "Einladungs-QR scannen",
                            "招待QRを読み取る"
                        )
                    )
                }
            }
        }
        item {
            Text(
                language.text(
                    "選擇自己已加入的公會（${state.joinedGuilds.size}）",
                    "Choose one of your guilds (${state.joinedGuilds.size})",
                    "Eine deiner Gilden auswählen (${state.joinedGuilds.size})",
                    "参加済みギルドを選択（${state.joinedGuilds.size}）"
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        if (state.joinedGuilds.isEmpty()) {
            item {
                Text(
                    language.text(
                        "你尚未加入任何公會。",
                        "You have not joined a guild yet.",
                        "Du bist noch keiner Gilde beigetreten.",
                        "まだギルドに参加していません。"
                    )
                )
            }
        }
        items(state.joinedGuilds, key = { it.id }) { guild ->
            GuildSelectionCard(
                guild,
                if (guild.id in state.user.orEmptyManagedGuildIds()) {
                    language.text(
                        "你是此公會管理者",
                        "You manage this guild",
                        "Du verwaltest diese Gilde",
                        "このギルドの管理者です"
                    )
                } else {
                    language.text("已加入", "Joined", "Beigetreten", "参加済み")
                }
            ) {
                controller.selectGuild(guild.id)
            }
        }
    }
}

private fun UserProfile?.orEmptyManagedGuildIds(): List<String> = this?.managedGuildIds.orEmpty()

@Composable
private fun GuildSelectionCard(guild: Guild, subtitle: String, onClick: () -> Unit) {
    val language = LocalAppLanguage.current
    GuildCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(guild.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle)
            }
            FilledTonalButton(onClick = onClick) {
                Text(language.text("進入", "Enter", "Öffnen", "入る"))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GuildHome(
    state: GuildUiState,
    viewModel: GuildController,
    requestedQuestId: String?,
    onQuestRequestConsumed: () -> Unit
) {
    val language = LocalAppLanguage.current
    var tab by remember { mutableIntStateOf(0) }
    val user = state.user ?: return
    val guild = state.activeGuild
    val canReview = guild != null && (
        user.hasGuildPermission(guild, GuildPermission.REVIEW_QUESTS) ||
            user.hasGuildPermission(guild, GuildPermission.REVIEW_NEARBY_SUBMISSIONS) ||
            state.pendingSubmissions.isNotEmpty() ||
            user.hasGuildPermission(guild, GuildPermission.REVIEW_REDEMPTIONS) ||
            user.hasGuildPermission(guild, GuildPermission.MANAGE_QUEST_PENALTIES)
        )
    val tabs = buildList {
        add("首頁")
        add("成員")
        add("任務")
        if (canReview) add("審核")
        add("商店")
    }

    LaunchedEffect(requestedQuestId) {
        if (requestedQuestId != null) {
            tab = tabs.indexOf("任務").coerceAtLeast(0)
        }
    }

    LaunchedEffect(guild?.id) {
        if (guild == null) return@LaunchedEffect
        while (true) {
            delay(10_000)
            viewModel.refreshCounterSessions()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.activeGuild?.name ?: "Adventurer Guild") },
                actions = {
                    GuildDestinationButton(
                        icon = Icons.Default.PhoneAndroid,
                        label = language.text("帳號", "Account", "Konto", "アカウント"),
                        contentDescription = language.text(
                            "前往帳號入口",
                            "Go to account entry",
                            "Zum Kontozugang",
                            "アカウント入口へ"
                        ),
                        onClick = viewModel::returnToAccountEntry
                    )
                    GuildDestinationButton(
                        icon = Icons.Default.SwitchAccount,
                        label = language.text("身分", "Role", "Rolle", "役割"),
                        contentDescription = language.text(
                            "前往身分選擇",
                            "Go to role selection",
                            "Zur Rollenauswahl",
                            "役割選択へ"
                        ),
                        onClick = viewModel::returnToRoleSelection
                    )
                    GuildDestinationButton(
                        icon = Icons.Default.Business,
                        label = language.text("公會", "Guild", "Gilde", "ギルド"),
                        contentDescription = language.text(
                            "前往公會入口",
                            "Go to guild entrance",
                            "Zum Gildeneingang",
                            "ギルド入口へ"
                        ),
                        onClick = viewModel::backToGuildPortal
                    )
                    GuildDestinationButton(
                        icon = Icons.Default.Home,
                        label = language.text("首頁", "Home", "Start", "ホーム"),
                        contentDescription = language.text(
                            "回到目前公會首頁",
                            "Return to current guild home",
                            "Zur Startseite der aktuellen Gilde",
                            "現在のギルドホームに戻る"
                        ),
                        onClick = { tab = 0 }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GuildBrown, titleContentColor = Color.White, actionIconContentColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFFFFF7E8)) {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            Icon(
                                tabIcon(label),
                                contentDescription = guildNavigationLabel(label, language)
                            )
                        },
                        label = { Text(guildNavigationLabel(label, language)) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Color(0xFFF3E7CE))) {
            when (tabs[tab]) {
                "首頁" -> DashboardScreen(
                    state = state,
                    controller = viewModel,
                    onOpenQuests = {
                        tab = tabs.indexOf("任務").coerceAtLeast(0)
                    }
                )
                "成員" -> MembersScreen(state, viewModel)
                "任務" -> QuestScreen(state, viewModel, requestedQuestId, onQuestRequestConsumed)
                "審核" -> AdminReviewScreen(state, viewModel)
                "商店" -> RewardShopScreen(state, viewModel)
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun GuildDestinationButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(width = 52.dp, height = 56.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

private fun guildNavigationLabel(label: String, language: AppLanguage): String = when (label) {
    "首頁" -> language.text("首頁", "Home", "Start", "ホーム")
    "成員" -> language.text("成員", "Members", "Mitglieder", "メンバー")
    "任務" -> language.text("任務", "Quests", "Aufgaben", "クエスト")
    "審核" -> language.text("審核", "Review", "Prüfung", "審査")
    "商店" -> language.text("商店", "Shop", "Shop", "ショップ")
    else -> label
}

private fun tabIcon(label: String) = when (label) {
    "首頁" -> Icons.Default.Shield
    "成員" -> Icons.Default.Badge
    "任務" -> Icons.Default.ListAlt
    "審核" -> Icons.Default.RateReview
    "商店" -> Icons.Default.Store
    else -> Icons.Default.Groups
}

@Composable
private fun WeekdaySelector(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val days = listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            days.take(4).forEach { (value, label) ->
                FilterChip(
                    selected = value in selected,
                    onClick = { onChange(if (value in selected) selected - value else selected + value) },
                    label = { Text(label) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            days.drop(4).forEach { (value, label) ->
                FilterChip(
                    selected = value in selected,
                    onClick = { onChange(if (value in selected) selected - value else selected + value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun SingleWeekdaySelector(selected: Int?, onChange: (Int) -> Unit) {
    val days = listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            days.take(4).forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onChange(value) },
                    label = { Text(label) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            days.drop(4).forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onChange(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun QuestMetaText(quest: Quest) {
    val language = LocalAppLanguage.current
    val announcedAt = formatDate(quest.announcedAtMillis)
        ?: language.text("立即公告", "Immediately", "Sofort", "即時")
    val acceptanceAt = formatDate(quest.acceptStartsAtMillis)
        ?: language.text("立即開放", "Immediately", "Sofort", "即時")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(language.text(
            "公告：$announcedAt",
            "Announcement: $announcedAt",
            "Ankündigung: $announcedAt",
            "告知：$announcedAt"
        ))
        Text(if (quest.type.isStrictCycleType()) {
            language.text(
                "開放回報：$acceptanceAt",
                "Submission opens: $acceptanceAt",
                "Abgabe ab: $acceptanceAt",
                "報告開始：$acceptanceAt"
            )
        } else {
            language.text(
                "開放接取：$acceptanceAt",
                "Acceptance opens: $acceptanceAt",
                "Annahme ab: $acceptanceAt",
                "受注開始：$acceptanceAt"
            )
        })
        Text(language.text(
            "難度：${quest.difficulty.displayName} · 最低 Rank：${quest.minRank.displayName}",
            "Difficulty: ${language.systemText(quest.difficulty.displayName)} · Minimum rank: ${language.systemText(quest.minRank.displayName)}",
            "Schwierigkeit: ${language.systemText(quest.difficulty.displayName)} · Mindestrang: ${language.systemText(quest.minRank.displayName)}",
            "難易度：${language.systemText(quest.difficulty.displayName)}・最低ランク：${language.systemText(quest.minRank.displayName)}"
        ))
        if (quest.tags.isNotEmpty()) {
            Text(language.text(
                "標籤：${quest.tags.joinToString("、")}",
                "Tags: ${quest.tags.joinToString(", ")}",
                "Tags: ${quest.tags.joinToString(", ")}",
                "タグ：${quest.tags.joinToString("、")}"
            ))
        }
        if (quest.assignedAdventurerIds.isNotEmpty()) {
            Text(language.text(
                "指名：${quest.assignedAdventurerIds.size} 位冒險者",
                "Assigned: ${quest.assignedAdventurerIds.size} adventurer(s)",
                "Zugewiesen: ${quest.assignedAdventurerIds.size} Abenteurer",
                "指名：冒険者${quest.assignedAdventurerIds.size}名"
            ), color = Moss, fontWeight = FontWeight.Bold)
        }
        if (quest.bonusGp > 0 || quest.bonusExp > 0) {
            Text("Bonus: +${quest.bonusGp} GP / +${quest.bonusExp} EXP", color = Moss)
        }
        if (quest.type.isStrictCycleType()) {
            Text(language.text(
                "固定週期：到期直接進入下一輪，無寬限或補交",
                "Fixed cycle: moves directly to the next cycle with no grace or late submission",
                "Fester Zyklus: ohne Kulanz oder Nachreichen direkt in den nächsten Zyklus",
                "固定周期：猶予・再提出なしで次周期へ移行"
            ))
        } else if (quest.gracePeriodDays > 0 || quest.submissionDeadlineDays > 0) {
            Text(language.text(
                "寬限 ${quest.gracePeriodDays} 天 · 補交 ${quest.submissionDeadlineDays} 天",
                "Grace ${quest.gracePeriodDays} day(s) · Late submission ${quest.submissionDeadlineDays} day(s)",
                "Kulanz ${quest.gracePeriodDays} Tag(e) · Nachreichen ${quest.submissionDeadlineDays} Tag(e)",
                "猶予${quest.gracePeriodDays}日・再提出${quest.submissionDeadlineDays}日"
            ))
        }
        if (quest.hasTimeLimit) {
            val unset = language.text("未設定", "Not set", "Nicht festgelegt", "未設定")
            Text(language.text(
                "期間：${formatDate(quest.startsAtMillis) ?: unset} - ${formatDate(quest.endsAtMillis) ?: unset}",
                "Period: ${formatDate(quest.startsAtMillis) ?: unset} - ${formatDate(quest.endsAtMillis) ?: unset}",
                "Zeitraum: ${formatDate(quest.startsAtMillis) ?: unset} - ${formatDate(quest.endsAtMillis) ?: unset}",
                "期間：${formatDate(quest.startsAtMillis) ?: unset} - ${formatDate(quest.endsAtMillis) ?: unset}"
            ))
        } else {
            Text(language.text(
                "期間：無時間限制",
                "Period: no time limit",
                "Zeitraum: ohne Zeitlimit",
                "期間：期限なし"
            ))
        }
        if (quest.type == QuestType.DAILY_QUEST) {
            Text(language.text(
                "星期：${formatWeekdays(quest.activeWeekdays, language)}",
                "Days: ${formatWeekdays(quest.activeWeekdays, language)}",
                "Tage: ${formatWeekdays(quest.activeWeekdays, language)}",
                "曜日：${formatWeekdays(quest.activeWeekdays, language)}"
            ))
        }
        if (quest.type == QuestType.WEEKLY_QUEST) {
            Text(language.text(
                "每週結算/刷新：${formatWeekdays(listOf(quest.weeklyRefreshWeekday ?: 1), language)}",
                "Weekly settlement/reset: ${formatWeekdays(listOf(quest.weeklyRefreshWeekday ?: 1), language)}",
                "Wöchentliche Abrechnung/Reset: ${formatWeekdays(listOf(quest.weeklyRefreshWeekday ?: 1), language)}",
                "週間精算・更新：${formatWeekdays(listOf(quest.weeklyRefreshWeekday ?: 1), language)}"
            ))
        }
        if (quest.type == QuestType.MONTHLY_QUEST) {
            Text(language.text(
                "每月結算/刷新：${quest.monthlyRefreshDay ?: 1} 日（沒有該日則用月底）",
                "Monthly settlement/reset: day ${quest.monthlyRefreshDay ?: 1} (or month end)",
                "Monatliche Abrechnung/Reset: Tag ${quest.monthlyRefreshDay ?: 1} (sonst Monatsende)",
                "月間精算・更新：${quest.monthlyRefreshDay ?: 1}日（存在しない月は月末）"
            ))
        }
        if (quest.type == QuestType.REPEATABLE_QUEST) {
            Text(language.text(
                "常駐限制：${quest.repeatLimitLabel(language)}",
                "Repeat limit: ${quest.repeatLimitLabel(language)}",
                "Wiederholungslimit: ${quest.repeatLimitLabel(language)}",
                "常駐制限：${quest.repeatLimitLabel(language)}"
            ))
        }
        if (quest.type == QuestType.LIMITED_EVENT_QUEST) {
            Text(language.text(
                "活動任務：過期後封存，不產生未完成處罰，也不開放補交",
                "Event quest: archived at expiry with no failure penalty or late submission",
                "Eventauftrag: wird nach Ablauf ohne Strafe oder Nachreichen archiviert",
                "イベントクエスト：期限後は罰則・再提出なしでアーカイブ"
            ), color = Wine, fontWeight = FontWeight.Bold)
        }
        if (quest.type == QuestType.MAIN_QUEST) {
            Text(if (quest.prerequisiteQuestIds.isEmpty()) {
                language.text(
                    "命運篇章：無前置任務",
                    "Fate Chapter: no prerequisites",
                    "Schicksalskapitel: keine Voraussetzungen",
                    "運命篇章：前提なし"
                )
            } else {
                language.text(
                    "命運篇章：需完成 ${quest.prerequisiteQuestIds.size} 個前置任務",
                    "Fate Chapter: complete ${quest.prerequisiteQuestIds.size} prerequisite(s)",
                    "Schicksalskapitel: ${quest.prerequisiteQuestIds.size} Voraussetzung(en) abschließen",
                    "運命篇章：前提クエスト${quest.prerequisiteQuestIds.size}件の完了が必要"
                )
            }, color = Moss, fontWeight = FontWeight.Bold)
        }
        if (quest.type == QuestType.PROMOTION_QUEST) {
            Text(language.text(
                "晉階試煉：達到下一階 EXP 門檻後，審核通過才會升 Rank",
                "Promotion Trial: meet the next EXP threshold and pass review to rank up",
                "Aufstiegsprüfung: EXP-Schwelle erreichen und Prüfung bestehen",
                "昇格試練：次ランクのEXP条件達成後、審査承認で昇格"
            ), color = Moss, fontWeight = FontWeight.Bold)
        }
        if (quest.type == QuestType.FORMATION_QUEST) {
            Text(language.text(
                "戰團編成：${quest.formationSlots.size} 個位置，每人 ${quest.formationMinSlotsPerUser}-${quest.formationMaxSlotsPerUser} 個",
                "Formation: ${quest.formationSlots.size} position(s), ${quest.formationMinSlotsPerUser}-${quest.formationMaxSlotsPerUser} per person",
                "Formation: ${quest.formationSlots.size} Position(en), ${quest.formationMinSlotsPerUser}-${quest.formationMaxSlotsPerUser} pro Person",
                "戦団編成：${quest.formationSlots.size}ポジション、1人${quest.formationMinSlotsPerUser}～${quest.formationMaxSlotsPerUser}個"
            ))
            val rollDate = quest.formationAutoRollAtMillis?.let { " (${formatDate(it)})" }.orEmpty()
            Text("Roll: ${language.systemText(quest.formationRollMode.displayName)}$rollDate")
        }
        if (quest.penaltyGp > 0 || quest.penaltyExp > 0) {
            Text(language.text(
                "未完成處罰：-${quest.penaltyGp} GP / -${quest.penaltyExp} EXP",
                "Failure penalty: -${quest.penaltyGp} GP / -${quest.penaltyExp} EXP",
                "Strafe bei Nichterfüllung: -${quest.penaltyGp} GP / -${quest.penaltyExp} EXP",
                "未完了罰則：-${quest.penaltyGp} GP / -${quest.penaltyExp} EXP"
            ), color = Wine)
        }
        if (!quest.pendingChangeSummary.isNullOrBlank()) {
            Text(
                language.text(
                    "下個循環變更：${quest.pendingChangeSummary}（${quest.pendingChangeEffectiveCycle ?: "下一個循環"}生效）",
                    "Next-cycle change: ${quest.pendingChangeSummary} (effective ${quest.pendingChangeEffectiveCycle ?: "next cycle"})",
                    "Änderung im nächsten Zyklus: ${quest.pendingChangeSummary} (gültig ${quest.pendingChangeEffectiveCycle ?: "im nächsten Zyklus"})",
                    "次周期の変更：${quest.pendingChangeSummary}（${quest.pendingChangeEffectiveCycle ?: "次周期"}から適用）"
                ),
                color = Brass,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun parseDateMillis(raw: String): Long? =
    runCatching {
        if (raw.isBlank()) null else SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw)?.time
    }.getOrNull()

private fun formatDate(millis: Long?): String? =
    millis?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it) }

private fun formatWeekdays(days: List<Int>, language: AppLanguage): String {
    if (days.isEmpty()) {
        return language.text("每日", "Every day", "Jeden Tag", "毎日")
    }
    val separator = language.text("、", ", ", ", ", "・")
    return days.sorted().joinToString(separator) { day ->
        when (day) {
            1 -> language.text("週一", "Mon", "Mo", "月")
            2 -> language.text("週二", "Tue", "Di", "火")
            3 -> language.text("週三", "Wed", "Mi", "水")
            4 -> language.text("週四", "Thu", "Do", "木")
            5 -> language.text("週五", "Fri", "Fr", "金")
            6 -> language.text("週六", "Sat", "Sa", "土")
            7 -> language.text("週日", "Sun", "So", "日")
            else -> language.text("未知", "Unknown", "Unbekannt", "不明")
        }
    }
}

private fun parseTags(raw: String): List<String> =
    raw.split(",", "，", "、").map { it.trim() }.filter { it.isNotBlank() }.distinct()

private fun sanitizeMonthlyRefreshDay(raw: String): Int =
    raw.toIntOrNull()?.coerceIn(1, 31) ?: 1

private fun Quest.repeatLimitLabel(language: AppLanguage): String =
    if (repeatLimitType == RepeatLimitType.NONE || repeatLimitCount <= 0) {
        language.text("無提交上限", "No submission limit", "Kein Abgabelimit", "提出上限なし")
    } else {
        language.text(
            "${repeatLimitType.displayName} $repeatLimitCount 次",
            "${language.systemText(repeatLimitType.displayName)}: $repeatLimitCount",
            "${language.systemText(repeatLimitType.displayName)}: $repeatLimitCount",
            "${language.systemText(repeatLimitType.displayName)}：$repeatLimitCount 回"
        )
    }

private val normalQuestTypes: List<QuestType> = listOf(
    QuestType.DAILY_QUEST,
    QuestType.WEEKLY_QUEST,
    QuestType.MONTHLY_QUEST,
    QuestType.REPEATABLE_QUEST,
    QuestType.FORMATION_QUEST,
    QuestType.LIMITED_EVENT_QUEST,
    QuestType.MAIN_QUEST,
    QuestType.PROMOTION_QUEST
)

private fun Quest.supportedCreationType(): QuestType =
    when (type) {
        QuestType.HIDDEN_QUEST -> QuestType.LIMITED_EVENT_QUEST
        QuestType.SIDE_QUEST -> if (hasTimeLimit) QuestType.LIMITED_EVENT_QUEST else QuestType.REPEATABLE_QUEST
        QuestType.GUILD_RAID -> QuestType.FORMATION_QUEST
        else -> type
    }

private enum class DatePickerTarget { ANNOUNCE, ACCEPT, START, END, FORMATION_ROLL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuildDatePickerDialog(
    title: String,
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.selectedDateMillis) }) {
                Text("確定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    ) {
        DatePicker(
            state = state,
            title = { Text(title, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
            headline = null
        )
    }
}

@Composable
private fun DashboardScreen(
    state: GuildUiState,
    controller: GuildController,
    onOpenQuests: () -> Unit
) {
    val language = LocalAppLanguage.current
    val user = state.user ?: return
    val activeGuild = state.activeGuild
    val userSubmissions = state.submissions.filter { it.userId == user.uid }
    val approvedCount = if (user.role == UserRole.ADVENTURER) {
        userSubmissions.count { it.status == SubmissionStatus.APPROVED }
    } else {
        state.submissions.count { it.status == SubmissionStatus.APPROVED }
    }
    val submittedCount = if (user.role == UserRole.ADVENTURER) {
        userSubmissions.count { it.status == SubmissionStatus.SUBMITTED }
    } else {
        state.submissions.count { it.status == SubmissionStatus.SUBMITTED }
    }
    val publishedQuestCount = state.quests.count { it.status == QuestStatus.PUBLISHED || it.status == QuestStatus.AVAILABLE }
    val draftQuestCount = state.quests.count { it.status == QuestStatus.DRAFT }
    val adventurerQuestStates = if (user.role == UserRole.ADVENTURER) {
        state.quests.mapNotNull { it.questStateFor(user, state.submissions) }
    } else {
        emptyList()
    }
    val handQuestCount = adventurerQuestStates.count { it == AdventurerQuestState.IN_PROGRESS || it == AdventurerQuestState.REVISION }
    val boardQuestCount = adventurerQuestStates.count { it == AdventurerQuestState.BOARD }
    val pendingReviewCount = state.pendingSubmissions.size + state.redemptions.size + state.pendingPenaltyRecords.size
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (user.role == UserRole.GUILD_ADMIN) {
                GuildAdminNameplate(user, activeGuild)
            } else {
                AdventurerNameplate(user, activeGuild)
            }
            activeGuild?.let { guild ->
                val roleTitle = user.guildRoleTitle(guild.id)
                if (roleTitle.isNotBlank()) {
                    Text(
                        language.text(
                            "公會職務：$roleTitle",
                            "Guild role: $roleTitle",
                            "Gildenrolle: $roleTitle",
                            "ギルド役職：$roleTitle"
                        ),
                        color = Moss,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (activeGuild != null && user.isGuildManager(activeGuild)) {
            item {
                ManagedGuildInvitationCard(
                    guild = activeGuild,
                    state = state,
                    controller = controller,
                    showEnterButton = false
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (user.role == UserRole.GUILD_ADMIN) {
                    StatCard(
                        language.text("已上架", "Published", "Veröffentlicht", "公開中"),
                        publishedQuestCount.toString(),
                        Icons.Default.Publish,
                        Modifier.weight(1f)
                    )
                    StatCard(
                        language.text("待上架", "Drafts", "Entwürfe", "下書き"),
                        draftQuestCount.toString(),
                        Icons.Default.Inventory,
                        Modifier.weight(1f)
                    )
                    StatCard(
                        language.text("待處理", "Pending", "Offen", "対応待ち"),
                        pendingReviewCount.toString(),
                        Icons.Default.RateReview,
                        Modifier.weight(1f)
                    )
                } else {
                    StatCard(
                        language.text("手上", "Active", "Aktiv", "進行中"),
                        handQuestCount.toString(),
                        Icons.Default.Backpack,
                        Modifier.weight(1f)
                    )
                    StatCard(
                        language.text("看板", "Board", "Brett", "掲示板"),
                        boardQuestCount.toString(),
                        Icons.Default.ListAlt,
                        Modifier.weight(1f)
                    )
                    StatCard(
                        language.text("待審", "Review", "Prüfung", "審査待ち"),
                        submittedCount.toString(),
                        Icons.Default.HourglassTop,
                        Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            GuildCard(modifier = Modifier.clickable(onClick = onOpenQuests)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (user.role == UserRole.ADVENTURER) {
                            language.text(
                                "我的任務進度",
                                "My Quest Progress",
                                "Mein Aufgabenfortschritt",
                                "自分のクエスト進捗"
                            )
                        } else {
                            language.text(
                                "公會任務進度",
                                "Guild Quest Progress",
                                "Gildenfortschritt",
                                "ギルドクエスト進捗"
                            )
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = language.text(
                            "前往任務",
                            "Open quests",
                            "Aufgaben öffnen",
                            "クエストを開く"
                        ),
                        tint = Moss
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (user.role == UserRole.ADVENTURER) {
                        language.text(
                            "已完成 $approvedCount 件 · 手上 $handQuestCount 件 · 看板 $boardQuestCount 件",
                            "Completed $approvedCount · Active $handQuestCount · Board $boardQuestCount",
                            "Erledigt $approvedCount · Aktiv $handQuestCount · Brett $boardQuestCount",
                            "完了 $approvedCount · 進行中 $handQuestCount · 掲示板 $boardQuestCount"
                        )
                    } else {
                        language.text(
                            "已核准 $approvedCount 件 · 待審核 ${state.pendingSubmissions.size} 件 · 待兌換 ${state.redemptions.size} 件",
                            "Approved $approvedCount · Reviews ${state.pendingSubmissions.size} · Redemptions ${state.redemptions.size}",
                            "Genehmigt $approvedCount · Prüfungen ${state.pendingSubmissions.size} · Einlösungen ${state.redemptions.size}",
                            "承認済み $approvedCount · 審査待ち ${state.pendingSubmissions.size} · 交換待ち ${state.redemptions.size}"
                        )
                    }
                )
                LinearProgressIndicator(
                    progress = { if (approvedCount + submittedCount == 0) 0f else approvedCount.toFloat() / (approvedCount + submittedCount) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
        state.error?.let { item { ErrorText(it) } }
    }
}

private fun UserProfile.roleSubtitle(): String =
    when (role) {
        UserRole.GUILD_ADMIN -> "管理方 · 職務權限"
        UserRole.ADVENTURER -> "${rank.displayName} · ${displayTitle()}"
    }

@Composable
private fun AdventurerNameplate(user: UserProfile, guild: Guild?) {
    val colors = rankPlateColors(user.rank)
    val rankTitle = guild?.rankTitle(user.rank) ?: user.rank.displayName
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(2.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(colors.background))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(rankTitle, color = colors.accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(user.displayName, color = colors.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text(user.roleSubtitle(), color = colors.text.copy(alpha = 0.88f), fontWeight = FontWeight.Bold)
            }
            Text(
                user.rank.name,
                color = colors.accent.copy(alpha = 0.35f),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun GuildAdminNameplate(user: UserProfile, guild: Guild?) {
    val roleTitle = guild?.let { user.guildRoleTitle(it.id) }.orEmpty().ifBlank {
        if (guild?.ownerUid == user.uid) "公會會長" else "管理成員"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(2.dp, Brass),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF2A2118), Color(0xFF5A4227), Color(0xFF1D1712))))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(roleTitle, color = Brass, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(user.displayName, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("公會管理方 · 不參與冒險者等級與任務接取", color = Color.White.copy(alpha = 0.88f), fontWeight = FontWeight.Bold)
            }
            Icon(
                Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = Brass.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.CenterEnd).size(56.dp)
            )
        }
    }
}

private data class RankPlateColors(
    val background: List<Color>,
    val border: Color,
    val accent: Color,
    val text: Color
)

private fun rankPlateColors(rank: AdventurerRank): RankPlateColors = when (rank) {
    AdventurerRank.F -> RankPlateColors(listOf(Color(0xFF2F1F13), Color(0xFF7A4E2A), Color(0xFF4A2F1B)), Color(0xFF9E7047), Color(0xFFE5C09A), Color(0xFFFFF0DF))
    AdventurerRank.E -> RankPlateColors(listOf(Color(0xFF1D2023), Color(0xFF5E676D), Color(0xFF101214)), Color(0xFF9EA8AE), Color(0xFFD7E0E5), Color(0xFFF5FBFF))
    AdventurerRank.D -> RankPlateColors(listOf(Color(0xFF4A2A17), Color(0xFFB87333), Color(0xFF6E3F22)), Color(0xFFD79A5E), Color(0xFFFFD0A0), Color(0xFFFFF3E6))
    AdventurerRank.C -> RankPlateColors(listOf(Color(0xFF30343B), Color(0xFFD6DCE3), Color(0xFF5A6470)), Color(0xFFEAF1F6), Color(0xFFFFFFFF), Color(0xFF111820))
    AdventurerRank.B -> RankPlateColors(listOf(Color(0xFF5F4307), Color(0xFFFFD56A), Color(0xFF8E6817)), Color(0xFFFFE08A), Color(0xFFFFF3B0), Color(0xFF241800))
    AdventurerRank.A -> RankPlateColors(listOf(Color(0xFF102B2C), Color(0xFFC8FFF6), Color(0xFF6FC7BD), Color(0xFFEAFDF8)), Color(0xFFD8FFF8), Color(0xFFFFFFFF), Color(0xFF061A1A))
    AdventurerRank.S -> RankPlateColors(listOf(Color(0xFF17131F), Color(0xFF434A58), Color(0xFFE8EDF5), Color(0xFFB58B2E)), Color(0xFFFFF0A6), Color(0xFFFFF7C7), Color(0xFFFFFFFF))
}

private enum class MembersPane {
    OVERVIEW,
    NOTICE,
    ACCOUNT_TRANSFER,
    JOIN_REQUESTS,
    MEMBERS,
    RANK_TITLES,
    ROLE_PERMISSIONS
}

@Composable
private fun MembersScreen(state: GuildUiState, viewModel: GuildController) {
    val user = state.user ?: return
    val guild = state.activeGuild ?: return
    val language = LocalAppLanguage.current
    val canReviewJoin = user.hasGuildPermission(guild, GuildPermission.REVIEW_JOIN_REQUESTS)
    val canRemoveMembers = user.hasGuildPermission(guild, GuildPermission.REMOVE_MEMBERS)
    val canPostAnnouncements = user.hasGuildPermission(guild, GuildPermission.POST_ANNOUNCEMENTS)
    val canSetVacation = user.hasGuildPermission(guild, GuildPermission.SET_VACATION)
    val canAssignRoles = user.hasGuildPermission(guild, GuildPermission.ASSIGN_ROLES)
    val canManagePermissions = user.hasGuildPermission(guild, GuildPermission.MANAGE_ROLE_PERMISSIONS)
    val canManageGuildSettings = user.hasGuildPermission(guild, GuildPermission.MANAGE_GUILD_SETTINGS)
    val canAdjustGpExp = user.hasGuildPermission(guild, GuildPermission.MANUAL_ADJUST_GP_EXP)
    var customTitle by remember(user.uid, user.customTitle) { mutableStateOf(user.customTitle.ifBlank { user.displayTitle() }) }
    var pane by remember(guild.id, user.uid) { mutableStateOf(MembersPane.OVERVIEW) }

    if (pane != MembersPane.OVERVIEW) {
        MembersSubPage(
            title = when (pane) {
                MembersPane.NOTICE -> language.text("公會公告與休假", "Guild notice and vacation", "Gildenankündigung und Urlaub", "ギルド告知と休暇")
                MembersPane.ACCOUNT_TRANSFER -> language.text("帳號移機", "Transfer account", "Konto übertragen", "アカウント移行")
                MembersPane.JOIN_REQUESTS -> language.text("加入申請", "Join requests", "Beitrittsanfragen", "参加申請")
                MembersPane.MEMBERS -> language.text("公會成員", "Guild members", "Gildenmitglieder", "ギルドメンバー")
                MembersPane.RANK_TITLES -> language.text("冒險者分級名牌", "Rank nameplates", "Rangschilder", "ランク名札")
                MembersPane.ROLE_PERMISSIONS -> language.text("職務權限", "Role permissions", "Rollenberechtigungen", "役職権限")
                MembersPane.OVERVIEW -> language.text("成員", "Members", "Mitglieder", "メンバー")
            },
            onBack = { pane = MembersPane.OVERVIEW }
        ) {
            when (pane) {
                MembersPane.NOTICE -> item { GuildNoticePanel(guild, canPostAnnouncements, canSetVacation, viewModel) }
                MembersPane.ACCOUNT_TRANSFER -> item { AccountTransferPanel(state, viewModel) }
                MembersPane.JOIN_REQUESTS -> item { JoinRequestsPanel(state.joinRequests, viewModel) }
                MembersPane.RANK_TITLES -> item { RankTitleSettings(guild, viewModel) }
                MembersPane.ROLE_PERMISSIONS -> item { RolePermissionSettings(guild, viewModel) }
                MembersPane.MEMBERS -> {
                    if (state.guildMembers.isEmpty()) {
                        item { Text("目前沒有可顯示的成員。") }
                    }
                    items(state.guildMembers, key = { it.uid }) { member ->
                        MemberCard(
                            member = member,
                            guild = guild,
                            quests = state.quests,
                            submissions = state.submissions,
                            canManage = canAssignRoles,
                            canAdjustGpExp = canAdjustGpExp,
                            canRemove = canRemoveMembers && member.uid != user.uid && member.uid != guild.ownerUid,
                            onAssign = { viewModel.assignGuildRole(member, guild.id, it) },
                            onAdjust = { gpDelta, expDelta, reason -> viewModel.adjustMemberGpExp(member, gpDelta, expDelta, reason) },
                            onRemove = { viewModel.removeGuildMember(member) }
                        )
                    }
                }
                MembersPane.OVERVIEW -> Unit
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            GuildCard {
                Text("我的稱號", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (user.role == UserRole.GUILD_ADMIN) {
                    val role = language.systemText(user.guildRoleTitle(guild.id).ifBlank { if (guild.ownerUid == user.uid) "公會會長" else "管理成員" })
                    language.text("管理職務：$role", "Management role: $role", "Verwaltungsrolle: $role", "管理役職：$role")
                } else {
                    "${guild.rankTitle(user.rank)} · ${user.displayTitle()}"
                })
                OutlinedTextField(
                    customTitle,
                    { customTitle = it },
                    label = { Text("自訂稱號") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = { viewModel.updateCustomTitle(customTitle) },
                    enabled = customTitle.isNotBlank()
                ) {
                    Icon(Icons.Default.Badge, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("更新稱號")
                }
            }
        }
        item {
            MembersEntryCard(
                icon = Icons.Default.Campaign,
                title = language.text("公會公告與休假", "Guild notice and vacation", "Gildenankündigung und Urlaub", "ギルド告知と休暇"),
                subtitle = if (guild.vacationEnabled) {
                    language.text("休假中，常態循環任務暫停處罰", "Vacation active; recurring quest penalties are paused", "Urlaub aktiv; Strafen für wiederkehrende Aufträge sind pausiert", "休暇中・定期クエストの罰則を停止")
                } else {
                    guild.announcement.ifBlank {
                        language.text("管理公告與公會休假設定", "Manage notices and guild vacation", "Ankündigungen und Gildenurlaub verwalten", "告知とギルド休暇を管理")
                    }
                },
                onClick = { pane = MembersPane.NOTICE }
            )
        }
        if (canReviewJoin) {
            item {
                MembersEntryCard(
                    icon = Icons.Default.PersonAdd,
                    title = language.text("加入申請", "Join requests", "Beitrittsanfragen", "参加申請"),
                    subtitle = language.text("待審 ${state.joinRequests.size} 人", "${state.joinRequests.size} pending", "${state.joinRequests.size} offen", "審査待ち ${state.joinRequests.size}人"),
                    onClick = { pane = MembersPane.JOIN_REQUESTS }
                )
            }
        }
        item {
            MembersEntryCard(
                icon = Icons.Default.Groups,
                title = language.text("公會成員", "Guild members", "Gildenmitglieder", "ギルドメンバー"),
                subtitle = language.text(
                    "${state.guildMembers.size} 位成員，指派職務與調整 GP/EXP",
                    "${state.guildMembers.size} member(s); assign roles and adjust GP/EXP",
                    "${state.guildMembers.size} Mitglied(er); Rollen und GP/EXP verwalten",
                    "${state.guildMembers.size}人・役職とGP/EXPを調整"
                ),
                onClick = { pane = MembersPane.MEMBERS }
            )
        }
        if (canManageGuildSettings) {
            item {
                MembersEntryCard(
                    icon = Icons.Default.MilitaryTech,
                    title = language.text("冒險者分級名牌", "Rank nameplates", "Rangschilder", "ランク名札"),
                    subtitle = language.text("設定木、鐵、銅、銀、金、密銀、精鋼等級名稱", "Name the wood, iron, copper, silver, gold, mithril, and adamant ranks", "Namen für Holz-, Eisen-, Kupfer-, Silber-, Gold-, Mithril- und Adamant-Ränge festlegen", "木・鉄・銅・銀・金・ミスリル・アダマンタイトの名称を設定"),
                    onClick = { pane = MembersPane.RANK_TITLES }
                )
            }
        }
        if (canManagePermissions) {
            item {
                MembersEntryCard(
                    icon = Icons.Default.AdminPanelSettings,
                    title = language.text("職務權限", "Role permissions", "Rollenberechtigungen", "役職権限"),
                    subtitle = language.text("逐一設定會長、副會長、任務官、獎勵官等職務", "Configure permissions for each management role", "Berechtigungen jeder Verwaltungsrolle festlegen", "管理役職ごとの権限を設定"),
                    onClick = { pane = MembersPane.ROLE_PERMISSIONS }
                )
            }
        }
        item {
            MembersEntryCard(
                icon = Icons.Default.PhoneAndroid,
                title = language.text("帳號移機", "Transfer account", "Konto übertragen", "アカウント移行"),
                subtitle = language.text("產生一次性移機碼，繼承永久 UUID 帳號", "Create a one-time code to transfer the permanent UUID account", "Einmalcode zum Übertragen des dauerhaften UUID-Kontos erstellen", "ワンタイムコードで永続UUIDアカウントを移行"),
                onClick = { pane = MembersPane.ACCOUNT_TRANSFER }
            )
        }
    }
}

@Composable
private fun MembersSubPage(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("返回成員")
            }
        }
        item { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        content()
    }
}

@Composable
private fun MembersEntryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    GuildCard(modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = GuildBrown, modifier = Modifier.size(34.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun AccountTransferPanel(state: GuildUiState, controller: GuildController) {
    val clipboard = LocalClipboardManager.current
    GuildCard {
        Text("帳號移機", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("帳號以永久 UUID 保存。移機碼只能使用一次，並會在 10 分鐘後失效。")
        FilledTonalButton(onClick = controller::createAccountTransferCode) {
            Icon(Icons.Default.PhoneAndroid, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("產生移機碼")
        }
        val userId = state.accountTransferUserId
        val code = state.accountTransferCode
        if (!userId.isNullOrBlank() && !code.isNullOrBlank()) {
            Text("帳號 UUID：$userId")
            Text("一次性移機碼：$code", fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString("帳號 UUID：$userId\n移機碼：$code"))
                }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("複製移機資料")
            }
        }
    }
}

@Composable
private fun GuildNoticePanel(guild: Guild, canPost: Boolean, canVacation: Boolean, viewModel: GuildController) {
    var announcement by remember(guild.id, guild.announcement) { mutableStateOf(guild.announcement) }
    var vacation by remember(guild.id, guild.vacationEnabled) { mutableStateOf(guild.vacationEnabled) }
    var vacationNote by remember(guild.id, guild.vacationNote) { mutableStateOf(guild.vacationNote) }
    GuildCard {
        Text("公會公告與休假", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (guild.announcement.isNotBlank()) Text(guild.announcement)
        if (guild.vacationEnabled) {
            Text("公會休假中：常態循環任務暫停處罰", color = Wine, fontWeight = FontWeight.Bold)
            if (guild.vacationNote.isNotBlank()) Text(guild.vacationNote)
        }
        if (canPost) {
            OutlinedTextField(announcement, { announcement = it }, label = { Text("公會公告") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            FilledTonalButton(onClick = { viewModel.updateGuildAnnouncement(announcement) }) { Text("發布公告") }
        }
        if (canVacation) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vacation, onCheckedChange = { vacation = it })
                Text("公會休假")
            }
            OutlinedTextField(vacationNote, { vacationNote = it }, label = { Text("休假說明") }, modifier = Modifier.fillMaxWidth())
            FilledTonalButton(onClick = { viewModel.updateGuildVacation(vacation, vacationNote) }) { Text("儲存休假設定") }
        }
    }
}

@Composable
private fun JoinRequestsPanel(requests: List<UserProfile>, viewModel: GuildController) {
    GuildCard {
        Text("加入申請", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (requests.isEmpty()) {
            Text("目前沒有待審加入申請。")
        }
        requests.forEach { request ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(request.displayName, fontWeight = FontWeight.Bold)
                    Text(request.email)
                }
                TextButton(onClick = { viewModel.approveJoinRequest(request, false) }) { Text("拒絕") }
                Button(onClick = { viewModel.approveJoinRequest(request, true) }) { Text("核准") }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RolePermissionSettings(guild: Guild, viewModel: GuildController) {
    val language = LocalAppLanguage.current
    var selectedRole by remember(guild.id) { mutableStateOf(GuildRoleCatalog.defaultRoles.first()) }
    GuildCard {
        Text("職務權限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("先選擇一個職務，再設定它可以使用哪些操作。")
        DropdownEnum(selectedRole, GuildRoleCatalog.defaultRoles, { selectedRole = it }) { it }
        val enabledPermissions = guild.rolePermissions[selectedRole].orEmpty()
        Text(
            language.text(
                "目前已啟用 ${enabledPermissions.size} 項權限",
                "${enabledPermissions.size} permission(s) enabled",
                "${enabledPermissions.size} Berechtigung(en) aktiviert",
                "${enabledPermissions.size}件の権限を有効化"
            ),
            color = Moss,
            fontWeight = FontWeight.Bold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GuildPermission.entries.forEach { permission ->
                val checked = permission.name in enabledPermissions
                FilterChip(
                    selected = checked,
                    onClick = { viewModel.updateGuildRolePermission(selectedRole, permission, !checked) },
                    label = { Text(permission.displayName) }
                )
            }
        }
    }
}

@Composable
private fun RankTitleSettings(guild: Guild, viewModel: GuildController) {
    GuildCard {
        Text("冒險者分級名牌", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("公會長可修改各階級顯示名稱；材質名牌會依階級自動變豪華。")
        AdventurerRank.entries.forEach { rank ->
            var title by remember(guild.id, rank.name, guild.rankTitle(rank)) { mutableStateOf(guild.rankTitle(rank)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(rank.name, modifier = Modifier.width(28.dp), fontWeight = FontWeight.Bold)
                OutlinedTextField(title, { title = it }, modifier = Modifier.weight(1f), singleLine = true)
                FilledTonalButton(onClick = { viewModel.updateGuildRankTitle(rank, title) }, enabled = title.isNotBlank()) {
                    Text("儲存")
                }
            }
        }
    }
}

@Composable
private fun MemberCard(
    member: UserProfile,
    guild: Guild,
    quests: List<Quest>,
    submissions: List<QuestSubmission>,
    canManage: Boolean,
    canAdjustGpExp: Boolean,
    canRemove: Boolean,
    onAssign: (String) -> Unit,
    onAdjust: (Long, Long, String) -> Unit,
    onRemove: () -> Unit
) {
    val language = LocalAppLanguage.current
    val isManager = member.isGuildManager(guild)
    var gpAdjust by remember(member.uid) { mutableStateOf("0") }
    var expAdjust by remember(member.uid) { mutableStateOf("0") }
    var adjustReason by remember(member.uid) { mutableStateOf("") }
    var roleTitle by remember(member.uid, member.guildRoleTitle(guild.id)) {
        mutableStateOf(member.guildRoleTitle(guild.id).ifBlank {
            if (isManager) "公會會長" else "一般成員"
        })
    }
    GuildCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(member.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (isManager) {
                    language.text("管理方", "Management", "Verwaltung", "管理側") +
                        " · " +
                        language.systemText(member.guildRoleTitle(guild.id).ifBlank {
                            if (member.uid == guild.ownerUid) "公會會長" else "管理成員"
                        })
                } else {
                    "${guild.rankTitle(member.rank)} · ${member.displayTitle()}"
                })
                val displayedRole = language.systemText(
                    member.guildRoleTitle(guild.id).ifBlank { if (isManager) "公會管理員" else "未指派" }
                )
                Text(language.text(
                    "職務：$displayedRole",
                    "Role: $displayedRole",
                    "Rolle: $displayedRole",
                    "役職：$displayedRole"
                ))
            }
            AssistChip(
                onClick = {},
                label = { Text(if (isManager) "Admin" else "Adventurer") }
            )
        }
        if (!isManager) {
            MemberQuestStatusPanel(member, quests, submissions)
        }
        if (canManage) {
            DropdownEnum(roleTitle, GuildRoleCatalog.defaultRoles, { roleTitle = it }) { it }
            OutlinedTextField(
                roleTitle,
                { roleTitle = it },
                label = { Text("自訂或選擇職務") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            FilledTonalButton(onClick = { onAssign(roleTitle) }, enabled = roleTitle.isNotBlank()) {
                Icon(Icons.Default.AssignmentInd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("指派職務")
            }
            if (canRemove) {
                OutlinedButton(onClick = onRemove) {
                    Icon(Icons.Default.PersonRemove, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("移除會員")
                }
            }
        }
        if (canAdjustGpExp) {
            Text("事後獎懲修正", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(gpAdjust, { gpAdjust = it.filter { c -> c.isDigit() || c == '-' }.take(6) }, label = { Text("GP +/-") }, modifier = Modifier.weight(1f))
                OutlinedTextField(expAdjust, { expAdjust = it.filter { c -> c.isDigit() || c == '-' }.take(6) }, label = { Text("EXP +/-") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(adjustReason, { adjustReason = it }, label = { Text("修正原因") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            FilledTonalButton(
                onClick = {
                    onAdjust(gpAdjust.toLongOrNull() ?: 0, expAdjust.toLongOrNull() ?: 0, adjustReason)
                    gpAdjust = "0"
                    expAdjust = "0"
                    adjustReason = ""
                },
                enabled = (gpAdjust.toLongOrNull() ?: 0) != 0L || (expAdjust.toLongOrNull() ?: 0) != 0L
            ) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("套用修正")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MemberQuestStatusPanel(
    member: UserProfile,
    quests: List<Quest>,
    submissions: List<QuestSubmission>
) {
    val language = LocalAppLanguage.current
    val relevant = quests
        .filter { it.status == QuestStatus.PUBLISHED || it.status == QuestStatus.AVAILABLE }
        .mapNotNull { quest ->
            val state = quest.questStateFor(member, submissions) ?: return@mapNotNull null
            quest to state
        }
    val attention = relevant
        .filter { it.second in setOf(AdventurerQuestState.IN_PROGRESS, AdventurerQuestState.SUBMITTED, AdventurerQuestState.REVISION) }
        .sortedBy { it.second.ordinal }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("任務執行狀況", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            QuestInfoPill(language.text("手上 ${relevant.count { it.second == AdventurerQuestState.IN_PROGRESS }}", "Active ${relevant.count { it.second == AdventurerQuestState.IN_PROGRESS }}", "Aktiv ${relevant.count { it.second == AdventurerQuestState.IN_PROGRESS }}", "進行中 ${relevant.count { it.second == AdventurerQuestState.IN_PROGRESS }}"))
            QuestInfoPill(language.text("待審 ${relevant.count { it.second == AdventurerQuestState.SUBMITTED }}", "Submitted ${relevant.count { it.second == AdventurerQuestState.SUBMITTED }}", "Eingereicht ${relevant.count { it.second == AdventurerQuestState.SUBMITTED }}", "審査待ち ${relevant.count { it.second == AdventurerQuestState.SUBMITTED }}"))
            QuestInfoPill(language.text("補件 ${relevant.count { it.second == AdventurerQuestState.REVISION }}", "Revision ${relevant.count { it.second == AdventurerQuestState.REVISION }}", "Ergänzung ${relevant.count { it.second == AdventurerQuestState.REVISION }}", "要修正 ${relevant.count { it.second == AdventurerQuestState.REVISION }}"))
            QuestInfoPill(language.text("完成 ${relevant.count { it.second == AdventurerQuestState.COMPLETED }}", "Completed ${relevant.count { it.second == AdventurerQuestState.COMPLETED }}", "Erledigt ${relevant.count { it.second == AdventurerQuestState.COMPLETED }}", "完了 ${relevant.count { it.second == AdventurerQuestState.COMPLETED }}"))
        }
        if (attention.isEmpty()) {
            Text("目前沒有執行中、待審或需補件的任務。", color = Moss)
        } else {
            attention.take(5).forEach { (quest, state) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(quest.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(quest.type.localizedName(language), style = MaterialTheme.typography.bodySmall)
                    }
                    AssistChip(onClick = {}, label = { Text(state.localizedLabel(language)) })
                }
            }
            if (attention.size > 5) {
                Text(language.text(
                    "另有 ${attention.size - 5} 件需要關注。",
                    "${attention.size - 5} more item(s) need attention.",
                    "${attention.size - 5} weitere Einträge benötigen Aufmerksamkeit.",
                    "ほかに${attention.size - 5}件の確認が必要です。"
                ), color = Moss)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    GuildCard(modifier) {
        Icon(icon, contentDescription = null, tint = Brass)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label)
    }
}

@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    GuildCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Brass, modifier = Modifier.size(36.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(body, color = Moss)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuestScreen(
    state: GuildUiState,
    viewModel: GuildController,
    requestedQuestId: String? = null,
    onQuestRequestConsumed: () -> Unit = {}
) {
    val language = LocalAppLanguage.current
    val user = state.user ?: return
    val guild = state.activeGuild
    val canPublish = guild != null && user.hasGuildPermission(guild, GuildPermission.PUBLISH_QUESTS)
    val canEdit = guild != null && user.hasGuildPermission(guild, GuildPermission.EDIT_QUESTS)
    val canUnpublish = guild != null && user.hasGuildPermission(guild, GuildPermission.UNPUBLISH_QUESTS)
    val canManageTemplates = guild != null && user.hasGuildPermission(guild, GuildPermission.MANAGE_QUEST_TEMPLATES)
    val adventurerMembers = if (guild == null) {
        emptyList()
    } else {
        state.guildMembers.filter { it.isGuildAdventurer(guild) }
    }
    val managerMembers = if (guild == null) {
        emptyList()
    } else {
        (state.guildMembers + user)
            .distinctBy { it.uid }
            .filter { it.isGuildManager(guild) }
            .sortedBy { it.displayName }
    }
    var selectedQuest by remember { mutableStateOf<Quest?>(null) }
    var editingQuest by remember { mutableStateOf<Quest?>(null) }
    val questTabs = if (user.role == UserRole.GUILD_ADMIN) QuestListTab.adminTabs else QuestListTab.adventurerTabs
    var tab by remember(user.role) { mutableStateOf(questTabs.first()) }
    val sortedQuests = state.quests.sortedWith(compareByDescending<Quest> { it.pinned }.thenBy { it.sortOrder }.thenBy { it.title })
    val visibleQuests = sortedQuests.filter { quest -> quest.matchesTab(tab, user, state.submissions) }

    LaunchedEffect(questTabs) {
        if (tab !in questTabs) tab = questTabs.first()
    }

    LaunchedEffect(requestedQuestId, state.quests) {
        val questId = requestedQuestId ?: return@LaunchedEffect
        val quest = state.quests.firstOrNull { it.id == questId }
        if (quest != null) {
            selectedQuest = quest
            onQuestRequestConsumed()
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.error?.let {
            item { ErrorText(it) }
        }
        if (state.counterSessions.any { it.adventurerUid == user.uid }) {
            item {
                CounterSessionPanel(
                    sessions = state.counterSessions.filter { it.adventurerUid == user.uid },
                    currentUser = user,
                    nearbyState = state.nearbyCounter,
                    onConfirm = viewModel::confirmCounterSession,
                    onCancel = viewModel::cancelCounterSession,
                    onStartNearby = viewModel::startNearbyCounter,
                    onConfirmNearby = viewModel::confirmNearbyCounter,
                    onStopNearby = viewModel::stopNearbyCounter
                )
            }
        }
        if (canPublish) {
            item { CreateQuestPanel(viewModel, state.questTemplates, adventurerMembers, managerMembers, state.quests) }
        }
        item {
            QuestTabRow(questTabs, tab, onChange = { tab = it })
        }
        if (visibleQuests.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = if (tab == QuestListTab.IN_PROGRESS) Icons.Default.Backpack else Icons.Default.ListAlt,
                    title = tab.emptyTitle(language),
                    body = tab.emptyBody(language)
                )
            }
        }
        items(visibleQuests, key = { it.id }) { quest ->
            QuestListCard(
                quest = quest,
                canPublish = canPublish,
                canEdit = canEdit,
                canUnpublish = canUnpublish,
                canManageTemplates = canManageTemplates,
                onOpen = { selectedQuest = quest },
                onEdit = { editingQuest = quest },
                onDuplicate = { viewModel.duplicateQuest(quest) },
                onSaveTemplate = { viewModel.saveQuestAsTemplate(quest) },
                onPublish = { viewModel.setQuestStatus(quest, QuestStatus.PUBLISHED) },
                onUnpublish = { viewModel.setQuestStatus(quest, QuestStatus.DRAFT) },
                onCancel = { viewModel.setQuestStatus(quest, QuestStatus.CANCELLED) }
            )
        }
    }
    selectedQuest?.let { quest ->
        val liveQuest = state.quests.firstOrNull { it.id == quest.id } ?: quest
        val liveUser = state.user ?: user
        QuestDetailDialog(
            quest = liveQuest,
            user = liveUser,
            submissions = state.submissions,
            adventurers = adventurerMembers,
            onDismiss = { selectedQuest = null },
            onAccept = {
                viewModel.acceptQuest(quest)
            },
            onSelectFormationSlot = { slot ->
                viewModel.selectFormationSlot(liveQuest, slot)
            },
            onRollFormation = {
                viewModel.rollFormationQuest(liveQuest)
            },
            onSubmit = {
                viewModel.submitQuest(liveQuest, it.proofText, null, it.overachieved, it.overachievementText)
                selectedQuest = null
            }
        )
    }
    editingQuest?.let { quest ->
        QuestEditDialog(
            quest = quest,
            adventurers = adventurerMembers,
            managers = managerMembers,
            quests = state.quests,
            onDismiss = { editingQuest = null },
            onSave = { updated, summary ->
                viewModel.editQuest(quest, updated, summary)
                editingQuest = null
            }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuestListCard(
    quest: Quest,
    canPublish: Boolean,
    canEdit: Boolean,
    canUnpublish: Boolean,
    canManageTemplates: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onSaveTemplate: () -> Unit,
    onPublish: () -> Unit,
    onUnpublish: () -> Unit,
    onCancel: () -> Unit
) {
    val language = LocalAppLanguage.current
    GuildCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        quest.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (quest.pinned) {
                        Icon(Icons.Default.PushPin, contentDescription = null, tint = Brass, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    quest.description.ifBlank {
                        language.text(
                            "尚未填寫任務說明",
                            "No quest description",
                            "Keine Aufgabenbeschreibung",
                            "クエスト説明はありません"
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    QuestInfoPill(quest.type.localizedName(language))
                    QuestInfoPill(quest.status.localizedLabel(language))
                    QuestInfoPill("+${quest.gpReward} GP")
                    QuestInfoPill("+${quest.expReward} EXP")
                    if (quest.type == QuestType.REPEATABLE_QUEST) QuestInfoPill(quest.repeatLimitLabel(language))
                    if (quest.assignedAdventurerIds.isNotEmpty()) {
                        QuestInfoPill(language.text(
                            "指名 ${quest.assignedAdventurerIds.size}",
                            "Assigned ${quest.assignedAdventurerIds.size}",
                            "Zugewiesen ${quest.assignedAdventurerIds.size}",
                            "指名 ${quest.assignedAdventurerIds.size}"
                        ))
                    }
                    if (quest.hasTimeLimit) {
                        val unset = language.text("未定", "TBD", "Offen", "未定")
                        QuestInfoPill("${formatDate(quest.startsAtMillis) ?: unset} - ${formatDate(quest.endsAtMillis) ?: unset}")
                    }
                    else {
                        QuestInfoPill(
                            language.text("無期限", "No deadline", "Ohne Frist", "期限なし")
                        )
                    }
                }
            }
            FilledTonalIconButton(onClick = onOpen, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.OpenInNew, contentDescription = "查看任務")
            }
        }
        if (canPublish || canEdit || canUnpublish || canManageTemplates) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canEdit && quest.canAdminEdit()) {
                    AssistChip(
                        onClick = onEdit,
                        label = { Text(if (quest.isAnnounced() && quest.isFixedCycleQuest()) "變更下循環" else "編輯", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                }
                if (canPublish) {
                    AssistChip(
                        onClick = onDuplicate,
                        label = { Text("複製", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                    )
                }
                if (canManageTemplates) {
                    AssistChip(
                        onClick = onSaveTemplate,
                        label = { Text("存模板", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) }
                    )
                }
                if (canPublish && quest.status != QuestStatus.PUBLISHED && quest.status != QuestStatus.AVAILABLE) {
                    AssistChip(onClick = onPublish, label = { Text("上架", maxLines = 1) })
                }
                if (canUnpublish && (quest.status == QuestStatus.PUBLISHED || quest.status == QuestStatus.AVAILABLE)) {
                    AssistChip(onClick = onUnpublish, label = { Text("下架", maxLines = 1) })
                }
                if (canUnpublish && quest.status != QuestStatus.CANCELLED) {
                    AssistChip(onClick = onCancel, label = { Text("取消", maxLines = 1) })
                }
            }
        }
    }
}

@Composable
private fun QuestInfoPill(text: String) {
    Surface(
        color = Color(0xFFF1E4C5),
        contentColor = Ink,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFFE1C98E))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun QuestStatus.localizedLabel(language: AppLanguage): String = when (this) {
    QuestStatus.DRAFT -> language.text("待上架", "Draft", "Entwurf", "下書き")
    QuestStatus.PUBLISHED, QuestStatus.AVAILABLE ->
        language.text("已上架", "Published", "Veröffentlicht", "公開中")
    QuestStatus.ACCEPTED, QuestStatus.IN_PROGRESS ->
        language.text("進行中", "In progress", "In Bearbeitung", "進行中")
    QuestStatus.SUBMITTED -> language.text("待審", "Submitted", "Eingereicht", "審査待ち")
    QuestStatus.APPROVED -> language.text("已完成", "Completed", "Erledigt", "完了")
    QuestStatus.REJECTED -> language.text("退回", "Returned", "Zurückgegeben", "差し戻し")
    QuestStatus.EXPIRED -> language.text("已過期", "Expired", "Abgelaufen", "期限切れ")
    QuestStatus.CANCELLED -> language.text("已取消", "Cancelled", "Storniert", "取消済み")
}

private fun QuestType.localizedName(language: AppLanguage): String = when (this) {
    QuestType.DAILY_QUEST -> language.text("每日任務", "Daily Quest", "Tägliche Aufgabe", "デイリークエスト")
    QuestType.WEEKLY_QUEST -> language.text("每週委託", "Weekly Quest", "Wöchentlicher Auftrag", "ウィークリー依頼")
    QuestType.MONTHLY_QUEST -> language.text("月度征伐", "Monthly Quest", "Monatsaufgabe", "月間クエスト")
    QuestType.REPEATABLE_QUEST -> language.text("常駐委託", "Repeatable Quest", "Wiederholbarer Auftrag", "常設依頼")
    QuestType.LIMITED_EVENT_QUEST -> language.text("限時討伐令", "Limited Event", "Zeitlich begrenztes Ereignis", "期間限定討伐令")
    QuestType.GUILD_RAID -> language.text("公會討伐戰", "Guild Raid", "Gildenraid", "ギルド討伐戦")
    QuestType.HIDDEN_QUEST -> language.text("秘匿委託", "Hidden Quest", "Verborgene Aufgabe", "秘匿依頼")
    QuestType.MAIN_QUEST -> language.text("命運篇章", "Main Quest", "Hauptaufgabe", "運命の章")
    QuestType.SIDE_QUEST -> language.text("支援委託", "Side Quest", "Nebenaufgabe", "支援依頼")
    QuestType.PROMOTION_QUEST -> language.text("晉階試煉", "Promotion Trial", "Aufstiegsprüfung", "昇格試練")
    QuestType.FORMATION_QUEST -> language.text("戰團編成令", "Formation Order", "Formationsauftrag", "戦団編成令")
}

private fun QuestType.localizedDescription(language: AppLanguage): String = when (this) {
    QuestType.DAILY_QUEST -> language.text("每日刷新、簡單、低獎勵", "Resets daily; simple, low reward", "Täglicher Reset; einfach, geringe Belohnung", "毎日更新・簡単・低報酬")
    QuestType.WEEKLY_QUEST -> language.text("每週刷新、中等獎勵", "Resets weekly; medium reward", "Wöchentlicher Reset; mittlere Belohnung", "毎週更新・中報酬")
    QuestType.MONTHLY_QUEST -> language.text("每月刷新、高獎勵", "Resets monthly; high reward", "Monatlicher Reset; hohe Belohnung", "毎月更新・高報酬")
    QuestType.REPEATABLE_QUEST -> language.text("可重複提交", "Can be submitted repeatedly", "Kann wiederholt abgegeben werden", "繰り返し提出可能")
    QuestType.LIMITED_EVENT_QUEST -> language.text("期間限定活動", "Limited-time event", "Zeitlich begrenztes Event", "期間限定イベント")
    QuestType.GUILD_RAID -> language.text("全公會共同累積進度", "Guild-wide shared progress", "Gildenweiter gemeinsamer Fortschritt", "ギルド全体で進捗を共有")
    QuestType.HIDDEN_QUEST -> language.text("隱藏條件觸發", "Triggered by hidden conditions", "Durch verborgene Bedingungen ausgelöst", "隠し条件で発生")
    QuestType.MAIN_QUEST -> language.text("主線劇情與功能解鎖", "Story progression and feature unlocks", "Storyfortschritt und Freischaltungen", "メインストーリーと機能解放")
    QuestType.SIDE_QUEST -> language.text("支線與額外挑戰", "Side story and extra challenges", "Nebenstory und Zusatzherausforderungen", "サブストーリーと追加挑戦")
    QuestType.PROMOTION_QUEST -> language.text("Rank 晉升任務", "Rank promotion quest", "Auftrag zum Rangaufstieg", "ランク昇格クエスト")
    QuestType.FORMATION_QUEST -> language.text("多人位置分工，可自選或 Roll 分派", "Multi-person positions with self-selection or roll assignment", "Mehrpersonen-Positionen mit Selbstwahl oder Auslosung", "複数人の役割分担・自己選択または抽選")
}

@Composable
private fun QuestTabRow(tabs: List<QuestListTab>, selected: QuestListTab, onChange: (QuestListTab) -> Unit) {
    val language = LocalAppLanguage.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        tabs.forEach { tab ->
            FilterChip(
                selected = selected == tab,
                onClick = { onChange(tab) },
                label = {
                    Text(
                        tab.localizedLabel(language),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            )
        }
    }
}

private enum class QuestListTab(val label: String) {
    PUBLISHED("已上架"),
    DRAFT("待上架"),
    SCHEDULED("未公告"),
    INACTIVE("已下架"),
    AVAILABLE("任務看板"),
    IN_PROGRESS("手上任務"),
    SUBMITTED("待審"),
    COMPLETED("已完成"),
    REVISION("需補件");

    companion object {
        val adminTabs = listOf(PUBLISHED, DRAFT, SCHEDULED, INACTIVE)
        val adventurerTabs = listOf(IN_PROGRESS, AVAILABLE, SUBMITTED, REVISION, COMPLETED)
    }
}

private fun QuestListTab.localizedLabel(language: AppLanguage): String = when (this) {
    QuestListTab.PUBLISHED -> language.text("已上架", "Published", "Veröffentlicht", "公開中")
    QuestListTab.DRAFT -> language.text("待上架", "Drafts", "Entwürfe", "下書き")
    QuestListTab.SCHEDULED -> language.text("未公告", "Scheduled", "Geplant", "公開予定")
    QuestListTab.INACTIVE -> language.text("已下架", "Inactive", "Inaktiv", "非公開")
    QuestListTab.AVAILABLE -> language.text("任務看板", "Quest Board", "Aufgabenbrett", "クエスト掲示板")
    QuestListTab.IN_PROGRESS -> language.text("手上任務", "Active Quests", "Aktive Aufgaben", "進行中")
    QuestListTab.SUBMITTED -> language.text("待審", "Submitted", "Eingereicht", "審査待ち")
    QuestListTab.COMPLETED -> language.text("已完成", "Completed", "Erledigt", "完了")
    QuestListTab.REVISION -> language.text("需補件", "Revision", "Nachbesserung", "修正必要")
}

private fun QuestListTab.emptyTitle(language: AppLanguage): String = when (this) {
    QuestListTab.IN_PROGRESS -> language.text(
        "目前沒有手上任務",
        "No active quests",
        "Keine aktiven Aufgaben",
        "進行中のクエストはありません"
    )
    QuestListTab.AVAILABLE -> language.text(
        "任務看板目前沒有可接取項目",
        "No quests are available on the board",
        "Am Aufgabenbrett sind keine Aufgaben verfügbar",
        "掲示板に受注可能なクエストはありません"
    )
    QuestListTab.SUBMITTED -> language.text(
        "目前沒有待審回報",
        "No submissions awaiting review",
        "Keine Einreichungen warten auf Prüfung",
        "審査待ちの報告はありません"
    )
    QuestListTab.REVISION -> language.text(
        "目前沒有需要補件的任務",
        "No quests need revision",
        "Keine Aufgaben müssen nachgebessert werden",
        "修正が必要なクエストはありません"
    )
    QuestListTab.COMPLETED -> language.text(
        "目前還沒有完成紀錄",
        "No completed quests yet",
        "Noch keine erledigten Aufgaben",
        "完了記録はまだありません"
    )
    QuestListTab.PUBLISHED -> language.text(
        "目前沒有已上架任務",
        "No published quests",
        "Keine veröffentlichten Aufgaben",
        "公開中のクエストはありません"
    )
    QuestListTab.DRAFT -> language.text(
        "目前沒有待上架草稿",
        "No quest drafts",
        "Keine Aufgabenentwürfe",
        "クエストの下書きはありません"
    )
    QuestListTab.SCHEDULED -> language.text(
        "目前沒有未公告任務",
        "No scheduled quests",
        "Keine geplanten Aufgaben",
        "公開予定のクエストはありません"
    )
    QuestListTab.INACTIVE -> language.text(
        "目前沒有下架或封存任務",
        "No inactive or archived quests",
        "Keine inaktiven oder archivierten Aufgaben",
        "非公開または保管中のクエストはありません"
    )
}

private fun QuestListTab.emptyBody(language: AppLanguage): String = when (this) {
    QuestListTab.IN_PROGRESS -> language.text(
        "固定週期任務、已接取任務與已分派戰團位置會出現在這裡。",
        "Recurring, accepted and assigned formation quests appear here.",
        "Wiederkehrende, angenommene und zugewiesene Aufgaben erscheinen hier.",
        "定期、受注済み、編成で割り当てられたクエストがここに表示されます。"
    )
    QuestListTab.AVAILABLE -> language.text(
        "公會發布的新委託會出現在任務看板。",
        "New guild commissions appear on the quest board.",
        "Neue Gildenaufträge erscheinen am Aufgabenbrett.",
        "ギルドの新しい依頼が掲示板に表示されます。"
    )
    QuestListTab.SUBMITTED -> language.text(
        "送出回報後會在這裡等待公會審核。",
        "Submitted reports wait here for guild review.",
        "Eingereichte Berichte warten hier auf die Gildenprüfung.",
        "提出した報告はここでギルドの審査を待ちます。"
    )
    QuestListTab.REVISION -> language.text(
        "被退回或要求補件的任務會集中在這裡。",
        "Returned quests and revision requests appear here.",
        "Zurückgegebene Aufgaben und Nachforderungen erscheinen hier.",
        "差し戻しや修正依頼のクエストがここに表示されます。"
    )
    QuestListTab.COMPLETED -> language.text(
        "審核通過的任務會保留在完成紀錄。",
        "Approved quests remain in your completion history.",
        "Genehmigte Aufgaben bleiben im Abschlussverlauf.",
        "承認済みクエストは完了履歴に残ります。"
    )
    QuestListTab.PUBLISHED -> language.text(
        "上架任務會顯示給符合條件的冒險者。",
        "Published quests are shown to eligible adventurers.",
        "Veröffentlichte Aufgaben werden geeigneten Abenteurern angezeigt.",
        "公開したクエストは条件を満たす冒険者に表示されます。"
    )
    QuestListTab.DRAFT -> language.text(
        "新建任務可先保存為草稿，確認後再上架。",
        "New quests can be saved as drafts before publication.",
        "Neue Aufgaben können vor der Veröffentlichung als Entwurf gespeichert werden.",
        "新しいクエストは公開前に下書き保存できます。"
    )
    QuestListTab.SCHEDULED -> language.text(
        "設定未來公告日期的任務會先放在這裡。",
        "Quests with a future announcement date appear here.",
        "Aufgaben mit künftigem Ankündigungsdatum erscheinen hier.",
        "将来の告知日時を設定したクエストがここに表示されます。"
    )
    QuestListTab.INACTIVE -> language.text(
        "已取消、過期或封存的任務會集中在這裡。",
        "Cancelled, expired and archived quests appear here.",
        "Stornierte, abgelaufene und archivierte Aufgaben erscheinen hier.",
        "取消、期限切れ、保管中のクエストがここに表示されます。"
    )
}

private enum class AdventurerQuestState(val label: String) {
    BOARD("看板"),
    IN_PROGRESS("執行中"),
    SUBMITTED("待審"),
    COMPLETED("已完成"),
    REVISION("需補件"),
    LOCKED("未解鎖")
}

private fun AdventurerQuestState.localizedLabel(language: AppLanguage): String = when (this) {
    AdventurerQuestState.BOARD -> language.text("看板", "Board", "Brett", "掲示板")
    AdventurerQuestState.IN_PROGRESS -> language.text("執行中", "In progress", "In Bearbeitung", "進行中")
    AdventurerQuestState.SUBMITTED -> language.text("待審", "Submitted", "Eingereicht", "審査待ち")
    AdventurerQuestState.COMPLETED -> language.text("已完成", "Completed", "Erledigt", "完了")
    AdventurerQuestState.REVISION -> language.text("需補件", "Needs revision", "Ergänzung nötig", "要修正")
    AdventurerQuestState.LOCKED -> language.text("未解鎖", "Locked", "Gesperrt", "未解放")
}

private fun Quest.matchesTab(tab: QuestListTab, user: UserProfile, submissions: List<QuestSubmission>): Boolean {
    if (user.role == UserRole.GUILD_ADMIN) {
        val announced = isAnnounced()
        return when (tab) {
            QuestListTab.PUBLISHED -> (status == QuestStatus.PUBLISHED || status == QuestStatus.AVAILABLE) && announced
            QuestListTab.DRAFT -> status == QuestStatus.DRAFT
            QuestListTab.SCHEDULED -> (status == QuestStatus.PUBLISHED || status == QuestStatus.AVAILABLE) && !announced
            QuestListTab.INACTIVE -> status == QuestStatus.CANCELLED || status == QuestStatus.EXPIRED
            else -> false
        }
    }
    val mandatory = type.isStrictCycleType()
    val repeatable = type == QuestType.REPEATABLE_QUEST
    val formation = type == QuestType.FORMATION_QUEST
    val accepted = id in user.acceptedQuestIds
    val relevantSubmissions = submissions.filter { submission ->
        submission.questId == id &&
            submission.userId == user.uid &&
            (!mandatory || submission.submittedAtMillis in currentCycleWindow())
    }
    val latestSubmission = relevantSubmissions.maxByOrNull { it.submittedAtMillis }
    val repeatLimitReached = repeatable && isRepeatLimitReached(user.uid, submissions)
    val repeatPending = repeatable && latestSubmission?.status == SubmissionStatus.SUBMITTED
    val formationAssigned = formation && assignedFormationSlots(user.uid).isNotEmpty()
    return when (tab) {
        QuestListTab.AVAILABLE -> when {
            repeatable -> !repeatPending && !repeatLimitReached
            formation -> latestSubmission == null && !formationAssigned
            else -> !mandatory && !accepted && latestSubmission == null
        }
        QuestListTab.IN_PROGRESS -> ((mandatory || accepted) && latestSubmission == null) || repeatPending || (formationAssigned && latestSubmission == null)
        QuestListTab.SUBMITTED -> latestSubmission?.status == SubmissionStatus.SUBMITTED
        QuestListTab.COMPLETED -> latestSubmission?.status == SubmissionStatus.APPROVED
        QuestListTab.REVISION -> latestSubmission?.status == SubmissionStatus.NEEDS_REVISION || latestSubmission?.status == SubmissionStatus.REJECTED
        QuestListTab.PUBLISHED, QuestListTab.DRAFT, QuestListTab.SCHEDULED, QuestListTab.INACTIVE -> false
    }
}

private fun Quest.questStateFor(user: UserProfile, submissions: List<QuestSubmission>): AdventurerQuestState? {
    if (!canBeSeenByUi(user)) return null
    if (user.rank.ordinal < minRank.ordinal) return AdventurerQuestState.LOCKED
    val mandatory = type.isStrictCycleType()
    val repeatable = type == QuestType.REPEATABLE_QUEST
    val formation = type == QuestType.FORMATION_QUEST
    val relevantSubmissions = submissions
        .filter { it.questId == id && it.userId == user.uid }
        .filter { !mandatory || it.submittedAtMillis in currentCycleWindow() }
    val latest = relevantSubmissions.maxByOrNull { it.submittedAtMillis }
    return when {
        latest?.status == SubmissionStatus.SUBMITTED -> AdventurerQuestState.SUBMITTED
        latest?.status == SubmissionStatus.APPROVED -> AdventurerQuestState.COMPLETED
        latest?.status == SubmissionStatus.NEEDS_REVISION || latest?.status == SubmissionStatus.REJECTED -> AdventurerQuestState.REVISION
        type == QuestType.MAIN_QUEST && missingPrerequisiteQuestIds(user.uid, submissions).isNotEmpty() -> AdventurerQuestState.LOCKED
        type == QuestType.PROMOTION_QUEST && !user.canStartPromotionTrial() -> AdventurerQuestState.LOCKED
        mandatory -> AdventurerQuestState.IN_PROGRESS
        formation && assignedFormationSlots(user.uid).isNotEmpty() -> AdventurerQuestState.IN_PROGRESS
        !repeatable && id in user.acceptedQuestIds -> AdventurerQuestState.IN_PROGRESS
        repeatable && !isRepeatLimitReached(user.uid, submissions) -> AdventurerQuestState.BOARD
        repeatable -> AdventurerQuestState.LOCKED
        else -> AdventurerQuestState.BOARD
    }
}

private fun Quest.canBeSeenByUi(user: UserProfile): Boolean =
    assignedAdventurerIds.isEmpty() || user.uid in assignedAdventurerIds

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AdventurerAssignmentSelector(
    adventurers: List<UserProfile>,
    selectedIds: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    val selectedAdventurers = adventurers.filter { it.uid in selectedIds }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("委託對象", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            if (selectedIds.isEmpty()) {
                "預設為全體冒險者可見；切換為指定後，只有被選中的冒險者會看到這份委託。"
            } else {
                "已指定 ${selectedIds.size} 位冒險者。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Moss
        )
        if (adventurers.isEmpty()) {
            Text("目前公會沒有可指定的冒險者。請先讓成員以冒險者身分加入這個公會。", color = Wine)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selectedIds.isEmpty(),
                    onClick = { onChange(emptySet()) },
                    label = { Text("全體冒險者") },
                    leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) }
                )
                adventurers.forEach { adventurer ->
                    FilterChip(
                        selected = adventurer.uid in selectedIds,
                        onClick = {
                            onChange(
                                if (adventurer.uid in selectedIds) selectedIds - adventurer.uid
                                else selectedIds + adventurer.uid
                            )
                        },
                        label = { Text(adventurer.displayName) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                    )
                }
            }
            if (selectedAdventurers.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    selectedAdventurers.forEach { adventurer ->
                        AssistChip(
                            onClick = { onChange(selectedIds - adventurer.uid) },
                            label = { Text(adventurer.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) }
                        )
                    }
                }
                TextButton(onClick = { onChange(emptySet()) }) {
                    Icon(Icons.Default.Groups, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("改回全體冒險者")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuestReviewerSelector(
    managers: List<UserProfile>,
    selectedIds: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    val selectedManagers = managers.filter { it.uid in selectedIds }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("指定審核員", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            if (selectedIds.isEmpty()) {
                "未指定時依公會職務權限審核；指定後，只有被選到的管理員能審核這個任務。"
            } else {
                "已指定 ${selectedIds.size} 位管理員可審核此任務；其他管理員即使有審核權限也不能審核，公會會長例外。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Moss
        )
        if (managers.isEmpty()) {
            Text("目前沒有可指定的公會管理方成員。", color = Wine)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selectedIds.isEmpty(),
                    onClick = { onChange(emptySet()) },
                    label = { Text("依職務權限") },
                    leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) }
                )
                managers.forEach { manager ->
                    FilterChip(
                        selected = manager.uid in selectedIds,
                        onClick = {
                            onChange(
                                if (manager.uid in selectedIds) selectedIds - manager.uid
                                else selectedIds + manager.uid
                            )
                        },
                        label = { Text(manager.displayName) },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
                    )
                }
            }
            if (selectedManagers.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    selectedManagers.forEach { manager ->
                        AssistChip(
                            onClick = { onChange(selectedIds - manager.uid) },
                            label = { Text(manager.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PrerequisiteQuestSelector(
    quests: List<Quest>,
    selectedIds: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    val candidates = quests
        .filterNot { it.type == QuestType.GUILD_RAID }
        .sortedWith(compareBy<Quest> { it.type.ordinal }.thenBy { it.title })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("篇章前置任務", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            if (selectedIds.isEmpty()) {
                "未設定前置時，冒險者可直接接取；設定後必須先完成指定任務，命運篇章才會解鎖。"
            } else {
                "已設定 ${selectedIds.size} 個前置任務。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Moss
        )
        if (candidates.isEmpty()) {
            Text("目前沒有可作為前置條件的任務。", color = Moss)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selectedIds.isEmpty(),
                    onClick = { onChange(emptySet()) },
                    label = { Text("無前置") },
                    leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                )
                candidates.forEach { quest ->
                    FilterChip(
                        selected = quest.id in selectedIds,
                        onClick = {
                            onChange(
                                if (quest.id in selectedIds) selectedIds - quest.id
                                else selectedIds + quest.id
                            )
                        },
                        label = { Text(quest.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FormationSlotEditor(slots: List<QuestSlot>, onChange: (List<QuestSlot>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("戰團位置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("每個位置可設定名額、完成獎勵與未完成處罰。", style = MaterialTheme.typography.bodySmall, color = Moss)
        slots.forEachIndexed { index, slot ->
            Surface(
                color = Color(0xFFFFFAEF),
                contentColor = Ink,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFE6D3A3)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("位置 ${index + 1}", fontWeight = FontWeight.Bold)
                    if (slots.size > 1) {
                        TextButton(onClick = { onChange(slots.filterIndexed { slotIndex, _ -> slotIndex != index }) }) {
                            Text("移除")
                        }
                    }
                }
                OutlinedTextField(
                    slot.name,
                    { value -> onChange(slots.updated(index, slot.copy(name = value))) },
                    label = { Text("位置名稱") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    slot.description,
                    { value -> onChange(slots.updated(index, slot.copy(description = value))) },
                    label = { Text("位置說明") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        slot.capacity.toString(),
                        { value -> onChange(slots.updated(index, slot.copy(capacity = value.filter(Char::isDigit).toIntOrNull() ?: 1))) },
                        label = { Text("名額") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        slot.gpReward.toString(),
                        { value -> onChange(slots.updated(index, slot.copy(gpReward = value.filter(Char::isDigit).toLongOrNull() ?: 0))) },
                        label = { Text("GP") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        slot.expReward.toString(),
                        { value -> onChange(slots.updated(index, slot.copy(expReward = value.filter(Char::isDigit).toLongOrNull() ?: 0))) },
                        label = { Text("EXP") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        slot.penaltyGp.toString(),
                        { value -> onChange(slots.updated(index, slot.copy(penaltyGp = value.filter(Char::isDigit).toLongOrNull() ?: 0))) },
                        label = { Text("扣 GP") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        slot.penaltyExp.toString(),
                        { value -> onChange(slots.updated(index, slot.copy(penaltyExp = value.filter(Char::isDigit).toLongOrNull() ?: 0))) },
                        label = { Text("扣 EXP") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(slot.selfSelectable, { checked -> onChange(slots.updated(index, slot.copy(selfSelectable = checked))) })
                    Text("開放冒險者自選")
                }
                }
            }
        }
        OutlinedButton(
            onClick = { onChange(slots + QuestSlot(name = "", capacity = 1)) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("新增位置")
        }
    }
}

private fun <T> List<T>.updated(index: Int, value: T): List<T> =
    mapIndexed { itemIndex, item -> if (itemIndex == index) value else item }

@Composable
private fun CreateQuestPanel(
    viewModel: GuildController,
    templates: List<QuestTemplate>,
    adventurers: List<UserProfile>,
    managers: List<UserProfile>,
    quests: List<Quest>
) {
    var expanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(QuestType.DAILY_QUEST) }
    var gp by remember { mutableStateOf("20") }
    var exp by remember { mutableStateOf("30") }
    var announcedAtMillis by remember { mutableStateOf<Long?>(null) }
    var acceptStartsAtMillis by remember { mutableStateOf<Long?>(null) }
    var hasTimeLimit by remember { mutableStateOf(false) }
    var startsAtMillis by remember { mutableStateOf<Long?>(null) }
    var endsAtMillis by remember { mutableStateOf<Long?>(null) }
    var datePickerTarget by remember { mutableStateOf<DatePickerTarget?>(null) }
    var penaltyGp by remember { mutableStateOf("0") }
    var penaltyExp by remember { mutableStateOf("0") }
    var weekdays by remember { mutableStateOf(setOf<Int>()) }
    var difficulty by remember { mutableStateOf(QuestDifficulty.NORMAL) }
    var tags by remember { mutableStateOf("") }
    var minRank by remember { mutableStateOf(AdventurerRank.F) }
    var assignedAdventurerIds by remember { mutableStateOf(setOf<String>()) }
    var assignedReviewerIds by remember { mutableStateOf(setOf<String>()) }
    var prerequisiteQuestIds by remember { mutableStateOf(setOf<String>()) }
    var bonusGp by remember { mutableStateOf("0") }
    var bonusExp by remember { mutableStateOf("0") }
    var gracePeriodDays by remember { mutableStateOf("0") }
    var submissionDeadlineDays by remember { mutableStateOf("0") }
    var weeklyRefreshWeekday by remember { mutableStateOf<Int?>(1) }
    var monthlyRefreshDay by remember { mutableStateOf("1") }
    var repeatLimitType by remember { mutableStateOf(RepeatLimitType.NONE) }
    var repeatLimitCount by remember { mutableStateOf("0") }
    var formationSlots by remember { mutableStateOf(listOf(QuestSlot(name = "", capacity = 1, gpReward = 10, expReward = 10))) }
    var formationRequired by remember { mutableStateOf(false) }
    var formationMinSlotsPerUser by remember { mutableStateOf("1") }
    var formationMaxSlotsPerUser by remember { mutableStateOf("1") }
    var formationRollMode by remember { mutableStateOf(FormationRollMode.OPTIONAL_SELF_SELECT) }
    var formationAutoRollAtMillis by remember { mutableStateOf<Long?>(null) }
    var proofMode by remember { mutableStateOf(QuestProofMode.TEXT) }
    var autoReviewEnabled by remember { mutableStateOf(false) }
    var pinned by remember { mutableStateOf(false) }
    var createAsDraft by remember { mutableStateOf(false) }
    var selectedTemplate by remember(templates) { mutableStateOf<QuestTemplate?>(templates.firstOrNull()) }
    val supportsLateSubmission = !type.isStrictCycleType() && type != QuestType.LIMITED_EVENT_QUEST
    val standardProofModes = listOf(QuestProofMode.NONE, QuestProofMode.TEXT)
    GuildCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("建立任務", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FilledTonalButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (expanded) "收合" else "新增")
            }
        }
        if (!expanded) {
            Text("展開後可建立任務或套用 CSV 模板。")
            return@GuildCard
        }
        FilledTonalButton(
            onClick = {
                viewModel.seedChildDailyQuests()
                expanded = false
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ChildCare, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("加入小朋友每日任務包")
        }
        if (templates.isNotEmpty()) {
            Text("任務模板", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            DropdownEnum(selectedTemplate ?: templates.first(), templates, { selectedTemplate = it }) { it.name }
            OutlinedButton(
                onClick = {
                    selectedTemplate?.quest?.let { templateQuest ->
                        title = templateQuest.title
                        description = templateQuest.description
                        type = templateQuest.supportedCreationType()
                        gp = templateQuest.gpReward.toString()
                        exp = templateQuest.expReward.toString()
                        announcedAtMillis = templateQuest.announcedAtMillis
                        acceptStartsAtMillis = templateQuest.acceptStartsAtMillis
                        hasTimeLimit = templateQuest.hasTimeLimit
                        startsAtMillis = templateQuest.startsAtMillis
                        endsAtMillis = templateQuest.endsAtMillis
                        penaltyGp = templateQuest.penaltyGp.toString()
                        penaltyExp = templateQuest.penaltyExp.toString()
                        weekdays = templateQuest.activeWeekdays.toSet()
                        difficulty = templateQuest.difficulty
                        tags = templateQuest.tags.joinToString(", ")
                        minRank = templateQuest.minRank
                        assignedAdventurerIds = templateQuest.assignedAdventurerIds.toSet()
                        assignedReviewerIds = templateQuest.assignedReviewerIds.toSet()
                        prerequisiteQuestIds = templateQuest.prerequisiteQuestIds.toSet()
                        bonusGp = templateQuest.bonusGp.toString()
                        bonusExp = templateQuest.bonusExp.toString()
                        gracePeriodDays = if (templateQuest.type.isStrictCycleType()) "0" else templateQuest.gracePeriodDays.toString()
                        submissionDeadlineDays = if (templateQuest.type.isStrictCycleType()) "0" else templateQuest.submissionDeadlineDays.toString()
                        weeklyRefreshWeekday = templateQuest.weeklyRefreshWeekday
                        monthlyRefreshDay = (templateQuest.monthlyRefreshDay ?: 1).toString()
                        repeatLimitType = templateQuest.repeatLimitType
                        repeatLimitCount = templateQuest.repeatLimitCount.toString()
                        formationSlots = templateQuest.formationSlots.ifEmpty { listOf(QuestSlot(name = "", capacity = 1, gpReward = 10, expReward = 10)) }
                        formationRequired = templateQuest.formationRequired
                        formationMinSlotsPerUser = templateQuest.formationMinSlotsPerUser.toString()
                        formationMaxSlotsPerUser = templateQuest.formationMaxSlotsPerUser.toString()
                        formationRollMode = templateQuest.formationRollMode
                        formationAutoRollAtMillis = templateQuest.formationAutoRollAtMillis
                        proofMode = templateQuest.proofMode
                        autoReviewEnabled = templateQuest.autoReviewEnabled
                        pinned = templateQuest.pinned
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("套用模板")
            }
        }
        OutlinedTextField(title, { title = it }, label = { Text("標題") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth())
        DropdownEnum(type, normalQuestTypes, {
            type = it
            if (it == QuestType.LIMITED_EVENT_QUEST) {
                hasTimeLimit = true
                gracePeriodDays = "0"
                submissionDeadlineDays = "0"
                penaltyGp = "0"
                penaltyExp = "0"
            }
            if (it != QuestType.MAIN_QUEST) prerequisiteQuestIds = emptySet()
        }) { it.displayName }
        DropdownEnum(difficulty, QuestDifficulty.entries, { difficulty = it }) { it.displayName }
        DropdownEnum(minRank, AdventurerRank.entries, { minRank = it }) { it.displayName }
        Text("完成證明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = proofMode == QuestProofMode.IN_PERSON,
                onCheckedChange = { useNearby ->
                    proofMode = if (useNearby) QuestProofMode.IN_PERSON else QuestProofMode.TEXT
                }
            )
            Text("需要 Nearby 當面提交")
        }
        if (proofMode == QuestProofMode.IN_PERSON) {
            Text("勾選後，冒險者最後回報時才會出現 Nearby 交付；照片、影片或其他證明由管理員當面查看，不上傳雲端。", color = Moss)
        } else {
            DropdownEnum(proofMode, standardProofModes, { proofMode = it }) { it.displayName }
            Text(proofMode.description, color = Moss)
        }
        OutlinedTextField(tags, { tags = it }, label = { Text("標籤（用逗號分隔）") }, modifier = Modifier.fillMaxWidth())
        AdventurerAssignmentSelector(adventurers, assignedAdventurerIds) { assignedAdventurerIds = it }
        QuestReviewerSelector(managers, assignedReviewerIds) { assignedReviewerIds = it }
        if (type == QuestType.MAIN_QUEST) {
            PrerequisiteQuestSelector(quests, prerequisiteQuestIds) { prerequisiteQuestIds = it }
            Text("命運篇章適合做主線與功能解鎖；前置任務完成後才會開放接取。", color = Moss)
        }
        if (type == QuestType.PROMOTION_QUEST) {
            Text("晉階試煉只適合 Rank 晉升；冒險者需先累積到下一階 EXP 門檻，通過審核後才會升階。", color = Moss, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(pinned, { pinned = it })
            Text("置頂任務")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(createAsDraft, { createAsDraft = it })
            Text("先存為待上架草稿")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(autoReviewEnabled, { autoReviewEnabled = it })
            Text("自動審核與自動處罰")
        }
        if (autoReviewEnabled) {
            Text("回報後自動發放基本獎勵；超額提交不自動加算。到期未完成會自動扣除設定處罰，可由管理員事後修正。", color = Moss)
        }
        Text("任務時程", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { datePickerTarget = DatePickerTarget.ANNOUNCE }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Campaign, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(formatDate(announcedAtMillis) ?: "公告日期")
            }
            OutlinedButton(onClick = { datePickerTarget = DatePickerTarget.ACCEPT }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.HowToReg, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(formatDate(acceptStartsAtMillis) ?: if (type.isStrictCycleType()) "開放回報" else "開放接取")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = hasTimeLimit,
                onCheckedChange = { if (type != QuestType.LIMITED_EVENT_QUEST) hasTimeLimit = it },
                enabled = type != QuestType.LIMITED_EVENT_QUEST
            )
            Text(if (type == QuestType.LIMITED_EVENT_QUEST) "活動期間限制（必填）" else "加入期間限制")
        }
        if (hasTimeLimit) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { datePickerTarget = DatePickerTarget.START }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(formatDate(startsAtMillis) ?: "開始日期")
                }
                OutlinedButton(onClick = { datePickerTarget = DatePickerTarget.END }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Event, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(formatDate(endsAtMillis) ?: "結束日期")
                }
            }
        }
        if (type == QuestType.LIMITED_EVENT_QUEST) {
            Text("限時討伐令是活動任務：必須設定結束日期，過期後封存，不產生未完成處罰，也不開放補交。", color = Wine, fontWeight = FontWeight.Bold)
        }
        Text("未完成處罰", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(penaltyGp, { penaltyGp = it.filter(Char::isDigit) }, label = { Text("扣 GP") }, modifier = Modifier.weight(1f))
            OutlinedTextField(penaltyExp, { penaltyExp = it.filter(Char::isDigit) }, label = { Text("扣 EXP") }, modifier = Modifier.weight(1f))
        }
        if (type == QuestType.DAILY_QUEST) {
            Text("每日任務星期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("可選週一到週日任意組合；不選代表每天生效。", style = MaterialTheme.typography.bodySmall, color = Moss)
            WeekdaySelector(weekdays) { weekdays = it }
        }
        if (type == QuestType.WEEKLY_QUEST) {
            Text("每週結算/刷新日", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("每週任務一週只會刷新一次，因此只能選一天。預設週一 00:00 結算上一輪並開始新週期。", style = MaterialTheme.typography.bodySmall, color = Moss)
            SingleWeekdaySelector(weeklyRefreshWeekday ?: 1) { weeklyRefreshWeekday = it }
        }
        if (type == QuestType.MONTHLY_QUEST) {
            Text("每月結算/刷新日", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(monthlyRefreshDay, { monthlyRefreshDay = it.filter(Char::isDigit).take(2) }, label = { Text("每月刷新日 1-31") }, modifier = Modifier.fillMaxWidth())
            Text("若該月沒有指定日期，系統會改在該月最後一天結算。", style = MaterialTheme.typography.bodySmall, color = Moss)
        }
        if (type == QuestType.REPEATABLE_QUEST) {
            Text("常駐提交限制", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("常駐委託會顯示在待解清單，不需接取；每次完成都可提交，但同一時間只能有一筆待審回報。", style = MaterialTheme.typography.bodySmall, color = Moss)
            DropdownEnum(repeatLimitType, RepeatLimitType.entries, { repeatLimitType = it }) { it.displayName }
            if (repeatLimitType != RepeatLimitType.NONE) {
                OutlinedTextField(
                    repeatLimitCount,
                    { repeatLimitCount = it.filter(Char::isDigit).take(3) },
                    label = { Text("提交上限次數") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (type == QuestType.FORMATION_QUEST) {
            Text("戰團編成設定", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(formationRequired, { formationRequired = it })
                Text("強制符合條件的冒險者參與")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    formationMinSlotsPerUser,
                    { formationMinSlotsPerUser = it.filter(Char::isDigit).take(2) },
                    label = { Text("每人最少") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    formationMaxSlotsPerUser,
                    { formationMaxSlotsPerUser = it.filter(Char::isDigit).take(2) },
                    label = { Text("每人最多") },
                    modifier = Modifier.weight(1f)
                )
            }
            DropdownEnum(formationRollMode, FormationRollMode.entries, { formationRollMode = it }) { it.displayName }
            OutlinedButton(onClick = { datePickerTarget = DatePickerTarget.FORMATION_ROLL }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Casino, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(formatDate(formationAutoRollAtMillis) ?: "設定 Roll 日期")
            }
            FormationSlotEditor(formationSlots) { formationSlots = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(gp, { gp = it.filter(Char::isDigit) }, label = { Text("GP") }, modifier = Modifier.weight(1f))
            OutlinedTextField(exp, { exp = it.filter(Char::isDigit) }, label = { Text("EXP") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(bonusGp, { bonusGp = it.filter(Char::isDigit) }, label = { Text("Bonus GP") }, modifier = Modifier.weight(1f))
            OutlinedTextField(bonusExp, { bonusExp = it.filter(Char::isDigit) }, label = { Text("Bonus EXP") }, modifier = Modifier.weight(1f))
        }
        if (supportsLateSubmission) {
            Text("寬限與補交", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("適合限時活動、主線、支線、晉階等非固定週期任務；固定每日/每週/每月任務到期會直接換下一輪。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(gracePeriodDays, { gracePeriodDays = it.filter(Char::isDigit) }, label = { Text("寬限天數") }, modifier = Modifier.weight(1f))
                OutlinedTextField(submissionDeadlineDays, { submissionDeadlineDays = it.filter(Char::isDigit) }, label = { Text("補交天數") }, modifier = Modifier.weight(1f))
            }
        } else {
            Text(
                if (type == QuestType.LIMITED_EVENT_QUEST) "限時討伐令過期即封存，不開放寬限或補交。"
                else "固定週期任務無寬限或補交；時間一過就進入下一個週期。",
                color = Wine
            )
        }
        Button(
            onClick = {
                viewModel.createQuest(
                    title = title,
                    description = description,
                    type = type,
                    gp = gp.toLongOrNull() ?: 0,
                    exp = exp.toLongOrNull() ?: 0,
                    announcedAtMillis = announcedAtMillis,
                    acceptStartsAtMillis = acceptStartsAtMillis,
                    hasTimeLimit = hasTimeLimit,
                    startsAtMillis = if (hasTimeLimit) startsAtMillis else null,
                    endsAtMillis = if (hasTimeLimit) endsAtMillis else null,
                    penaltyGp = penaltyGp.toLongOrNull() ?: 0,
                    penaltyExp = penaltyExp.toLongOrNull() ?: 0,
                    activeWeekdays = if (type == QuestType.DAILY_QUEST) weekdays.sorted() else emptyList(),
                    difficulty = difficulty,
                    tags = parseTags(tags),
                    minRank = minRank,
                    assignedAdventurerIds = assignedAdventurerIds.toList(),
                    assignedReviewerIds = assignedReviewerIds.toList(),
                    prerequisiteQuestIds = if (type == QuestType.MAIN_QUEST) prerequisiteQuestIds.toList() else emptyList(),
                    bonusGp = bonusGp.toLongOrNull() ?: 0,
                    bonusExp = bonusExp.toLongOrNull() ?: 0,
                    gracePeriodDays = if (supportsLateSubmission) gracePeriodDays.toIntOrNull() ?: 0 else 0,
                    submissionDeadlineDays = if (supportsLateSubmission) submissionDeadlineDays.toIntOrNull() ?: 0 else 0,
                    weeklyRefreshWeekday = if (type == QuestType.WEEKLY_QUEST) weeklyRefreshWeekday ?: 1 else null,
                    monthlyRefreshDay = if (type == QuestType.MONTHLY_QUEST) sanitizeMonthlyRefreshDay(monthlyRefreshDay) else null,
                    repeatLimitType = if (type == QuestType.REPEATABLE_QUEST) repeatLimitType else RepeatLimitType.NONE,
                    repeatLimitCount = if (type == QuestType.REPEATABLE_QUEST && repeatLimitType != RepeatLimitType.NONE) repeatLimitCount.toIntOrNull() ?: 0 else 0,
                    formationSlots = if (type == QuestType.FORMATION_QUEST) formationSlots.normalizedFormationSlots() else emptyList(),
                    formationRequired = type == QuestType.FORMATION_QUEST && formationRequired,
                    formationMinSlotsPerUser = if (type == QuestType.FORMATION_QUEST) formationMinSlotsPerUser.toIntOrNull() ?: 1 else 1,
                    formationMaxSlotsPerUser = if (type == QuestType.FORMATION_QUEST) formationMaxSlotsPerUser.toIntOrNull() ?: 1 else 1,
                    formationRollMode = if (type == QuestType.FORMATION_QUEST) formationRollMode else FormationRollMode.OPTIONAL_SELF_SELECT,
                    formationAutoRollAtMillis = if (type == QuestType.FORMATION_QUEST) formationAutoRollAtMillis else null,
                    proofMode = proofMode,
                    autoReviewEnabled = autoReviewEnabled,
                    pinned = pinned,
                    createAsDraft = createAsDraft
                )
                title = ""
                description = ""
                tags = ""
                assignedAdventurerIds = emptySet()
                assignedReviewerIds = emptySet()
                prerequisiteQuestIds = emptySet()
                createAsDraft = false
                expanded = false
            },
            enabled = title.isNotBlank() && (type != QuestType.LIMITED_EVENT_QUEST || endsAtMillis != null)
        ) {
            Icon(if (createAsDraft) Icons.Default.Inventory else Icons.Default.Publish, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (createAsDraft) "存為待上架" else "上架任務")
        }
    }
    datePickerTarget?.let { target ->
        GuildDatePickerDialog(
            title = when (target) {
                DatePickerTarget.ANNOUNCE -> "選擇公告日期"
                DatePickerTarget.ACCEPT -> "選擇開放回報/接取日期"
                DatePickerTarget.START -> "選擇開始日期"
                DatePickerTarget.END -> "選擇結束日期"
                DatePickerTarget.FORMATION_ROLL -> "選擇 Roll 日期"
            },
            initialMillis = when (target) {
                DatePickerTarget.ANNOUNCE -> announcedAtMillis
                DatePickerTarget.ACCEPT -> acceptStartsAtMillis
                DatePickerTarget.START -> startsAtMillis
                DatePickerTarget.END -> endsAtMillis
                DatePickerTarget.FORMATION_ROLL -> formationAutoRollAtMillis
            },
            onDismiss = { datePickerTarget = null },
            onConfirm = { selected ->
                when (target) {
                    DatePickerTarget.ANNOUNCE -> announcedAtMillis = selected
                    DatePickerTarget.ACCEPT -> acceptStartsAtMillis = selected
                    DatePickerTarget.START -> startsAtMillis = selected
                    DatePickerTarget.END -> endsAtMillis = selected
                    DatePickerTarget.FORMATION_ROLL -> formationAutoRollAtMillis = selected
                }
                datePickerTarget = null
            }
        )
    }
}

@Composable
private fun QuestEditDialog(
    quest: Quest,
    adventurers: List<UserProfile>,
    managers: List<UserProfile>,
    quests: List<Quest>,
    onDismiss: () -> Unit,
    onSave: (Quest, String) -> Unit
) {
    var title by remember(quest.id) { mutableStateOf(quest.title) }
    var description by remember(quest.id) { mutableStateOf(quest.description) }
    var type by remember(quest.id) { mutableStateOf(quest.supportedCreationType()) }
    var gp by remember(quest.id) { mutableStateOf(quest.gpReward.toString()) }
    var exp by remember(quest.id) { mutableStateOf(quest.expReward.toString()) }
    var announcedAtMillis by remember(quest.id) { mutableStateOf(quest.announcedAtMillis) }
    var acceptStartsAtMillis by remember(quest.id) { mutableStateOf(quest.acceptStartsAtMillis) }
    var hasTimeLimit by remember(quest.id) { mutableStateOf(quest.hasTimeLimit) }
    var startsAtMillis by remember(quest.id) { mutableStateOf(quest.startsAtMillis) }
    var endsAtMillis by remember(quest.id) { mutableStateOf(quest.endsAtMillis) }
    var penaltyGp by remember(quest.id) { mutableStateOf(quest.penaltyGp.toString()) }
    var penaltyExp by remember(quest.id) { mutableStateOf(quest.penaltyExp.toString()) }
    var weekdays by remember(quest.id) { mutableStateOf(quest.activeWeekdays.toSet()) }
    var difficulty by remember(quest.id) { mutableStateOf(quest.difficulty) }
    var tags by remember(quest.id) { mutableStateOf(quest.tags.joinToString(", ")) }
    var minRank by remember(quest.id) { mutableStateOf(quest.minRank) }
    var assignedAdventurerIds by remember(quest.id) { mutableStateOf(quest.assignedAdventurerIds.toSet()) }
    var assignedReviewerIds by remember(quest.id) { mutableStateOf(quest.assignedReviewerIds.toSet()) }
    var prerequisiteQuestIds by remember(quest.id) { mutableStateOf(quest.prerequisiteQuestIds.toSet()) }
    var bonusGp by remember(quest.id) { mutableStateOf(quest.bonusGp.toString()) }
    var bonusExp by remember(quest.id) { mutableStateOf(quest.bonusExp.toString()) }
    var gracePeriodDays by remember(quest.id) { mutableStateOf(quest.gracePeriodDays.toString()) }
    var submissionDeadlineDays by remember(quest.id) { mutableStateOf(quest.submissionDeadlineDays.toString()) }
    var weeklyRefreshWeekday by remember(quest.id) { mutableStateOf(quest.weeklyRefreshWeekday) }
    var monthlyRefreshDay by remember(quest.id) { mutableStateOf((quest.monthlyRefreshDay ?: 1).toString()) }
    var repeatLimitType by remember(quest.id) { mutableStateOf(quest.repeatLimitType) }
    var repeatLimitCount by remember(quest.id) { mutableStateOf(quest.repeatLimitCount.toString()) }
    var formationSlots by remember(quest.id) { mutableStateOf(quest.formationSlots.ifEmpty { listOf(QuestSlot(name = "", capacity = 1, gpReward = 10, expReward = 10)) }) }
    var formationRequired by remember(quest.id) { mutableStateOf(quest.formationRequired) }
    var formationMinSlotsPerUser by remember(quest.id) { mutableStateOf(quest.formationMinSlotsPerUser.toString()) }
    var formationMaxSlotsPerUser by remember(quest.id) { mutableStateOf(quest.formationMaxSlotsPerUser.toString()) }
    var formationRollMode by remember(quest.id) { mutableStateOf(quest.formationRollMode) }
    var formationAutoRollAtMillis by remember(quest.id) { mutableStateOf(quest.formationAutoRollAtMillis) }
    var proofMode by remember(quest.id) { mutableStateOf(quest.proofMode) }
    var autoReviewEnabled by remember(quest.id) { mutableStateOf(quest.autoReviewEnabled) }
    var pinned by remember(quest.id) { mutableStateOf(quest.pinned) }
    var changeSummary by remember(quest.id) { mutableStateOf(quest.pendingChangeSummary.orEmpty()) }
    var datePickerTarget by remember { mutableStateOf<DatePickerTarget?>(null) }
    val pendingOnly = quest.isAnnounced() && quest.isFixedCycleQuest()
    val supportsLateSubmission = !type.isStrictCycleType() && type != QuestType.LIMITED_EVENT_QUEST
    val standardProofModes = listOf(QuestProofMode.NONE, QuestProofMode.TEXT)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pendingOnly) "設定下個循環變更" else "編輯任務") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pendingOnly) {
                    Text("此固定任務已公告；本次修改會先顯示在冒險者任務欄，並在下一個循環生效。", color = Wine)
                } else {
                    Text("此任務尚未到公告時間，儲存後會直接更新任務設定。", color = Moss)
                }
                OutlinedTextField(title, { title = it }, label = { Text("標題") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                DropdownEnum(type, normalQuestTypes, {
                    type = it
                    if (it == QuestType.LIMITED_EVENT_QUEST) {
                        hasTimeLimit = true
                        gracePeriodDays = "0"
                        submissionDeadlineDays = "0"
                        penaltyGp = "0"
                        penaltyExp = "0"
                    }
                    if (it != QuestType.MAIN_QUEST) prerequisiteQuestIds = emptySet()
                }) { it.displayName }
                DropdownEnum(difficulty, QuestDifficulty.entries, { difficulty = it }) { it.displayName }
                DropdownEnum(minRank, AdventurerRank.entries, { minRank = it }) { it.displayName }
                Text("完成證明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = proofMode == QuestProofMode.IN_PERSON,
                        onCheckedChange = { useNearby ->
                            proofMode = if (useNearby) QuestProofMode.IN_PERSON else QuestProofMode.TEXT
                        }
                    )
                    Text("需要 Nearby 當面提交")
                }
                if (proofMode == QuestProofMode.IN_PERSON) {
                    Text("勾選後，冒險者最後回報時才會出現 Nearby 交付；照片、影片或其他證明由管理員當面查看，不上傳雲端。", color = Moss)
                } else {
                    DropdownEnum(proofMode, standardProofModes, { proofMode = it }) { it.displayName }
                    Text(proofMode.description, color = Moss)
                }
                OutlinedTextField(tags, { tags = it }, label = { Text("標籤（用逗號分隔）") }, modifier = Modifier.fillMaxWidth())
                AdventurerAssignmentSelector(adventurers, assignedAdventurerIds) { assignedAdventurerIds = it }
                QuestReviewerSelector(managers, assignedReviewerIds) { assignedReviewerIds = it }
                if (type == QuestType.MAIN_QUEST) {
                    PrerequisiteQuestSelector(quests.filterNot { it.id == quest.id }, prerequisiteQuestIds) { prerequisiteQuestIds = it }
                    Text("命運篇章適合做主線與功能解鎖；前置任務完成後才會開放接取。", color = Moss)
                }
                if (type == QuestType.PROMOTION_QUEST) {
                    Text("晉階試煉只適合 Rank 晉升；冒險者需先累積到下一階 EXP 門檻，通過審核後才會升階。", color = Moss, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(pinned, { pinned = it })
                    Text("置頂任務")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(autoReviewEnabled, { autoReviewEnabled = it })
                    Text("自動審核與自動處罰")
                }
                if (autoReviewEnabled) {
                    Text("回報後自動發放基本獎勵；超額提交不自動加算。到期未完成會自動扣除設定處罰，可由管理員事後修正。", color = Moss)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { datePickerTarget = DatePickerTarget.ANNOUNCE }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Campaign, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(formatDate(announcedAtMillis) ?: "公告日期")
                    }
                    OutlinedButton(onClick = { datePickerTarget = DatePickerTarget.ACCEPT }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.HowToReg, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(formatDate(acceptStartsAtMillis) ?: if (type.isStrictCycleType()) "開放回報" else "開放接取")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasTimeLimit,
                        onCheckedChange = { if (type != QuestType.LIMITED_EVENT_QUEST) hasTimeLimit = it },
                        enabled = type != QuestType.LIMITED_EVENT_QUEST
                    )
                    Text(if (type == QuestType.LIMITED_EVENT_QUEST) "活動期間限制（必填）" else "加入期間限制")
                }
                if (hasTimeLimit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { datePickerTarget = DatePickerTarget.START }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(formatDate(startsAtMillis) ?: "開始日期")
                        }
                        OutlinedButton(onClick = { datePickerTarget = DatePickerTarget.END }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Event, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(formatDate(endsAtMillis) ?: "結束日期")
                        }
                    }
                }
                if (type == QuestType.LIMITED_EVENT_QUEST) {
                    Text("限時討伐令是活動任務：必須設定結束日期，過期後封存，不產生未完成處罰，也不開放補交。", color = Wine, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(gp, { gp = it.filter(Char::isDigit) }, label = { Text("GP") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(exp, { exp = it.filter(Char::isDigit) }, label = { Text("EXP") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(bonusGp, { bonusGp = it.filter(Char::isDigit) }, label = { Text("Bonus GP") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(bonusExp, { bonusExp = it.filter(Char::isDigit) }, label = { Text("Bonus EXP") }, modifier = Modifier.weight(1f))
                }
                if (supportsLateSubmission) {
                    Text("寬限與補交", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(gracePeriodDays, { gracePeriodDays = it.filter(Char::isDigit) }, label = { Text("寬限天數") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(submissionDeadlineDays, { submissionDeadlineDays = it.filter(Char::isDigit) }, label = { Text("補交天數") }, modifier = Modifier.weight(1f))
                    }
                } else {
                    Text("固定週期任務無寬限或補交；時間一過就進入下一個週期。", color = Wine)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(penaltyGp, { penaltyGp = it.filter(Char::isDigit) }, label = { Text("扣 GP") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(penaltyExp, { penaltyExp = it.filter(Char::isDigit) }, label = { Text("扣 EXP") }, modifier = Modifier.weight(1f))
                }
                if (type == QuestType.DAILY_QUEST) {
                    Text("每日任務星期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("可選週一到週日任意組合；不選代表每天生效。", style = MaterialTheme.typography.bodySmall, color = Moss)
                    WeekdaySelector(weekdays) { weekdays = it }
                }
                if (type == QuestType.WEEKLY_QUEST) {
                    Text("每週結算/刷新日", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("每週任務一週只會刷新一次，因此只能選一天。預設週一 00:00 結算上一輪並開始新週期。", style = MaterialTheme.typography.bodySmall, color = Moss)
                    SingleWeekdaySelector(weeklyRefreshWeekday ?: 1) { weeklyRefreshWeekday = it }
                }
                if (type == QuestType.MONTHLY_QUEST) {
                    Text("每月結算/刷新日", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(monthlyRefreshDay, { monthlyRefreshDay = it.filter(Char::isDigit).take(2) }, label = { Text("每月刷新日 1-31") }, modifier = Modifier.fillMaxWidth())
                    Text("若該月沒有指定日期，系統會改在該月最後一天結算。", style = MaterialTheme.typography.bodySmall, color = Moss)
                }
                if (type == QuestType.REPEATABLE_QUEST) {
                    Text("常駐提交限制", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("常駐委託會顯示在待解清單，不需接取；每次完成都可提交，但同一時間只能有一筆待審回報。", style = MaterialTheme.typography.bodySmall, color = Moss)
                    DropdownEnum(repeatLimitType, RepeatLimitType.entries, { repeatLimitType = it }) { it.displayName }
                    if (repeatLimitType != RepeatLimitType.NONE) {
                        OutlinedTextField(
                            repeatLimitCount,
                            { repeatLimitCount = it.filter(Char::isDigit).take(3) },
                            label = { Text("提交上限次數") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (type == QuestType.FORMATION_QUEST) {
                    Text("戰團編成設定", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(formationRequired, { formationRequired = it })
                        Text("強制符合條件的冒險者參與")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            formationMinSlotsPerUser,
                            { formationMinSlotsPerUser = it.filter(Char::isDigit).take(2) },
                            label = { Text("每人最少") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            formationMaxSlotsPerUser,
                            { formationMaxSlotsPerUser = it.filter(Char::isDigit).take(2) },
                            label = { Text("每人最多") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    DropdownEnum(formationRollMode, FormationRollMode.entries, { formationRollMode = it }) { it.displayName }
                    OutlinedButton(onClick = { datePickerTarget = DatePickerTarget.FORMATION_ROLL }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Casino, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(formatDate(formationAutoRollAtMillis) ?: "設定 Roll 日期")
                    }
                    FormationSlotEditor(formationSlots) { formationSlots = it }
                }
                if (pendingOnly) {
                    OutlinedTextField(
                        changeSummary,
                        { changeSummary = it },
                        label = { Text("變更內容提示") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        quest.copy(
                            title = title,
                            description = description,
                            type = type,
                            gpReward = gp.toLongOrNull() ?: 0,
                            expReward = exp.toLongOrNull() ?: 0,
                            announcedAtMillis = announcedAtMillis,
                            acceptStartsAtMillis = acceptStartsAtMillis,
                            hasTimeLimit = hasTimeLimit,
                            startsAtMillis = if (hasTimeLimit) startsAtMillis else null,
                            endsAtMillis = if (hasTimeLimit) endsAtMillis else null,
                            penaltyGp = penaltyGp.toLongOrNull() ?: 0,
                            penaltyExp = penaltyExp.toLongOrNull() ?: 0,
                            activeWeekdays = if (type == QuestType.DAILY_QUEST) weekdays.sorted() else emptyList(),
                            difficulty = difficulty,
                            tags = parseTags(tags),
                            minRank = minRank,
                            assignedAdventurerIds = assignedAdventurerIds.toList(),
                            assignedReviewerIds = assignedReviewerIds.toList(),
                            prerequisiteQuestIds = if (type == QuestType.MAIN_QUEST) prerequisiteQuestIds.toList() else emptyList(),
                            bonusGp = bonusGp.toLongOrNull() ?: 0,
                            bonusExp = bonusExp.toLongOrNull() ?: 0,
                            gracePeriodDays = if (supportsLateSubmission) gracePeriodDays.toIntOrNull() ?: 0 else 0,
                            submissionDeadlineDays = if (supportsLateSubmission) submissionDeadlineDays.toIntOrNull() ?: 0 else 0,
                            weeklyRefreshWeekday = if (type == QuestType.WEEKLY_QUEST) weeklyRefreshWeekday ?: 1 else null,
                            monthlyRefreshDay = if (type == QuestType.MONTHLY_QUEST) sanitizeMonthlyRefreshDay(monthlyRefreshDay) else null,
                            repeatLimitType = if (type == QuestType.REPEATABLE_QUEST) repeatLimitType else RepeatLimitType.NONE,
                            repeatLimitCount = if (type == QuestType.REPEATABLE_QUEST && repeatLimitType != RepeatLimitType.NONE) repeatLimitCount.toIntOrNull() ?: 0 else 0,
                            formationSlots = if (type == QuestType.FORMATION_QUEST) formationSlots.normalizedFormationSlots() else emptyList(),
                            formationRequired = type == QuestType.FORMATION_QUEST && formationRequired,
                            formationMinSlotsPerUser = if (type == QuestType.FORMATION_QUEST) formationMinSlotsPerUser.toIntOrNull() ?: 1 else 1,
                            formationMaxSlotsPerUser = if (type == QuestType.FORMATION_QUEST) formationMaxSlotsPerUser.toIntOrNull() ?: 1 else 1,
                            formationRollMode = if (type == QuestType.FORMATION_QUEST) formationRollMode else FormationRollMode.OPTIONAL_SELF_SELECT,
                            formationAutoRollAtMillis = if (type == QuestType.FORMATION_QUEST) formationAutoRollAtMillis else null,
                            proofMode = proofMode,
                            autoReviewEnabled = autoReviewEnabled,
                            pinned = pinned
                        ),
                        changeSummary
                    )
                },
                enabled = title.isNotBlank() && (type != QuestType.LIMITED_EVENT_QUEST || endsAtMillis != null)
            ) {
                Text("儲存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
    datePickerTarget?.let { target ->
        GuildDatePickerDialog(
            title = when (target) {
                DatePickerTarget.ANNOUNCE -> "選擇公告日期"
                DatePickerTarget.ACCEPT -> "選擇開放回報/接取日期"
                DatePickerTarget.START -> "選擇開始日期"
                DatePickerTarget.END -> "選擇結束日期"
                DatePickerTarget.FORMATION_ROLL -> "選擇 Roll 日期"
            },
            initialMillis = when (target) {
                DatePickerTarget.ANNOUNCE -> announcedAtMillis
                DatePickerTarget.ACCEPT -> acceptStartsAtMillis
                DatePickerTarget.START -> startsAtMillis
                DatePickerTarget.END -> endsAtMillis
                DatePickerTarget.FORMATION_ROLL -> formationAutoRollAtMillis
            },
            onDismiss = { datePickerTarget = null },
            onConfirm = { selected ->
                when (target) {
                    DatePickerTarget.ANNOUNCE -> announcedAtMillis = selected
                    DatePickerTarget.ACCEPT -> acceptStartsAtMillis = selected
                    DatePickerTarget.START -> startsAtMillis = selected
                    DatePickerTarget.END -> endsAtMillis = selected
                    DatePickerTarget.FORMATION_ROLL -> formationAutoRollAtMillis = selected
                }
                datePickerTarget = null
            }
        )
    }
}

private data class SubmissionDraft(
    val proofText: String,
    val overachieved: Boolean,
    val overachievementText: String
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuestParticipantPanel(
    quest: Quest,
    adventurers: List<UserProfile>,
    submissions: List<QuestSubmission>
) {
    val language = LocalAppLanguage.current
    val rows = adventurers
        .filter { quest.canBeSeenByUi(it) }
        .mapNotNull { adventurer ->
            val state = quest.questStateFor(adventurer, submissions) ?: return@mapNotNull null
            adventurer to state
        }
        .sortedWith(compareBy<Pair<UserProfile, AdventurerQuestState>> { it.second.ordinal }.thenBy { it.first.displayName })
    val activeRows = rows.filter { it.second in setOf(AdventurerQuestState.IN_PROGRESS, AdventurerQuestState.SUBMITTED, AdventurerQuestState.REVISION) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("執行人員", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            QuestInfoPill(language.text("執行中 ${rows.count { it.second == AdventurerQuestState.IN_PROGRESS }}", "In progress ${rows.count { it.second == AdventurerQuestState.IN_PROGRESS }}", "In Bearbeitung ${rows.count { it.second == AdventurerQuestState.IN_PROGRESS }}", "進行中 ${rows.count { it.second == AdventurerQuestState.IN_PROGRESS }}"))
            QuestInfoPill(language.text("待審 ${rows.count { it.second == AdventurerQuestState.SUBMITTED }}", "Submitted ${rows.count { it.second == AdventurerQuestState.SUBMITTED }}", "Eingereicht ${rows.count { it.second == AdventurerQuestState.SUBMITTED }}", "審査待ち ${rows.count { it.second == AdventurerQuestState.SUBMITTED }}"))
            QuestInfoPill(language.text("需補件 ${rows.count { it.second == AdventurerQuestState.REVISION }}", "Revision ${rows.count { it.second == AdventurerQuestState.REVISION }}", "Ergänzung ${rows.count { it.second == AdventurerQuestState.REVISION }}", "要修正 ${rows.count { it.second == AdventurerQuestState.REVISION }}"))
            QuestInfoPill(language.text("已完成 ${rows.count { it.second == AdventurerQuestState.COMPLETED }}", "Completed ${rows.count { it.second == AdventurerQuestState.COMPLETED }}", "Erledigt ${rows.count { it.second == AdventurerQuestState.COMPLETED }}", "完了 ${rows.count { it.second == AdventurerQuestState.COMPLETED }}"))
        }
        val displayRows = if (activeRows.isNotEmpty()) activeRows else rows
        if (displayRows.isEmpty()) {
            Text("目前沒有符合此任務條件的冒險者。", color = Moss)
        } else {
            displayRows.take(12).forEach { (adventurer, state) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(adventurer.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${language.systemText(adventurer.rank.displayName)} · ${adventurer.displayTitle()}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    AssistChip(onClick = {}, label = { Text(state.localizedLabel(language)) })
                }
            }
            if (displayRows.size > 12) {
                Text(language.text(
                    "另有 ${displayRows.size - 12} 位未顯示。",
                    "${displayRows.size - 12} more adventurer(s) not shown.",
                    "${displayRows.size - 12} weitere Abenteurer werden nicht angezeigt.",
                    "ほかに${displayRows.size - 12}名が非表示です。"
                ), color = Moss)
            }
        }
    }
}

@Composable
private fun FormationSlotsPanel(
    quest: Quest,
    user: UserProfile,
    onSelectSlot: (QuestSlot) -> Unit,
    onRoll: () -> Unit
) {
    val language = LocalAppLanguage.current
    val assignedSlots = quest.assignedFormationSlots(user.uid)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("戰團位置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            language.text(
                "每人 ${quest.formationMinSlotsPerUser}-${quest.formationMaxSlotsPerUser} 個位置 · ${quest.formationRollMode.displayName}",
                "${quest.formationMinSlotsPerUser}-${quest.formationMaxSlotsPerUser} position(s) per person · ${language.systemText(quest.formationRollMode.displayName)}",
                "${quest.formationMinSlotsPerUser}-${quest.formationMaxSlotsPerUser} Position(en) pro Person · ${language.systemText(quest.formationRollMode.displayName)}",
                "1人${quest.formationMinSlotsPerUser}～${quest.formationMaxSlotsPerUser}ポジション・${language.systemText(quest.formationRollMode.displayName)}"
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Moss
        )
        quest.formationAutoRollAtMillis?.let {
            Text(language.text(
                "Roll 日期：${formatDate(it)}",
                "Roll date: ${formatDate(it)}",
                "Auslosung: ${formatDate(it)}",
                "抽選日：${formatDate(it)}"
            ), style = MaterialTheme.typography.bodySmall, color = Wine)
        }
        if (assignedSlots.isNotEmpty()) {
            Text(language.text(
                "你的分派：${assignedSlots.joinToString("、") { it.name }}",
                "Your assignment: ${assignedSlots.joinToString(", ") { it.name }}",
                "Deine Zuweisung: ${assignedSlots.joinToString(", ") { it.name }}",
                "あなたの担当：${assignedSlots.joinToString("・") { it.name }}"
            ), fontWeight = FontWeight.Bold, color = Moss)
        }
        quest.formationSlots.forEach { slot ->
            val used = quest.formationAssignments.count { it.slotId == slot.id }
            val selectedByUser = quest.formationAssignments.any { it.slotId == slot.id && it.userId == user.uid }
            Surface(
                color = Color(0xFFFFF7E8),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFE1C98E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(slot.name.ifBlank { language.text("未命名位置", "Unnamed position", "Unbenannte Position", "名称未設定") }, fontWeight = FontWeight.Bold)
                            Text(language.text(
                                "${used}/${slot.capacity} · +${slot.gpReward} GP / +${slot.expReward} EXP · 未完成 -${slot.penaltyGp} GP / -${slot.penaltyExp} EXP",
                                "${used}/${slot.capacity} · +${slot.gpReward} GP / +${slot.expReward} EXP · Failure -${slot.penaltyGp} GP / -${slot.penaltyExp} EXP",
                                "${used}/${slot.capacity} · +${slot.gpReward} GP / +${slot.expReward} EXP · Nicht erfüllt -${slot.penaltyGp} GP / -${slot.penaltyExp} EXP",
                                "${used}/${slot.capacity}・+${slot.gpReward} GP / +${slot.expReward} EXP・未完了 -${slot.penaltyGp} GP / -${slot.penaltyExp} EXP"
                            ), style = MaterialTheme.typography.bodySmall)
                            if (slot.description.isNotBlank()) Text(slot.description, style = MaterialTheme.typography.bodySmall)
                        }
                        if (user.role == UserRole.ADVENTURER) {
                            Button(
                                onClick = { onSelectSlot(slot) },
                                enabled = slot.selfSelectable &&
                                    !selectedByUser &&
                                    used < slot.capacity &&
                                    quest.formationAssignments.count { it.userId == user.uid } < quest.formationMaxSlotsPerUser.coerceAtLeast(1)
                            ) {
                                Text(if (selectedByUser) "已選" else "選位")
                            }
                        }
                    }
                }
            }
        }
        if (user.role == UserRole.GUILD_ADMIN) {
            OutlinedButton(onClick = onRoll, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Casino, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Roll 未選者")
            }
        }
    }
}

@Composable
private fun QuestDetailDialog(
    quest: Quest,
    user: UserProfile,
    submissions: List<QuestSubmission>,
    adventurers: List<UserProfile>,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onSelectFormationSlot: (QuestSlot) -> Unit,
    onRollFormation: () -> Unit,
    onSubmit: (SubmissionDraft) -> Unit
) {
    val language = LocalAppLanguage.current
    var proof by remember { mutableStateOf("") }
    var overachieved by remember { mutableStateOf(false) }
    var overachievementText by remember { mutableStateOf("") }
    val accepted = quest.id in user.acceptedQuestIds
    val mandatory = quest.type.isStrictCycleType()
    val repeatable = quest.type == QuestType.REPEATABLE_QUEST
    val formation = quest.type == QuestType.FORMATION_QUEST
    val assignedFormationSlots = if (formation) quest.assignedFormationSlots(user.uid) else emptyList()
    val missingPrerequisites = if (quest.type == QuestType.MAIN_QUEST) {
        quest.missingPrerequisiteQuestIds(user.uid, submissions)
    } else {
        emptyList()
    }
    val nextPromotionRank = user.nextPromotionRank()
    val promotionEligible = quest.type != QuestType.PROMOTION_QUEST || user.canStartPromotionTrial()
    val latestSubmission = submissions
        .filter { it.questId == quest.id }
        .filter { it.userId == user.uid }
        .filter { !mandatory || it.submittedAtMillis in quest.currentCycleWindow() }
        .maxByOrNull { it.submittedAtMillis }
    val alreadySubmitted = latestSubmission?.status == SubmissionStatus.SUBMITTED
    val alreadyApproved = !repeatable && latestSubmission?.status == SubmissionStatus.APPROVED
    val repeatLimitReached = repeatable && quest.isRepeatLimitReached(user.uid, submissions)
    val canSubmit = (accepted || mandatory || repeatable || (formation && assignedFormationSlots.isNotEmpty())) &&
        missingPrerequisites.isEmpty() &&
        promotionEligible &&
        !alreadySubmitted &&
        !alreadyApproved &&
        !repeatLimitReached
    val rankEligible = user.rank.ordinal >= quest.minRank.ordinal
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(quest.title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("${quest.type.localizedName(language)} · ${quest.type.localizedDescription(language)}")
                Text(quest.description)
                Text(language.text(
                    "獎勵：${quest.gpReward} GP / ${quest.expReward} EXP",
                    "Reward: ${quest.gpReward} GP / ${quest.expReward} EXP",
                    "Belohnung: ${quest.gpReward} GP / ${quest.expReward} EXP",
                    "報酬：${quest.gpReward} GP / ${quest.expReward} EXP"
                ))
                Text(language.text(
                    "證明方式：${quest.proofMode.displayName}",
                    "Proof: ${language.systemText(quest.proofMode.displayName)}",
                    "Nachweis: ${language.systemText(quest.proofMode.displayName)}",
                    "証明方法：${language.systemText(quest.proofMode.displayName)}"
                ), fontWeight = FontWeight.Bold)
                QuestMetaText(quest)
                if (quest.type == QuestType.FORMATION_QUEST) {
                    FormationSlotsPanel(
                        quest = quest,
                        user = user,
                        onSelectSlot = onSelectFormationSlot,
                        onRoll = onRollFormation
                    )
                }
                if (missingPrerequisites.isNotEmpty()) {
                    Text(language.text(
                        "尚未解鎖：還有 ${missingPrerequisites.size} 個前置任務未完成。",
                        "Locked: ${missingPrerequisites.size} prerequisite(s) remain.",
                        "Gesperrt: ${missingPrerequisites.size} Voraussetzung(en) fehlen.",
                        "未解放：前提クエストが${missingPrerequisites.size}件残っています。"
                    ), color = Wine, fontWeight = FontWeight.Bold)
                }
                if (quest.type == QuestType.PROMOTION_QUEST) {
                    Text(
                        nextPromotionRank?.let {
                            language.text(
                                "下一階：${it.displayName} · 需要 ${it.minExp} EXP，目前 ${user.exp} EXP",
                                "Next rank: ${language.systemText(it.displayName)} · requires ${it.minExp} EXP, current ${user.exp} EXP",
                                "Nächster Rang: ${language.systemText(it.displayName)} · benötigt ${it.minExp} EXP, aktuell ${user.exp} EXP",
                                "次ランク：${language.systemText(it.displayName)}・必要${it.minExp} EXP、現在${user.exp} EXP"
                            )
                        } ?: language.text(
                            "已達最高階，無法再晉階。",
                            "Highest rank reached.",
                            "Höchster Rang erreicht.",
                            "最高ランクに到達しています。"
                        ),
                        color = if (promotionEligible) Moss else Wine,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (user.role == UserRole.GUILD_ADMIN) {
                    Text("管理員檢視：此角色可建立、編輯與審核任務；任務回報需切換 Adventurer 身分進行。", color = Wine, fontWeight = FontWeight.Bold)
                    QuestParticipantPanel(quest, adventurers, submissions)
                }
                if (user.role == UserRole.ADVENTURER && quest.type != QuestType.GUILD_RAID && canSubmit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = overachieved, onCheckedChange = { overachieved = it })
                        Text("超額提交")
                    }
                    if (overachieved) {
                        OutlinedTextField(
                            overachievementText,
                            { overachievementText = it },
                            label = { Text("超額內容，例如：目標 60 分，實際 90 分") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        Text("額外獎勵由公會管理員審核後決定。")
                    }
                }
                if (user.role == UserRole.ADVENTURER && quest.type != QuestType.GUILD_RAID) {
                    Text(
                        when {
                            alreadySubmitted -> language.text("狀態：已提交，等待公會審核", "Status: submitted for guild review", "Status: zur Gildenprüfung eingereicht", "状態：提出済み・ギルド審査待ち")
                            alreadyApproved -> language.text("狀態：本輪已完成", "Status: completed this cycle", "Status: in diesem Zyklus erledigt", "状態：今周期は完了済み")
                            latestSubmission?.status == SubmissionStatus.NEEDS_REVISION -> language.text("狀態：需補件，可重新提交", "Status: revision requested; resubmission allowed", "Status: Ergänzung nötig; erneute Abgabe möglich", "状態：要修正・再提出可能")
                            latestSubmission?.status == SubmissionStatus.REJECTED -> language.text("狀態：已退回，可重新提交", "Status: returned; resubmission allowed", "Status: zurückgegeben; erneute Abgabe möglich", "状態：差し戻し・再提出可能")
                            missingPrerequisites.isNotEmpty() -> language.text("狀態：命運篇章尚未解鎖", "Status: Fate Chapter locked", "Status: Schicksalskapitel gesperrt", "状態：運命篇章は未解放")
                            !promotionEligible -> language.text("狀態：尚未達到晉階門檻", "Status: promotion threshold not met", "Status: Aufstiegsschwelle nicht erreicht", "状態：昇格条件未達")
                            mandatory -> language.text("狀態：強制任務（無需接取）", "Status: mandatory quest (no acceptance required)", "Status: Pflichtauftrag (keine Annahme nötig)", "状態：必須クエスト（受注不要）")
                            accepted -> language.text("狀態：已接取", "Status: accepted", "Status: angenommen", "状態：受注済み")
                            else -> language.text("狀態：可接取", "Status: available", "Status: verfügbar", "状態：受注可能")
                        }
                    )
                    if (canSubmit) {
                        if (quest.proofMode != QuestProofMode.NONE) {
                            OutlinedTextField(
                                proof,
                                { proof = it },
                                label = {
                                    Text(if (quest.proofMode == QuestProofMode.TEXT) "文字完成證明" else "當面查驗備註（可空白）")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                        when (quest.proofMode) {
                            QuestProofMode.NONE -> Text("本任務不需附證明，提交後會等待公會審核。")
                            QuestProofMode.TEXT -> Text("文字回報會直接提交給公會審核，不需要 Nearby。")
                            QuestProofMode.IN_PERSON -> Text(
                                "需要當面查驗照片、影片或其他證明時才使用 Nearby。交付時直接給管理員查看，Nearby 只傳簽核，不傳媒體檔案。",
                                color = Wine,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (user.role == UserRole.ADVENTURER && quest.type != QuestType.GUILD_RAID) {
                if (!rankEligible) {
                    TextButton(onClick = onDismiss) { Text("Rank 不足") }
                } else if (!quest.isOpenForAccept()) {
                    TextButton(onClick = onDismiss) { Text(if (mandatory) "尚未開放回報" else "尚未開放接取") }
                } else if (alreadySubmitted) {
                    TextButton(onClick = onDismiss) { Text("已提交待審") }
                } else if (alreadyApproved) {
                    TextButton(onClick = onDismiss) { Text("本輪已完成") }
                } else if (repeatLimitReached) {
                    TextButton(onClick = onDismiss) { Text("已達提交上限") }
                } else if (missingPrerequisites.isNotEmpty()) {
                    TextButton(onClick = onDismiss) { Text("尚未解鎖") }
                } else if (!promotionEligible) {
                    TextButton(onClick = onDismiss) { Text("未達晉階門檻") }
                } else if (canSubmit) {
                    Button(onClick = {
                        onSubmit(
                            SubmissionDraft(
                                proofText = proof,
                                overachieved = overachieved,
                                overachievementText = overachievementText
                            )
                        )
                    }, enabled = quest.proofMode != QuestProofMode.TEXT || proof.isNotBlank()) {
                        Text(if (quest.proofMode == QuestProofMode.IN_PERSON) "開啟 Nearby 交付" else "提交回報")
                    }
                } else if (!repeatable && !formation) {
                    Button(onClick = onAccept) { Text("前往櫃檯接取") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}

private fun Quest.isOpenForAccept(now: Long = System.currentTimeMillis()): Boolean =
    acceptStartsAtMillis == null || acceptStartsAtMillis <= now

private fun Quest.canAdminEdit(now: Long = System.currentTimeMillis()): Boolean =
    !isAnnounced(now) || isFixedCycleQuest()

private fun Quest.isAnnounced(now: Long = System.currentTimeMillis()): Boolean =
    announcedAtMillis == null || announcedAtMillis <= now

private fun Quest.isFixedCycleQuest(): Boolean =
    type == QuestType.DAILY_QUEST || type == QuestType.WEEKLY_QUEST || type == QuestType.MONTHLY_QUEST

private fun Quest.currentCycleWindow(now: Long = System.currentTimeMillis()): LongRange {
    val today = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = now }.startOfDay()
    return when (type) {
        QuestType.DAILY_QUEST -> {
            val start = today.timeInMillis
            start..(start + TimeUnit.DAYS.toMillis(1) - 1)
        }
        QuestType.WEEKLY_QUEST -> {
            val refreshWeekday = weeklyRefreshWeekday ?: 1
            val todayWeekday = today.get(Calendar.DAY_OF_WEEK).toIsoWeekday()
            val daysSinceRefresh = (todayWeekday - refreshWeekday + 7) % 7
            val start = today.timeInMillis - TimeUnit.DAYS.toMillis(daysSinceRefresh.toLong())
            start..(start + TimeUnit.DAYS.toMillis(7) - 1)
        }
        QuestType.MONTHLY_QUEST -> {
            val refreshDay = (monthlyRefreshDay ?: 1).coerceIn(1, 31)
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

private fun Quest.isRepeatLimitReached(
    userId: String,
    submissions: List<QuestSubmission>,
    now: Long = System.currentTimeMillis()
): Boolean {
    if (type != QuestType.REPEATABLE_QUEST || repeatLimitType == RepeatLimitType.NONE || repeatLimitCount <= 0) return false
    val window = repeatLimitWindow(now)
    val used = submissions.count {
        it.questId == id &&
            it.userId == userId &&
            it.status != SubmissionStatus.REJECTED &&
            it.status != SubmissionStatus.NEEDS_REVISION &&
            (window == null || it.submittedAtMillis in window)
    }
    return used >= repeatLimitCount
}

private fun Quest.missingPrerequisiteQuestIds(userId: String, submissions: List<QuestSubmission>): List<String> {
    if (prerequisiteQuestIds.isEmpty()) return emptyList()
    val completedQuestIds = submissions
        .filter { it.userId == userId && it.status == SubmissionStatus.APPROVED }
        .map { it.questId }
        .toSet()
    return prerequisiteQuestIds.filterNot { it in completedQuestIds }
}

private fun UserProfile.nextPromotionRank(): AdventurerRank? =
    AdventurerRank.entries.firstOrNull { it.ordinal > rank.ordinal }

private fun UserProfile.canStartPromotionTrial(): Boolean {
    val nextRank = nextPromotionRank() ?: return false
    return exp >= nextRank.minExp
}

private fun Quest.repeatLimitWindow(now: Long = System.currentTimeMillis()): LongRange? {
    val today = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = now }.startOfDay()
    return when (repeatLimitType) {
        RepeatLimitType.NONE, RepeatLimitType.TOTAL -> null
        RepeatLimitType.DAILY -> {
            val start = today.timeInMillis
            start..(start + TimeUnit.DAYS.toMillis(1) - 1)
        }
        RepeatLimitType.WEEKLY -> {
            val start = Calendar.getInstance(Locale.getDefault()).apply {
                timeInMillis = today.timeInMillis
                val weekday = get(Calendar.DAY_OF_WEEK).toIsoWeekday()
                add(Calendar.DAY_OF_YEAR, -((weekday - 1 + 7) % 7))
                startOfDay()
            }.timeInMillis
            start..(start + TimeUnit.DAYS.toMillis(7) - 1)
        }
        RepeatLimitType.MONTHLY -> {
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
    }
}

private fun Int.toIsoWeekday(): Int =
    if (this == Calendar.SUNDAY) 7 else this - 1

private fun Calendar.startOfDay(): Calendar = apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

@Composable
private fun CounterSessionPanel(
    sessions: List<GuildCounterSession>,
    currentUser: UserProfile,
    nearbyState: NearbyCounterState,
    onConfirm: (GuildCounterSession) -> Unit,
    onCancel: (GuildCounterSession) -> Unit,
    onStartNearby: (GuildCounterSession) -> Unit,
    onConfirmNearby: (GuildCounterSession) -> Unit,
    onStopNearby: () -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    var pendingNearbySession by remember { mutableStateOf<GuildCounterSession?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val session = pendingNearbySession
        pendingNearbySession = null
        if (session != null && grants.values.all { it }) onStartNearby(session)
    }
    fun startWithPermission(session: GuildCounterSession) {
        val permissions = NearbyCounterCoordinator.requiredRuntimePermissions()
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            onStartNearby(session)
        } else {
            pendingNearbySession = session
            permissionLauncher.launch(permissions)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sessions.forEach { session ->
            GuildCard {
                val isAdventurer = currentUser.uid == session.adventurerUid
                val title = when (session.action) {
                    GuildCounterAction.ACCEPT_QUEST -> language.text("任務接取櫃檯", "Quest acceptance counter", "Schalter zur Auftragsannahme", "クエスト受注受付")
                    GuildCounterAction.SUBMIT_QUEST -> language.text("任務交付櫃檯", "Quest handoff counter", "Schalter zur Auftragsabgabe", "クエスト提出受付")
                    GuildCounterAction.SETTLE_SUBMISSION -> language.text("獎勵結算櫃檯", "Reward settlement counter", "Belohnungsabrechnung", "報酬精算受付")
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(session.questTitle)
                Text(
                    when (session.action) {
                        GuildCounterAction.ACCEPT_QUEST ->
                            if (isAdventurer) {
                                language.text("等待管理員當面確認接取。", "Waiting for a manager to confirm acceptance in person.", "Warte auf die persönliche Bestätigung der Verwaltung.", "管理者の対面受注確認を待っています。")
                            } else {
                                language.text(
                                    "${session.adventurerName} 正在櫃檯申請接取。",
                                    "${session.adventurerName} is requesting acceptance at the counter.",
                                    "${session.adventurerName} beantragt die Annahme am Schalter.",
                                    "${session.adventurerName}が受付で受注を申請しています。"
                                )
                            }
                        GuildCounterAction.SUBMIT_QUEST ->
                            if (isAdventurer) {
                                language.text("等待管理員確認已接收回報。", "Waiting for a manager to confirm the report.", "Warte auf die Bestätigung des Berichts.", "管理者の報告受領確認を待っています。")
                            } else {
                                language.text(
                                    "${session.adventurerName} 正在櫃檯交付任務。",
                                    "${session.adventurerName} is handing in a quest.",
                                    "${session.adventurerName} gibt einen Auftrag am Schalter ab.",
                                    "${session.adventurerName}が受付でクエストを提出しています。"
                                )
                            }
                        GuildCounterAction.SETTLE_SUBMISSION ->
                            if (session.approved == true) {
                                language.text(
                                    "管理員提出核准：基本獎勵與額外 +${session.proposedBonusGp} GP / +${session.proposedBonusExp} EXP",
                                    "Manager proposes approval: base reward plus +${session.proposedBonusGp} GP / +${session.proposedBonusExp} EXP",
                                    "Verwaltung schlägt Genehmigung vor: Grundbelohnung plus +${session.proposedBonusGp} GP / +${session.proposedBonusExp} EXP",
                                    "管理者が承認を提案：基本報酬と追加 +${session.proposedBonusGp} GP / +${session.proposedBonusExp} EXP"
                                )
                            } else {
                                val reason = session.reviewNote.orEmpty().ifBlank {
                                    language.text("未填原因", "No reason provided", "Kein Grund angegeben", "理由未入力")
                                }
                                language.text(
                                    "管理員提出退回：$reason",
                                    "Manager proposes return: $reason",
                                    "Verwaltung schlägt Rückgabe vor: $reason",
                                    "管理者が差し戻しを提案：$reason"
                                )
                            }
                    }
                )
                if (session.action == GuildCounterAction.SUBMIT_QUEST) {
                    Text(language.text(
                        "證明方式：${session.proofMode.displayName}",
                        "Proof: ${language.systemText(session.proofMode.displayName)}",
                        "Nachweis: ${language.systemText(session.proofMode.displayName)}",
                        "証明方法：${language.systemText(session.proofMode.displayName)}"
                    ), fontWeight = FontWeight.Bold)
                    if (session.proofMode != QuestProofMode.NONE) {
                        val note = session.proofText.ifBlank { language.text("無", "None", "Keine", "なし") }
                        Text(language.text("備註：$note", "Note: $note", "Notiz: $note", "メモ：$note"))
                    }
                    if (session.proofMode == QuestProofMode.IN_PERSON) {
                        Text(
                            "請直接查看冒險者手機上的照片或影片。Nearby 只傳簽核回執。",
                            color = Wine,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    "Nearby · ${formatCounterExpiry(session.expiresAtMillis, language)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val canConfirm = when (session.action) {
                        GuildCounterAction.ACCEPT_QUEST,
                        GuildCounterAction.SUBMIT_QUEST -> !isAdventurer
                        GuildCounterAction.SETTLE_SUBMISSION -> isAdventurer
                    }
                    if (session.action == GuildCounterAction.SETTLE_SUBMISSION && canConfirm) {
                        Button(onClick = { onConfirm(session) }) { Text("確認結算") }
                    } else if (canConfirm) {
                        Button(onClick = { startWithPermission(session) }) {
                            Icon(Icons.Default.Sensors, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("開啟近距離簽核")
                        }
                    } else if (session.action != GuildCounterAction.SETTLE_SUBMISSION) {
                        Button(onClick = { startWithPermission(session) }) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("舉起手機交付")
                        }
                    }
                    OutlinedButton(onClick = { onCancel(session) }) { Text("取消交付") }
                }
            }
        }
    }
    sessions.firstOrNull { it.id == nearbyState.sessionId }?.let { activeSession ->
        NearbySigningDialog(
            session = activeSession,
            currentUser = currentUser,
            state = nearbyState,
            onManagerSeal = { onConfirmNearby(activeSession) },
            onDismiss = onStopNearby
        )
    }
}

@Composable
private fun NearbySigningDialog(
    session: GuildCounterSession,
    currentUser: UserProfile,
    state: NearbyCounterState,
    onManagerSeal: () -> Unit,
    onDismiss: () -> Unit
) {
    val isAdventurer = currentUser.uid == session.adventurerUid
    val haptic = LocalHapticFeedback.current
    val ready = state.phase == NearbyCounterPhase.READY_TO_SIGN
    val signed = state.phase == NearbyCounterPhase.SIGNED
    AlertDialog(
        onDismissRequest = { if (!signed) onDismiss() },
        icon = {
            Icon(
                imageVector = if (signed) Icons.Default.Verified else Icons.Default.GppGood,
                contentDescription = null,
                tint = if (signed) Moss else Brass,
                modifier = Modifier.size(64.dp)
            )
        },
        title = {
            Text(
                if (signed) "公會契約已封印" else "公會契約之印",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(session.questTitle, fontWeight = FontWeight.Bold)
                Surface(
                    modifier = Modifier.size(176.dp),
                    shape = RoundedCornerShape(88.dp),
                    color = when {
                        signed -> Moss.copy(alpha = 0.16f)
                        ready -> Brass.copy(alpha = 0.22f)
                        else -> GuildBrown.copy(alpha = 0.08f)
                    },
                    border = BorderStroke(3.dp, if (ready || signed) Brass else GuildBrown.copy(alpha = 0.35f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (ready || signed) Icons.Default.Handshake else Icons.Default.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(58.dp),
                            tint = if (signed) Moss else GuildBrown
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (state.phase) {
                                NearbyCounterPhase.ADVERTISING -> "等待管理員手機"
                                NearbyCounterPhase.DISCOVERING -> "尋找冒險者手機"
                                NearbyCounterPhase.CONNECTING -> "徽記正在相合"
                                NearbyCounterPhase.CONNECTED -> "建立安全通道"
                                NearbyCounterPhase.READY_TO_SIGN -> "近距離確認完成"
                                NearbyCounterPhase.SIGNED -> "簽核回執完成"
                                NearbyCounterPhase.ERROR -> "感應失敗"
                                else -> "準備感應"
                            },
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                state.peerName?.let { Text("對方：$it") }
                when {
                    state.phase == NearbyCounterPhase.ERROR ->
                        Text(state.error.orEmpty(), color = Wine, fontWeight = FontWeight.Bold)
                    signed ->
                        Text("雙方裝置已完成近距離簽核。媒體證明仍留在冒險者手機。", color = Moss)
                    ready && isAdventurer ->
                        Text("請將手機交給管理員查看證明，等待管理員刻印。")
                    ready && !isAdventurer && session.proofMode == QuestProofMode.IN_PERSON ->
                        Text("請先查看冒險者手機上的照片或影片，確認無誤後長按刻印。", color = Wine)
                    ready && !isAdventurer ->
                        Text("請核對任務與回報內容，確認無誤後長按刻印。")
                    else ->
                        Text("請將兩支手機靠近，保持本畫面開啟。")
                }
                if (ready && !isAdventurer) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .pointerInput(session.id) {
                                detectTapGestures(onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onManagerSeal()
                                })
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = Brass,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Approval, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("長按刻下公會印章", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (signed) "完成" else "中止感應") }
        }
    )
}

private fun formatCounterExpiry(expiresAtMillis: Long, language: AppLanguage): String {
    val remainingSeconds = ((expiresAtMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val value = "${minutes}:${seconds.toString().padStart(2, '0')}"
    return language.text("剩餘 $value", "$value remaining", "$value verbleibend", "残り $value")
}

@Composable
private fun AdminReviewScreen(state: GuildUiState, viewModel: GuildController) {
    val user = state.user ?: return
    val guild = state.activeGuild ?: return
    val language = LocalAppLanguage.current
    val canReviewQuests = user.hasGuildPermission(guild, GuildPermission.REVIEW_QUESTS)
    val canReviewNearbySubmissions = user.hasGuildPermission(guild, GuildPermission.REVIEW_NEARBY_SUBMISSIONS)
    val canReviewQuestSubmissions = canReviewQuests || canReviewNearbySubmissions || state.pendingSubmissions.isNotEmpty()
    val canReviewRedemptions = user.hasGuildPermission(guild, GuildPermission.REVIEW_REDEMPTIONS)
    val canReviewPenalties = user.hasGuildPermission(guild, GuildPermission.MANAGE_QUEST_PENALTIES)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val counterRequests = state.counterSessions.filter {
            it.status == GuildCounterSessionStatus.WAITING_FOR_COUNTERPART &&
                when (it.action) {
                    GuildCounterAction.ACCEPT_QUEST -> true
                    GuildCounterAction.SUBMIT_QUEST -> {
                        val quest = state.quests.firstOrNull { quest -> quest.id == it.questId }
                        quest != null && user.canReviewQuestSubmission(guild, quest)
                    }
                    GuildCounterAction.SETTLE_SUBMISSION -> false
                }
        }
        item { Text("櫃檯待辦", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("當面交付", counterRequests.size.toString(), Icons.Default.Sensors, Modifier.weight(1f))
                StatCard("任務回報", state.pendingSubmissions.size.toString(), Icons.Default.RateReview, Modifier.weight(1f))
                StatCard("兌換/處罰", (state.redemptions.size + state.pendingPenaltyRecords.size).toString(), Icons.Default.ReceiptLong, Modifier.weight(1f))
            }
        }
        if (counterRequests.isNotEmpty()) {
            item {
                Text("公會櫃檯", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            item {
                CounterSessionPanel(
                    sessions = counterRequests,
                    currentUser = user,
                    nearbyState = state.nearbyCounter,
                    onConfirm = viewModel::confirmCounterSession,
                    onCancel = viewModel::cancelCounterSession,
                    onStartNearby = viewModel::startNearbyCounter,
                    onConfirmNearby = viewModel::confirmNearbyCounter,
                    onStopNearby = viewModel::stopNearbyCounter
                )
            }
        }
        if (canReviewQuestSubmissions) {
            item { Text("任務回報審核", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.pendingSubmissions.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.RateReview,
                        title = "任務回報已清空",
                        body = "冒險者提交任務後，待審回報會出現在這裡。"
                    )
                }
            }
        }
        if (canReviewQuestSubmissions) items(state.pendingSubmissions, key = { it.id }) { submission ->
            var bonusGp by remember(submission.id) { mutableStateOf("0") }
            var bonusExp by remember(submission.id) { mutableStateOf("0") }
            GuildCard {
                Text(submission.questTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${submission.userName} · ${language.systemText(submission.proofMode.displayName)}")
                if (submission.proofMode != QuestProofMode.NONE) {
                    val note = submission.proofText.ifBlank { language.text("無", "None", "Keine", "なし") }
                    Text(language.text("回報備註：$note", "Report note: $note", "Berichtsnotiz: $note", "報告メモ：$note"))
                }
                if (submission.proofMode == QuestProofMode.IN_PERSON) {
                    Text("媒體證明已於 Nearby 櫃檯當面查看，未保存副本。", color = Moss, fontWeight = FontWeight.Bold)
                }
                if (submission.overachieved) {
                    val detail = submission.overachievementText.ifBlank {
                        language.text("未填寫超額說明", "No overachievement details", "Keine Details zur Mehrleistung", "超過達成の説明なし")
                    }
                    Text(language.text(
                        "超額提交：$detail",
                        "Overachievement: $detail",
                        "Mehrleistung: $detail",
                        "超過達成：$detail"
                    ), color = Wine, fontWeight = FontWeight.Bold)
                }
                Text(language.text(
                    "基本獎勵：+${submission.gpReward} GP / +${submission.expReward} EXP",
                    "Base reward: +${submission.gpReward} GP / +${submission.expReward} EXP",
                    "Grundbelohnung: +${submission.gpReward} GP / +${submission.expReward} EXP",
                    "基本報酬：+${submission.gpReward} GP / +${submission.expReward} EXP"
                ))
                if (submission.overachieved) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            bonusGp,
                            { bonusGp = it.filter(Char::isDigit) },
                            label = { Text("額外 GP") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            bonusExp,
                            { bonusExp = it.filter(Char::isDigit) },
                            label = { Text("額外 EXP") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        viewModel.reviewSubmission(
                            submission = submission,
                            approved = true,
                            bonusGp = bonusGp.toLongOrNull() ?: 0,
                            bonusExp = bonusExp.toLongOrNull() ?: 0
                        )
                    }) { Text("提出核准結算") }
                    OutlinedButton(onClick = { viewModel.reviewSubmission(submission, false, "證明不足") }) {
                        Text("提出退回")
                    }
                }
                Text("結算提出後，需冒險者在線確認才會更新 GP／EXP。", color = Moss)
            }
        }
        if (canReviewRedemptions) {
            item { Text("兌換審核", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.redemptions.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Store,
                        title = "目前沒有兌換申請",
                        body = "冒險者使用 GP 兌換獎勵後會在這裡等待核准。"
                    )
                }
            }
        }
        if (canReviewRedemptions) items(state.redemptions, key = { it.id }) { redemption ->
            GuildCard {
                Text(redemption.rewardName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${redemption.userName} · ${redemption.gpCost} GP")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.reviewRedemption(redemption, true) }) { Text("核准") }
                    OutlinedButton(onClick = { viewModel.reviewRedemption(redemption, false) }) { Text("拒絕") }
                }
            }
        }
        if (canReviewPenalties) {
            item { Text("未完成處罰確認", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.pendingPenaltyRecords.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Gavel,
                        title = "目前沒有待確認處罰",
                        body = "未完成且需要人工確認的處罰會集中在這裡。"
                    )
                }
            }
        }
        if (canReviewPenalties) items(state.pendingPenaltyRecords, key = { it.id }) { record ->
            GuildCard {
                Text(record.questTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${record.userName} · ${record.reason}")
                Text("週期：${record.cycleKey}", style = MaterialTheme.typography.bodySmall)
                Text("處罰：-${record.penaltyGp} GP / -${record.penaltyExp} EXP", color = Wine, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.reviewPenalty(record, true) }) { Text("確認扣除") }
                    OutlinedButton(onClick = { viewModel.reviewPenalty(record, false) }) { Text("免除") }
                }
            }
        }
    }
}

@Composable
private fun RewardShopScreen(state: GuildUiState, viewModel: GuildController) {
    val user = state.user ?: return
    val guild = state.activeGuild
    val canManageRewards = guild != null && user.hasGuildPermission(guild, GuildPermission.MANAGE_REWARDS)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (canManageRewards) {
            item { CreateRewardPanel(viewModel) }
        }
        if (state.rewards.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Store,
                    title = "獎勵商店尚未上架",
                    body = if (canManageRewards) "管理方可以新增 GP 兌換獎勵，冒險者之後就能在這裡兌換。" else "公會管理方上架獎勵後，這裡會顯示可兌換項目。"
                )
            }
        }
        items(state.rewards, key = { it.id }) { reward ->
            GuildCard {
                Text(reward.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(reward.description)
                Text("${reward.gpCost} GP", color = Moss, fontWeight = FontWeight.Bold)
                if (user.role == UserRole.ADVENTURER) {
                    Button(onClick = { viewModel.redeem(reward) }, enabled = user.gp >= reward.gpCost) {
                        Icon(Icons.Default.ShoppingBag, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("兌換")
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateRewardPanel(viewModel: GuildController) {
    var expanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("100") }
    GuildCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("新增獎勵", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FilledTonalButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (expanded) "收合" else "新增")
            }
        }
        if (!expanded) {
            Text("展開後可建立新的 GP 兌換項目。")
            return@GuildCard
        }
        OutlinedTextField(name, { name = it }, label = { Text("名稱") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cost, { cost = it.filter(Char::isDigit) }, label = { Text("GP 成本") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                viewModel.createReward(name, description, cost.toLongOrNull() ?: 0)
                name = ""
                description = ""
                cost = "100"
                expanded = false
            },
            enabled = name.isNotBlank()
        ) {
            Icon(Icons.Default.AddBusiness, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("上架")
        }
    }
}

@Composable
private fun RaidScreen(state: GuildUiState, viewModel: GuildController) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.raids, key = { it.id }) { raid ->
            GuildCard {
                val leaders = state.raidContributions
                    .filter { it.raidId == raid.id }
                    .groupBy { it.userName }
                    .mapValues { entry -> entry.value.sumOf { it.amount } }
                    .toList()
                    .sortedByDescending { it.second }
                    .take(5)
                Text(raid.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(raid.description)
                LinearProgressIndicator(
                    progress = { (raid.currentProgress.toFloat() / raid.targetProgress).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = Wine
                )
                Text("${raid.currentProgress} / ${raid.targetProgress}")
                if (leaders.isNotEmpty()) {
                    Text("貢獻排行榜", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    leaders.forEachIndexed { index, (name, amount) ->
                        Text("${index + 1}. $name · $amount")
                    }
                }
                Button(onClick = { viewModel.contributeToRaid(raid, 1) }) {
                    Icon(Icons.Default.Whatshot, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("增加 1 點貢獻")
                }
            }
        }
    }
}

@Composable
private fun <T> DropdownEnum(value: T, values: Iterable<T>, onChange: (T) -> Unit, label: (T) -> String) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label(value))
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach {
                DropdownMenuItem(text = { Text(label(it)) }, onClick = {
                    onChange(it)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun GuildCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E9)),
        border = BorderStroke(1.dp, Color(0xFFE1C98E))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun ErrorText(message: String?) {
    if (!message.isNullOrBlank()) {
        val language = LocalAppLanguage.current
        MaterialText(
            language.localizedErrorText(message),
            color = Wine,
            fontWeight = FontWeight.Bold
        )
    }
}
