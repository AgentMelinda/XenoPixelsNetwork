from __future__ import annotations

import unittest

import generate_changelog as changelog


class ChangelogGeneratorTest(unittest.TestCase):
    def test_semantic_tag_order_ignores_lexical_order(self) -> None:
        tags = ["v0.1.10", "v0.1.9", "v0.1.8-1.21.1", "v0.1.11"]
        self.assertEqual(
            ["v0.1.8-1.21.1", "v0.1.9", "v0.1.10", "v0.1.11"],
            sorted(tags, key=changelog.version_key),
        )

    def test_alias_range_is_empty_and_keeps_provenance(self) -> None:
        release = changelog.Release(
            tag="v0.1.8-1.21.1",
            previous="v0.1.6",
            commit="abc",
            date="2026-07-26",
            declared_version="0.1.6-1.20.1",
            commits=(),
            alias_of="v0.1.6",
        )
        text = changelog.render_release(release)
        self.assertIn("Alias release", text)
        self.assertIn("same commit as `v0.1.6`", text)
        self.assertIn("1.20.1", text)

    def test_commits_are_assigned_once(self) -> None:
        releases = [
            changelog.Release("v1", None, "a", "2026-01-01", "1.0-1.20.1", (("1", "first"),)),
            changelog.Release("v2", "v1", "b", "2026-01-02", "2.0-1.21.1", (("2", "second"),)),
        ]
        self.assertEqual({"1", "2"}, changelog.validate_coverage(releases, {"1", "2"}))
        with self.assertRaises(ValueError):
            changelog.validate_coverage(releases, {"1", "2", "3"})

    def test_existing_release_heading_is_not_duplicated(self) -> None:
        original = "# Changelog\n\n# v1\n\nOld notes.\n"
        updated = changelog.insert_release(original, "# v1\n\nNew notes.\n")
        self.assertEqual(original, updated)

    def test_existing_manual_release_is_not_overwritten_without_force(self) -> None:
        from tempfile import TemporaryDirectory
        from pathlib import Path

        release = changelog.Release("v1", None, "a", "2026-01-01", "1.0-1.20.1", (("1", "first"),))
        with TemporaryDirectory() as temp:
            path = Path(temp) / "v1.md"
            path.write_text("manual notes\n", encoding="utf-8")
            changelog.write_release(release, Path(temp))
            self.assertEqual("manual notes\n", path.read_text(encoding="utf-8"))
            changelog.write_release(release, Path(temp), force=True)
            self.assertIn("# v1", path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
