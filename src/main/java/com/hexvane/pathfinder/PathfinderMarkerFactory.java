package com.hexvane.pathfinder;

import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import javax.annotation.Nonnull;

/** Builds MapMarker instances for the current Hytale API. */
public final class PathfinderMarkerFactory {

    private PathfinderMarkerFactory() {
    }

    @Nonnull
    public static MapMarker create(
            @Nonnull String id,
            @Nonnull String name,
            @Nonnull String iconPath,
            double x, double y, double z
    ) {
        Transform transform = new Transform(
                new Position(x, y, z),
                new Direction(0.0F, 0.0F, 0.0F)
        );
        MapMarker marker = new MapMarker();
        marker.id = id;
        marker.name = Message.raw(name).getFormattedMessage();
        marker.markerImage = iconPath;
        marker.transform = transform;
        return marker;
    }
}
