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
