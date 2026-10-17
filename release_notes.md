# DuoRhythm v1.0.0-beta

### Highlights & Fixes
- **Project-Wide Purge of Hardcoded Dark Artifacts**:
  - Eliminated static dark backgrounds (`#0A0E17`, `#0F172A`, `#131D2F`, `#172033`, `#1E293B`, `#203049`, `#243654`, `#2A364F`) across all layouts, drawables, and Kotlin controllers.
  - Full theme parity between Light Mode and Dark Mode with zero contrast regressions.
- **Subject & Course Detail Screen Parity**:
  - **Credit Badge**: Replaced hardcoded dark navy gradient with dynamic badge background (`#E0F2FE` soft tint in Light Mode, `#1E293B` in Dark Mode) and high-contrast text (`#0369A1` in Light Mode, `#38BDF8` in Dark Mode).
  - **Subject Code Badge**: Updated to dynamic code badge background (`#E0F2FE` in Light Mode, `#0F2942` in Dark Mode) and text (`#0284C7` in Light Mode, `#38BDF8` in Dark Mode).
  - **Empty State Icon Container**: Replaced hardcoded dark square container (`bg_glass_capsule_icon`) with dynamic soft surface (`@color/surface_variant` / `@color/card_stroke`) and dynamic icon tint (`?attr/colorPrimary`).
  - Added dynamic `app:navigationIconTint="@color/text_primary"` to the Subject Section toolbar.
- **Dedicated Wallet Ledger UI Parity**:
  - Restored full Light/Dark Theme parity using dynamic `?android:attr/colorBackground`.
  - Bound dynamic icon tint `?attr/colorOnSurface` to toolbar action icons (Back Arrow, Edit, Delete).
  - Updated "Bucket Ledger History" header and "Bucket Net Balance" overview card text colors to dynamic theme attributes (`?attr/colorOnSurface` and `?attr/colorOnSurfaceVariant`).
  - Added theme-adaptive Choice Chip styling for transaction filter chips (All, Income, Expense) with subtle stroke (`#E2E8F0` in Light Mode) and Primary Cyan/Blue active state.
  - Updated Mini Chart and Empty State Ring (`bg_chart_empty_ring`) with dynamic surface and muted colors.
- **Financial Analytics & Dues / Debts**:
  - Replaced hardcoded `#131D2F` and `#203049` in cashflow summary banner and dynamic chart container with `@color/surface_card` and `@color/card_stroke`.
  - Added semantic summary card colors for Lent (`#ECFDF5` in Light Mode, `#064E3B` in Dark Mode) and Borrowed (`#FEF2F2` in Light Mode, `#4C1D24` in Dark Mode).
  - Updated MPAndroidChart axes, legends, grid lines, and center donut labels to dynamically use `text_primary`, `text_secondary`, and `card_stroke`.
- **Category & Bucket Creation Dialogs**:
  - Category type toggle buttons and bucket icon selection cards now dynamically adapt to `surface_variant`, `card_stroke`, and `text_secondary`.
- **Home Header Typography**:
  - Updated user greeting and username text in `fragment_home.xml` and `layout_home_header.xml` to dynamic `@color/text_primary` and `@color/text_secondary`.
- **Input Validation & Interactivity**:
  - MaterialTimePicker integration formatted as `hh:mm a`.
  - Phone number regex enforcement (`^\+?[0-9]{7,15}$`) with inline validation errors.
  - Community URL validation with automatic `https://` prefixing.
  - Enabled group link editing, class schedule editing, and reminder rescheduling.
