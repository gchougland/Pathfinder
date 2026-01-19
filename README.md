# Pathfinder

A Hytale mod that helps you find biomes by placing map markers on your compass. Similar to Nature's Compass for Minecraft, Pathfinder allows you to search for any biome in your world and automatically places a marker on your map that's visible at any distance.

## Features

- **Biome Search**: Search for any biome in your world using a command
- **Map Markers**: Automatically places markers on your compass that are visible at any distance
- **Biome Listing**: List all available biomes in your world
- **Marker Management**: Clear specific biome markers or all markers at once
- **Unlimited Visibility**: Markers are always visible on your compass, regardless of distance

## Commands

### `/pathfinder` or `/pf`

Main command with the following subcommands:

- **`/pathfinder search <biome>`** - Search for a specific biome and place a marker
  - Example: `/pathfinder search Valley_Glacier`
  
- **`/pathfinder list`** - List all available biomes in the world
  - Shows all unique biome types registered in the world generator
  
- **`/pathfinder clear [biome]`** - Clear markers
  - `/pathfinder clear` - Clears all Pathfinder markers
  - `/pathfinder clear <biome>` - Clears the marker for a specific biome
  - Example: `/pathfinder clear Valley_Glacier`

## Installation

1. Build the mod using Gradle (see Building from Source below)
2. Place the generated `.jar` file in your Hytale server's `plugins` directory
3. Restart your server

## Building from Source

### Prerequisites

- Java 17 or higher
- Gradle 8.0 or higher

### Build Steps

1. Clone this repository
2. Run the build command:
   ```bash
   ./gradlew build
   ```
   On Windows:
   ```bash
   gradlew.bat build
   ```
3. The compiled plugin will be in `build/libs/`

## How It Works

Pathfinder uses Hytale's world generation API to search for biomes without needing to load chunks. It performs a spiral search pattern starting from your current location, checking biome types at various coordinates until it finds the target biome. Once found, it creates a map marker that's stored in your player data and displayed on your compass.

The mod includes a custom marker provider that ensures markers are always visible on your compass, regardless of how far away they are from your current position.

## Technical Details

- **Package**: `com.hexvane.pathfinder`
- **Main Class**: `PathfinderPlugin`
- **World Gen Support**: Currently supports v1 world generation (ChunkGenerator)
- **Search Radius**: Default maximum search radius is 5000 blocks

## Credits

- Built using the Hytale Plugin Template created by Up and modified by Kaupenjoe
- Inspired by Nature's Compass for Minecraft

## License

This mod is provided as-is for use with Hytale.