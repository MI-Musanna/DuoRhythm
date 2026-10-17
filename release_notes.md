# DuoRhythm v1.0.0-beta

### Highlights & Fixes
- **Dedicated Wallet Ledger UI Parity**:
  - Eliminated hardcoded dark backgrounds (`#080C14` / `#0A0E17`) and restored full Light/Dark Theme parity using dynamic `?android:attr/colorBackground`.
  - Fixed invisible toolbar action icons (Back Arrow, Edit, Delete) by binding dynamic icon tint `?attr/colorOnSurface`.
  - Updated "Bucket Ledger History" header and "Bucket Net Balance" overview card text colors to dynamic theme attributes (`?attr/colorOnSurface` and `?attr/colorOnSurfaceVariant`).
  - Added theme-adaptive Choice Chip styling for transaction filter chips (All, Income, Expense) with subtle stroke (`#E2E8F0` in Light Mode) and Primary Cyan/Blue active state.
- **Light Theme & Semantic Dynamic Colors**:
  - Dynamic surface nav dock, settings information panels, wallet card containers, and attendance badge indicators.
- **Strict Input Validation**:
  - MaterialTimePicker integration formatted as `hh:mm a`.
  - Phone number regex enforcement (`^\+?[0-9]{7,15}$`) with inline validation errors.
  - Community URL validation with automatic `https://` prefixing.
- **Interactive Management**:
  - Fixed group link editing in student and work tabs.
  - Enabled class schedule editing and reminder rescheduling.
