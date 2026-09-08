"""Generate version-bound release notes from Git history.

Single range:
    python tools/generate_changelog.py --from-tag v0.1.9 --to-tag v0.1.10
Full branch history:
    python tools/generate_changelog.py --all --branch origin/1.21.1 --first-tag v0.0.5
"""
from __future__ import annotations

import argparse
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Release:
    tag: str
    previous: str | None
    commit: str
    date: str
    declared_version: str
    commits: tuple[tuple[str, str], ...]
    alias_of: str | None = None


def git(*args: str, check: bool = True) -> str:
    result = subprocess.run(["git", *args], text=True, capture_output=True, check=check)
    return result.stdout.strip()


def version_key(tag: str) -> tuple[int, ...]:
    match = re.search(r"v?(\d+)\.(\d+)\.(\d+)", tag)
    return tuple(map(int, match.groups())) if match else (0, 0, 0)


def category(subject: str) -> str:
    text = subject.lower()
    if any(word in text for word in ("fix", "bug", "crash", "correct")):
        return "Fixes"
    if any(word in text for word in ("key", "input", "control", "bind", "rush", "combat")):
        return "Controls and combat"
    if any(word in text for word in ("npc", "dmz", "compat", "mixin")):
        return "Compatibility"
    if any(word in text for word in ("config", "breaking", "protocol", "version")):
        return "Configuration and compatibility"
    return "Features and improvements"


def declared_version(ref: str) -> str:
    text = git("show", f"{ref}:gradle.properties", check=False)
    match = re.search(r"^mod_version=(.+)$", text, re.MULTILINE)
    return match.group(1).strip() if match else "unknown"


def platform(version: str) -> str:
    if "1.20.1" in version:
        return "Minecraft 1.20.1 (legacy Forge line)"
    if "1.21.1" in version:
        return "Minecraft 1.21.1 (NeoForge line)"
    return "Platform not declared"


def commits_in_range(previous: str | None, tag: str) -> tuple[tuple[str, str], ...]:
    range_spec = f"{previous}..{tag}" if previous else tag
    lines = git("log", "--reverse", "--format=%H%x09%s", range_spec).splitlines()
    return tuple(tuple(line.split("\t", 1)) for line in lines if "\t" in line)  # type: ignore[return-value]


def collect_releases(branch: str, first_tag: str) -> list[Release]:
    tags = git("tag", "--merged", branch).splitlines()
    tags = [tag for tag in tags if version_key(tag) >= version_key(first_tag)]
    tags.sort(key=lambda tag: (version_key(tag), git("log", "-1", "--format=%ct", tag), tag))
    releases: list[Release] = []
    previous: str | None = None
    seen_commits: dict[str, str] = {}
    for tag in tags:
        commit = git("rev-parse", tag)
        alias = seen_commits.get(commit)
        releases.append(Release(
            tag=tag,
            previous=previous,
            commit=commit,
            date=git("log", "-1", "--format=%cs", tag),
            declared_version=declared_version(tag),
            commits=() if alias else commits_in_range(previous, tag),
            alias_of=alias,
        ))
        seen_commits.setdefault(commit, tag)
        previous = tag
    return releases


def validate_coverage(releases: list[Release], expected: set[str]) -> set[str]:
    assigned = [commit for release in releases for commit, _ in release.commits]
    duplicates = {commit for commit in assigned if assigned.count(commit) > 1}
    missing = expected - set(assigned)
    extra = set(assigned) - expected
    if duplicates or missing or extra:
        raise ValueError(f"Invalid commit coverage: duplicates={duplicates}, missing={missing}, extra={extra}")
    return set(assigned)


def render_release(release: Release) -> str:
    lines = [f"# {release.tag}", "", f"- **Released:** {release.date}",
             f"- **Declared build:** `{release.declared_version}`",
             f"- **Platform:** {platform(release.declared_version)}"]
    if release.previous:
        lines.append(f"- **Git range:** `{release.previous}..{release.tag}`")
    else:
        lines.append(f"- **Git range:** repository root through `{release.tag}`")
    lines.append("")
    if release.alias_of:
        lines += ["## Alias release", "", f"This tag points to the same commit as `{release.alias_of}` and contains no unique commits.",
                  "The checked-in version still identifies the 1.20.1 build, so the tag name alone is not evidence of a completed 1.21.1 port.", ""]
    else:
        groups: dict[str, list[str]] = {}
        for commit, subject in release.commits:
            groups.setdefault(category(subject), []).append(f"- {subject} ([`{commit}`](https://github.com/AgentMelinda/XenoPixelsNetwork/commit/{commit}))")
        for title in ("Features and improvements", "Controls and combat", "Compatibility",
                      "Configuration and compatibility", "Fixes"):
            if groups.get(title):
                lines += [f"## {title}", "", *groups[title], ""]
        lines += ["## Included commits", "", *[f"- `{commit}` — {subject}" for commit, subject in release.commits], ""]
    return "\n".join(lines).rstrip() + "\n"


def insert_release(existing: str, release: str) -> str:
    heading = release.splitlines()[0]
    if re.search(rf"^{re.escape(heading)}$", existing, re.MULTILINE):
        return existing
    title = "# Changelog"
    body = existing[len(title):].lstrip() if existing.startswith(title) else existing.lstrip()
    return f"{title}\n\n{release.rstrip()}\n\n{body}".rstrip() + "\n"


def write_release(release: Release, output_dir: Path, force: bool = False) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    safe = re.sub(r"[^A-Za-z0-9_.-]+", "-", release.tag)
    path = output_dir / f"{safe}.md"
    if force or not path.exists():
        path.write_text(render_release(release), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--from-tag")
    parser.add_argument("--to-tag", default="HEAD")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--branch", default="origin/1.21.1")
    parser.add_argument("--first-tag", default="v0.0.5")
    parser.add_argument("--output-dir", default="docs/releases")
    parser.add_argument("--root", default="CHANGELOG.md")
    parser.add_argument("--force", action="store_true", help="overwrite existing per-release files")
    args = parser.parse_args()
    output_dir = Path(args.output_dir)

    if args.all:
        releases = collect_releases(args.branch, args.first_tag)
        if releases:
            expected = set(git("rev-list", releases[-1].tag).splitlines())
            validate_coverage(releases, expected)
        for release in releases:
            write_release(release, output_dir, args.force)
        return

    if not args.from_tag:
        parser.error("--from-tag is required unless --all is used")
    version = args.to_tag if args.to_tag == "HEAD" else git("describe", "--tags", "--exact-match", args.to_tag)
    release = Release(version, args.from_tag, git("rev-parse", args.to_tag),
                      git("log", "-1", "--format=%cs", args.to_tag), declared_version(args.to_tag),
                      commits_in_range(args.from_tag, args.to_tag))
    write_release(release, output_dir)
    root = Path(args.root)
    existing = root.read_text(encoding="utf-8") if root.exists() else "# Changelog\n"
    root.write_text(insert_release(existing, render_release(release)), encoding="utf-8")


if __name__ == "__main__":
    main()
