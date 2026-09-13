#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply-connected-followup.py <CrmScreen.kt>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

premature_close = '''                    onRowClick = { collectingReceivable = it },
                )
            }
        }
        item {
            ErpDashboardHero(
'''
premature_fixed = '''                    onRowClick = { collectingReceivable = it },
                )
            }
        item {
            ErpDashboardHero(
'''

function_boundary = '''            }
        }
    }
}

private data class BranchReceivableSummary(
'''
function_boundary_fixed = '''            }
        }
    }
}
}

private data class BranchReceivableSummary(
'''

checks = [
    ("premature LazyColumn close", premature_close, premature_fixed),
    ("CrmScreen function close", function_boundary, function_boundary_fixed),
]

for label, old, new in checks:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")

for label, old, _ in checks:
    if old in text:
        raise SystemExit(f"{label}: old pattern remains after transform")

print(f"CONNECTED_FOLLOWUP_APPLIED={path}")
