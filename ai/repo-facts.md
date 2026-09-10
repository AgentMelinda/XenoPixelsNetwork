# Verified Repository Facts

**Checked:** 2026-09-10

- Branch: `1.21.1`.
- Language/toolchain: Java 21 with Gradle wrapper and ModDevGradle 2.0.143.
- Game stack: Minecraft 1.21.1, NeoForge 21.1.248, Parchment 2024.11.17.
- Mod identity: `net.bullettrain.xenopixelsmod`, mod id `xenopixelsmod`, version
  `0.3.5-1.21.1`.
- Required integrations include DragonMineZ 2.1.3, Sable, and Sable Companion; several other
  integrations are optional and must remain class-loading safe when absent.
- Main Java source: `src/main/java`; tests: `src/test/java`; authored resources:
  `src/main/resources`; generated resources: `src/generated/resources`.
- Public addon API: `src/main/java/net/bullettrain/xenopixelsmod/api`.
- Standalone API example: `examples/xenopixels-api-addon`.
- The main packet channel is `xenopixelsmod:main`, exact protocol `63`, with private sequential
  registration in `ModNetwork`.
- The addon channel is `xenopixelsmod:addons`, protocol generation 1 plus a deterministic packet
  registry fingerprint.
- CI verifies `libs/dragonminez-2.1.3.jar` before compilation using the SHA-256 stored in
  `gradle.properties`.
- Client distribution embeds Modern UI; `serverJar` deliberately excludes all
  `META-INF/jarjar` entries.

Recheck these facts when version properties, build plugins, dependency jars, network protocols, or
source layout change.
