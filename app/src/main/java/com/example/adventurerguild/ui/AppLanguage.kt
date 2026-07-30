package com.example.adventurerguild.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val compactLabel: String
) {
    TRADITIONAL_CHINESE("zh-TW", "繁體中文", "中"),
    ENGLISH("en", "English", "EN"),
    GERMAN("de", "Deutsch", "DE"),
    JAPANESE("ja", "日本語", "日")
}

object AppLanguageStore {
    private const val PREFS_NAME = "app_language_preferences"
    private const val KEY_LANGUAGE = "language"

    fun load(context: Context): AppLanguage {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return AppLanguage.entries.firstOrNull { it.code == saved }
            ?: AppLanguage.TRADITIONAL_CHINESE
    }

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
    }
}

val LocalAppLanguage = staticCompositionLocalOf {
    AppLanguage.TRADITIONAL_CHINESE
}

fun AppLanguage.text(
    zh: String,
    en: String,
    de: String,
    ja: String
): String = when (this) {
    AppLanguage.TRADITIONAL_CHINESE -> zh
    AppLanguage.ENGLISH -> en
    AppLanguage.GERMAN -> de
    AppLanguage.JAPANESE -> ja
}

private data class SystemTranslation(
    val en: String,
    val de: String,
    val ja: String
)

private fun translation(en: String, de: String, ja: String) =
    SystemTranslation(en = en, de = de, ja = ja)

