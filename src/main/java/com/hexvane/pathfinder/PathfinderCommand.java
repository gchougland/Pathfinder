package com.hexvane.pathfinder;

import com.hypixel.hytale.common.util.ArrayUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.worldgen.biome.Biome;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.hypixel.hytale.server.worldgen.chunk.ZoneBiomeResult;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PathfinderCommand extends AbstractPlayerCommand {
    private static final Message MESSAGE_BIOME_NOT_FOUND = Message.raw("Biome not found within search radius.");
    private static final Message MESSAGE_BIOME_FOUND = Message.raw("Biome found! Marker placed on map.");
    private static final Message MESSAGE_MARKER_CLEARED = Message.raw("Pathfinder marker(s) cleared.");
    private static final Message MESSAGE_NO_MARKERS = Message.raw("No pathfinder markers found.");
    private static final Message MESSAGE_SPECIFIC_BIOME_NOT_FOUND = Message.raw("No marker found for that biome.");
    private static final Message MESSAGE_USAGE = Message.raw("Usage: /pathfinder search <biome> | clear [biome] | list");
    private static final Message MESSAGE_LISTING_BIOMES = Message.raw("Discovering biomes in the area...");
    
    public PathfinderCommand() {
        super("pathfinder", "Search for biomes and place markers on the map compass");
        this.addAliases("pf");
        this.setAllowsExtraArguments(true);
    }
    
    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        String inputString = context.getInputString();
        String rawArgs = CommandUtil.stripCommandName(inputString).trim();
        
        if (rawArgs.isEmpty()) {
            context.sendMessage(MESSAGE_USAGE);
            return;
        }
        
        String[] parts = rawArgs.split("\\s+", 2);
        String action = parts[0].toLowerCase();
        
        if ("search".equals(action)) {
            if (parts.length < 2 || parts[1].trim().isEmpty()) {
                context.sendMessage(Message.raw("Usage: /pathfinder search <biome>"));
                return;
            }
            String biomeName = parts[1].trim();
            handleSearch(context, store, ref, world, biomeName);
        } else if ("clear".equals(action)) {
            String biomeName = parts.length >= 2 && !parts[1].trim().isEmpty() ? parts[1].trim() : null;
            handleClear(context, store, ref, world, biomeName);
        } else if ("list".equals(action)) {
            handleList(context, store, ref, world);
        } else {
            context.sendMessage(MESSAGE_USAGE);
        }
    }
    
    private void handleSearch(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world,
            @Nonnull String biomeName
    ) {
        // Get player position
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Unable to get player position."));
            return;
        }
        
        Vector3d position = transform.getPosition();
        int startX = (int)position.getX();
        int startZ = (int)position.getZ();
        
        // First, check what biome the player is currently in
        IWorldGen worldGen = world.getChunkStore().getGenerator();
        if (worldGen instanceof ChunkGenerator generator) {
            try {
                int seed = (int)world.getWorldConfig().getSeed();
                ZoneBiomeResult currentResult = generator.getZoneBiomeResultAt(seed, startX, startZ);
                Biome currentBiome = currentResult.getBiome();
                String currentBiomeName = currentBiome.getName();
                context.sendMessage(Message.raw("You are currently in biome: '" + currentBiomeName + "'"));
                context.sendMessage(Message.raw("Searching for biome: '" + biomeName + "'"));
            } catch (Exception e) {
                context.sendMessage(Message.raw("Error checking current biome: " + e.getMessage()));
            }
        } else {
            context.sendMessage(Message.raw("Warning: World generator is not ChunkGenerator (v1). Type: " + worldGen.getClass().getName()));
        }
        
        // Perform search asynchronously
        // Note: AbstractPlayerCommand already runs in async context, so we use world's executor
        CompletableFuture.supplyAsync(() -> {
            try {
                com.hypixel.hytale.logger.HytaleLogger logger = com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();
                logger.atInfo().log("Starting async biome search for '%s'", biomeName);
                int[] result = PathfinderSearchUtil.searchForBiome(world, startX, startZ, biomeName);
                logger.atInfo().log("Biome search completed, result: %s", result != null ? "found" : "not found");
                return result;
            } catch (Exception e) {
                com.hypixel.hytale.logger.HytaleLogger logger = com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();
                logger.atSevere().log("Error during biome search: %s", e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, world).thenAcceptAsync(result -> {
            com.hypixel.hytale.logger.HytaleLogger callbackLogger = com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();
            callbackLogger.atInfo().log("Marker creation callback started, result is null: %s", result == null);
            try {
                if (result == null) {
                    context.sendMessage(MESSAGE_BIOME_NOT_FOUND);
                    return;
                }
                
                int targetX = result[0];
                int targetZ = result[1];
            
            // Calculate distance
            double distance = Math.sqrt(Math.pow(targetX - startX, 2) + Math.pow(targetZ - startZ, 2));
            
            // Create marker - do this synchronously to ensure it's added before world map update
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            if (playerComponent == null) {
                context.sendMessage(Message.raw("Unable to get player component."));
                return;
            }
            
            PlayerWorldData perWorldData = playerComponent.getPlayerConfigData().getPerWorldData(world.getName());
            
            String markerId = "pathfinder_" + biomeName + "_" + System.currentTimeMillis();
            MapMarker marker = new MapMarker(
                    markerId,
                    biomeName,
                    "Coordinate.png", // markerImage - using standard marker icon
                    new Transform(
                            new Position(targetX, 128.0, targetZ),
                            new Direction(0.0F, 0.0F, 0.0F) // orientation (yaw, pitch, roll) - 0 for no rotation
                    ),
                    null // contextMenuItems
            );
            
            MapMarker[] existingMarkers = perWorldData.getWorldMapMarkers();
            MapMarker[] newMarkers = ArrayUtil.append(existingMarkers, marker);
            perWorldData.setWorldMapMarkers(newMarkers);
            
            // Verify marker was added
            MapMarker[] verifyMarkers = perWorldData.getWorldMapMarkers();
            boolean markerFound = false;
            if (verifyMarkers != null) {
                for (MapMarker m : verifyMarkers) {
                    if (markerId.equals(m.id)) {
                        markerFound = true;
                        break;
                    }
                }
            }
            
            // Log for debugging
            callbackLogger.atInfo().log("Created marker: id=%s, name=%s, position=(%d, %d), distance=%.1f, stored=%s, totalMarkers=%d", 
                    markerId, biomeName, targetX, targetZ, distance, markerFound, 
                    verifyMarkers != null ? verifyMarkers.length : 0);
            
            // Log marker details
            if (marker.transform != null && marker.transform.position != null) {
                callbackLogger.atInfo().log("Marker transform: position=(%.2f, %.2f, %.2f)", 
                        marker.transform.position.x, marker.transform.position.y, marker.transform.position.z);
            } else {
                callbackLogger.atWarning().log("Marker transform or position is null!");
            }
            
            // Send confirmation with details
            context.sendMessage(MESSAGE_BIOME_FOUND);
            context.sendMessage(Message.raw("Marker placed at coordinates: (" + targetX + ", " + targetZ + ")"));
            context.sendMessage(Message.raw("Distance: " + String.format("%.1f", distance) + " blocks away"));
            context.sendMessage(Message.raw("Marker ID: " + markerId));
            context.sendMessage(Message.raw("Total markers stored: " + (verifyMarkers != null ? verifyMarkers.length : 0)));
            
            // Try to get world map tracker and verify it can see the marker
            try {
                com.hypixel.hytale.server.core.universe.world.WorldMapTracker tracker = playerComponent.getWorldMapTracker();
                if (tracker != null) {
                    // Get current player chunk position for debugging
                    TransformComponent transformComp = store.getComponent(ref, TransformComponent.getComponentType());
                    if (transformComp != null) {
                        Vector3d pos = transformComp.getPosition();
                        int playerChunkX = (int)pos.getX() >> 5; // Divide by 32 (chunk size)
                        int playerChunkZ = (int)pos.getZ() >> 5;
                        int markerChunkX = targetX >> 5;
                        int markerChunkZ = targetZ >> 5;
                        callbackLogger.atInfo().log("Player chunk: (%d, %d), Marker chunk: (%d, %d)", 
                                playerChunkX, playerChunkZ, markerChunkX, markerChunkZ);
                    }
                }
            } catch (Exception e) {
                callbackLogger.atWarning().log("Error accessing world map tracker: %s", e.getMessage());
            }
            } catch (Exception e) {
                callbackLogger.atSevere().log("Error in marker creation callback: %s", e.getMessage());
                e.printStackTrace();
                try {
                    context.sendMessage(Message.raw("Error creating marker: " + e.getMessage()));
                } catch (Exception e2) {
                    callbackLogger.atSevere().log("Error sending error message: %s", e2.getMessage());
                }
            }
        }, world);
    }
    
    private void handleClear(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world,
            @Nullable String biomeName
    ) {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            context.sendMessage(Message.raw("Unable to get player component."));
            return;
        }
        
        PlayerWorldData perWorldData = playerComponent.getPlayerConfigData().getPerWorldData(world.getName());
        MapMarker[] existingMarkers = perWorldData.getWorldMapMarkers();
        
        if (existingMarkers == null || existingMarkers.length == 0) {
            context.sendMessage(MESSAGE_NO_MARKERS);
            return;
        }
        
        List<MapMarker> filtered = new ArrayList<>();
        boolean found = false;
        
        if (biomeName != null) {
            // Clear specific biome marker
            for (MapMarker marker : existingMarkers) {
                if (isPathfinderMarker(marker) && biomeName.equals(marker.name)) {
                    found = true;
                    // Skip this marker (don't add to filtered list)
                } else {
                    filtered.add(marker);
                }
            }
            
            if (!found) {
                context.sendMessage(MESSAGE_SPECIFIC_BIOME_NOT_FOUND);
                return;
            }
        } else {
            // Clear all pathfinder markers
            for (MapMarker marker : existingMarkers) {
                if (!isPathfinderMarker(marker)) {
                    filtered.add(marker);
                }
            }
        }
        
        MapMarker[] newMarkers = filtered.toArray(new MapMarker[0]);
        perWorldData.setWorldMapMarkers(newMarkers.length > 0 ? newMarkers : null);
        context.sendMessage(MESSAGE_MARKER_CLEARED);
    }
    
    private void handleList(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world
    ) {
        context.sendMessage(MESSAGE_LISTING_BIOMES);
        
        // Get all biomes from the registry (much better than sampling!)
        CompletableFuture.supplyAsync(() -> {
            return PathfinderSearchUtil.getAllBiomes(world);
        }).thenAccept(biomes -> {
            if (biomes == null || biomes.isEmpty()) {
                context.sendMessage(Message.raw("No biomes found or world generator not supported."));
                return;
            }
            
            context.sendMessage(Message.raw("Found " + biomes.size() + " unique biome types:"));
            for (String biomeName : biomes) {
                context.sendMessage(Message.raw("  - " + biomeName));
            }
        });
    }
    
    private boolean isPathfinderMarker(@Nonnull MapMarker marker) {
        return marker.id != null && marker.id.startsWith("pathfinder_");
    }
}
