package com.hexvane.pathfinder;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hexvane.pathfinder.gui.PathfinderPageSupplier;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;

public class PathfinderPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public PathfinderPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        this.getCommandRegistry().registerCommand(new PathfinderCommand());
        
        // Register the custom UI page supplier
        OpenCustomUIInteraction.PAGE_CODEC.register(
                "PathfinderBiomeSearch",
                PathfinderPageSupplier.class,
                PathfinderPageSupplier.CODEC
        );
        LOGGER.atInfo().log("Registered Pathfinder custom UI page supplier");
        
        // Register event listener for when worlds are added
        this.getEventRegistry().registerGlobal(
                AddWorldEvent.class,
                (AddWorldEvent event) -> {
                    try {
                        event.getWorld().getWorldMapManager().addMarkerProvider("pathfinder", PathfinderMarkerProvider.INSTANCE);
                        LOGGER.atInfo().log("Registered Pathfinder marker provider for world: %s", event.getWorld().getName());
                    } catch (Exception e) {
                        LOGGER.atWarning().log("Failed to register marker provider for world %s: %s", 
                                event.getWorld().getName(), e.getMessage());
                    }
                }
        );
    }

    @Override
    protected void start() {
        // Register marker provider for existing worlds
        // This handles worlds that were already loaded before the plugin started
        try {
            Universe universe = Universe.get();
            if (universe != null) {
                for (var worldEntry : universe.getWorlds().entrySet()) {
                    var world = worldEntry.getValue();
                    var worldMapManager = world.getWorldMapManager();
                    if (worldMapManager != null) {
                        worldMapManager.addMarkerProvider("pathfinder", PathfinderMarkerProvider.INSTANCE);
                        LOGGER.atInfo().log("Registered Pathfinder marker provider for world: %s", world.getName());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to register marker provider: %s", e.getMessage());
        }
    }
}
