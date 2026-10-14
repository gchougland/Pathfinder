package com.hexvane.pathfinder;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public class PathfinderSearchService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static void searchForBiome(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world,
            @Nonnull String biomeName,
            @Nonnull Consumer<Message> messageCallback
    ) {
        searchForBiome(store, ref, world, biomeName, PathfinderSearchUtil.DEFAULT_MAX_RADIUS, messageCallback);
    }

    public static void searchForBiome(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world,
            @Nonnull String biomeName,
            int maxRadius,
            @Nonnull Consumer<Message> messageCallback
    ) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            messageCallback.accept(Message.translation("server.pathfinder.noPosition"));
            return;
        }

        Vector3d position = transform.getPosition();
        int startX = (int) position.x;
        int startZ = (int) position.z;

        ChunkGenerator generator = PathfinderSearchUtil.asChunkGenerator(world);
        if (generator == null) {
            messageCallback.accept(Message.translation("server.pathfinder.noGenerator"));
            return;
        }

        String canonicalName = PathfinderSearchUtil.canonicalBiomeName(world, biomeName);
        if (canonicalName == null) {
            messageCallback.accept(
                    Message.translation("server.pathfinder.notFoundInWorld").param("name", biomeName)
            );
            Set<String> biomes = PathfinderSearchUtil.getAllBiomes(world);
            if (biomes != null) {
                PathfinderSearchUtil.sendDidYouMean(messageCallback, biomeName, biomes);
            }
            return;
        }

        int seed = (int) world.getWorldConfig().getSeed();
        try {
            String currentBiomeName = generator.getZoneBiomeResultAt(seed, startX, startZ).getBiome().getName();
            messageCallback.accept(
                    Message.translation("server.pathfinder.currentBiome").param("biome", currentBiomeName)
            );
        } catch (Exception e) {
            LOGGER.atFine().log("Unable to read current biome: %s", e.getMessage());
        }
        messageCallback.accept(Message.translation("server.pathfinder.searching").param("biome", canonicalName));

        CompletableFuture.supplyAsync(() -> {
            try {
                return PathfinderSearchUtil.searchForBiome(
                        generator, seed, startX, startZ, canonicalName, maxRadius
                );
            } catch (Exception e) {
                LOGGER.atSevere().log("Error during biome search: %s", e.getMessage());
                return null;
            }
        }, ForkJoinPool.commonPool()).thenAcceptAsync(result -> {
            try {
                if (result == null) {
                    messageCallback.accept(
                            Message.translation("server.pathfinder.biomeNotFound")
                                    .param("name", canonicalName)
                                    .param("radius", maxRadius)
                    );
                    return;
                }

                Player playerComponent = store.getComponent(ref, Player.getComponentType());
                if (playerComponent == null) {
                    messageCallback.accept(Message.translation("server.pathfinder.noPlayer"));
                    return;
                }

                double distance = Math.hypot(result.x() - startX, result.z() - startZ);
                MapMarker marker = PathfinderMarkerFactory.createForBiome(canonicalName, result.x(), result.z());
                PathfinderMarkerStorage.addMarker(world.getName(), playerComponent, marker);

                messageCallback.accept(Message.translation("server.pathfinder.biomeFound"));
                messageCallback.accept(
                        Message.translation("server.pathfinder.markerPlaced")
                                .param("x", result.x())
                                .param("z", result.z())
                );
                messageCallback.accept(
                        Message.translation("server.pathfinder.distance").param("distance", String.format("%.1f", distance))
                );
            } catch (Exception e) {
                LOGGER.atSevere().log("Error in marker creation callback: %s", e.getMessage());
                messageCallback.accept(
                        Message.translation("server.pathfinder.markerError").param("error", e.getMessage())
                );
            }
        }, world);
    }
}
