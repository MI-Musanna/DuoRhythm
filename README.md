# DuoRhythm

**Dual-Mode Academic, Productivity & Financial Ledger Ecosystem for Android**  
Built natively in **Kotlin** with a **Local-First, Offline-First MVVM + Repository Architecture**.

---

## Overview

**DuoRhythm** unifies daily university/academic tracking (`Student Mode`) and freelance/client project operations (`Work Mode`) inside a single high-speed Android application without requiring cloud accounts, subscriptions, or external telemetry.

- **Current Release:** `v1.6.7-beta`
- **Author & Maintainer:** Mahdi Islam Musanna ([GitHub](https://github.com/MI-Musanna))
- **Architecture:** Single-Activity (`MainActivity` + `ViewPager2`), MVVM, Room / SQLite, Custom `Canvas` Views, Native Hardware Automation

---

## Core Modules

1. **Unified Dashboard (`Home`)**
   - Live bidirectional `Student <-> Work` workspace toggle with a mode-colored **Luminous Wavefront Circular Reveal** (`#38BDF8` Sky-Blue for Student Mode, `#10B981` Emerald Green for Work Mode).
   - Real-time **Next Up** lecture/deadline carousel, financial summary snapshot, active missions counter, collapsible scratchpad notes, and quick-launch tools (Pomodoro Timer, CGPA & Marks Calculator).

2. **Academic & Work Portal (`Portal`)**
   - **Student Mode:** Enrolled course management with cascade deletion, attendance tracking, homework/practicals breakdown, reactive assessment scheduler (`Subject + Custom Category` composition with 1-tap clear), and instructor/classmate directory.
   - **Work Mode:** Client order pipeline, deliverable deadlines, and deterministic contact avatars.

3. **Smart Schedule & Hardware Automation (`Schedule`)**
   - Weekly timetable with exact `AlarmManager` (`setExactAndAllowWhileIdle`) pre-class alerts and live session indicators.
   - **Auto-Silent During Classes (DND):** Automatically engages device Silent/DND mode via `AudioManager` and `NotificationManager` when a lecture begins, detects consecutive back-to-back classes (`<= 5 min` gap) to keep silent mode uninterrupted, and honors user preference on whether to restore General mode after the final lecture.
   - **Configurable Exam Reminders:** Pre-exam lead alerts (`24h`/`1h` for Midterms & Finals, `2h`/`20m` for Quizzes & Class Tests) plus a configurable **Default Exam Reminder Time** (or `OFF`) for untimed exams.

4. **Tasks & Milestones (`Tasks`)**
   - Priority-tagged task management, goal progress tracking, live search/category filtering, and tactile spring completion animations.

5. **Offline Financial Ledger (`Wallet`)**
   - Multi-account balance tracking, income/expense categorization, subscription renewal alerts, and `Dues & Debts` settlement tracking with optional biometric lock.

---

## Technical Architecture

| Layer | Technologies & Implementation |
| :--- | :--- |
| **Language & UI** | 100% Native **Kotlin**, XML Layouts, Type-Safe **ViewBinding**, Material Components |
| **Custom Graphics** | Zero third-party animation bloat; custom `Canvas` & `Paint` rendering (`SpeedDialFabView`, `NotificationBadgeView`, `WormDotsIndicatorView`, `DeterministicAvatarHelper`, `RevealWaveRingView`) |
| **Motion Engine** | `DuoMotionHelper` (`0ms` `ACTION_DOWN` press feedback, Snapshot-Backed Circular Reveal with glowing edge ring, `OvershootInterpolator` spring physics, damped validation shake) |
| **Persistence** | **SQLite** + **Room Persistence Library** (`DAO` + `DatabaseHelper`) with strict `profileId` multi-profile isolation and `SharedPreferences` |
| **Background Jobs** | `AlarmManager`, `BroadcastReceiver` (`ClassAlarmReceiver`, `ClassEndReceiver`, `AssessmentAlarmReceiver`), and `WorkManager` (`AutoBackupWorker`, `BriefingAlarmScheduler`) |
| **Backup & OTA** | Storage Access Framework (SAF) JSON backup/restore + GitHub Releases API in-app OTA updater (`UpdateManager`) |
| **Verification** | Automated **JUnit4 + Robolectric** Gatekeeper Suite (`DuoRhythmFullAppAuditTest` — 22 automated architectural & UI audits) |

---

## License & Copyright

Copyright (c) 2026 **Mahdi Islam Musanna**. **All Rights Reserved.**  
See the [LICENSE](LICENSE) file for full terms. Unauthorized copying, redistribution, or derivative publishing is strictly prohibited.
