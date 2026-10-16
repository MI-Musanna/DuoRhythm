# DuoRhythm 🎵⚡
> **Local-First Dual-Rhythm Productivity & Life Operations Cockpit for Android**

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%201.9-purple.svg)](https://kotlinlang.org)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2F%20Modular%20MVVM-blue.svg)](https://developer.android.com/topic/architecture)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](LICENSE)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Local--First%20%2F%20Zero%20Tracking-success.svg)](#privacy--security-model)

---

## 🌟 Overview

**DuoRhythm** is a privacy-first, offline-capable productivity suite built for individuals who juggle dual rhythms of life — students balancing academic curriculums with freelancing, or professionals managing side gigs alongside personal goals. 

Engineered with modern Kotlin, Android Jetpack, and a glassmorphic design system, DuoRhythm keeps your finances, timetables, tasks, focus tools, and sensitive documents entirely on your local device.

---

## 🚀 Key Features

### 💼 1. Financial Hub & Multi-Wallet Buckets
- **Multi-Wallet Buckets**: Organize funds into custom buckets (Cash, Mobile Wallets like bKash/Nagad/Rocket, Bank Accounts, Emergency Funds, Credit Cards).
- **On-Device Financial Alert Parser**: Automatically parses transactional payment notifications on your device with 1-tap `[Log Expense]` actions. Zero cloud transmission.
- **Subscription Tracker**: Tracks recurring commitments, renewal schedules, and monthly cost equivalents.
- **Multi-Currency Engine**: Built-in support for BDT, USD, EUR, GBP, JPY, INR, CAD, AUD with offline caching and live rate updates.

### 📚 2. Academic Timetable & Smart Attendance
- **Weekly Schedule**: Color-coded timetable for lectures, labs, tutorials, and seminars.
- **Live Status Badges**: Automatically detects `IN SESSION NOW` and `NEXT UP` classes.
- **Automated Silent/DND Mode**: Automatically silences device during class sessions and restores volume when lectures conclude.
- **15-Minute Alarms**: Exact alarm reminders prior to scheduled classes.
- **Attendance Monitor**: Live attendance percentage tracker with safety alerts when falling below the 75% threshold.

### ✅ 3. To-Do Tasks & Goal Milestones
- **Categorized Tasks**: Prioritize items by High, Medium, Low, or None.
- **Goal Progress Tracking**: Interactive sliders to record milestone percentages (0%–100%).
- **Custom Category System**: Add, manage, and filter by dynamic tags.
- **Staggered Animations**: Smooth micro-interactions and tactile touch response on every action.

### 🎓 4. Dual Portal: Academic vs. Work & Client Hub
- **Student Profile**: Manage subjects, credit hours, instructors, syllabus files, exam timelines, and assignment deliverables.
- **Client & Work Portal**: Track freelance work orders, unit pricing, client deliverables, contracts, and attachment documents.
- **Seamless Switching**: 1-tap profile toggle that dynamically adapts the dashboard and shortcuts.

### 🧘 5. Focus & Productivity Suite
- **Procedural DSP Ambient Sounds**: Built-in procedural sound generator for Gentle Rain, Ocean Night Waves, Tibetan Singing Bowls, 10Hz Alpha Focus Pads, and Deep Brown Noise.
- **Pomodoro Timer**: Customizable intervals with haptic feedback vibrations upon cycle completion.
- **Percentage & Exam Calculator**: Compute X% of Y, discounts, grade scores, and percentage variance.
- **Quick Notes Scratchpad**: Lightweight capture area on the home dashboard.

### 🛡️ 6. Privacy, Security & Data Sovereignty
- **100% Offline-Capable SQLite**: Everything stays on your local device. No telemetry, no third-party cloud sync, no tracking.
- **Biometric Security**: Hardware-backed biometric authentication (Fingerprint, Face Unlock, Device PIN) to protect financial ledgers and settings.
- **Automated Local Backups**: Background periodic JSON backups via Android WorkManager with automated pruning to maintain the 3 most recent snapshots.
- **Storage Access Framework (SAF)**: Export and import database snapshots to user-selected locations (Local Storage, SD card, or user-managed cloud drives).

---

## 🏗️ Architecture & Package Structure

DuoRhythm follows clean architecture principles with distinct separation of concerns:

```
com.pocketdev.duorhythm/
├── data/
│   ├── local/
│   │   ├── db/          # DatabaseHelper (SQLite, migrations, queries)
│   │   └── pref/        # UserProfileManager, ThemeManager (SharedPreferences)
│   ├── model/           # Data models (CardItem, TaskItem, StudentSubject, etc.)
│   └── repository/      # BackupService, CurrencyService
├── ui/
│   ├── common/          # Shared dialogs, PermissionRationaleDialogs
│   ├── home/            # HomeFragment, dashboard widgets, quick notes
│   ├── portal/          # PortalFragment, StudentSubjectDetailActivity, adapters
│   ├── schedule/        # ScheduleFragment, ClassesAdapter, AttendanceAdapter
│   ├── settings/        # SettingsActivity, accordions, theme selector
│   ├── tasks/           # TasksFragment, TasksAdapter
│   ├── tools/           # ToolsFragment, Pomodoro, Sound, Calculator dialogs
│   └── wallet/          # WalletFragment, WalletBucketDetailActivity, adapters
├── worker/              # LocalBackupWorker (WorkManager periodic backup)
├── service/             # ReminderManager, FinancialNotificationListenerService
├── receiver/            # ReminderReceiver, FinancialExpenseActionReceiver, etc.
├── security/            # LifeSyncSecurityManager (Biometrics)
├── audio/               # AmbientSoundPlayer (AudioTrack DSP synthesis)
├── automation/          # SmartAutomationManager, FinancialExpenseParser
└── util/                # BlurUtil, AnimationUtils
```

---

## 🎨 UI/UX & Native Micro-Animations

- **Fluid Accordions**: Smooth expand/collapse using `AutoTransition` with 180° chevron rotation.
- **Staggered Entry**: RecyclerView items slide up from 30dp with alpha fade in 180ms.
- **Tactile Feedback**: Spring scale-down (0.96f) on button press and release.
- **Navigation Crossfade**: Seamless alpha transitions between bottom navigation destinations.
- **Liquid Glassmorphism**: Translucent surfaces powered by Dimezis BlurView with hardware-accelerated RenderEffect on Android 12+.

---

## 🛠️ Build & Installation

### Requirements
- **Android Studio**: Iguana (2023.2.1) or newer
- **JDK**: Java 17
- **Android SDK**: `minSdk 26`, `compileSdk 36`, `targetSdk 35`
- **Gradle**: 8.x+

### Command-Line Build
To build the debug APK:
```bash
gradle assembleDebug
```

Output APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔒 Privacy & Security Model

- **No Remote Servers**: DuoRhythm operates without proprietary backends.
- **Biometric Protection**: Sensitive financial information is gated behind Android BiometricPrompt.
- **Local Notification Processing**: SMS / Notification Listener scans payment strings strictly in-memory; no raw message contents are logged or stored.
- **Data Exportability**: All data is exportable as standard UTF-8 JSON via Android SAF.

---

## 📄 License

DuoRhythm is open-source software licensed under the [Apache License, Version 2.0](LICENSE).
