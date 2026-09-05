#!/usr/bin/env python3
"""Print exact JUnit counts from Gradle XML evidence."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--label", default="TEST")
    args = parser.parse_args()

    files = sorted(args.root.rglob("TEST-*.xml"))
    if not files:
        print(f"{args.label}_EVIDENCE=FAIL no JUnit XML under {args.root}", file=sys.stderr)
        return 1

    tests = failures = errors = skipped = 0
    for path in files:
        suite = ET.parse(path).getroot()
        tests += int(suite.attrib.get("tests", 0))
        failures += int(suite.attrib.get("failures", 0))
        errors += int(suite.attrib.get("errors", 0))
        skipped += int(suite.attrib.get("skipped", 0))

    failed = failures + errors
    passed = tests - failed - skipped
    print(f"{args.label}_TOTAL={tests}")
    print(f"{args.label}_PASS={passed}")
    print(f"{args.label}_FAIL={failed}")
    print(f"{args.label}_SKIP={skipped}")
    print(f"{args.label}_XML_FILES={len(files)}")
    print(f"{args.label}_EVIDENCE=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
