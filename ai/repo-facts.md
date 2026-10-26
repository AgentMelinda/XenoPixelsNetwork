# Verified Repository Facts

**Checked:** 2026-09-26

- Branch: `1.21.1`.
- Language/toolchain: Java 21 with Gradle wrapper and ModDevGradle 2.0.143.
- Game stack: Minecraft 1.21.1, NeoForge 21.1.248, Parchment 2024.11.17.
- Mod identity: `net.bullettrain.xenopixelsmod`, mod id `xenopixelsmod`, version
  `0.5.0-1.21.1`.
- Required integrations include DragonMineZ 2.2 (dependency migrated 2026-10-23), Sable, and Sable Companion; several other
  integrations are optional and must remain class-loading safe when absent.
- Main Java source: `src/main/java`; tests: `src/test/java`; authored resources:
  `src/main/resources`; generated resources: `src/generated/resources`.
- Public addon API: `src/main/java/net/bullettrain/xenopixelsmod/api`.
- Standalone API example: `examples/xenopixels-api-addon`.
- The main packet channel is `xenopixelsmod:main`, exact protocol `113` as of 2026-10-22, with
  private sequential registration in `ModNetwork`.
- The addon channel is `xenopixelsmod:addons`, protocol generation 1 plus a deterministic packet
  registry fingerprint.
- Gradle and CI verify `libs/dragonminez-2.2.jar` before compilation using the SHA-256 stored in
  `gradle.properties`.
- Client distribution embeds Modern UI and AAA Particles; `serverJar` embeds AAA Particles only
  (since 2026-10-02) and no other `META-INF/jarjar` entry.

Recheck these facts when version properties, build plugins, dependency jars, network protocols, or
source layout change.
