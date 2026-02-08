# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
