#!/usr/bin/env python3
"""Extracts the images attached to a Claude Code session into a folder.

Why this exists: images pasted into a chat live in the conversation, not on disk. They are written
into the session transcript (``~/.claude/projects/<project>/<session>.jsonl``) only when the turn
that carried them finishes - so a batch of screenshots cannot be exported during the same turn it
was sent in, but can be the moment that turn ends.

Usage::

    python tools/extract_session_images.py --out XenoMyNpcsGuiOrderImages

By default it reads the newest transcript for this project and writes ``NN-image.png`` files
numbered in the order the images appear, which is the order they were sent. Pass ``--session`` to
pick a specific transcript, ``--start`` to shift the numbering (the first image of a batch may not
be image #1 of the conversation), and ``--skip-existing`` to leave already-named files alone.
"""

import argparse
import base64
import glob
import io
import json
import os
import sys

PROJECTS = os.path.expanduser("~/.claude/projects")

EXT = {
    "image/png": ".png",
    "image/jpeg": ".jpg",
    "image/jpg": ".jpg",
    "image/gif": ".gif",
    "image/webp": ".webp",
}


def newest_transcript(project_dir):
    files = glob.glob(os.path.join(project_dir, "*.jsonl"))
    if not files:
        raise SystemExit(f"no transcripts in {project_dir}")
    return max(files, key=os.path.getmtime)


def images_in(path):
    """Yields (media_type, raw_bytes) for every image block, in transcript order."""
    with io.open(path, encoding="utf-8", errors="ignore") as handle:
        for line in handle:
            if '"type":"image"' not in line:
                continue
            try:
                entry = json.loads(line)
            except ValueError:
                continue
            content = (entry.get("message") or {}).get("content")
            if not isinstance(content, list):
                continue
            for block in content:
                if not isinstance(block, dict) or block.get("type") != "image":
                    continue
                source = block.get("source") or {}
                data = source.get("data")
                if not data:
                    continue
                try:
                    yield source.get("media_type", "image/png"), base64.b64decode(data)
                except Exception:
                    continue


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", required=True, help="directory to write the images into")
    parser.add_argument("--session", default=None, help="transcript path; default is the newest")
    parser.add_argument("--project", default=None,
                        help="project folder under ~/.claude/projects; default is this repo's")
    parser.add_argument("--start", type=int, default=1, help="number to give the first image")
    parser.add_argument("--skip-existing", action="store_true",
                        help="do not overwrite a file that is already there")
    args = parser.parse_args()

    if args.session:
        transcript = args.session
    else:
        project = args.project or os.path.join(
            PROJECTS, "C--Users-Admin--grok-worktrees-dragonminez-XenoPixelsNetwork-qwen")
        transcript = newest_transcript(project)

    os.makedirs(args.out, exist_ok=True)
    written = 0
    for offset, (media_type, raw) in enumerate(images_in(transcript)):
        number = args.start + offset
        name = f"{number:02d}-image{EXT.get(media_type, '.png')}"
        target = os.path.join(args.out, name)
        if args.skip_existing and os.path.exists(target):
            continue
        with open(target, "wb") as handle:
            handle.write(raw)
        print(f"{name}  {len(raw):>9,} bytes  {media_type}")
        written += 1

    print(f"\n{written} image(s) from {os.path.basename(transcript)} -> {args.out}")
    if not written:
        print("Nothing written. A batch sent during the current turn is not in the transcript "
              "yet - it lands there once that turn ends.", file=sys.stderr)


if __name__ == "__main__":
    main()
