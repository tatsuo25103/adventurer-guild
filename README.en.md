# Adventurer Guild

**[繁體中文](README.md)** · English · [Deutsch](README.de.md) · [日本語](README.ja.md)

Adventurer Guild is an Android app that turns daily routines, learning goals, and group responsibilities into RPG-style guild quests. Parents, caregivers, teachers, or community organizers manage quests and rewards; children or members complete quests to earn GP and EXP.

> **Current status: V0.2.0 Public Beta.** Features, data formats, and server APIs may change. Keep a separate record of important information.

[Download the V0.2.0 public beta](https://github.com/tatsuo25103/adventurer-guild/releases/tag/v0.2.0) · [Traditional Chinese user guide](docs/USER_GUIDE.zh-TW.md) · [V0.2.0 release notes](docs/RELEASE_NOTES_V0.2.0.zh-TW.md) · [Changelog](CHANGELOG.md)

## Installation

- Requires Android 7.0 (API 24) or later.
- Download `adventurer-guild-0.2.0-debug.apk` from GitHub Releases.
- Allow your browser or file manager to install unknown apps, then open the APK.
- To update while keeping local data, install the newer APK over the existing app. **Do not uninstall the old app first.**

An update must use the same package name and signing identity, and a higher `versionCode`. The current public beta uses a test signing identity and is not a store-ready production build.

## Quick Start

1. Create a device UUID account and choose a display name.
2. Enter as an Adventurer or Guild Administrator.
3. Create a guild or join one with an invitation code or QR code.
4. Administrators publish quests and rewards.
5. Adventurers complete quests and submit results.
6. Authorized administrators approve submissions and settle GP/EXP.

One account may participate in multiple guilds and use different roles in different guilds. It cannot be both an Adventurer and an administrator in the same guild.

## Core Features

### Adventurer

- View active quests before the public quest board.
- Accept optional quests; daily, weekly, and monthly recurring quests are assigned automatically.
- Submit text reports, request excess-performance rewards, and use Nearby only when required by the quest.
- Earn GP/EXP, increase level and rank, choose a title, and redeem rewards.
- Configure multiple home-screen widgets for different guilds.

### Guild Administration

- Create guilds, invitation codes, reusable QR codes, and one-time QR codes.
- Publish, schedule, edit, cancel, and template quests.
- Assign quests to selected Adventurers and assign one or more dedicated reviewers.
- Review normal submissions and Nearby in-person confirmations through separate permissions.
- Manage members, roles, permissions, guild holidays, rewards, and redemptions.

The guild owner has all permissions. If a quest has dedicated reviewers, other administrators cannot approve it even if they normally have review permission; the owner remains the exception.

## Quest Types

| Type | Purpose |
| --- | --- |
| Daily Quest | Runs on selected weekdays and settles every active day |
| Weekly Quest | Refreshes on one selected weekday |
| Monthly Quest | Refreshes on a selected day; missing dates use the month's final day |
| Repeatable Quest | Remains available and supports repeated submissions with an optional limit |
| Formation Order | Splits one activity into limited positions with separate rewards and penalties |
| Limited Event Quest | A time-limited event with explicit start and end conditions |
| Main Quest | A progression chain that unlocks story or functionality |
| Promotion Quest | A rank-promotion trial with explicit eligibility requirements |

Achievements are a separate system and are not quest types.

## Data and Privacy

- The device UUID is the primary account identifier. A short-lived, single-use transfer code is available when moving to a new phone.
- Cloudflare Workers and D1 synchronize only the minimum text state needed for coordination.
- Photos, videos, and detailed evidence stay on the user's device. When a quest enables Nearby, an administrator inspects the evidence in person and the two devices confirm the review locally.
- Do not publish real child information, invitation codes, UUIDs, transfer codes, or private quest evidence in GitHub issues.

## Build

The project uses Kotlin, Jetpack Compose, MVVM, Gradle, and an optional Cloudflare Workers + D1 backend.

```powershell
Copy-Item private.properties.example private.properties
.\gradlew.bat :app:assembleDebug
```

Use your own backend URL and public verification key in `private.properties`. Never commit credentials, private keys, database IDs, or production service URLs.

## Known Public Beta Limitations

- The downloadable APK is a debug-signed public beta.
- Account recovery is limited if the old device is lost before a transfer code is created.
- Conflict handling after simultaneous offline edits needs broader real-world testing.
- Nearby requires two physical Android devices for representative testing.

This project is designed to encourage cooperation and communication. It should not be used to shame, intimidate, excessively monitor, or financially exploit children or members.
