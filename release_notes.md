# 📜 DuoRhythm Release Notes & Historical Changelog

Detailed release history and changelog for DuoRhythm builds.

---

### [v1.0.1-beta] - 2026-10-25
- **Profile Avatar Isolation & Cache Busting:** Avatars are now saved into slot-isolated files (`avatar_profile_${id}.jpg`) with timestamp query parameters to invalidate stale caches across workspace switches and prevent cross-profile avatar overwriting.
- **Header Suite & App Logo Polish:** Enhanced top-left DuoRhythm emblem with 2dp padding and `scaleType="fitCenter"` for prominent presentation; unified header lightning quick-action button with elevated `bg_header_action_button` background and `#00E5FF` cyan tint.
- **Direct Biometric & PIN Fallback Auth:** Upgraded biometric authentication across workspace switching, editing, and deletion with clear "Use Password/PIN" negative button and automatic fallback to credentials dialog. Updated settings subtitle to highlight fingerprint, face unlock, or PIN support.
- **Active Workspace Delete Guard & Slot 1 Promotion:** Protected active workspaces against accidental deletion by hiding delete buttons on active profiles. Deleting Slot 1 from an active secondary slot cleanly re-links all 14 database tables, copies avatar files, promotes the active workspace to Slot 1, and prunes the old slot.
- **Zombie Alarm Purging & Rescheduling:** Implemented `cancelAlarmsForClass` using `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` across all alarm intent tokens and pending intents to reliably wipe stale alarms on class edits and deletions, backed by `rescheduleAllActiveAlarms` during `onResume()` and boot completion.
- **About Developer Dialog Version String:** Corrected legacy "v2.1" version labels to current "v1.0.1-beta" build tag across About dialogs and system info sheets.
- **Biometric Workspace Authentication:** Integrated BiometricPrompt fingerprint verification for one-touch workspace switching and profile editing with secure password fallback.
- **Inline Switcher Controls:** Added inline Edit and Delete controls directly inside Workspace Profiles Bottom Sheet with cascading Room database cleanup.
- **Avatar Cache & Invalidation Polish:** Fixed avatar image caching bug when switching between configured and unconfigured workspaces with explicit memory clearing.
- **Elevated Header Actions:** Standardized Settings Hub header buttons with elevated surface styling (`bg_header_action_button`).
- **Floating Surface Badges:** Redesigned icon containers across Home, Schedule, Portal, and Wallet from dark debossed cutouts into elevated high-contrast floating badges.
- **Illuminated App Logo Frame:** Upgraded top header app logo frame with an illuminated cyan border accent.
- **Elevated Metric Grids & Action Chips:** Overhauled summary statistic cards in Student and Work Portals with elevated surface styling (`#161F34`), removing debossed borders and nested dark square icon containers. Flattened interactive action pills with elevated cyan surface and refined padding.
- **Elevated Flat-Surface Styling:** Overhauled UI styling to replace debossed/sunken buttons with modern elevated flat surfaces and clean 1dp accent strokes.
- **Unified Ripple Controls:** Standardized ripple states across header icons, segmented switches, and quick action docks.
- **Photoshop-Style 2D HSV Color Studio:** Integrated Photoshop-style 2D HSV color picker dialog replacing basic RGB sliders.
- **Searchable Vector Icon Library (120+ Icons):** Added 120+ searchable vector icon library with dedicated bottom sheet grid picker.
- **Expanded 16-Color Palette:** Expanded accent color palette to 16 curated non-duplicate shades.
- **Custom RGB/Hex Color Picker:** Integrated custom RGB/Hex color picker dialog for custom bucket and category personalization.
- **Touch Gesture Navigation:** Re-enabled touch gesture swiping across dashboard tabs with synchronized bottom navigation callbacks and `offscreenPageLimit = 4` memory caching.
- **OLED Dark Mode Standard:** Enforced unified OLED Dark Mode ecosystem application-wide, optimizing battery life and visual ergonomics.
- **Cleaned Settings Hub:** Removed redundant "Appearance & Theme" accordion from settings in favor of system-wide dark mode.
- **Orion Store Badge:** Integrated official Orion Store download badge and verified routing redirect in documentation.
- **Decoupled Release Notes:** Streamlined `README.md` by decoupling comprehensive release history into dedicated `release_notes.md`.
- **Developer GitHub Profile:** Corrected official developer GitHub link to `MI-Musanna` across About dialogs and Settings.
- **ViewPager Direct Jumps:** Removed intermediate tab scroll stutter on direct bottom bar item selection.
- **Exact Alarms & Doze Resilience:** Transitioned timetable alerts to `setExactAndAllowWhileIdle()` to eliminate Android Doze mode alarm batching.
- **Consecutive Class Lookahead:** Implemented smart lookahead logic for back-to-back classes to seamlessly preserve DND without unmuting or duplicate alerts.
- **Distinct Request Codes:** Assigned unique PendingIntent request codes for pre-class (`classId * 10 + 1`), start (`classId * 10 + 2`), and end (`classId * 10 + 3`) alarms.
- **In-Class Pre-Alert Polish:** 5-minute pre-alerts fire silently if user is currently attending an ongoing lecture.
- **Dedicated Profile Workstation:** Full-screen personal profile manager bound directly to Home welcome avatar.
- **Fast Workspace Switcher:** Quick switcher icon in top header for instant 3-profile switching.
- **Cleaned Settings Profiles:** Removed redundant profile management accordions from Settings.
- **Unified Onboarding:** Consolidated onboarding showcase and added explicit storage permission check.
- **In-App GitHub Update Detection:** Built-in update detector querying official GitHub releases with full release notes display and direct APK download link.
- **Circular Progress Ring:** Integrated dynamic Circular Progress Ring in Tasks module.
- **Monthly Cash-Flow Chart:** Added dynamic Inflow/Outflow Comparative Bar Chart to Wallet Ledger.
- **Version Bump:** Bumped `versionCode` to `3` and `versionName` to `1.0.1-beta`.

---

### [v1.0.0-beta] - 2026-10-17
Welcome to the public beta of **DuoRhythm**!
- **Financial Vault:** Isolated multi-account buckets, atomic transfers, Donut/Bar analytics, and bilateral debt settlement.
- **Academic Hub:** Weekly routine with MaterialTimePicker, attendance safeguard calculations, and batch-document course vault.
- **Task Architecture:** Separation of daily chores vs long-term milestones with Material calendar integration.
- **Work & Directory:** Client job tracking, direct WhatsApp/phone actions, and verified channel links.
- **Privacy Core:** 100% offline Room database, zero telemetry, and robust data integrity.
