#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: phase3-hotfix-06.py <phase3-source-root>')
root = Path(sys.argv[1]).resolve()
path = root / 'app/build.gradle.kts'
text = path.read_text(encoding='utf-8')

old_runtime = '''    // Runtime-only compatibility fence for Room 2.8.4 migration bundle serializers.\n    // Keep KSP/Room compiler on its declared processor classpath; only app/test APK runtimes are pinned.\n    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3") { version { strictly("1.7.3") } }\n    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") { version { strictly("1.7.3") } }\n    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.7.3") { version { strictly("1.7.3") } }\n'''
new_runtime = '''    // Room 2.8.4 room-migration is compiled for kotlinx.serialization 1.8.1.\n    // Its generated serializers rely on the 1.8+ default GeneratedSerializer ABI.\n    // Keep app/test runtime classloading on the exact Room-declared ABI; KSP remains untouched.\n    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1") { version { strictly("1.8.1") } }\n    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1") { version { strictly("1.8.1") } }\n'''
if text.count(old_runtime) != 1:
    raise SystemExit('app/build.gradle.kts: expected hotfix-05 runtime compatibility block exactly once')
text = text.replace(old_runtime, new_runtime, 1)

old_android = '''    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3") { version { strictly("1.7.3") } }\n    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") { version { strictly("1.7.3") } }\n    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.7.3") { version { strictly("1.7.3") } }\n'''
new_android = '''    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1") { version { strictly("1.8.1") } }\n    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1") { version { strictly("1.8.1") } }\n'''
if text.count(old_android) != 1:
    raise SystemExit('app/build.gradle.kts: expected hotfix-05 androidTest compatibility block exactly once')
text = text.replace(old_android, new_android, 1)

if 'kotlinx-serialization-json-okio:1.7.3' in text or 'kotlinx-serialization-core:1.7.3' in text or 'kotlinx-serialization-json:1.7.3' in text:
    raise SystemExit('app/build.gradle.kts: stale serialization 1.7.3 pin remains')

path.write_text(text, encoding='utf-8')
print('PHASE3_HOTFIX_06=APPLIED')
