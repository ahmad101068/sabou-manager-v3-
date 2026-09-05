#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: phase3-hotfix-05.py <phase3-source-root>')
root = Path(sys.argv[1]).resolve()
path = root / 'app/build.gradle.kts'
text = path.read_text(encoding='utf-8')

global_pin = '''// Room 2.8.4 migration serializers are binary-incompatible with kotlinx.serialization 1.8.x.\n// The application does not use kotlinx.serialization directly; keep Room's migration runtime on 1.7.3\n// across app/test/KSP classpaths until Room is upgraded to a compatible release.\nconfigurations.configureEach {\n    resolutionStrategy.eachDependency {\n        if (requested.group == "org.jetbrains.kotlinx" && requested.name in setOf(\n                "kotlinx-serialization-core",\n                "kotlinx-serialization-json",\n                "kotlinx-serialization-json-okio",\n            )\n        ) {\n            useVersion("1.7.3")\n            because("Room 2.8.4 migration serializer ABI requires kotlinx.serialization < 1.8")\n        }\n    }\n}\n\n'''
if text.count(global_pin) != 1:
    raise SystemExit('app/build.gradle.kts: expected global serialization pin from hotfix-04 exactly once')
text = text.replace(global_pin, '', 1)

anchor = '    implementation(libs.androidx.sqlite)\n'
runtime_pins = '''    implementation(libs.androidx.sqlite)\n    // Runtime-only compatibility fence for Room 2.8.4 migration bundle serializers.\n    // Keep KSP/Room compiler on its declared processor classpath; only app/test APK runtimes are pinned.\n    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3") { version { strictly("1.7.3") } }\n    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") { version { strictly("1.7.3") } }\n    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.7.3") { version { strictly("1.7.3") } }\n'''
if text.count(anchor) != 1:
    raise SystemExit('app/build.gradle.kts: implementation sqlite anchor is not unique')
text = text.replace(anchor, runtime_pins, 1)

android_anchor = '    androidTestImplementation(libs.androidx.sqlite.framework)\n'
android_pins = '''    androidTestImplementation(libs.androidx.sqlite.framework)\n    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3") { version { strictly("1.7.3") } }\n    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") { version { strictly("1.7.3") } }\n    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.7.3") { version { strictly("1.7.3") } }\n'''
if text.count(android_anchor) != 1:
    raise SystemExit('app/build.gradle.kts: androidTest sqlite anchor is not unique')
text = text.replace(android_anchor, android_pins, 1)
path.write_text(text, encoding='utf-8')
print('PHASE3_HOTFIX_05=APPLIED')
