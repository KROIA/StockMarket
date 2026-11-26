#!/usr/bin/env python3
"""Regenerates common/src/main/resources/news/pictures/legacy_hashes.json from git history.

The file lists, per default news event id, the SHA-1 of every earlier version of its
baked picture (every PNG blob git ever had for <eventId>.png, also under the old
news_pictures/ folder from before commit 724a6ffc). DefaultNewsPictures uses it to
replace a config picture that still equals an old shipped version with the current
one, while leaving admin art alone. The current (working tree) picture is not listed.

Run it whenever redrawn pictures ship, after the new PNGs are in place:
    python scripts/update_legacy_picture_hashes.py
"""
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(subprocess.run(["git", "rev-parse", "--show-toplevel"], check=True,
                           capture_output=True, text=True, cwd=Path(__file__).parent).stdout.strip())
RESOURCES = "common/src/main/resources/"
PICTURE_DIRS = [RESOURCES + "news/pictures", RESOURCES + "news_pictures"]  # current, before 724a6ffc
OUT = ROOT / RESOURCES / "news/pictures/legacy_hashes.json"


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, cwd=ROOT).stdout


def main():
    ids = json.loads((ROOT / RESOURCES / "news/index.json").read_text(encoding="utf-8"))
    # Every blob id that ever appeared for <id>.png in any commit of any ref.
    blobs = {}
    log = git("log", "--all", "--raw", "--no-abbrev", "--no-renames", "--format=", "--", *PICTURE_DIRS)
    for line in log.decode().splitlines():
        if "\t" not in line:
            continue
        meta, path = line.split("\t", 1)
        name = path.rsplit("/", 1)[-1]
        if name.endswith(".png"):
            for blob in meta.split()[2:4]:  # old and new blob; all zeros = none
                if blob.strip("0"):
                    blobs.setdefault(name[:-4], set()).add(blob)

    result = {}
    for event_id in ids:
        current_file = ROOT / PICTURE_DIRS[0] / (event_id + ".png")
        current = hashlib.sha1(current_file.read_bytes()).hexdigest() if current_file.exists() else None
        hashes = {hashlib.sha1(git("cat-file", "blob", b)).hexdigest() for b in blobs.get(event_id, ())}
        hashes.discard(current)
        if hashes:
            result[event_id] = sorted(hashes)

    OUT.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"{OUT.relative_to(ROOT)}: {len(result)} ids, {sum(map(len, result.values()))} hashes")


if __name__ == "__main__":
    main()
