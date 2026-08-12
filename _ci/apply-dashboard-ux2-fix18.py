#!/usr/bin/env python3
from pathlib import Path

BUILD = Path("app/build.gradle.kts")
text = BUILD.read_text(encoding="utf-8")

runner_anchor = '        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"\n'
runner_new = '        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"\n        testInstrumentationRunnerArguments["clearPackageData"] = "true"\n'
if runner_new not in text:
    if runner_anchor not in text:
        raise SystemExit("FIX18_RUNNER_ANCHOR_MISSING")
    text = text.replace(runner_anchor, runner_new, 1)

options_anchor = '''    packaging {\n        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"\n    }\n\n'''
options_new = '''    packaging {\n        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"\n    }\n\n    testOptions {\n        execution = "ANDROIDX_TEST_ORCHESTRATOR"\n    }\n\n'''
if options_new not in text:
    if options_anchor not in text:
        raise SystemExit("FIX18_TEST_OPTIONS_ANCHOR_MISSING")
    text = text.replace(options_anchor, options_new, 1)

dep_anchor = '    androidTestImplementation(libs.androidx.compose.ui.test.junit4)\n'
dep_new = '    androidTestImplementation(libs.androidx.compose.ui.test.junit4)\n    androidTestUtil("androidx.test:orchestrator:1.6.1")\n'
if dep_new not in text:
    if dep_anchor not in text:
        raise SystemExit("FIX18_DEPENDENCY_ANCHOR_MISSING")
    text = text.replace(dep_anchor, dep_new, 1)

BUILD.write_text(text, encoding="utf-8")

check = BUILD.read_text(encoding="utf-8")
for needle in (
    'testInstrumentationRunnerArguments["clearPackageData"] = "true"',
    'execution = "ANDROIDX_TEST_ORCHESTRATOR"',
    'androidTestUtil("androidx.test:orchestrator:1.6.1")',
):
    if needle not in check:
        raise SystemExit(f"FIX18_VERIFY_FAIL:{needle}")
print("DASHBOARD_UX2_FIX18_TEST_ISOLATION=PASS orchestrator=1.6.1 clearPackageData=true")
