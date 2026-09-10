# Skill: Build and Release

- Keep `dragonminez_sha256` synchronized with the tracked DragonMineZ jar in the same commit.
- Use the wrapper and pinned properties; do not introduce dynamic versions such as `+` or
  `latest.release`.
- Build both the client jar and `xenopixelsmod-Server-<version>.jar`.
- Inspect the server jar and require zero `META-INF/jarjar/` entries.
- Review workflow edits against local task names and CI hash checks.
- Do not tag, publish, or push without explicit approval and a recorded artifact verification.
