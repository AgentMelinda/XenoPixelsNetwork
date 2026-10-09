# Pause-menu music player

The bottom-left player is attached only to the real Escape pause menu. Its generated
150x40 plate and native-size chips use the green DMZ atlas explicitly; they do not follow
another screen's global theme. Background playback continues when the menu closes.

Client commands (only the issuing client's preferences change):

- `/xenoset music on` or `/xenoset music off`
- `/xenoset music next` or `/xenoset music prev`
- `/xenoset music track 33` (one-based playlist index)
- `/xenoset music volume 0.45` (range 0–1, multiplied by Minecraft's Music volume)
- `/xenoset music list`

The existing `/xenomusic` commands remain aliases. Scroll vertically over the pause
player to change volume. Changing volume restarts the current track at the new volume.
Pause is separate from power; pressing Play while powered off enables playback.

The default playlist reuses the 39 streamed menu sound events already shipped in the
pinned DragonMineZ 2.1.3 jar. Exact `sounds.json` inspection on 2026-10-08 confirms the
event ids and `stream: true`. Entries 31–33 reference `call_for_a_miracle`,
`gokus_father-son_victory`, and `vegetas_sacrifice`; labels describe these asset filenames,
not independently verified official soundtrack credits. No extra audio was downloaded.
Other track labels remain “DMZ Menu N” because their identities have not been audited.

`musicTracks` in the existing client JSON config accepts loaded resource-pack sound-event
ids. To supply additional local music, add streamed sound events in a resource pack and
put their ids in this list. Do not put MP4 or arbitrary filesystem paths in the playlist.
The player advances naturally and skips missing events. If an entire playlist fails,
it waits 30 seconds before retrying instead of repeatedly attempting every tick.

Existing generator: `tools/atlas-panels/xeno_extra_specs.py`, spec `xeno_music_panel`.
Generated resources and registry already existed and were preserved.

Validation status: exact dependency event/audio presence inspected; focused test added.
Pinned NeoForge 21.1.248 `ClientCommandHandler` source confirms that client commands merge
with server suggestions and unknown client subcommands fall through to the server. The
local `xenoset` root has no executable command of its own, preserving that fallback.
No Gradle tasks or fresh client were run for this slice. Rendering, audibility, command
merging with the server's `/xenoset`, and pause/background playback remain runtime pending.
