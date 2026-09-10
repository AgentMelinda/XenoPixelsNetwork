# Repository Safety

## Before work

- Capture `git status --short`, current branch, upstream divergence, and relevant file hashes.
- Identify user-owned modifications and choose an explicit write/stage set.
- Check for running client/server Java processes before using logs as evidence.

## During work

- Use patch-based edits and keep changes scoped to the task.
- Before recursive delete or move, resolve and inspect the absolute target path.
- Do not use cleanup commands against `run/`, `ui/`, `libs/`, generated references, or
  archives merely because they are large.
- Never silently replace a binary dependency. Record source build, byte size, hash, and matching
  property update.
- Treat decompiled code as evidence of the inspected binary, not as an API promise.

## Git and commits

- Stage paths explicitly and run `git diff --cached --check` plus `git diff --cached --stat`.
- Keep binary/hash, API, runtime example, data migration, networking, and guidance changes in
  independently reviewable commits when practical.
- Do not amend, rebase, reset, tag, or push unless requested.
