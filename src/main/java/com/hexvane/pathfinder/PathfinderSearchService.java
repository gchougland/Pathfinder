package com.hexvane.pathfinder;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

public class PathfinderSearchService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Message MESSAGE_BIOME_NOT_FOUND = Message.raw("Biome not found within search radius.");
    private static final Message MESSAGE_BIOME_FOUND = Message.raw("Biome found! Marker placed on map.");

    /**
     * Performs a biome search and creates a marker if found.
     * 
     * @param store The entity store
     * @param ref The player entity reference
     * @param world The world to search in
     * @param biomeName The biome to search for
     * @param messageCallback Callback to send messages to the player (can be null)
     */
    public static void searchForBiome(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world,
            @Nonnull String biomeName,
            @Nonnull Consumer<Message> messageCallback
    ) {
        // Get player position
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            messageCallback.accept(Message.raw("Unable to get player position."));
            return;
        }

        Vector3d position = transform.getPosition();
        int startX = (int)position.getX();
        int startZ = (int)position.getZ();

        // Perform search asynchronously
        CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.atInfo().log("Starting async biome search for '%s'", biomeName);
                int[] result = PathfinderSearchUtil.searchForBiome(world, startX, startZ, biomeName);
                LOGGER.atInfo().log("Biome search completed, result: %s", result != null ? "found" : "not found");
                return result;
            } catch (Exception e) {
                LOGGER.atSevere().log("Error during biome search: %s", e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, world).thenAcceptAsync(result -> {
            try {
                if (result == null) {
                    messageCallback.accept(MESSAGE_BIOME_NOT_FOUND);
                    return;
                }

                int targetX = result[0];
                int targetZ = result[1];
                double distance = Math.sqrt(Math.pow(targetX - startX, 2) + Math.pow(targetZ - startZ, 2));

                // Create marker
                Player playerComponent = store.getComponent(ref, Player.getComponentType());
                if (playerComponent == null) {
                    messageCallback.accept(Message.raw("Unable to get player component."));
                    return;
                }

                String markerId = "pathfinder_" + biomeName + "_" + System.currentTimeMillis();
                MapMarker marker = PathfinderMarkerFactory.create(
                        markerId,
                        biomeName,
                        "Coordinate.png",
                        targetX, 128.0, targetZ
                );
                PathfinderMarkerStorage.addMarker(world.getName(), playerComponent, marker);

                // Send confirmation
                messageCallback.accept(MESSAGE_BIOME_FOUND);
                messageCallback.accept(Message.raw("Marker placed at coordinates: (" + targetX + ", " + targetZ + ")"));
                messageCallback.accept(Message.raw("Distance: " + String.format("%.1f", distance) + " blocks away"));
            } catch (Exception e) {
                LOGGER.atSevere().log("Error in marker creation callback: %s", e.getMessage());
                e.printStackTrace();
                messageCallback.accept(Message.raw("Error creating marker: " + e.getMessage()));
            }
        }, world);
    }
}