private val systemTranslations = mapOf(
    "冒險者" to translation("Adventurer", "Abenteurer", "冒険者"),
    "管理方" to translation("Guild management", "Gildenverwaltung", "ギルド管理"),
    "公會會長" to translation("Guild master", "Gildenmeister", "ギルドマスター"),
    "副會長" to translation("Deputy guild master", "Stellvertretender Gildenmeister", "副ギルドマスター"),
    "任務官" to translation("Quest officer", "Auftragsoffizier", "クエスト担当"),
    "獎勵官" to translation("Reward officer", "Belohnungsoffizier", "報酬担当"),
    "討伐隊長" to translation("Raid captain", "Raid-Hauptmann", "討伐隊長"),
    "生活導師" to translation("Life mentor", "Alltagsmentor", "生活指導員"),
    "見習成員" to translation("Trainee member", "Mitglied in Ausbildung", "見習いメンバー"),
    "一般成員" to translation("Member", "Mitglied", "一般メンバー"),
    "管理成員" to translation("Management member", "Verwaltungsmitglied", "管理メンバー"),
    "公會管理員" to translation("Guild manager", "Gildenverwalter", "ギルド管理者"),
    "未指派" to translation("Unassigned", "Nicht zugewiesen", "未割り当て"),
    "公會成員" to translation("Guild members", "Gildenmitglieder", "ギルドメンバー"),
    "成員" to translation("Members", "Mitglieder", "メンバー"),
    "修改顯示名稱" to translation("Change display name", "Anzeigenamen ändern", "表示名を変更"),
    "名稱" to translation("Name", "Name", "名前"),
    "此名稱會顯示在你加入的所有公會。" to translation(
        "This name is shown in every guild you join.",
        "Dieser Name wird in allen deinen Gilden angezeigt.",
        "この名前は参加しているすべてのギルドで表示されます。"
    ),
    "儲存" to translation("Save", "Speichern", "保存"),
    "取消" to translation("Cancel", "Abbrechen", "キャンセル"),
    "建立測試公會" to translation("Create test guild", "Testgilde erstellen", "テストギルドを作成"),
    "替換常用碼" to translation("Replace permanent code", "Dauercode ersetzen", "常用コードを更新"),
    "一次性 QR" to translation("One-time QR", "Einmal-QR", "ワンタイムQR"),
    "替換常用邀請碼？" to translation(
        "Replace the permanent invite code?",
        "Dauerhaften Einladungscode ersetzen?",
        "常用招待コードを更新しますか？"
    ),
    "舊的邀請碼與 QR 會立即失效，尚未送出的加入連結也無法再使用。" to translation(
        "The old code, QR, and unsent join links will stop working immediately.",
        "Der alte Code, QR-Code und noch nicht versendete Beitrittslinks werden sofort ungültig.",
        "古いコード、QR、未送信の参加リンクはすぐに無効になります。"
    ),
    "確認替換" to translation("Replace", "Ersetzen", "更新する"),
    "確定" to translation("Confirm", "Bestätigen", "確定"),
    "公會管理方 · 不參與冒險者等級與任務接取" to translation(
        "Guild management · cannot earn adventurer ranks or accept quests",
        "Gildenverwaltung · nimmt nicht an Abenteurerrängen oder Aufträgen teil",
        "ギルド管理側・冒険者ランクやクエスト受注の対象外"
    ),
    "目前沒有可顯示的成員。" to translation(
        "No members to display.",
        "Keine Mitglieder zum Anzeigen.",
        "表示できるメンバーはいません。"
    ),
    "我的稱號" to translation("My title", "Mein Titel", "自分の称号"),
    "自訂稱號" to translation("Custom title", "Eigener Titel", "カスタム称号"),
    "更新稱號" to translation("Update title", "Titel aktualisieren", "称号を更新"),
    "返回成員" to translation("Back to members", "Zurück zu Mitgliedern", "メンバーへ戻る"),
    "帳號移機" to translation("Transfer account", "Konto übertragen", "アカウント移行"),
    "帳號以永久 UUID 保存。移機碼只能使用一次，並會在 10 分鐘後失效。" to translation(
        "Your account uses a permanent UUID. A transfer code works once and expires after 10 minutes.",
        "Das Konto verwendet eine dauerhafte UUID. Ein Übertragungscode gilt einmalig und läuft nach 10 Minuten ab.",
        "アカウントは永続UUIDで保存されます。移行コードは1回限りで、10分後に失効します。"
    ),
    "產生移機碼" to translation("Create transfer code", "Übertragungscode erstellen", "移行コードを作成"),
    "複製移機資料" to translation("Copy transfer details", "Übertragungsdaten kopieren", "移行情報をコピー"),
    "公會公告與休假" to translation("Guild notice and vacation", "Gildenankündigung und Urlaub", "ギルド告知と休暇"),
    "公會休假中：常態循環任務暫停處罰" to translation(
        "Guild vacation: recurring quest penalties are paused",
        "Gildenurlaub: Strafen für wiederkehrende Aufträge sind pausiert",
        "ギルド休暇中：定期クエストの罰則は停止中"
    ),
    "公會公告" to translation("Guild notice", "Gildenankündigung", "ギルド告知"),
    "發布公告" to translation("Publish notice", "Ankündigung veröffentlichen", "告知を公開"),
    "公會休假" to translation("Guild vacation", "Gildenurlaub", "ギルド休暇"),
    "休假說明" to translation("Vacation note", "Urlaubshinweis", "休暇メモ"),
    "儲存休假設定" to translation("Save vacation settings", "Urlaubseinstellungen speichern", "休暇設定を保存"),
    "加入申請" to translation("Join requests", "Beitrittsanfragen", "参加申請"),
    "目前沒有待審加入申請。" to translation(
        "No pending join requests.",
        "Keine offenen Beitrittsanfragen.",
        "審査待ちの参加申請はありません。"
    ),
    "拒絕" to translation("Reject", "Ablehnen", "拒否"),
    "核准" to translation("Approve", "Genehmigen", "承認"),
    "職務權限" to translation("Role permissions", "Rollenberechtigungen", "役職権限"),
    "先選擇一個職務，再設定它可以使用哪些操作。" to translation(
        "Select a role, then choose which actions it may use.",
        "Wähle eine Rolle und lege dann ihre erlaubten Aktionen fest.",
        "役職を選び、利用できる操作を設定します。"
    ),
    "冒險者分級名牌" to translation("Adventurer rank nameplates", "Abenteurer-Rangschilder", "冒険者ランク名札"),
    "公會長可修改各階級顯示名稱；材質名牌會依階級自動變豪華。" to translation(
        "The guild master can rename ranks; nameplates become more ornate at higher ranks.",
        "Der Gildenmeister kann Ränge umbenennen; höhere Ränge erhalten prächtigere Namensschilder.",
        "ギルドマスターはランク名を変更でき、上位ほど名札が豪華になります。"
    ),
    "指派職務" to translation("Assign role", "Rolle zuweisen", "役職を割り当て"),
    "移除會員" to translation("Remove member", "Mitglied entfernen", "メンバーを削除"),
    "事後獎懲修正" to translation("Adjust rewards or penalties", "Belohnung oder Strafe anpassen", "報酬・罰則を修正"),
    "修正原因" to translation("Reason for adjustment", "Grund der Anpassung", "修正理由"),
    "套用修正" to translation("Apply adjustment", "Anpassung anwenden", "修正を適用"),
    "任務執行狀況" to translation("Quest activity", "Auftragsstatus", "クエスト進行状況"),
    "目前沒有執行中、待審或需補件的任務。" to translation(
        "No quests are active, awaiting review, or awaiting revision.",
        "Keine aktiven, zu prüfenden oder zu ergänzenden Aufträge.",
        "進行中・審査待ち・要修正のクエストはありません。"
    ),
    "上架" to translation("Publish", "Veröffentlichen", "公開"),
    "下架" to translation("Unpublish", "Zurückziehen", "公開停止"),
    "存模板" to translation("Save template", "Vorlage speichern", "テンプレート保存"),
    "委託對象" to translation("Quest recipients", "Auftragsempfänger", "依頼対象"),
    "目前公會沒有可指定的冒險者。請先讓成員以冒險者身分加入這個公會。" to translation(
        "There are no adventurers to assign. Members must first join this guild as adventurers.",
        "Es gibt keine zuweisbaren Abenteurer. Mitglieder müssen der Gilde zuerst als Abenteurer beitreten.",
        "指定できる冒険者がいません。先に冒険者としてこのギルドへ参加してもらってください。"
    ),
    "全體冒險者" to translation("All adventurers", "Alle Abenteurer", "冒険者全員"),
    "改回全體冒險者" to translation("Assign to all adventurers", "Allen Abenteurern zuweisen", "冒険者全員に戻す"),
    "指定審核員" to translation("Assigned reviewers", "Zugewiesene Prüfer", "指定審査担当"),
    "目前沒有可指定的公會管理方成員。" to translation(
        "There are no guild managers to assign.",
        "Es gibt keine zuweisbaren Gildenverwalter.",
        "指定できるギルド管理メンバーがいません。"
    ),
    "依職務權限" to translation("Use role permissions", "Rollenberechtigungen verwenden", "役職権限に従う"),
    "篇章前置任務" to translation("Chapter prerequisites", "Kapitelvoraussetzungen", "篇章の前提クエスト"),
    "目前沒有可作為前置條件的任務。" to translation(
        "No quests can be used as prerequisites.",
        "Keine Aufträge können als Voraussetzung verwendet werden.",
        "前提条件にできるクエストはありません。"
    ),
    "無前置" to translation("No prerequisites", "Keine Voraussetzungen", "前提なし"),
    "戰團位置" to translation("Formation positions", "Trupppositionen", "戦団ポジション"),
    "每個位置可設定名額、完成獎勵與未完成處罰。" to translation(
        "Each position can have its own capacity, reward, and failure penalty.",
        "Jede Position kann eigene Plätze, Belohnungen und Strafen haben.",
        "各ポジションに定員、報酬、未完了時の罰則を設定できます。"
    ),
    "移除" to translation("Remove", "Entfernen", "削除"),
    "位置名稱" to translation("Position name", "Positionsname", "ポジション名"),
    "位置說明" to translation("Position description", "Positionsbeschreibung", "ポジション説明"),
    "名額" to translation("Capacity", "Plätze", "定員"),
    "扣 GP" to translation("GP penalty", "GP-Abzug", "GP減点"),
    "扣 EXP" to translation("EXP penalty", "EXP-Abzug", "EXP減点"),
    "開放冒險者自選" to translation("Allow self-selection", "Selbstauswahl erlauben", "冒険者の自己選択を許可"),
    "新增位置" to translation("Add position", "Position hinzufügen", "ポジションを追加"),
    "建立任務" to translation("Create quest", "Auftrag erstellen", "クエスト作成"),
    "收合" to translation("Collapse", "Einklappen", "閉じる"),
    "新增" to translation("Add", "Hinzufügen", "追加"),
    "展開後可建立任務或套用 CSV 模板。" to translation(
        "Expand to create a quest or apply a CSV template.",
        "Aufklappen, um einen Auftrag zu erstellen oder eine CSV-Vorlage anzuwenden.",
        "展開するとクエスト作成やCSVテンプレートの適用ができます。"
    ),
    "加入小朋友每日任務包" to translation(
        "Add children's daily quest pack",
        "Tägliches Aufgabenpaket für Kinder hinzufügen",
        "子ども向けデイリークエスト集を追加"
    ),
    "任務模板" to translation("Quest templates", "Auftragsvorlagen", "クエストテンプレート"),
    "套用模板" to translation("Apply template", "Vorlage anwenden", "テンプレートを適用"),
    "標題" to translation("Title", "Titel", "タイトル"),
    "描述" to translation("Description", "Beschreibung", "説明"),
    "完成證明" to translation("Completion proof", "Abschlussnachweis", "完了証明"),
    "需要 Nearby 當面提交" to translation(
        "Require in-person Nearby handoff",
        "Persönliche Nearby-Übergabe erforderlich",
        "Nearbyによる対面提出を必須にする"
    ),
    "勾選後，冒險者最後回報時才會出現 Nearby 交付；照片、影片或其他證明由管理員當面查看，不上傳雲端。" to translation(
        "When enabled, Nearby appears only at final submission. Photos, videos, and other proof are checked in person and are not uploaded.",
        "Wenn aktiviert, erscheint Nearby erst bei der Abgabe. Fotos, Videos und andere Nachweise werden persönlich geprüft und nicht hochgeladen.",
        "有効にすると最終報告時のみNearby提出が表示されます。写真・動画などは対面確認し、クラウドへはアップロードしません。"
    ),
    "標籤（用逗號分隔）" to translation("Tags (comma-separated)", "Tags (durch Kommas getrennt)", "タグ（カンマ区切り）"),
    "命運篇章適合做主線與功能解鎖；前置任務完成後才會開放接取。" to translation(
        "Fate Chapters are for story progression and feature unlocks; they open after prerequisites are complete.",
        "Schicksalskapitel dienen Story und Freischaltungen; sie öffnen nach Abschluss der Voraussetzungen.",
        "運命篇章はメインストーリーや機能解放向けで、前提クエスト完了後に受注できます。"
    ),
    "晉階試煉只適合 Rank 晉升；冒險者需先累積到下一階 EXP 門檻，通過審核後才會升階。" to translation(
        "Promotion Trials are only for rank promotion. The next EXP threshold and approval are both required.",
        "Aufstiegsprüfungen dienen nur dem Rangaufstieg. EXP-Schwelle und Genehmigung sind erforderlich.",
        "昇格試練はランク昇格専用です。次ランクのEXP条件を満たし、承認される必要があります。"
    ),
    "置頂任務" to translation("Pin quest", "Auftrag anheften", "クエストを固定"),
    "先存為待上架草稿" to translation("Save as draft first", "Zuerst als Entwurf speichern", "下書きとして保存"),
    "自動審核與自動處罰" to translation("Automatic review and penalties", "Automatische Prüfung und Strafen", "自動審査と自動罰則"),
    "回報後自動發放基本獎勵；超額提交不自動加算。到期未完成會自動扣除設定處罰，可由管理員事後修正。" to translation(
        "Base rewards are granted automatically after submission. Overachievement is excluded. Missed deadlines apply the configured penalty and can be adjusted later.",
        "Grundbelohnungen werden nach Abgabe automatisch vergeben. Mehrleistung wird nicht automatisch angerechnet. Versäumnisse lösen die festgelegte Strafe aus und können später angepasst werden.",
        "提出後に基本報酬を自動付与します。超過達成分は自動加算しません。期限切れの罰則は後から管理者が修正できます。"
    ),
    "任務時程" to translation("Quest schedule", "Auftragszeitplan", "クエスト日程"),
    "未完成處罰" to translation("Failure penalty", "Strafe bei Nichterfüllung", "未完了時の罰則"),
    "每日任務星期" to translation("Active days for daily quests", "Aktive Tage für tägliche Aufgaben", "デイリークエストの曜日"),
    "可選週一到週日任意組合；不選代表每天生效。" to translation(
        "Choose any combination of weekdays. No selection means every day.",
        "Beliebige Wochentage wählen. Ohne Auswahl gilt der Auftrag täglich.",
        "曜日を自由に組み合わせられます。未選択なら毎日有効です。"
    ),
    "每週結算/刷新日" to translation("Weekly settlement/reset day", "Wöchentlicher Abrechnungs-/Reset-Tag", "週間精算・更新日"),
    "每週任務一週只會刷新一次，因此只能選一天。預設週一 00:00 結算上一輪並開始新週期。" to translation(
        "Weekly quests reset once per week, so choose one day. Default: Monday 00:00.",
        "Wöchentliche Aufträge werden einmal pro Woche zurückgesetzt. Standard: Montag 00:00.",
        "ウィークリークエストは週1回のみ更新されます。既定は月曜00:00です。"
    ),
    "每月結算/刷新日" to translation("Monthly settlement/reset day", "Monatlicher Abrechnungs-/Reset-Tag", "月間精算・更新日"),
    "每月刷新日 1-31" to translation("Monthly reset day 1-31", "Monatlicher Reset-Tag 1-31", "月間更新日 1～31"),
    "若該月沒有指定日期，系統會改在該月最後一天結算。" to translation(
        "If the day does not exist in a month, settlement occurs on that month's last day.",
        "Existiert der Tag in einem Monat nicht, erfolgt die Abrechnung am letzten Monatstag.",
        "指定日がない月は、その月の最終日に精算します。"
    ),
    "常駐提交限制" to translation("Repeatable submission limit", "Limit für wiederholbare Abgaben", "常駐依頼の提出制限"),
    "常駐委託會顯示在待解清單，不需接取；每次完成都可提交，但同一時間只能有一筆待審回報。" to translation(
        "Repeatable quests stay in the available list and need no acceptance. Each completion may be submitted, with only one pending review at a time.",
        "Wiederholbare Aufträge bleiben verfügbar und müssen nicht angenommen werden. Jeweils nur eine Abgabe darf auf Prüfung warten.",
        "常駐依頼は未解決一覧に表示され、受注不要です。何度でも提出できますが、審査待ちは同時に1件までです。"
    ),
    "提交上限次數" to translation("Submission limit", "Abgabelimit", "提出上限回数"),
    "戰團編成設定" to translation("Formation settings", "Truppformation", "戦団編成設定"),
    "強制符合條件的冒險者參與" to translation(
        "Require all eligible adventurers to participate",
        "Teilnahme aller berechtigten Abenteurer verlangen",
        "条件を満たす冒険者全員を参加必須にする"
    ),
    "每人最少" to translation("Minimum per person", "Minimum pro Person", "1人あたり最少"),
    "每人最多" to translation("Maximum per person", "Maximum pro Person", "1人あたり最大"),
    "寬限與補交" to translation("Grace period and late submission", "Kulanz und Nachreichen", "猶予と再提出"),
    "適合限時活動、主線、支線、晉階等非固定週期任務；固定每日/每週/每月任務到期會直接換下一輪。" to translation(
        "Use for non-recurring event, story, side, and promotion quests. Daily, weekly, and monthly quests move directly to the next cycle.",
        "Für nicht periodische Event-, Story-, Neben- und Aufstiegsaufträge. Tägliche, wöchentliche und monatliche Aufträge wechseln direkt in den nächsten Zyklus.",
        "非定期のイベント・主線・支線・昇格クエスト向けです。日・週・月クエストは期限後すぐ次周期へ移ります。"
    ),
    "寬限天數" to translation("Grace days", "Kulanztage", "猶予日数"),
    "補交天數" to translation("Late-submission days", "Nachreichfrist in Tagen", "再提出日数"),
    "存為待上架" to translation("Save as draft", "Als Entwurf speichern", "下書き保存"),
    "上架任務" to translation("Publish quest", "Auftrag veröffentlichen", "クエスト公開"),
    "設定下個循環變更" to translation("Schedule next-cycle changes", "Änderung für nächsten Zyklus planen", "次周期の変更を設定"),
    "編輯任務" to translation("Edit quest", "Auftrag bearbeiten", "クエスト編集"),
    "此固定任務已公告；本次修改會先顯示在冒險者任務欄，並在下一個循環生效。" to translation(
        "This recurring quest is already announced. Changes will be shown now and take effect next cycle.",
        "Dieser wiederkehrende Auftrag ist bereits angekündigt. Änderungen werden angezeigt und gelten ab dem nächsten Zyklus.",
        "この定期クエストは告知済みです。変更内容を表示し、次周期から適用します。"
    ),
    "此任務尚未到公告時間，儲存後會直接更新任務設定。" to translation(
        "This quest has not reached its announcement time, so saving updates it immediately.",
        "Der Ankündigungszeitpunkt ist noch nicht erreicht; Speichern aktualisiert den Auftrag sofort.",
        "まだ告知前のため、保存すると設定が直ちに更新されます。"
    ),
    "固定週期任務無寬限或補交；時間一過就進入下一個週期。" to translation(
        "Recurring quests have no grace period or late submission and move directly to the next cycle.",
        "Wiederkehrende Aufträge haben keine Kulanz oder Nachreichfrist und wechseln direkt in den nächsten Zyklus.",
        "定期クエストに猶予・再提出はなく、期限後すぐ次周期へ移ります。"
    ),
    "執行人員" to translation("Participants", "Teilnehmer", "実行メンバー"),
    "目前沒有符合此任務條件的冒險者。" to translation(
        "No adventurers meet this quest's conditions.",
        "Keine Abenteurer erfüllen die Bedingungen dieses Auftrags.",
        "このクエストの条件を満たす冒険者はいません。"
    ),
    "Roll 未選者" to translation("Roll unassigned members", "Nicht zugewiesene Mitglieder auslosen", "未選択者を抽選"),
    "管理員檢視：此角色可建立、編輯與審核任務；任務回報需切換 Adventurer 身分進行。" to translation(
        "Manager view: this role can create, edit, and review quests. Switch to Adventurer to submit a quest.",
        "Verwaltungsansicht: Diese Rolle kann Aufträge erstellen, bearbeiten und prüfen. Zum Abgeben zur Abenteurerrolle wechseln.",
        "管理画面：クエストの作成・編集・審査ができます。提出は冒険者側へ切り替えてください。"
    ),
    "超額提交" to translation("Overachievement", "Mehrleistung", "超過達成"),
    "超額內容，例如：目標 60 分，實際 90 分" to translation(
        "Overachievement details, e.g. target 60, actual 90",
        "Details zur Mehrleistung, z. B. Ziel 60, erreicht 90",
        "超過達成の内容（例：目標60点、実績90点）"
    ),
    "額外獎勵由公會管理員審核後決定。" to translation(
        "Extra rewards are decided during guild review.",
        "Zusatzbelohnungen werden bei der Gildenprüfung festgelegt.",
        "追加報酬はギルド管理者の審査で決定します。"
    ),
    "本任務不需附證明，提交後會等待公會審核。" to translation(
        "This quest needs no proof. Submission will await guild review.",
        "Für diesen Auftrag ist kein Nachweis erforderlich. Die Abgabe wartet auf die Gildenprüfung.",
        "このクエストは証明不要です。提出後はギルド審査を待ちます。"
    ),
    "文字回報會直接提交給公會審核，不需要 Nearby。" to translation(
        "Text reports go directly to guild review and do not use Nearby.",
        "Textberichte gehen direkt zur Gildenprüfung und benötigen kein Nearby.",
        "テキスト報告は直接ギルド審査へ送られ、Nearbyは不要です。"
    ),
    "Rank 不足" to translation("Rank too low", "Rang zu niedrig", "ランク不足"),
    "尚未開放回報" to translation("Submission not open", "Abgabe noch nicht geöffnet", "報告受付前"),
    "尚未開放接取" to translation("Acceptance not open", "Annahme noch nicht geöffnet", "受注開始前"),
    "已提交待審" to translation("Submitted for review", "Zur Prüfung eingereicht", "審査待ち"),
    "本輪已完成" to translation("Completed this cycle", "In diesem Zyklus erledigt", "今周期は完了済み"),
    "已達提交上限" to translation("Submission limit reached", "Abgabelimit erreicht", "提出上限に到達"),
    "尚未解鎖" to translation("Not unlocked", "Noch nicht freigeschaltet", "未解放"),
    "未達晉階門檻" to translation("Promotion threshold not met", "Aufstiegsschwelle nicht erreicht", "昇格条件未達"),
    "開啟 Nearby 交付" to translation("Open Nearby handoff", "Nearby-Übergabe öffnen", "Nearby提出を開く"),
    "提交回報" to translation("Submit report", "Bericht abgeben", "報告を提出"),
    "前往櫃檯接取" to translation("Accept at guild counter", "Am Gildenschalter annehmen", "受付で受注"),
    "關閉" to translation("Close", "Schließen", "閉じる"),
    "確認結算" to translation("Confirm settlement", "Abrechnung bestätigen", "精算を確認"),
    "開啟近距離簽核" to translation("Open nearby approval", "Nahbereichsfreigabe öffnen", "近距離承認を開く"),
    "舉起手機交付" to translation("Raise phone for handoff", "Telefon zur Übergabe anheben", "スマートフォンをかざして提出"),
    "取消交付" to translation("Cancel handoff", "Übergabe abbrechen", "提出を中止"),
    "雙方裝置已完成近距離簽核。媒體證明仍留在冒險者手機。" to translation(
        "Nearby approval is complete on both devices. Media proof remains on the adventurer's phone.",
        "Die Nahbereichsfreigabe ist auf beiden Geräten abgeschlossen. Mediendaten bleiben auf dem Telefon des Abenteurers.",
        "両端末の近距離承認が完了しました。メディア証明は冒険者の端末に残ります。"
    ),
    "請將手機交給管理員查看證明，等待管理員刻印。" to translation(
        "Show the proof to the manager and wait for the guild seal.",
        "Zeige den Nachweis der Verwaltung und warte auf das Gildensiegel.",
        "管理者に証明を見せ、ギルド印を待ってください。"
    ),
    "請先查看冒險者手機上的照片或影片，確認無誤後長按刻印。" to translation(
        "Check the photo or video on the adventurer's phone, then hold to apply the seal.",
        "Prüfe Foto oder Video auf dem Telefon des Abenteurers und halte dann zum Siegeln gedrückt.",
        "冒険者の端末で写真・動画を確認し、問題なければ長押しで印を刻んでください。"
    ),
    "請核對任務與回報內容，確認無誤後長按刻印。" to translation(
        "Check the quest and report, then hold to apply the seal.",
        "Prüfe Auftrag und Bericht und halte dann zum Siegeln gedrückt.",
        "クエストと報告内容を確認し、長押しで印を刻んでください。"
    ),
    "請將兩支手機靠近，保持本畫面開啟。" to translation(
        "Keep both phones close with this screen open.",
        "Halte beide Telefone nahe beieinander und lasse diesen Bildschirm geöffnet.",
        "2台の端末を近づけ、この画面を開いたままにしてください。"
    ),
    "長按刻下公會印章" to translation("Hold to apply guild seal", "Halten, um das Gildensiegel anzubringen", "長押しでギルド印を刻む"),
    "完成" to translation("Done", "Fertig", "完了"),
    "中止感應" to translation("Stop sensing", "Erkennung beenden", "検出を中止"),
    "櫃檯待辦" to translation("Guild counter queue", "Warteschlange am Gildenschalter", "ギルド受付の待機項目"),
    "公會櫃檯" to translation("Guild counter", "Gildenschalter", "ギルド受付"),
    "當面交付" to translation("In-person handoff", "Persönliche Übergabe", "対面提出"),
    "任務回報" to translation("Quest reports", "Auftragsberichte", "クエスト報告"),
    "兌換/處罰" to translation("Redemptions / penalties", "Einlösungen / Strafen", "交換・罰則"),
    "任務回報審核" to translation("Quest report review", "Prüfung von Auftragsberichten", "クエスト報告審査"),
    "任務回報已清空" to translation("No quest reports pending", "Keine Auftragsberichte offen", "審査待ち報告なし"),
    "冒險者提交任務後，待審回報會出現在這裡。" to translation(
        "Submitted quest reports appear here for review.",
        "Eingereichte Auftragsberichte erscheinen hier zur Prüfung.",
        "提出されたクエスト報告はここに表示されます。"
    ),
    "目前沒有兌換申請" to translation("No redemption requests", "Keine Einlösungsanfragen", "交換申請なし"),
    "冒險者使用 GP 兌換獎勵後會在這裡等待核准。" to translation(
        "GP redemption requests wait here for approval.",
        "GP-Einlösungen warten hier auf Genehmigung.",
        "GP交換申請はここで承認を待ちます。"
    ),
    "目前沒有待確認處罰" to translation("No penalties awaiting confirmation", "Keine Strafen zur Bestätigung", "確認待ち罰則なし"),
    "未完成且需要人工確認的處罰會集中在這裡。" to translation(
        "Failure penalties requiring manual confirmation appear here.",
        "Manuell zu bestätigende Nichterfüllungsstrafen erscheinen hier.",
        "手動確認が必要な未完了罰則はここに表示されます。"
    ),
    "獎勵商店尚未上架" to translation("Reward shop is empty", "Belohnungsladen ist leer", "報酬ショップは準備中"),
    "管理方可以新增 GP 兌換獎勵，冒險者之後就能在這裡兌換。" to translation(
        "Guild management can add GP rewards for adventurers to redeem here.",
        "Die Gildenverwaltung kann GP-Belohnungen hinzufügen, die hier eingelöst werden.",
        "管理側がGP交換報酬を追加すると、冒険者がここで交換できます。"
    ),
    "公會管理方上架獎勵後，這裡會顯示可兌換項目。" to translation(
        "Published guild rewards appear here.",
        "Veröffentlichte Gildenbelohnungen erscheinen hier.",
        "公開されたギルド報酬がここに表示されます。"
    ),
    "媒體證明已於 Nearby 櫃檯當面查看，未保存副本。" to translation(
        "Media proof was checked in person through Nearby; no copy was stored.",
        "Der Mediennachweis wurde persönlich über Nearby geprüft; es wurde keine Kopie gespeichert.",
        "メディア証明はNearby受付で対面確認し、コピーは保存していません。"
    ),
    "額外 GP" to translation("Extra GP", "Zusätzliche GP", "追加GP"),
    "額外 EXP" to translation("Extra EXP", "Zusätzliche EXP", "追加EXP"),
    "提出核准結算" to translation("Propose approval settlement", "Genehmigungsabrechnung vorschlagen", "承認精算を提案"),
    "提出退回" to translation("Return for revision", "Zur Überarbeitung zurückgeben", "差し戻す"),
    "結算提出後，需冒險者在線確認才會更新 GP／EXP。" to translation(
        "After settlement is proposed, the adventurer must confirm online before GP/EXP changes.",
        "Nach dem Abrechnungsvorschlag muss der Abenteurer online bestätigen, bevor GP/EXP geändert werden.",
        "精算提案後、冒険者がオンラインで確認するとGP/EXPが更新されます。"
    ),
    "兌換審核" to translation("Redemption review", "Prüfung von Einlösungen", "交換審査"),
    "未完成處罰確認" to translation("Failure penalty review", "Prüfung der Nichterfüllungsstrafe", "未完了罰則の確認"),
    "確認扣除" to translation("Confirm deduction", "Abzug bestätigen", "減点を確定"),
    "免除" to translation("Waive", "Erlassen", "免除"),
    "兌換" to translation("Redeem", "Einlösen", "交換"),
    "新增獎勵" to translation("Add reward", "Belohnung hinzufügen", "報酬を追加"),
    "展開後可建立新的 GP 兌換項目。" to translation(
        "Expand to create a new GP reward.",
        "Aufklappen, um eine neue GP-Belohnung zu erstellen.",
        "展開すると新しいGP交換アイテムを作成できます。"
    ),
    "GP 成本" to translation("GP cost", "GP-Kosten", "必要GP"),
    "簡單" to translation("Easy", "Einfach", "簡単"),
    "普通" to translation("Normal", "Normal", "普通"),
    "困難" to translation("Hard", "Schwer", "難しい"),
    "傳說" to translation("Legendary", "Legendär", "伝説"),
    "F 級冒險者" to translation("F-rank adventurer", "Abenteurer Rang F", "F級冒険者"),
    "E 級冒險者" to translation("E-rank adventurer", "Abenteurer Rang E", "E級冒険者"),
    "D 級冒險者" to translation("D-rank adventurer", "Abenteurer Rang D", "D級冒険者"),
    "C 級冒險者" to translation("C-rank adventurer", "Abenteurer Rang C", "C級冒険者"),
    "B 級冒險者" to translation("B-rank adventurer", "Abenteurer Rang B", "B級冒険者"),
    "A 級冒險者" to translation("A-rank adventurer", "Abenteurer Rang A", "A級冒険者"),
    "S 級冒險者" to translation("S-rank adventurer", "Abenteurer Rang S", "S級冒険者"),
    "不需證明" to translation("No proof", "Kein Nachweis", "証明不要"),
    "提交完成即可，由公會依任務結果認定" to translation(
        "Submit completion; the guild decides from the result.",
        "Abschluss melden; die Gilde entscheidet anhand des Ergebnisses.",
        "完了を提出し、結果に基づいてギルドが認定します。"
    ),
    "文字回報" to translation("Text report", "Textbericht", "テキスト報告"),
    "只同步文字說明，不上傳照片或影片" to translation(
        "Sync text only; photos and videos are not uploaded.",
        "Nur Text wird synchronisiert; Fotos und Videos werden nicht hochgeladen.",
        "テキストのみ同期し、写真や動画はアップロードしません。"
    ),
    "當面查看" to translation("In-person check", "Persönliche Prüfung", "対面確認"),
    "照片或影片保留在冒險者裝置，由管理員當面查看後認定" to translation(
        "Photos and videos stay on the adventurer's device and are checked in person.",
        "Fotos und Videos bleiben auf dem Gerät des Abenteurers und werden persönlich geprüft.",
        "写真や動画は冒険者の端末に残し、管理者が対面で確認します。"
    ),
    "無限制" to translation("Unlimited", "Unbegrenzt", "無制限"),
    "每日上限" to translation("Daily limit", "Tageslimit", "1日上限"),
    "每週上限" to translation("Weekly limit", "Wochenlimit", "週間上限"),
    "每月上限" to translation("Monthly limit", "Monatslimit", "月間上限"),
    "總次數上限" to translation("Total limit", "Gesamtlimit", "合計上限"),
    "自選優先，截止後 Roll" to translation(
        "Self-select first, then roll at deadline",
        "Zuerst selbst wählen, dann nach Frist auslosen",
        "自己選択を優先し、締切後に抽選"
    ),
    "全員立即 Roll" to translation("Roll everyone immediately", "Alle sofort auslosen", "全員を即時抽選"),
    "管理員手動 Roll" to translation("Manager rolls manually", "Verwaltung lost manuell aus", "管理者が手動抽選"),
    "自選但不強制" to translation("Optional self-selection", "Freiwillige Selbstauswahl", "任意の自己選択"),
    "審核加入人員" to translation("Review join requests", "Beitrittsanfragen prüfen", "参加申請を審査"),
    "發布任務" to translation("Publish quests", "Aufträge veröffentlichen", "クエストを公開"),
    "審查任務" to translation("Review quest reports", "Auftragsberichte prüfen", "クエスト報告を審査"),
    "審核 Nearby 提交" to translation("Review Nearby submissions", "Nearby-Abgaben prüfen", "Nearby提出を審査"),
    "兌換獎勵" to translation("Review redemptions", "Einlösungen prüfen", "報酬交換を審査"),
    "發布公會公告" to translation("Post guild notices", "Gildenankündigungen veröffentlichen", "ギルド告知を公開"),
    "設定公會休假" to translation("Set guild vacation", "Gildenurlaub festlegen", "ギルド休暇を設定"),
    "指派職務" to translation("Assign roles", "Rollen zuweisen", "役職を割り当て"),
    "修改職務權限" to translation("Edit role permissions", "Rollenberechtigungen bearbeiten", "役職権限を編集"),
    "編輯任務" to translation("Edit quests", "Aufträge bearbeiten", "クエストを編集"),
    "下架/取消任務" to translation("Unpublish or cancel quests", "Aufträge zurückziehen oder stornieren", "クエストを非公開・取消"),
    "查看所有成員任務進度" to translation("View all member quest progress", "Auftragsfortschritt aller Mitglieder sehen", "全メンバーの進行状況を表示"),
    "查看操作紀錄" to translation("View audit log", "Aktivitätsprotokoll anzeigen", "操作履歴を表示"),
    "設定強制任務" to translation("Configure mandatory quests", "Pflichtaufträge konfigurieren", "必須クエストを設定"),
    "設定任務處罰" to translation("Configure quest penalties", "Auftragsstrafen konfigurieren", "クエスト罰則を設定"),
    "管理任務模板" to translation("Manage quest templates", "Auftragsvorlagen verwalten", "テンプレートを管理"),
    "匯入/匯出任務模板 CSV" to translation("Import/export quest template CSV", "Auftragsvorlagen als CSV importieren/exportieren", "テンプレートCSVを入出力"),
    "調整超額提交獎勵" to translation("Adjust overachievement rewards", "Belohnungen für Mehrleistung anpassen", "超過達成報酬を調整"),
    "手動補發/扣除 GP EXP" to translation("Manually adjust GP/EXP", "GP/EXP manuell anpassen", "GP/EXPを手動調整"),
    "查看獎懲紀錄" to translation("View reward and penalty log", "Belohnungs- und Strafprotokoll anzeigen", "報酬・罰則履歴を表示"),
    "建立/編輯/上下架獎勵" to translation("Create, edit, or publish rewards", "Belohnungen erstellen, bearbeiten oder veröffentlichen", "報酬を作成・編集・公開"),
    "標記獎勵已發放" to translation("Mark rewards as delivered", "Belohnungen als ausgegeben markieren", "報酬を配布済みにする"),
    "管理獎勵庫存" to translation("Manage reward stock", "Belohnungsbestand verwalten", "報酬在庫を管理"),
    "退還兌換 GP" to translation("Refund redemption GP", "GP für Einlösung erstatten", "交換GPを返金"),
    "設定活動期間" to translation("Set event periods", "Eventzeiträume festlegen", "イベント期間を設定"),
    "設定刷新規則" to translation("Set reset rules", "Reset-Regeln festlegen", "更新ルールを設定"),
    "設定特殊補償日" to translation("Set compensation days", "Ausgleichstage festlegen", "特別補償日を設定"),
    "發送提醒通知" to translation("Send reminders", "Erinnerungen senden", "リマインダーを送信"),
    "建立/編輯討伐戰" to translation("Create or edit guild raids", "Gildenraids erstellen oder bearbeiten", "ギルド討伐戦を作成・編集"),
    "結算討伐戰" to translation("Settle guild raids", "Gildenraids abrechnen", "ギルド討伐戦を精算"),
    "調整個人貢獻值" to translation("Adjust personal contributions", "Persönliche Beiträge anpassen", "個人貢献値を調整"),
    "查看貢獻排行榜" to translation("View contribution leaderboard", "Beitragsrangliste anzeigen", "貢献ランキングを表示"),
    "匯出公會資料" to translation("Export guild data", "Gildendaten exportieren", "ギルドデータを出力"),
    "匯入公會資料" to translation("Import guild data", "Gildendaten importieren", "ギルドデータを取込"),
    "管理備份" to translation("Manage backups", "Sicherungen verwalten", "バックアップを管理"),
    "還原誤操作" to translation("Restore mistaken changes", "Fehländerungen wiederherstellen", "誤操作を復元"),
    "管理公會基本設定" to translation("Manage guild settings", "Gildeneinstellungen verwalten", "ギルド基本設定を管理"),
    "解散公會" to translation("Disband guild", "Gilde auflösen", "ギルドを解散"),
    "增加 1 點貢獻" to translation("Add 1 contribution", "1 Beitrag hinzufügen", "貢献を1追加"),
    "自訂或選擇職務" to translation("Choose or enter a role", "Rolle wählen oder eingeben", "役職を選択または入力"),
    "複製" to translation("Duplicate", "Duplizieren", "複製"),
    "變更內容提示" to translation("Change summary", "Änderungsübersicht", "変更内容の概要"),
    "貢獻排行榜" to translation("Contribution leaderboard", "Beitragsrangliste", "貢献ランキング"),
    "限時討伐令是活動任務：必須設定結束日期，過期後封存，不產生未完成處罰，也不開放補交。" to translation(
        "Limited Events require an end date. They are archived at expiry with no failure penalty or late submission.",
        "Zeitlich begrenzte Events benötigen ein Enddatum und werden danach ohne Strafe oder Nachreichen archiviert.",
        "期間限定クエストには終了日が必要です。期限後は罰則・再提出なしでアーカイブされます。"
    )
)

