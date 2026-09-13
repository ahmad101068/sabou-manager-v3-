#!/usr/bin/env bash
set -euo pipefail

# Historical compatibility entrypoint. The previous aggregate Phase-2 payload is
# obsolete/corrupt; delegate fail-closed to the canonical six-patch reconstruction.
exec bash "$(dirname "$0")/reconstruct-phase2-canonical.sh" "$@"
