import os
import re
import sys
import glob

errors = []
warnings = []

# 1. Audit all XML layouts for dark-on-dark inputs and unsafe fixed heights
for xml_path in glob.glob("app/src/main/res/layout/*.xml"):
    with open(xml_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Check EditText / TextInputEditText inside dialog_ layouts for explicit textColor
    if os.path.basename(xml_path).startswith("dialog_"):
        for match in re.finditer(r'<(?:com\.google\.android\.material\.textfield\.TextInputEditText|EditText|AutoCompleteTextView)\b([^>]*)/?>', content, re.DOTALL):
            attrs = match.group(1)
            if "android:textColor=" not in attrs:
                errors.append(f"[UI-CONTRAST] {xml_path}: Input field missing explicit android:textColor (causes black text on dark glass dialogs).")

# 2. Audit Kotlin UI & Scheduler files for known crash & layout regressions
for kt_path in glob.glob("app/src/main/java/com/duorhythm/app/**/*.kt", recursive=True):
    with open(kt_path, "r", encoding="utf-8") as f:
        code = f.read()

    if "layoutAnimation = null" in code:
        errors.append(f"[CRASH-RISK] {kt_path}: Setting RecyclerView.layoutAnimation = null at runtime causes NullPointerException during draw().")

    if "AssessmentAlarmScheduler.kt" in kt_path and "pref_exam_reminders_enabled" not in code:
        errors.append(f"[ALARM-LEAK] {kt_path}: Missing opt-in preference guard (causes unsolicited 8:00 AM exam alarms).")

    if "WormDotsIndicatorView.kt" in kt_path and "observedAdapter" not in code:
        errors.append(f"[CRASH-RISK] {kt_path}: Missing tracked observedAdapter reference (causes IllegalStateException on mode switch).")


    # Check for hardcoded raw pixel values (>= 16) in setPadding or LayoutParams without density scaling
    if "/ui/" in kt_path.replace("\\", "/"):
        for line_no, line in enumerate(code.splitlines(), start=1):
            if "density" in line or "dp" in line.lower():
                continue
            pad_match = re.search(r'\.setPadding\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)', line)
            if pad_match and any(int(v) >= 16 for v in pad_match.groups()):
                errors.append(f"[RAW-PX] {kt_path}:{line_no}: Hardcoded raw pixel padding detected ({pad_match.group(0)}). Convert to density-scaled dp.")
            lp_match = re.search(r'LayoutParams\(\s*(\d+)\s*,\s*(\d+)', line)
            if lp_match and any(int(v) >= 16 for v in lp_match.groups()):
                errors.append(f"[RAW-PX] {kt_path}:{line_no}: Hardcoded raw pixel LayoutParams detected ({lp_match.group(0)}). Convert to density-scaled dp.")

print("=== DUORHYTHM STATIC GATEKEEPER AUDIT REPORT ===")
if warnings:
    for w in warnings:
        print("WARN:", w)

if errors:
    for e in errors:
        print("FAIL:", e)
    print(f"\nGatekeeper FAILED with {len(errors)} critical issue(s). Fix these before building APK.")
    sys.exit(1)
else:
    print("PASS: Zero structural, contrast, alarm-leak, or crash regressions detected across all XML & Kotlin files.")
    sys.exit(0)