fun AppLanguage.systemText(source: String): String {
    if (this == AppLanguage.TRADITIONAL_CHINESE) return source
    val translation = systemTranslations[source] ?: return source
    return translation.forLanguage(this)
}

private val errorTranslations = mapOf(
    "正式版本不提供測試帳號。" to translation(
        "Test accounts are unavailable in release builds.",
        "Testkonten sind in Release-Versionen nicht verfügbar.",
        "正式版ではテストアカウントを利用できません。"
    ),
    "請先登出目前帳號再進行移機。" to translation(
        "Sign out of the current account before transferring.",
        "Melde dich vor der Übertragung vom aktuellen Konto ab.",
        "移行する前に現在のアカウントからログアウトしてください。"
    ),
    "帳號 UUID 與移機碼不可空白。" to translation(
        "Account UUID and transfer code are required.",
        "Konto-UUID und Übertragungscode sind erforderlich.",
        "アカウントUUIDと移行コードを入力してください。"
    ),
    "名稱需為 2 至 40 個字元。" to translation(
        "The name must contain 2 to 40 characters.",
        "Der Name muss 2 bis 40 Zeichen lang sein.",
        "名前は2～40文字で入力してください。"
    ),
    "尚未設定雲端服務位址，請在 private.properties 設定 CLOUDFLARE_API_BASE_URL。" to translation(
        "The cloud service URL is not configured.",
        "Die URL des Cloud-Dienstes ist nicht konfiguriert.",
        "クラウドサービスのURLが設定されていません。"
    ),
    "任務不屬於目前公會。" to translation(
        "This quest does not belong to the current guild.",
        "Dieser Auftrag gehört nicht zur aktuellen Gilde.",
        "このクエストは現在のギルドに属していません。"
    ),
    "任務標題不可空白。" to translation(
        "Quest title is required.",
        "Ein Auftragstitel ist erforderlich.",
        "クエスト名を入力してください。"
    ),
    "任務獎勵不可為負數。" to translation(
        "Quest rewards cannot be negative.",
        "Auftragsbelohnungen dürfen nicht negativ sein.",
        "クエスト報酬を負の値にはできません。"
    ),
    "未完成處罰不可為負數。" to translation(
        "Failure penalties cannot be negative.",
        "Strafen dürfen nicht negativ sein.",
        "未達成時のペナルティを負の値にはできません。"
    ),
    "每日任務星期必須介於週一到週日。" to translation(
        "Daily quest weekdays must be Monday through Sunday.",
        "Wochentage für tägliche Aufträge müssen Montag bis Sonntag sein.",
        "デイリークエストの曜日は月曜から日曜の範囲で指定してください。"
    ),
    "每週任務只能選擇一個週一到週日的刷新日。" to translation(
        "A weekly quest needs one reset day from Monday through Sunday.",
        "Ein Wochenauftrag benötigt genau einen Reset-Tag von Montag bis Sonntag.",
        "ウィークリークエストの更新曜日は月曜から日曜のうち1日を指定してください。"
    ),
    "每月任務刷新日必須介於 1 到 31 日。" to translation(
        "A monthly quest reset day must be between 1 and 31.",
        "Der monatliche Reset-Tag muss zwischen 1 und 31 liegen.",
        "マンスリークエストの更新日は1日から31日の範囲で指定してください。"
    ),
    "公告日期不可晚於開放接取日期。" to translation(
        "Announcement time cannot be later than the acceptance time.",
        "Die Ankündigung darf nicht nach dem Annahmebeginn liegen.",
        "告知日時を受注開始日時より後には設定できません。"
    ),
    "任務開始日期不可晚於結束日期。" to translation(
        "Quest start time cannot be later than its end time.",
        "Der Auftragsbeginn darf nicht nach dem Ende liegen.",
        "クエスト開始日時を終了日時より後には設定できません。"
    ),
    "開放接取日期不可晚於任務結束日期。" to translation(
        "Acceptance time cannot be later than the quest end time.",
        "Der Annahmebeginn darf nicht nach dem Auftragsende liegen.",
        "受注開始日時をクエスト終了日時より後には設定できません。"
    ),
    "限時討伐令必須設定活動結束日期。" to translation(
        "A Limited Event requires an end date.",
        "Ein zeitlich begrenztes Event benötigt ein Enddatum.",
        "期間限定クエストには終了日時が必要です。"
    ),
    "晉階試煉不可使用自動審核。" to translation(
        "Promotion Trials cannot use automatic review.",
        "Aufstiegsprüfungen können nicht automatisch geprüft werden.",
        "昇格試練では自動審査を利用できません。"
    ),
    "指名對象必須是此公會的冒險者。" to translation(
        "Assigned adventurers must belong to this guild.",
        "Zugewiesene Abenteurer müssen dieser Gilde angehören.",
        "指名対象はこのギルドの冒険者である必要があります。"
    ),
    "指定審核者必須是此公會管理方。" to translation(
        "Assigned reviewers must be managers of this guild.",
        "Zugewiesene Prüfer müssen zur Verwaltung dieser Gilde gehören.",
        "指定審査担当者はこのギルドの管理メンバーである必要があります。"
    ),
    "設定提交上限時，次數必須大於 0。" to translation(
        "A submission limit must be greater than zero.",
        "Ein Abgabelimit muss größer als null sein.",
        "提出上限は1回以上に設定してください。"
    ),
    "戰團編成令至少需要一個位置。" to translation(
        "A Formation Order needs at least one position.",
        "Ein Formationsauftrag benötigt mindestens eine Position.",
        "戦団編成令には1つ以上の担当枠が必要です。"
    ),
    "任務尚未上架。" to translation(
        "This quest is not published yet.",
        "Dieser Auftrag ist noch nicht veröffentlicht.",
        "このクエストはまだ公開されていません。"
    ),
    "任務尚未公告。" to translation(
        "This quest has not been announced yet.",
        "Dieser Auftrag wurde noch nicht angekündigt.",
        "このクエストはまだ告知されていません。"
    ),
    "任務尚未開放接取。" to translation(
        "This quest is not open for acceptance yet.",
        "Dieser Auftrag kann noch nicht angenommen werden.",
        "このクエストはまだ受注できません。"
    ),
    "任務尚未開始。" to translation(
        "This quest has not started yet.",
        "Dieser Auftrag hat noch nicht begonnen.",
        "このクエストはまだ開始されていません。"
    ),
    "任務已過期。" to translation(
        "This quest has expired.",
        "Dieser Auftrag ist abgelaufen.",
        "このクエストは期限切れです。"
    ),
    "只有冒險者可以接取任務。" to translation(
        "Only adventurers can accept quests.",
        "Nur Abenteurer können Aufträge annehmen.",
        "クエストを受注できるのは冒険者だけです。"
    ),
    "只有冒險者可以提交任務。" to translation(
        "Only adventurers can submit quests.",
        "Nur Abenteurer können Aufträge abgeben.",
        "クエストを提出できるのは冒険者だけです。"
    ),
    "每日、每週與每月任務是強制任務，不需要接取。" to translation(
        "Daily, weekly, and monthly quests are mandatory and do not need to be accepted.",
        "Tägliche, wöchentliche und monatliche Aufträge sind verpflichtend und müssen nicht angenommen werden.",
        "デイリー・ウィークリー・マンスリークエストは必須のため受注操作は不要です。"
    ),
    "此任務已指名給其他冒險者。" to translation(
        "This quest is assigned to another adventurer.",
        "Dieser Auftrag ist einem anderen Abenteurer zugewiesen.",
        "このクエストは別の冒険者に指名されています。"
    ),
    "你已經接取此任務。" to translation(
        "You have already accepted this quest.",
        "Du hast diesen Auftrag bereits angenommen.",
        "このクエストはすでに受注しています。"
    ),
    "Rank 不足，無法接取此任務。" to translation(
        "Your rank is too low to accept this quest.",
        "Dein Rang ist zu niedrig, um diesen Auftrag anzunehmen.",
        "ランクが不足しているため、このクエストを受注できません。"
    ),
    "此任務需要填寫文字回報。" to translation(
        "This quest requires a text report.",
        "Dieser Auftrag erfordert einen Textbericht.",
        "このクエストにはテキスト報告が必要です。"
    ),
    "此任務已有待審回報，請等管理員審核後再提交。" to translation(
        "This quest already has a pending report. Wait for review before submitting again.",
        "Für diesen Auftrag wartet bereits ein Bericht auf Prüfung.",
        "このクエストには審査待ちの報告があります。審査後に再提出してください。"
    ),
    "GP 不足，無法兌換。" to translation(
        "You do not have enough GP for this reward.",
        "Du hast nicht genügend GP für diese Belohnung.",
        "この報酬の交換に必要なGPが不足しています。"
    ),
    "account transfer code is required" to translation(
        "An account transfer code is required.",
        "Ein Konto-Übertragungscode ist erforderlich.",
        "アカウント移行コードが必要です。"
    ),
    "account transfer code is invalid or expired" to translation(
        "The account transfer code is invalid or expired.",
        "Der Konto-Übertragungscode ist ungültig oder abgelaufen.",
        "アカウント移行コードが無効または期限切れです。"
    ),
    "account transfer code is invalid, expired, or already used" to translation(
        "The account transfer code is invalid, expired, or already used.",
        "Der Konto-Übertragungscode ist ungültig, abgelaufen oder wurde bereits verwendet.",
        "アカウント移行コードが無効、期限切れ、または使用済みです。"
    ),
    "device is already bound to another account" to translation(
        "This device is already bound to another account.",
        "Dieses Gerät ist bereits mit einem anderen Konto verbunden.",
        "この端末はすでに別のアカウントに紐付いています。"
    ),
    "device has been revoked" to translation(
        "This device has been revoked.",
        "Dieses Gerät wurde widerrufen.",
        "この端末は無効化されています。"
    ),
    "request timestamp is outside the allowed window" to translation(
        "The device clock differs too much from the server. Check date and time settings.",
        "Die Gerätezeit weicht zu stark vom Server ab. Prüfe Datum und Uhrzeit.",
        "端末時刻がサーバーと大きくずれています。日時設定を確認してください。"
    ),
    "request nonce has already been used" to translation(
        "This request has already been processed. Refresh and try again.",
        "Diese Anfrage wurde bereits verarbeitet. Aktualisiere und versuche es erneut.",
        "このリクエストは処理済みです。更新してから再試行してください。"
    ),
    "guild membership permission denied" to translation(
        "You do not have access to this guild.",
        "Du hast keinen Zugriff auf diese Gilde.",
        "このギルドにアクセスする権限がありません。"
    ),
    "manager role permission denied" to translation(
        "Your guild role cannot perform this action.",
        "Deine Gildenrolle darf diese Aktion nicht ausführen.",
        "現在のギルド役職ではこの操作を実行できません。"
    )
)

