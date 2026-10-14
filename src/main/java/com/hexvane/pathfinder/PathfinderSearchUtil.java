package com.hexvane.pathfinder;

import com.hypixel.hytale.common.util.StringUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.iterator.SpiralIterator;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.worldgen.biome.Biome;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.hypixel.hytale.server.worldgen.zone.Zone;
import com.hypixel.hytale.server.worldgen.zone.ZonePatternProvider;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PathfinderSearchUtil {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final int DEFAULT_MAX_RADIUS = 5000;
    public static final int MIN_ALLOWED_RADIUS = 32;
    public static final int MAX_ALLOWED_RADIUS = 100_000;

    public record BiomeLocation(int x, int z) {
    }

    @Nullable
    public static ChunkGenerator asChunkGenerator(@Nonnull World world) {
        IWorldGen worldGen = world.getChunkStore().getGenerator();
        if (worldGen instanceof ChunkGenerator generator) {
            return generator;
        }
        LOGGER.atWarning().log(
                "World generator is not ChunkGenerator (v1), it is: %s",
                worldGen.getClass().getName()
        );
        return null;
    }

    @Nullable
    public static BiomeLocation searchForBiome(
            @Nonnull World world,
            int startX,
            int startZ,
            @Nonnull String targetBiomeName
    ) {
        return searchForBiome(world, startX, startZ, targetBiomeName, DEFAULT_MAX_RADIUS);
    }

    @Nullable
    public static BiomeLocation searchForBiome(
            @Nonnull World world,
            int startX,
            int startZ,
            @Nonnull String targetBiomeName,
            int maxRadius
    ) {
        ChunkGenerator generator = asChunkGenerator(world);
        if (generator == null) {
            return null;
        }
        int seed = (int) world.getWorldConfig().getSeed();
        return searchForBiome(generator, seed, startX, startZ, targetBiomeName, maxRadius);
    }

    @Nullable
    public static BiomeLocation searchForBiome(
            @Nonnull ChunkGenerator generator,
            int seed,
            int startX,
            int startZ,
            @Nonnull String targetBiomeName,
            int maxRadius
    ) {
        LOGGER.atInfo().log(
                "Starting biome search: target='%s', start=(%d, %d), seed=%d, maxRadius=%d",
                targetBiomeName, startX, startZ, seed, maxRadius
        );

        try {
            String startBiomeName = generator.getZoneBiomeResultAt(seed, startX, startZ).getBiome().getName();
            if (targetBiomeName.equalsIgnoreCase(startBiomeName)) {
                LOGGER.atInfo().log("Found target biome at starting position!");
                return new BiomeLocation(startX, startZ);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to query biome at starting position: %s", e.getMessage());
        }

        int radiusInChunks = Math.max(1, maxRadius / ChunkUtil.SIZE);
        int chunkX = Math.floorDiv(startX, ChunkUtil.SIZE);
        int chunkZ = Math.floorDiv(startZ, ChunkUtil.SIZE);
        var iterator = new SpiralIterator(chunkX, chunkZ, radiusInChunks);
        int checkedCount = 0;

        while (iterator.hasNext()) {
            long key = iterator.next();
            int x = ChunkUtil.xOfChunkIndex(key) * ChunkUtil.SIZE;
            int z = ChunkUtil.zOfChunkIndex(key) * ChunkUtil.SIZE;
            checkedCount++;

            try {
                String biomeName = generator.getZoneBiomeResultAt(seed, x, z).getBiome().getName();
                if (targetBiomeName.equalsIgnoreCase(biomeName)) {
                    LOGGER.atInfo().log(
                            "Found target biome '%s' at (%d, %d) after checking %d chunks",
                            biomeName, x, z, checkedCount
                    );
                    return new BiomeLocation(x, z);
                }
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to query biome at (%d, %d): %s", x, z, e.getMessage());
            }
        }

        LOGGER.atWarning().log(
                "Biome '%s' not found within radius %d (checked %d chunks)",
                targetBiomeName, maxRadius, checkedCount
        );
        return null;
    }

    public static boolean biomeExists(@Nonnull World world, @Nonnull String name) {
        Set<String> biomes = getAllBiomes(world);
        if (biomes == null) {
            return false;
        }
        for (String biome : biomes) {
            if (name.equalsIgnoreCase(biome)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static String canonicalBiomeName(@Nonnull World world, @Nonnull String name) {
        Set<String> biomes = getAllBiomes(world);
        if (biomes == null) {
            return null;
        }
        for (String biome : biomes) {
            if (name.equalsIgnoreCase(biome)) {
                return biome;
            }
        }
        return null;
    }

    @Nullable
    public static Set<String> getAllBiomes(@Nonnull World world) {
        ChunkGenerator generator = asChunkGenerator(world);
        if (generator == null) {
            return null;
        }

        Set<String> biomes = new LinkedHashSet<>();
        try {
            ZonePatternProvider zonePatternProvider = generator.getZonePatternProvider();
            Zone[] zones = zonePatternProvider.getZones();
            for (Zone zone : zones) {
                for (Biome biome : zone.biomePatternGenerator().getBiomes()) {
                    biomes.add(biome.getName());
                }
            }
            LOGGER.atInfo().log("Found %d unique biomes across %d zones", biomes.size(), zones.length);
        } catch (Exception e) {
            LOGGER.atWarning().log("Error getting biomes from zone pattern provider: %s", e.getMessage());
            return null;
        }
        return biomes;
    }

    public static void sendDidYouMean(@Nonnull Consumer<Message> messageCallback, @Nonnull String input, @Nonnull Collection<String> validNames) {
        if (validNames.isEmpty()) {
            return;
        }
        List<String> suggestions = StringUtil.sortByFuzzyDistance(input, validNames, CommandUtil.RECOMMEND_COUNT);
        if (!suggestions.isEmpty()) {
            messageCallback.accept(
                    Message.translation("server.general.failed.didYouMean")
                            .param("choices", suggestions.toString())
            );
        }
    }

    public static int resolveRadius(int requestedRadius) {
        if (requestedRadius < MIN_ALLOWED_RADIUS || requestedRadius > MAX_ALLOWED_RADIUS) {
            return -1;
        }
        return requestedRadius;
    }
}
