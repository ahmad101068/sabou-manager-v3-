#!/usr/bin/env bash
set -euo pipefail

: "${PROJECT_DIR:?PROJECT_DIR is required}"
cd "$PROJECT_DIR"

for attempt in 1 2 3; do
  echo "Gradle prewarm attempt ${attempt}/3"
  if ./gradlew --no-daemon --console=plain --version; then
    echo "GRADLE_PREWARM=PASS attempt=${attempt}"
    exit 0
  fi
  if [ "$attempt" -lt 3 ]; then
    sleep $((attempt * 10))
  fi
done

echo "GRADLE_PREWARM=FAIL attempts=3" >&2
exit 1
