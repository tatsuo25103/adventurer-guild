# Abenteurergilde

**[繁體中文](README.md)** · [English](README.en.md) · Deutsch · [日本語](README.ja.md)

Abenteurergilde ist eine Android-App, die Alltagsroutinen, Lernziele und gemeinsame Aufgaben in RPG-ähnliche Gildenquests verwandelt. Eltern, Betreuungspersonen, Lehrkräfte oder Gruppenleitungen verwalten Aufgaben und Belohnungen; Kinder oder Mitglieder erledigen Quests und erhalten GP sowie EXP.

> **Aktueller Stand: V0.2.0 Public Beta.** Funktionen, Datenformate und Server-Schnittstellen können sich noch ändern. Wichtige Informationen sollten zusätzlich gesichert werden.

[Öffentliche Testversion V0.2.0 herunterladen](https://github.com/tatsuo25103/adventurer-guild/releases/tag/v0.2.0) · [Benutzerhandbuch auf Chinesisch](docs/USER_GUIDE.zh-TW.md) · [Versionshinweise V0.2.0](docs/RELEASE_NOTES_V0.2.0.zh-TW.md) · [Änderungsprotokoll](CHANGELOG.md)

## Installation

- Erfordert Android 7.0 (API 24) oder neuer.
- `adventurer-guild-0.2.0-debug.apk` von GitHub Releases herunterladen.
- Dem Browser oder Dateimanager die Installation unbekannter Apps erlauben und die APK öffnen.
- Für ein Update mit vorhandenen lokalen Daten die neue APK direkt über die installierte Version installieren. **Die alte App vorher nicht deinstallieren.**

Ein Update benötigt denselben Paketnamen, dieselbe Signatur und einen höheren `versionCode`. Die öffentliche Testversion nutzt derzeit eine Testsignatur und ist noch keine produktive Store-Version.

## Schnellstart

1. Ein Geräte-UUID-Konto anlegen und einen Anzeigenamen wählen.
2. Als Abenteurer oder Gildenverwaltung eintreten.
3. Eine Gilde gründen oder per Einladungscode beziehungsweise QR-Code beitreten.
4. Die Verwaltung veröffentlicht Quests und Belohnungen.
5. Abenteurer erledigen Quests und reichen Ergebnisse ein.
6. Berechtigte Personen prüfen die Einreichung und verbuchen GP/EXP.

Ein Konto kann mehreren Gilden angehören und in verschiedenen Gilden unterschiedliche Rollen haben. Innerhalb derselben Gilde darf ein Konto nicht gleichzeitig Abenteurer und Verwaltung sein.

## Hauptfunktionen

### Abenteurer

- Zuerst die eigenen laufenden Aufgaben, danach das öffentliche Questbrett sehen.
- Optionale Quests annehmen; tägliche, wöchentliche und monatliche Pflichtquests werden automatisch zugewiesen.
- Textberichte einreichen, Zusatzbelohnungen beantragen und Nearby nur bei entsprechend markierten Quests verwenden.
- GP/EXP verdienen, Level und Rang steigern, Titel wählen und Belohnungen einlösen.
- Mehrere Startbildschirm-Widgets für verschiedene Gilden konfigurieren.

### Gildenverwaltung

- Gilden, Einladungscodes, wiederverwendbare und einmalige QR-Codes verwalten.
- Quests erstellen, planen, bearbeiten, zurückziehen und als Vorlage speichern.
- Quests bestimmten Abenteurern sowie einem oder mehreren Prüfern zuweisen.
- Normale Einreichungen und Nearby-Bestätigungen über getrennte Berechtigungen prüfen.
- Mitglieder, Ämter, Berechtigungen, Gildenferien, Belohnungen und Einlösungen verwalten.

Der Gildenleiter besitzt alle Rechte. Werden für eine Quest bestimmte Prüfer festgelegt, dürfen andere Verwalter sie trotz allgemeiner Prüfberechtigung nicht freigeben; der Gildenleiter bleibt die Ausnahme.

## Questarten

| Typ | Zweck |
| --- | --- |
| Tägliche Quest | Gilt an ausgewählten Wochentagen und wird an jedem aktiven Tag abgerechnet |
| Wöchentlicher Auftrag | Wird an genau einem gewählten Wochentag erneuert |
| Monatsquest | Wird an einem gewählten Kalendertag erneuert; fehlt der Tag, gilt der Monatsletzte |
| Wiederholbarer Auftrag | Bleibt verfügbar und erlaubt wiederholte Einreichungen mit optionalem Limit |
| Formationsbefehl | Teilt eine Aktivität in begrenzte Positionen mit eigenen Belohnungen und Strafen |
| Befristeter Eventauftrag | Zeitlich begrenztes Ereignis mit klarer Start- und Endbedingung |
| Hauptquest | Fortschrittskette zum Freischalten von Handlung oder Funktionen |
| Aufstiegsprüfung | Rangprüfung mit festgelegten Teilnahmebedingungen |

Erfolge bilden ein eigenes System und sind keine Questart.

## Daten und Datenschutz

- Die Geräte-UUID ist die primäre Konto-ID. Für einen Handywechsel gibt es einen kurzlebigen, einmal verwendbaren Übertragungscode.
- Cloudflare Workers und D1 synchronisieren nur den minimal erforderlichen Textstatus.
- Fotos, Videos und ausführliche Nachweise bleiben auf dem Gerät. Bei einer Nearby-Quest wird der Nachweis persönlich geprüft und die Bestätigung zwischen den beiden Geräten durchgeführt.
- Keine echten Kinderdaten, Einladungscodes, UUIDs, Übertragungscodes oder privaten Nachweise in GitHub Issues veröffentlichen.

## Entwicklung und Build

Das Projekt verwendet Kotlin, Jetpack Compose, MVVM, Gradle sowie optional Cloudflare Workers + D1.

```powershell
Copy-Item private.properties.example private.properties
.\gradlew.bat :app:assembleDebug
```

Eigene Serveradresse und öffentlichen Prüfschlüssel in `private.properties` eintragen. Zugangsdaten, private Schlüssel, Datenbank-IDs und produktive Service-Adressen niemals committen.

## Bekannte Einschränkungen

- Die herunterladbare APK ist eine debug-signierte öffentliche Testversion.
- Ohne vorher erzeugten Übertragungscode ist die Wiederherstellung nach Verlust des alten Geräts eingeschränkt.
- Konflikte nach gleichzeitigen Offline-Änderungen benötigen weitere Praxistests.
- Nearby sollte mit zwei echten Android-Geräten getestet werden.

Die App soll Zusammenarbeit und Kommunikation fördern. Sie darf nicht zum Beschämen, Einschüchtern, übermäßigen Überwachen oder finanziellen Ausnutzen von Kindern oder Mitgliedern verwendet werden.
