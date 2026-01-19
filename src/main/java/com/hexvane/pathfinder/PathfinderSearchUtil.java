package com.hexvane.pathfinder;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.worldgen.biome.Biome;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.hypixel.hytale.server.worldgen.chunk.ZoneBiomeResult;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PathfinderSearchUtil {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int DEFAULT_MAX_RADIUS = 5000;
    
    /**
     * Searches for a biome starting from the given position using a spiral search pattern.
     * 
     * @param world The world to search in
     * @param startX Starting X coordinate
     * @param startZ Starting Z coordinate
     * @param targetBiomeName The name of the biome to search for (case-sensitive)
     * @param maxRadius Maximum search radius in blocks (default: 5000)
     * @return The coordinates of the found biome as [x, z], or null if not found
     */
    @Nullable
    public static int[] searchForBiome(
            @Nonnull World world,
            int startX,
            int startZ,
            @Nonnull String targetBiomeName,
            int maxRadius
    ) {
        IWorldGen worldGen = world.getChunkStore().getGenerator();
        LOGGER.atInfo().log("World generator type: %s", worldGen.getClass().getName());
        
        if (!(worldGen instanceof ChunkGenerator generator)) {
            LOGGER.atWarning().log("World generator is not ChunkGenerator (v1), it is: %s", worldGen.getClass().getName());
            return null;
        }
        
        int seed = (int)world.getWorldConfig().getSeed();
        LOGGER.atInfo().log("Starting biome search: target='%s', start=(%d, %d), seed=%d, maxRadius=%d", 
                targetBiomeName, startX, startZ, seed, maxRadius);
        
        // First, check the player's current position
        try {
            ZoneBiomeResult startResult = generator.getZoneBiomeResultAt(seed, startX, startZ);
            Biome startBiome = startResult.getBiome();
            String startBiomeName = startBiome.getName();
            LOGGER.atInfo().log("Player's current biome at (%d, %d): '%s'", startX, startZ, startBiomeName);
            
            if (targetBiomeName.equals(startBiomeName)) {
                LOGGER.atInfo().log("Found target biome at starting position!");
                return new int[]{startX, startZ};
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to query biome at starting position: %s", e.getMessage());
        }
        
        int checkedCount = 0;
        int lastLoggedRadius = -1;
        
        // Spiral search: start at center and expand outward
        for (int radius = 16; radius <= maxRadius; radius += 16) { // Start at 16 since we already checked 0
            // Log progress every 500 blocks
            if (radius - lastLoggedRadius >= 500) {
                LOGGER.atInfo().log("Searching at radius %d, checked %d coordinates so far...", radius, checkedCount);
                lastLoggedRadius = radius;
            }
            
            // Search in a square pattern at this radius
            for (int dx = -radius; dx <= radius; dx += 16) {
                for (int dz = -radius; dz <= radius; dz += 16) {
                    // Only check points on the perimeter of the square (spiral pattern)
                    if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                        int x = startX + dx;
                        int z = startZ + dz;
                        checkedCount++;
                        
                        try {
                            ZoneBiomeResult result = generator.getZoneBiomeResultAt(seed, x, z);
                            Biome biome = result.getBiome();
                            String biomeName = biome.getName();
                            
                            // Log first few biome names we encounter for debugging
                            if (checkedCount <= 5) {
                                LOGGER.atFine().log("Checked (%d, %d): biome='%s'", x, z, biomeName);
                            }
                            
                            if (targetBiomeName.equals(biomeName)) {
                                LOGGER.atInfo().log("Found target biome '%s' at (%d, %d) after checking %d coordinates", 
                                        biomeName, x, z, checkedCount);
                                return new int[]{x, z};
                            }
                        } catch (Exception e) {
                            // Continue searching if query fails
                            LOGGER.atFine().log("Failed to query biome at (%d, %d): %s", x, z, e.getMessage());
                            continue;
                        }
                    }
                }
            }
        }
        
        LOGGER.atWarning().log("Biome '%s' not found within radius %d (checked %d coordinates)", 
                targetBiomeName, maxRadius, checkedCount);
        return null; // Biome not found within search radius
    }
    
    /**
     * Searches for a biome with default max radius of 5000 blocks.
     */
    @Nullable
    public static int[] searchForBiome(
            @Nonnull World world,
            int startX,
            int startZ,
            @Nonnull String targetBiomeName
    ) {
        return searchForBiome(world, startX, startZ, targetBiomeName, DEFAULT_MAX_RADIUS);
    }
    
    /**
     * Gets all available biome types from the world generator's zone pattern provider.
     * This is the proper way to get all biomes without needing to sample the world.
     * 
     * @param world The world to get biomes from
     * @return Set of all unique biome names available in the world, or null if world generator is not supported
     */
    @Nullable
    public static java.util.Set<String> getAllBiomes(@Nonnull World world) {
        IWorldGen worldGen = world.getChunkStore().getGenerator();
        if (!(worldGen instanceof ChunkGenerator generator)) {
            LOGGER.atWarning().log("World generator is not ChunkGenerator (v1), it is: %s", worldGen.getClass().getName());
            return null;
        }
        
        java.util.Set<String> biomes = new java.util.HashSet<>();
        
        try {
            com.hypixel.hytale.server.worldgen.zone.ZonePatternProvider zonePatternProvider = generator.getZonePatternProvider();
            com.hypixel.hytale.server.worldgen.zone.Zone[] zones = zonePatternProvider.getZones();
            
            LOGGER.atInfo().log("Getting all biomes from %d zones", zones.length);
            
            for (com.hypixel.hytale.server.worldgen.zone.Zone zone : zones) {
                com.hypixel.hytale.server.worldgen.biome.BiomePatternGenerator biomePatternGenerator = zone.biomePatternGenerator();
                Biome[] zoneBiomes = biomePatternGenerator.getBiomes();
                
                for (Biome biome : zoneBiomes) {
                    String biomeName = biome.getName();
                    biomes.add(biomeName);
                    LOGGER.atFine().log("Found biome '%s' in zone '%s'", biomeName, zone.name());
                }
            }
            
            LOGGER.atInfo().log("Found %d unique biomes across all zones", biomes.size());
        } catch (Exception e) {
            LOGGER.atWarning().log("Error getting biomes from zone pattern provider: %s", e.getMessage());
            e.printStackTrace();
            return null;
        }
        
        return biomes;
    }
    
    /**
     * Discovers all unique biome types in an area around the given position.
     * This is a fallback method if getAllBiomes() doesn't work.
     * 
     * @param world The world to search in
     * @param startX Starting X coordinate
     * @param startZ Starting Z coordinate
     * @param radius Search radius in blocks
     * @return Set of unique biome names found, or null if world generator is not supported
     */
    @Nullable
    public static java.util.Set<String> discoverBiomes(
            @Nonnull World world,
            int startX,
            int startZ,
            int radius
    ) {
        // Try to get all biomes from the registry first
        java.util.Set<String> allBiomes = getAllBiomes(world);
        if (allBiomes != null && !allBiomes.isEmpty()) {
            return allBiomes;
        }
        
        // Fallback to sampling if registry method doesn't work
        IWorldGen worldGen = world.getChunkStore().getGenerator();
        if (!(worldGen instanceof ChunkGenerator generator)) {
            LOGGER.atWarning().log("World generator is not ChunkGenerator (v1), it is: %s", worldGen.getClass().getName());
            return null;
        }
        
        int seed = (int)world.getWorldConfig().getSeed();
        java.util.Set<String> biomes = new java.util.HashSet<>();
        
        LOGGER.atInfo().log("Discovering biomes by sampling: start=(%d, %d), radius=%d", startX, startZ, radius);
        
        int checkedCount = 0;
        // Sample biomes in a grid pattern (every 64 blocks for efficiency)
        for (int dx = -radius; dx <= radius; dx += 64) {
            for (int dz = -radius; dz <= radius; dz += 64) {
                int x = startX + dx;
                int z = startZ + dz;
                checkedCount++;
                
                try {
                    ZoneBiomeResult result = generator.getZoneBiomeResultAt(seed, x, z);
                    Biome biome = result.getBiome();
                    String biomeName = biome.getName();
                    biomes.add(biomeName);
                } catch (Exception e) {
                    LOGGER.atFine().log("Failed to query biome at (%d, %d): %s", x, z, e.getMessage());
                    continue;
                }
            }
        }
        
        LOGGER.atInfo().log("Discovered %d unique biomes after checking %d coordinates", biomes.size(), checkedCount);
        return biomes;
    }
}
