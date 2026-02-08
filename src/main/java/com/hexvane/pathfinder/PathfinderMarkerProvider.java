package com.hexvane.pathfinder;

import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import javax.annotation.Nonnull;

public class PathfinderMarkerProvider implements WorldMapManager.MarkerProvider {
    public static final PathfinderMarkerProvider INSTANCE = new PathfinderMarkerProvider();

    private PathfinderMarkerProvider() {
    }

    @Override
    public void update(
            @Nonnull World world,
            @Nonnull MapMarkerTracker tracker,
            int chunkViewRadius,
            int playerChunkX,
            int playerChunkZ
    ) {
        Player player = tracker.getPlayer();
        PlayerWorldData perWorldData = player.getPlayerConfigData().getPerWorldData(world.getName());
        MapMarker[] worldMapMarkers = perWorldData.getWorldMapMarkers();
        if (worldMapMarkers != null) {
            for (MapMarker marker : worldMapMarkers) {
                // Only handle pathfinder markers
                if (marker.id != null && marker.id.startsWith("pathfinder_")) {
                    // Use -1 for unlimited visibility (always show regardless of distance)
                    tracker.trySendMarker(-1, playerChunkX, playerChunkZ, marker);
                }
            }
        }
    }
}
