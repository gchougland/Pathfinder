package com.hexvane.pathfinder;

import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import javax.annotation.Nonnull;

public class PathfinderMarkerProvider implements WorldMapManager.MarkerProvider {
    public static final PathfinderMarkerProvider INSTANCE = new PathfinderMarkerProvider();

    private PathfinderMarkerProvider() {
    }

    @Override
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
        // Use Player as key - same instance is used when adding markers from command/GUI
        MapMarker[] markers = PathfinderMarkerStorage.getMarkers(world.getName(), player);
        for (MapMarker marker : markers) {
            if (marker.id != null && marker.id.startsWith("pathfinder_")) {
                // Use addIgnoreViewDistance so far-away biome markers still show on the map/compass
                collector.addIgnoreViewDistance(marker);
            }
        }
    }
}
