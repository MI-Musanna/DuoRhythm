<div align="center">

<img src="art/app_icon.png" width="100" height="100" alt="DuoRhythm" style="border-radius: 22px;">

# DuoRhythm
**Next-Gen Local-First Financial Ledger & Academic Companion**

[![Release](https://img.shields.io/github/v/release/MI-Musanna/DuoRhythm?color=38BDF8&label=Beta%20Build&style=flat-square)](https://github.com/MI-Musanna/DuoRhythm/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-10B981?style=flat-square)](https://github.com/MI-Musanna/DuoRhythm)
[![Stack](https://img.shields.io/badge/Stack-Kotlin_%7C_Room_%7C_MVVM-6366F1?style=flat-square)](https://github.com/MI-Musanna/DuoRhythm)
[![License](https://img.shields.io/badge/License-MIT-f59e0b?style=flat-square)](LICENSE)

An offline, zero-telemetry Android suite built for students and independent builders who need strict capital accounting alongside coursework engineering.

<p align="center">
  <a href="https://github.com/MI-Musanna/DuoRhythm/releases/latest">
    <img src="https://img.shields.io/badge/Download_APK-v1.0.1--beta-0284C7?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" />
  </a>
  <a href="https://rookieenough.github.io/Orion-Data/redirect.html?id=duorhythm">
    <img src="https://img.shields.io/badge/Orion_Store-Get_It_Here-7C3AED?style=for-the-badge&logo=googleplay&logoColor=white" alt="Get it on Orion Store" />
  </a>
  <a href="https://rookieenough.github.io/Orion-Data/redirect.html?id=duorhythm">
    <img src="https://raw.githubusercontent.com/RookieEnough/Orion-Store/refs/heads/main/assets/orion-badge.png" height="28" alt="Orion Store Badge" />
  </a>
  <a href="https://github.com/MI-Musanna/DuoRhythm/issues">
    <img src="https://img.shields.io/badge/Issue_Tracker-Report_Bug-EA580C?style=for-the-badge&logo=github&logoColor=white" alt="Issue Tracker" />
  </a>
</p>

</div>

---

## 📱 Interface Walkthrough

<div align="center">

| Home & Quick Brief | Wallet & Multi-Bucket | Task & Goal |
| :---: | :---: | :---: |
| <img src="screenshots/Home.png" width="230"/> | <img src="screenshots/Wallet.png" width="230"/> | <img src="screenshots/Task.png" width="230"/> |

| Academic Portal | Weekly Routine | Focus Suite |
| :---: | :---: | :---: |
| <img src="screenshots/Portal.png" width="230"/> | <img src="screenshots/Schedule.png" width="230"/> | <img src="screenshots/Focus.png" width="230"/> |

</div>

---

## ⚙️ Core Architecture & Capabilities

### 💼 Smart Financial Ledger
- **Multi-Account Buckets:** Isolate liquid cash into up to 5 dedicated wallets (Physical Cash, Bank, Mobile Financial Services) with independent running balances.
- **Monthly Financial Cash-Flow Bar Chart:** Visual dual comparative bars for monthly Inflow vs Spending with dynamic net flow calculations and cycle tracking.
- **Atomic Balance Transfers:** Inter-account fund shifting with double-entry integrity—eliminating discrepancies between sending and receiving ledgers.
- **Adaptive Visual Analytics:** Live Donut breakdown for category expenditures alongside dual-column bar charts comparing total inflows against outflows.
- **Bilateral Dues & Debt Tracker:** Clean ledger separating receivables ("They Owe") and liabilities ("You Owe") with one-tap partial or full settlement stamping.
- **Direct Currency Parity:** Native 1:1 BDT (৳) recordkeeping without forced international conversion clutter.

### 🎓 Academic Command Center
- **Smart Consecutive Schedule Engine:** Battery-optimized zero-delay exact alarm dispatcher with automatic back-to-back lecture detection, eliminating audio-state flapping between consecutive periods.
- **Coursework Management:** Maintain subject codes, credit hours, classroom locations, and faculty profiles in one unified card system.
- **Dynamic Weekly Timetable:** Intelligent 7-day class routine highlighting active lectures with 12-hour MaterialTimePicker schedule editing.
- **Interactive Calendar Attendance:** Full-screen monthly calendar heatmap with drag-to-expand sheet, single-tap holiday and off-day exclusions, and real-time minimum margin warnings.
- **Attendance Threshold Engine:** Real-time attendance percentage calculator with early-warning alerts for institutional minimums (e.g. 75% target).
- **Assessment Pipeline:** Track Semester Finals, Midterms, Class Tests, and Quizzes with deadline countdowns and status toggles.
- **Multi-Format Document Vault:** Batch file importer for lecture PDFs, whiteboard captures, and notes with dynamic badges (`PDF`, `IMG`, `DOC`) and category filter chips.

### 🎯 Intentional Tasks & Milestone Goals
- **Task & Goal Circular Progress Analytics Header:** Dynamic circular progress ring and secondary linear bar calculating overall completion rate for active tasks and milestone targets.
- **Decoupled Task Engine:** Hard separation between fleeting daily checklists and long-term milestones to prevent priority overload.
- **0–100% Milestone Tracking:** Granular progress control for multi-week projects with visual stage indicators.
- **Material 3 Calendar Dialogs:** Native MaterialDatePicker integration supporting optional deadlines and dynamic Start/End date ranges.

### 💼 Work Portal & Fast Directory
- **Client Job Board:** Track freelance jobs, technical specifications, and invoicing status without leaving your student dashboard.
- **1-Tap Communications:** Speed directory supporting instant cellular calls, direct WhatsApp messaging, and verified community channel routing.

### 🛡️ Privacy & Performance Standards
- **100% Offline-First:** Powered entirely by SQLite/Room DB. All data stays encrypted on your local hardware.
- **Zero Telemetry:** No analytics SDKs, no network pings, zero third-party data collection.
- **Calibrated Dual Theme Engine:** Handcrafted Deep Obsidian Dark Mode (`#0A0E17` to `#131D2F`) alongside an adaptive, high-contrast Light Mode with crisp white surfaces and visible dialog typography.

### 👤 3-Profile Workspace Isolation
- **Dedicated Profile Workstation:** Direct full-screen profile hub accessible via the home avatar, supporting real-time academic info edits, photo personalization, and password management.
- **Fast Workspace Switcher:** Top-bar account switcher icon providing instantaneous switching between the 3 isolated user workspaces.
- **Multi-Identity Architecture:** Maintain up to 3 completely isolated workspace profiles (Slot 1, Slot 2, Slot 3) on the same device.
- **Independent Databases:** Separate timetables, tasks, milestone goals, attendance records, wallets, and subscriptions for each profile.
- **Secure Switching:** Local SHA-256 password hash protection with "Remember this email on this profile" support and instant toolbar switching.

### 🚀 First-Run Setup & In-App Updates
- **5-Step Streamlined First-Run Onboarding:** First-launch interactive wizard featuring a pure hero welcome, consolidated feature showcase, dedicated notification and storage permission controls, Profile 1 configuration, and verified developer contact links.
- **In-App GitHub Update Detection:** Built-in update detector querying official GitHub releases with full release notes display and direct APK download link.

---

## 📜 Changelog

### [v1.0.1-beta] - 2026-10-25
- Corrected official developer GitHub link (MI-Musanna).
- Removed intermediate tab scroll animation during bottom bar navigation.
- Enabled persistent ViewPager2 tab caching (offscreenPageLimit = 4) for zero-latency switching.
- Integrated interactive release download buttons, Orion Store badges, and Issue Tracker quick links in documentation.
- Fixed delayed notifications caused by Android Doze mode using setExactAndAllowWhileIdle.
- Implemented smart lookahead logic for back-to-back classes to seamlessly preserve DND without unmuting.
- Added unique request codes for pre-class, start, and end alarms to stop intent collision.
- Decoupled user profile from the settings screen into a dedicated ProfileActivity.
- Bound Home welcome avatar directly to the profile workstation.
- Added dedicated workspace switcher icon in top header for instant 3-profile switching.
- Cleaned Settings Hub by removing redundant profile accordions.
- Unified onboarding feature showcase and added storage permission check.
- Corrected developer links to Contact Hub and eliminated duplicate action buttons.
- Integrated Circular Goal Progress Ring in Tasks module.
- Added Monthly Inflow/Outflow Bar Chart to Wallet Ledger.
- Bumped version to v1.0.1-beta.

---

## 📥 Installation

1. Grab the latest package from [Releases](https://github.com/MI-Musanna/DuoRhythm/releases/latest).
2. Install `DuoRhythm-v1.0.1-beta.apk` on Android 8.0 (API 26) or higher.
3. Grant storage permissions for document attachment management if prompted.

---

## 📄 License
Released under the [MIT License](LICENSE).
