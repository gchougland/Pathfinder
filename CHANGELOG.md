# Changelog

All notable changes to this project will be documented in this file.

## [1.3.0] - 2026-10-12

### Changed
- **Hytale Update 7** Pathfinder now runs on Hytale Update 7 (0.7.0).

### Fixed
- **Biome Map** The Biome Map shows its texture again when held.

## [1.2.0] - 2026-08-27

### Changed
- **Hytale Update 6 compatibility**
  - Manifest `ServerVersion` is now the SemverRange `>=0.6.0-pre.0 <0.7.0`.
  - Custom UI pages register through `OpenCustomUIInteraction.registerCustomPageSupplier` so they unload with the plugin.
  - Map markers are built with `MapMarkerBuilder` and a finite `Transform` instead of assigning protocol packet fields.
  - Player position uses JOML `Vector3d` (`.x` / `.z`).
  - `/pathfinder` is an `AbstractCommandCollection` with typed `search`, `clear`, and `list` subcommands, biome tab-complete, and the Adventurer permission group.
  - Biome search runs on the common pool with a chunk-resolution `SpiralIterator`, matching first-party `/locate biome`.
  - Command and search messages use translation keys.
  - Biome Map item adds `CarryHudInputBindings` for the secondary use action and `TextureComputedColor` on its block type.

### Fixed
- **`/pathfinder clear <biome>`** now matches marker ids (`pathfinder_<biome>_...`) instead of comparing against the marker's `FormattedMessage` name.
- **Biome search** no longer occupies the world thread while walking the spiral.

## [1.1.0] - 2026-02-19

### Added
- **PathfinderMarkerStorage** – In-memory storage for pathfinder markers per player per world. Replaces the removed `PlayerWorldData.getWorldMapMarkers()` / `setWorldMapMarkers()` API. Uses entity ref index as the player key so it is safe to call from both the World thread (when adding/clearing markers) and the WorldMap thread (when the map provider runs).
- **PathfinderMarkerFactory** – Builds `MapMarker` instances for the current Hytale API, using `Message.raw(name).getFormattedMessage()` for the marker name field.

### Changed
- **Hytale API compatibility (latest release)**  
  - **MarkerProvider** – Implemented new `update(World, Player, MarkersCollector)` signature; pathfinder markers are read from storage and added via the collector.  
  - **MapMarker** – No longer uses the old 5-argument constructor; markers are created via the no-arg constructor with field assignment and the correct `FormattedMessage` for the name.  
  - **Marker visibility** – Pathfinder markers now use `MarkersCollector.addIgnoreViewDistance()` so far-away biome markers still show on the map and compass (same behavior as the old “unlimited visibility” path).
- **PathfinderMarkerStorage.playerKey(Player)** – Uses only `Ref.getIndex()` (no `Store` access) so it can be used from the WorldMap thread without triggering Hytale’s thread-affinity assertion. Previously used deprecated `Entity.getUuid()` and then `UUIDComponent` via the store, which is not allowed off the World thread.
- **PathfinderCommand, PathfinderSearchService, PathfinderBiomeSearchPage** – All marker add/clear/list logic now uses `PathfinderMarkerStorage` and `PathfinderMarkerFactory` instead of `PlayerWorldData` and the old `MapMarker` constructor.

### Fixed
- **FormattedMessage** – Resolved `ClassCastException` when creating markers (e.g. from the biome map GUI). `Message` is not a `FormattedMessage`; the code now uses `Message.raw(name).getFormattedMessage()` to obtain the protocol type.
- **Far-away biome markers** – Markers for biomes found at large distances were not shown because `MarkersCollector.add()` only sends markers within chunk view distance. Pathfinder now uses `addIgnoreViewDistance()` for its markers so they always appear.

### Note
- Pathfinder markers are stored in memory only and do not persist across server restarts.

## [1.0.1] - 2026-01-22

### Fixed
- Fixed compilation error in `PathfinderMarkerProvider` by updating to use `MapMarkerTracker` type instead of `Object` and correcting parameter order to match the latest Hytale API
  - Changed method signature from `update(World, Object, int, int, int)` to `update(World, MapMarkerTracker, int chunkViewRadius, int playerChunkX, int playerChunkZ)`
  - Removed reflection code in favor of direct method calls
- Fixed deprecation warning in `PathfinderBiomeSearchPage` by replacing deprecated `addField()` method with `append().add()` pattern in `PathfinderEventData` CODEC
- Replaced deprecated `rebuild()` method call with manual rebuild using `sendUpdate()` to avoid deprecation warnings

### Changed
- Updated `PathfinderMarkerProvider` to use the new API directly without reflection
- Updated event data CODEC to use the new builder pattern (`append().add()` instead of `addField()`)

## [1.0.0] - Initial Release

Initial release of Pathfinder mod.