fun AppLanguage.localizedErrorText(source: String): String {
    if (this == AppLanguage.TRADITIONAL_CHINESE) return source
    val cloudflareMatch = Regex("""^Cloudflare API (\d+):\s*(.*)$""").matchEntire(source)
    val sourceMessage = cloudflareMatch?.groupValues?.get(2) ?: source
    val translated = errorTranslations[sourceMessage]?.forLanguage(this)
        ?: localizePermissionError(sourceMessage)
        ?: systemText(sourceMessage)
    return if (cloudflareMatch == null) {
        translated
    } else {
        val status = cloudflareMatch.groupValues[1]
        text(
            "雲端服務 $status：$translated",
            "Cloud service $status: $translated",
            "Cloud-Dienst $status: $translated",
            "クラウドサービス $status：$translated"
        )
    }
}

private fun AppLanguage.localizePermissionError(source: String): String? {
    if (!source.startsWith("沒有") || !source.endsWith("權限。")) return null
    val permission = source.removePrefix("沒有").removeSuffix("權限。")
    val translatedPermission = systemText(permission)
    return text(
        source,
        "Missing permission: $translatedPermission.",
        "Fehlende Berechtigung: $translatedPermission.",
        "権限がありません：$translatedPermission。"
    )
}

private fun SystemTranslation.forLanguage(language: AppLanguage): String =
    when (language) {
        AppLanguage.TRADITIONAL_CHINESE -> en
        AppLanguage.ENGLISH -> en
        AppLanguage.GERMAN -> de
        AppLanguage.JAPANESE -> ja
    }
