package com.hexvane.pathfinder;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import javax.annotation.Nonnull;

/** Builds MapMarker instances with the Update 6 MapMarkerBuilder API. */
public final class PathfinderMarkerFactory {
    public static final String ID_PREFIX = "pathfinder_";
    public static final String DEFAULT_ICON = "Coordinate.png";
    public static final double DEFAULT_MARKER_Y = 128.0;

    private PathfinderMarkerFactory() {
    }

    @Nonnull
    public static String markerId(@Nonnull String biomeName) {
        return ID_PREFIX + biomeName + "_" + System.currentTimeMillis();
    }

    @Nonnull
    public static MapMarker createForBiome(@Nonnull String biomeName, double x, double z) {
        return create(markerId(biomeName), biomeName, DEFAULT_ICON, x, DEFAULT_MARKER_Y, z);
    }

    @Nonnull
    public static MapMarker create(
            @Nonnull String id,
            @Nonnull String name,
            @Nonnull String iconPath,
            double x, double y, double z
    ) {
        return new MapMarkerBuilder(id, iconPath, new Transform(x, y, z, 0f, 0f, 0f))
                .withCustomName(name)
                .build();
    }

    public static boolean isPathfinderMarker(@Nonnull MapMarker marker) {
        return marker.id != null && marker.id.startsWith(ID_PREFIX);
    }

    public static boolean matchesBiome(@Nonnull MapMarker marker, @Nonnull String biomeName) {
        if (!isPathfinderMarker(marker)) {
            return false;
        }
        String rest = marker.id.substring(ID_PREFIX.length());
        int lastUnderscore = rest.lastIndexOf('_');
        if (lastUnderscore <= 0) {
            return false;
        }
        return rest.substring(0, lastUnderscore).equalsIgnoreCase(biomeName);
    }
}
