package com.hexvane.pathfinder;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * In-memory storage for pathfinder markers per player per world.
 * Replaces the removed PlayerWorldData.getWorldMapMarkers/setWorldMapMarkers API.
 */
public final class PathfinderMarkerStorage {
    private static final Map<String, List<MapMarker>> STORAGE = new ConcurrentHashMap<>();

    private PathfinderMarkerStorage() {
    }

    private static String key(@Nonnull String worldName, @Nonnull Object playerKey) {
        return worldName + ":" + playerKey;
    }

    /**
     * Stable key for a player. Uses only Ref.getIndex() so it is safe to call from any thread
     * (e.g. WorldMap thread in MarkerProvider.update). Must not call Store.getComponent() here
     * because Store is thread-affined to the World thread.
     */
    public static String playerKey(@Nonnull Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return "player@" + System.identityHashCode(player);
        }
        return String.valueOf(ref.getIndex());
    }

    @Nonnull
    public static MapMarker[] getMarkers(@Nonnull String worldName, @Nonnull String playerKey) {
        List<MapMarker> list = STORAGE.get(key(worldName, playerKey));
        return list == null ? new MapMarker[0] : list.toArray(new MapMarker[0]);
    }

    @Nonnull
    public static MapMarker[] getMarkers(@Nonnull String worldName, @Nonnull Player player) {
        return getMarkers(worldName, playerKey(player));
    }

    public static void setMarkers(@Nonnull String worldName, @Nonnull String playerKey, MapMarker[] markers) {
        String k = key(worldName, playerKey);
        if (markers == null || markers.length == 0) {
            STORAGE.remove(k);
            return;
        }
        List<MapMarker> list = new ArrayList<>(markers.length);
        for (MapMarker m : markers) {
            list.add(m);
        }
        STORAGE.put(k, list);
    }

    public static void setMarkers(@Nonnull String worldName, @Nonnull Player player, MapMarker[] markers) {
        setMarkers(worldName, playerKey(player), markers);
    }

    public static void addMarker(@Nonnull String worldName, @Nonnull String playerKey, @Nonnull MapMarker marker) {
        String k = key(worldName, playerKey);
        STORAGE.computeIfAbsent(k, x -> new ArrayList<>()).add(marker);
    }

    public static void addMarker(@Nonnull String worldName, @Nonnull Player player, @Nonnull MapMarker marker) {
        addMarker(worldName, playerKey(player), marker);
    }
}
