  # LinearReader-Compatible Hybrid Storage and AFK Compression

  ## Summary

  - Stop using the current server JAR until fixed: the September 18, 2026 log contains 2,531 Unknown frame descriptor
    failures and 1,179 failed chunk load/save messages.

  - Fix compatibility with existing LinearReader files. Its extended v1 format can store Zstd or Brotli while
    retaining the original Linear header layout; Youer currently assumes every body is Zstd. LinearReader
    intentionally combines whole-region compression with later idle recompression.

  - Replace the current per-chunk-only v2 layout with a v3 hybrid: one highly compressed whole-region checkpoint plus
    incremental per-chunk delta records.

  - Add manual /youer linear afk-compress commands, with automatic recompression disabled by default.
  - Keep Minecraft autosaves enabled while allowing migration archives to be disabled.

  ## Storage Changes

  - Parse legacy Linear files using the full 32-byte header and trailing signature.
  - Interpret compression bytes 1–22 as Zstd and 100–111 as Brotli quality 0–11.
  - Validate the footer, compressed length, chunk count, inner sizes, and LinearReader CRC32 extension before
    accepting a file.

  - For Brotli files, read the decompressed-size hint from the upper 32 bits of the checksum field and CRC32 from the
    lower 32 bits.

  - Package Brotli4j 1.23.0 plus Linux x86-64/AArch64, Windows x86-64, and macOS x86-64/AArch64 native providers.
  - Introduce Linear v3:
      - A whole-region base checkpoint containing the 8 KiB chunk index and all base chunk data.
      - Checkpoint metadata for algorithm, level, compressed/uncompressed lengths, generation, timestamp, and CRC.
      - Append-only per-chunk Zstd delta or tombstone records for live writes.
      - Latest valid delta overrides the checkpoint entry.
      - Invalid or partial tail records are truncated during recovery.

  - Adopt valid LinearReader Zstd/Brotli bodies directly as the initial v3 checkpoint without recompressing or
    inflating the 120 GB world.

  - Upgrade existing incremental v2 files to v3 by reading their live records and producing one checkpoint.
  - During .mca/.linear merging, append selected newer Anvil chunks as deltas, force them to disk, and only then
    archive or delete the .mca.

  ## Concurrency and Safety

  - Add per-region single-flight opening so only one worker can validate, migrate, or merge a region at a time.
  - Protect the non-thread-safe region cache separately while allowing different regions to open and decompress
    concurrently.

  - Prevent the repeated concurrent migrations visible in the log, where the same region was migrated and backed up
    several times by different I/O workers.

  - Keep source files untouched when validation, Brotli loading, migration, fsync, or atomic replacement fails.
  - Add linear-keep-migration-backups, default true; the production config will set it to false.
  - With backups disabled, use a temporary file and atomic replacement, deleting the source only after the v3
    replacement is durable.

  - Existing .v1.*.bak and .merged.*.bak files remain untouched.

  ## AFK Compression

  - Add operator-only commands:
      - /youer linear afk-compress — show status.
      - /youer linear afk-compress zstd start [dimension]
      - /youer linear afk-compress brotli start [dimension]
      - /youer linear afk-compress stop

  - Status reports mode, target algorithm, current region, scanned, recompressed, already optimal, skipped, failed,
    memory pauses, bytes saved, and elapsed time.

  - Recompression snapshots the checkpoint plus latest deltas, writes a new whole-region checkpoint to a temporary
    file, fsyncs it, atomically replaces the region, and removes stale deltas.

  - Zstd manual target is level 22; Brotli manual target is quality 11.
  - Use one low-priority maintenance worker on the Ryzen 5 5500U.
  - Pause between files when free heap is below 20%, server MSPT exceeds 40, shutdown begins, or normal Linear
    flushing has backlog.

  - A stop request finishes or safely abandons the current temporary file, then exits before starting another region.
  - Automatic idle detection remains disabled by default but the schema retains an opt-in switch for future use.

  ## Configuration

  unsupported-settings:
    region-file-format: LINEAR
    linear-preset: NORMAL

    linear-compression-level: -1
    linear-compression-threads: -1
    linear-flush-threads: -1
    linear-flush-interval-seconds: -1
    linear-region-cache-memory-budget-mb: -1
    linear-flush-queue-max-mb: -1
    linear-compaction-threshold-mb: -1

    linear-keep-migration-backups: false

    linear-afk-auto-enabled: false
    linear-afk-worker-threads: 1
    linear-afk-min-free-heap-percent: 20
    linear-afk-max-mspt: 40
    linear-afk-zstd-level: 22
    linear-afk-brotli-quality: 11

  - Advance the Paper global configuration schema to version 32.
  - Continue using the NORMAL live-write preset: two flush workers, no internal Zstd workers, level 3, 256 MiB cache,
    and 128 MiB queue.

  ## Test Plan

  - Add fixtures for original Xymb v1 Zstd, LinearReader Zstd with CRC, and LinearReader Brotli with decompressed-size
    metadata.

  - Verify direct adoption into v3 preserves every chunk and does not recompress or significantly enlarge the source.
  - Test corrupted signature, footer, CRC, length, compression byte, Zstd body, and Brotli body; each must fail
    without replacing the source.

  - Test concurrent reads/writes opening the same region and confirm exactly one migration occurs.
  - Test checkpoint reads, delta overrides, tombstones, crash-tail truncation, compaction, and v2-to-v3 conversion.
  - Test Zstd and Brotli manual recompression, dimension filtering, status output, stop behavior, memory/MSPT pauses,
    and shutdown.

  - Test duplicate .mca merging with migration backups both enabled and disabled.
  - Run targeted JUnit tests, :youer:applyPatches, :youer:test, and :youer:youerJar.
  - Validate against a copied production world by loading the previously failing chunks, teleporting across distant
    regions, running save-all flush, restarting twice, and confirming zero failed chunk saves, zero repeated
    migrations, and no watchdog stall.

  ## Rollout Assumptions

  - Make one external offline copy of the current world before testing because failed chunk saves already occurred.
  - Remove the LinearReader mod while testing Youer’s native implementation to avoid two storage engines patching the
    same classes.

  - Rebuild after the final commit and confirm the server version reports that commit rather than 069b0c15.
  - Existing migration backups are removed manually only after the repaired build has passed world verification.