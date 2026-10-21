<img src="assets/icon.svg" width="64" height="64" alt="">

# Chunk Remover

A NeoForge mod for Minecraft 1.21.1 that lets you select an area on the **FTB Chunks** map and delete every
generated chunk inside it. The chunks are regenerated normally the next time someone visits them, and the
world save shrinks accordingly.

## Why

In a heavily modded world, players explore and generate far more terrain than in vanilla, both to find
modded resources and because modded transport is fast. The number of generated chunks explodes and the save
grows by gigabytes. Most of that terrain is never visited again. Chunk Remover lets an admin erase those
chunks from the save while keeping the world coherent, since they come back exactly as the world generator
would have produced them (same seed, same mods).

## Requirements

| Dependency      | Minimum version |
|-----------------|-----------------|
| Minecraft       | 1.21.1          |
| NeoForge        | 21.1.264        |
| FTB Chunks      | 2101.1.22       |
| FTB Library     | 2101.1.37       |
| FTB Teams       | 2101.1.11       |
| Architectury API| 13.0.11         |

The mod must be installed on the client (map integration) and on the server (deletion). In singleplayer
both run in the same game.

## Usage

### From the FTB Chunks map

1. Open the large map (default key `M`).
2. Click the **bin icon** in the lower part of the button bar on the left. It is only shown to operators and
   to the owner of a singleplayer world.
3. Drag with the **left mouse button** to select an area. Hold **Shift** while dragging to add more
   rectangles. The middle button pans the map while the tool is active, right click clears the selection,
   `Esc` or the bin icon leaves the tool.
4. Press `Enter` or `Delete` and confirm. The chunks disappear from the save and from your map.

The server replies with a summary in chat: chunks deleted, chunks skipped, region files removed or rewritten,
disk space freed.

### From a command

```
/chunkremover delete chunks <x1> <z1> <x2> <z2> [dimension]   # chunk coordinates
/chunkremover delete blocks <x1> <z1> <x2> <z2> [dimension]   # block coordinates
```

Both corners can be given in any order. The dimension defaults to the one the command is run in. The command
works from the server console too.

## What is never deleted

- **Loaded chunks**: anything near a player, force-loaded (vanilla `/forceload`, FTB Chunks force-load, other
  mods), spawn chunks, or currently being generated, loaded or saved.
- **Claimed chunks** (any team in FTB Chunks), unless `protectClaimedChunks` is turned off in the config.
- Chunks that were never generated are simply reported as "not generated".

The deletion still cannot be undone. Make a backup first.

## Configuration

`config/chunkremover-common.toml`

| Option                    | Default | Meaning                                                                 |
|---------------------------|---------|-------------------------------------------------------------------------|
| `requiredPermissionLevel` | `2`     | Permission level needed (2 = operator). The singleplayer owner is always allowed. |
| `maxChunksPerRequest`     | `262144`| Largest selection accepted (512 x 512 chunks, 8192 x 8192 blocks).       |
| `protectClaimedChunks`    | `true`  | Skip chunks claimed in FTB Chunks.                                       |
| `logDeletions`            | `true`  | Log a summary of every deletion on the server.                           |

## How it works

Minecraft stores chunks in region files (`region/r.x.z.mca`, plus `entities/` and `poi/`). Clearing a chunk
inside a region file never shrinks the file, so Chunk Remover works on whole files instead:

- when every chunk left in a region file is selected, the file is deleted;
- otherwise the file is rewritten with only the surviving chunks, then swapped in place.

For each of the three storages the mod flushes pending writes and then runs the file work on that storage's
own IO thread, so it is serialized with every read or write the game performs. In-memory point-of-interest
data of the deleted chunks is dropped as well. The server is blocked while the deletion runs, which can take
a few seconds for very large selections.

On the client, the deleted chunks are removed from the FTB Chunks map data of the player who asked, so they
show as unexplored until visited again. Other players keep their old map until they come back to the area.

### Things to know

- Regenerated chunks match the original generation only if the world seed and the installed mods are the
  same. If world generation mods changed since the area was generated, you will see chunk borders.
- Player-built structures in the selection are lost; that is the point, so select carefully.
- Structures (villages, modded dungeons...) regenerate at the same places because generation is deterministic.

## Building

Requires a JDK 21.

```
./gradlew build
```

The mod jar is written to `build/libs/chunkremover-<version>.jar`. The Gradle wrapper downloads NeoForge,
FTB Chunks and the other dependencies from their Maven repositories.

Dev runs: `./gradlew runClient`, `./gradlew runServer`. Two self tests exist as well: `./gradlew runSelftest`
starts a dedicated server, generates chunks, deletes them, checks the region files and the regeneration, then
stops; `./gradlew runSelftestClient` starts the client and checks that the FTB Chunks mixins apply. The
self-test classes are not shipped in the jar.

## License

MIT, see [LICENSE](LICENSE).
