#!/usr/bin/env python3
import base64
import gzip
import hashlib
import pathlib
import subprocess
import sys
import tempfile

EXPECTED_PATCH_SHA256 = "865b2d29bad1ee39b116fd6e1e201cd40663f4e7aaa4254af664e255400284f4"
PART_COUNT = 8


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: phase8-1-hotfix-chunked.py <reconstructed-source-root>")
    root = pathlib.Path(sys.argv[1]).resolve()
    patch_dir = pathlib.Path(__file__).resolve().parent / "patch"
    encoded_parts = []
    for index in range(PART_COUNT):
        part = patch_dir / f"phase8-1-patch.part{index:02d}"
        if not part.is_file():
            raise SystemExit(f"missing Phase 8.1 patch chunk: {part}")
        encoded_parts.append(part.read_text(encoding="utf-8").strip())
    encoded = "".join(encoded_parts)
    patch = gzip.decompress(base64.b64decode(encoded))
    actual = hashlib.sha256(patch).hexdigest()
    if actual != EXPECTED_PATCH_SHA256:
        raise SystemExit(f"Phase 8.1 patch SHA-256 mismatch: {actual}")

    with tempfile.NamedTemporaryFile(prefix="phase8-1-", suffix=".patch", delete=False) as handle:
        handle.write(patch)
        patch_path = pathlib.Path(handle.name)
    try:
        subprocess.run(
            ["patch", "--dry-run", "--batch", "--forward", "-p1", "-i", str(patch_path)],
            cwd=root,
            check=True,
        )
        subprocess.run(
            ["patch", "--batch", "--forward", "-p1", "-i", str(patch_path)],
            cwd=root,
            check=True,
        )
    finally:
        patch_path.unlink(missing_ok=True)

    print(f"PHASE8_1_PATCH_SHA256={actual}")
    print(f"PHASE8_1_PATCH_CHUNKS={PART_COUNT}")
    print("PHASE8_1_PATCH_APPLIED=PASS")


if __name__ == "__main__":
    main()
