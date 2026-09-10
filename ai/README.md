# AI Work Guide

**Repository facts date:** 2026-09-10

`AGENTS.md` is the canonical contract. This directory adds task-specific playbooks and thin
agent adapters; it does not replace repository source or current runtime evidence.

## Start here

1. Read `AGENTS.md`.
2. Read `ai/repo-facts.md`, `ai/safety.md`, and `ai/validation.md`.
3. Select the relevant file under `ai/skills/`.
4. Use the matching `ai/agents/` adapter only for tool behavior.
5. Read the newest task-specific handoff, but verify its claims against the current tree.

## Skill playbooks

- `java-neoforge.md`: Java 21 and NeoForge implementation work.
- `public-api.md`: compatibility-sensitive public API changes and addon examples.
- `dmz-mixins.md`: DragonMineZ integration, bytecode verification, and mixins.
- `networking.md`: main-channel invariants and addon packet registration.
- `data-resources.md`: JSON, datapacks, DMZ patches, generated resources, and validation.
- `client-ui-assets.md`: client-only code, HUDs, animation, textures, and source asset bundles.
- `testing-runtime.md`: unit tests, dev runs, fresh-log evidence, and failure classification.
- `build-release.md`: dependency jars, CI hashes, client/server artifacts, and release safety.

## Agent adapters

Codex, Claude, GitHub Copilot, Qwen, and Grok all follow the same canonical rules. Qwen and Grok
have no repository auto-discovery mechanism established here; provide their adapter files manually.
