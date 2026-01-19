package com.hexvane.pathfinder.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hexvane.pathfinder.PathfinderSearchService;
import com.hexvane.pathfinder.PathfinderSearchUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class PathfinderBiomeSearchPage extends InteractiveCustomUIPage<PathfinderBiomeSearchPage.PathfinderEventData> {
    private final World world;
    private Set<String> allBiomes;
    private String searchFilter = "";
    private String selectedBiome = null;
    private boolean biomesLoaded = false;

    public PathfinderBiomeSearchPage(@Nonnull PlayerRef playerRef, @Nonnull World world) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PathfinderEventData.CODEC);
        this.world = world;
        loadBiomes();
    }

    private void loadBiomes() {
        CompletableFuture.supplyAsync(() -> {
            return PathfinderSearchUtil.getAllBiomes(this.world);
        }, this.world).thenAcceptAsync(biomes -> {
            if (biomes != null) {
                this.allBiomes = biomes;
                this.biomesLoaded = true;
                this.rebuild();
            }
        }, this.world);
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        // Append the UI layout
        commandBuilder.append("Pages/PathfinderBiomeSearchPage.ui");
        
        // Clear the biome list
        commandBuilder.clear("#BiomeList");
        
        // Set search filter value - CompactTextField uses .Value property
        commandBuilder.set("#SearchInput.Value", this.searchFilter);
        
        // Set search button disabled state based on selection
        // The TextButton style already has a disabled state defined, so we just set Disabled
        if (this.selectedBiome != null) {
            commandBuilder.set("#SearchButton.Disabled", false);
        } else {
            commandBuilder.set("#SearchButton.Disabled", true);
        }
        
        // Build filtered biome list
        if (this.biomesLoaded && this.allBiomes != null) {
            List<String> filteredBiomes = new ArrayList<>();
            for (String biome : this.allBiomes) {
                if (this.searchFilter.isEmpty() || biome.toLowerCase().contains(this.searchFilter.toLowerCase())) {
                    filteredBiomes.add(biome);
                }
            }
            
            // Add biome entries to the list using the biome entry template
            for (int i = 0; i < filteredBiomes.size(); i++) {
                String biome = filteredBiomes.get(i);
                boolean isSelected = biome.equals(this.selectedBiome);
                
                // Append biome entry using the template
                commandBuilder.append("#BiomeList", "Pages/PathfinderBiomeEntry.ui");
                
                // Set the biome name - use index-based selector with .Text property
                // When we append a UI file, it becomes a direct child, so the selector is just the index
                String entrySelector = "#BiomeList[" + i + "]";
                commandBuilder.set(entrySelector + " #Name.Text", biome);
                
                // Set selected state (highlight background) - apply selected style
                // Use a solid color for selected items (buttons expect PatchStyle or solid colors, not colors with opacity)
                if (isSelected) {
                    commandBuilder.set(entrySelector + ".Style.Default.Background", "#4a90e2");
                }
                // For non-selected items, don't set background - let the UI file default handle it
                
                // Register click event for this biome entry
                // The appended element itself is the Button, so we bind to the entry selector
                eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        entrySelector,
                        EventData.of("Biome", biome),
                        false
                );
            }
        }
        // Note: When loading, we just leave the list empty - it will show nothing until biomes are loaded
        
        // Register events
        // Search input value changed - use reference to get the value
        // The @ prefix indicates it's a reference that will be resolved by the client
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("@SearchQuery", "#SearchInput.Value"),
                false
        );
        
        // Search button click
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SearchButton",
                EventData.of("Action", "Search"),
                false
        );
        
        // Cancel button click
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CancelButton",
                EventData.of("Action", "Cancel"),
                false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PathfinderEventData data) {
        // Handle events from the client
        if (data.action != null) {
            switch (data.action) {
                case "Search":
                    if (this.selectedBiome != null) {
                        this.performSearch(this.selectedBiome);
                    }
                    break;
                case "Cancel":
                    this.close();
                    break;
            }
        } else if (data.biome != null) {
            // Biome entry was clicked - select it
            this.selectedBiome = data.biome;
            this.updateBiomeListAndButton(ref, store);
        } else if (data.searchQuery != null) {
            // Search filter changed - update only the biome list to preserve search input focus
            this.searchFilter = data.searchQuery;
            // Clear selection if the selected biome is no longer in the filtered list
            if (this.selectedBiome != null && this.allBiomes != null) {
                List<String> filteredBiomes = new ArrayList<>();
                for (String biome : this.allBiomes) {
                    if (this.searchFilter.isEmpty() || biome.toLowerCase().contains(this.searchFilter.toLowerCase())) {
                        filteredBiomes.add(biome);
                    }
                }
                if (!filteredBiomes.contains(this.selectedBiome)) {
                    this.selectedBiome = null;
                }
            }
            this.updateBiomeList(ref, store);
        }
    }

    private void updateBiomeList(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        // Update only the biome list without rebuilding the entire UI (preserves search input focus)
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        
        // Clear and rebuild the biome list
        commandBuilder.clear("#BiomeList");
        
        if (this.biomesLoaded && this.allBiomes != null) {
            List<String> filteredBiomes = new ArrayList<>();
            for (String biome : this.allBiomes) {
                if (this.searchFilter.isEmpty() || biome.toLowerCase().contains(this.searchFilter.toLowerCase())) {
                    filteredBiomes.add(biome);
                }
            }
            
            // Add biome entries
            for (int i = 0; i < filteredBiomes.size(); i++) {
                String biome = filteredBiomes.get(i);
                boolean isSelected = biome.equals(this.selectedBiome);
                
                commandBuilder.append("#BiomeList", "Pages/PathfinderBiomeEntry.ui");
                String entrySelector = "#BiomeList[" + i + "]";
                commandBuilder.set(entrySelector + " #Name.Text", biome);
                
                // Set selected state visual indication
                // Use a solid color for selected items
                if (isSelected) {
                    commandBuilder.set(entrySelector + ".Style.Default.Background", "#4a90e2");
                }
                // For non-selected items, don't set background - let the UI file default handle it
                
                eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        entrySelector,
                        EventData.of("Biome", biome),
                        false
                );
            }
        }
        
        // Update search button disabled state based on selection
        // The TextButton style already has a disabled state defined, so we just set Disabled
        if (this.selectedBiome != null) {
            commandBuilder.set("#SearchButton.Disabled", false);
        } else {
            commandBuilder.set("#SearchButton.Disabled", true);
        }
        
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }
    
    private void updateBiomeListAndButton(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        // Update biome list and search button state
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        
        // Clear and rebuild the biome list
        commandBuilder.clear("#BiomeList");
        
        if (this.biomesLoaded && this.allBiomes != null) {
            List<String> filteredBiomes = new ArrayList<>();
            for (String biome : this.allBiomes) {
                if (this.searchFilter.isEmpty() || biome.toLowerCase().contains(this.searchFilter.toLowerCase())) {
                    filteredBiomes.add(biome);
                }
            }
            
            // Add biome entries
            for (int i = 0; i < filteredBiomes.size(); i++) {
                String biome = filteredBiomes.get(i);
                boolean isSelected = biome.equals(this.selectedBiome);
                
                commandBuilder.append("#BiomeList", "Pages/PathfinderBiomeEntry.ui");
                String entrySelector = "#BiomeList[" + i + "]";
                commandBuilder.set(entrySelector + " #Name.Text", biome);
                
                // Set selected state visual indication
                // Use a solid color for selected items
                if (isSelected) {
                    commandBuilder.set(entrySelector + ".Style.Default.Background", "#4a90e2");
                }
                // For non-selected items, don't set background - let the UI file default handle it
                
                eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        entrySelector,
                        EventData.of("Biome", biome),
                        false
                );
            }
        }
        
        // Update search button disabled state based on selection
        // The TextButton style already has a disabled state defined, so we just set Disabled
        if (this.selectedBiome != null) {
            commandBuilder.set("#SearchButton.Disabled", false);
        } else {
            commandBuilder.set("#SearchButton.Disabled", true);
        }
        
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }
    
    private void performSearch(@Nonnull String biomeName) {
        Ref<EntityStore> ref = this.playerRef.getReference();
        if (ref == null) return;
        
        Store<EntityStore> store = ref.getStore();
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) return;
        
        this.close();
        
        // Use the shared search service
        PathfinderSearchService.searchForBiome(
                store,
                ref,
                this.world,
                biomeName,
                playerComponent::sendMessage
        );
    }

    public static class PathfinderEventData {
        static final String KEY_ACTION = "Action";
        static final String KEY_BIOME = "Biome";
        static final String KEY_SEARCH_QUERY = "SearchQuery";
        
        public static final BuilderCodec<PathfinderEventData> CODEC = BuilderCodec.builder(
                PathfinderEventData.class, PathfinderEventData::new
        )
        .addField(new KeyedCodec<>("Action", Codec.STRING), (data, s) -> data.action = s, data -> data.action)
        .addField(new KeyedCodec<>("Biome", Codec.STRING), (data, s) -> data.biome = s, data -> data.biome)
        .addField(new KeyedCodec<>("@SearchQuery", Codec.STRING), (data, s) -> data.searchQuery = s, data -> data.searchQuery)
        .build();
        
        private String action;
        private String biome;
        private String searchQuery;
    }
}
