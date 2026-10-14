package com.hexvane.pathfinder;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

public class PathfinderCommand extends AbstractCommandCollection {
    private static final SingleArgumentType<String> BIOME_NAME = new SingleArgumentType<>(
            "server.commands.parsing.argtype.string.name",
            "server.commands.parsing.argtype.string.usage"
    ) {
        @Override
        public String parse(String input, ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
                @Nonnull CommandSender sender,
                @Nonnull String textAlreadyEntered,
                int numParametersTyped,
                @Nonnull SuggestionResult result
        ) {
            if (!(sender instanceof PlayerRef playerRef)) {
                return;
            }
            var worldUuid = playerRef.getWorldUuid();
            if (worldUuid == null) {
                return;
            }
            var world = Universe.get().getWorld(worldUuid);
            if (world == null) {
                return;
            }
            Set<String> biomes = PathfinderSearchUtil.getAllBiomes(world);
            if (biomes == null) {
                return;
            }
            String prefix = textAlreadyEntered.toLowerCase();
            for (String biome : biomes) {
                if (prefix.isEmpty() || biome.toLowerCase().contains(prefix)) {
                    result.suggest(biome);
                }
            }
        }

        @Override
        public int getSuggestionValueCount() {
            return 1;
        }
    };

    public PathfinderCommand() {
        super("pathfinder", "server.commands.pathfinder.desc");
        this.addAliases("pf");
        this.setPermissionGroups(HytalePermissionsProvider.GROUP_ADVENTURER);
        this.addSubCommand(new SearchCommand());
        this.addSubCommand(new ClearCommand());
        this.addSubCommand(new ListCommand());
    }

    private static final class SearchCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> biomeNameArg = withRequiredArg(
                "biome",
                "server.commands.pathfinder.search.biome.desc",
                BIOME_NAME
        );
        @Nonnull
        private final OptionalArg<Integer> radiusArg = withOptionalArg(
                "radius",
                "server.commands.pathfinder.search.radius.desc",
                ArgTypes.INTEGER
        );

        SearchCommand() {
            super("search", "server.commands.pathfinder.search.desc");
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world
        ) {
            int maxRadius = PathfinderSearchUtil.DEFAULT_MAX_RADIUS;
            if (radiusArg.provided(context)) {
                maxRadius = PathfinderSearchUtil.resolveRadius(context.get(radiusArg));
                if (maxRadius < 0) {
                    context.sendMessage(
                            Message.translation("server.pathfinder.invalidRadius")
                                    .param("min", PathfinderSearchUtil.MIN_ALLOWED_RADIUS)
                                    .param("max", PathfinderSearchUtil.MAX_ALLOWED_RADIUS)
                    );
                    return;
                }
            }

            PathfinderSearchService.searchForBiome(
                    store,
                    ref,
                    world,
                    context.get(biomeNameArg),
                    maxRadius,
                    context::sendMessage
            );
        }
    }

    private static final class ClearCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> biomeNameArg = withOptionalArg(
                "biome",
                "server.commands.pathfinder.clear.biome.desc",
                BIOME_NAME
        );

        ClearCommand() {
            super("clear", "server.commands.pathfinder.clear.desc");
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world
        ) {
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            if (playerComponent == null) {
                context.sendMessage(Message.translation("server.pathfinder.noPlayer"));
                return;
            }

            MapMarker[] existingMarkers = PathfinderMarkerStorage.getMarkers(world.getName(), playerComponent);
            if (existingMarkers.length == 0) {
                context.sendMessage(Message.translation("server.pathfinder.noMarkers"));
                return;
            }

            List<MapMarker> filtered = new ArrayList<>();
            if (biomeNameArg.provided(context)) {
                String biomeName = context.get(biomeNameArg);
                boolean found = false;
                for (MapMarker marker : existingMarkers) {
                    if (PathfinderMarkerFactory.matchesBiome(marker, biomeName)) {
                        found = true;
                        continue;
                    }
                    filtered.add(marker);
                }
                if (!found) {
                    context.sendMessage(Message.translation("server.pathfinder.specificBiomeNotFound"));
                    return;
                }
            } else {
                for (MapMarker marker : existingMarkers) {
                    if (!PathfinderMarkerFactory.isPathfinderMarker(marker)) {
                        filtered.add(marker);
                    }
                }
            }

            MapMarker[] newMarkers = filtered.toArray(new MapMarker[0]);
            PathfinderMarkerStorage.setMarkers(world.getName(), playerComponent, newMarkers.length > 0 ? newMarkers : null);
            context.sendMessage(Message.translation("server.pathfinder.markerCleared"));
        }
    }

    private static final class ListCommand extends AbstractPlayerCommand {
        ListCommand() {
            super("list", "server.commands.pathfinder.list.desc");
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world
        ) {
            Set<String> biomes = PathfinderSearchUtil.getAllBiomes(world);
            if (biomes == null || biomes.isEmpty()) {
                context.sendMessage(Message.translation("server.pathfinder.noBiomes"));
                return;
            }

            context.sendMessage(Message.translation("server.pathfinder.biomeListHeader").param("count", biomes.size()));
            for (String biomeName : biomes) {
                context.sendMessage(Message.translation("server.pathfinder.biomeListEntry").param("biome", biomeName));
            }
        }
    }
}
