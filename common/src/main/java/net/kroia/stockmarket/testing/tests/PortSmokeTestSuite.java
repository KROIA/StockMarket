package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.NbtCompat;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.TestSuite;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.data.DataManager;
import net.kroia.stockmarket.minecraft.block.StockMarketBlocks;
import net.kroia.stockmarket.minecraft.entity.custom.StockMarketDisplayBlockEntity;
import net.kroia.stockmarket.screen.widgets.DisplayCandlestickChart;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.villagertrading.VillagerTradeData;
import net.kroia.stockmarket.villagertrading.VillagerTradeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SM-288: per-version port smoke checks. Things a Minecraft/loader port tends to break silently that no other
 * suite covers: recipes loading, NeoForge mod-bus setup events, the villager sidecar file round trip and the
 * display block's group/save logic. Runs on master and slave, every cell.
 */
public class PortSmokeTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.PORT_SMOKE;
    }

    @Override
    public void registerTests() {
        addTest("recipes_loaded", this::test_recipesLoaded);
        addTest("neoforge_setup_events_fired", this::test_neoForgeSetupEvents);
        addTest("villager_sidecar_save_reload", this::test_villagerSidecarRoundTrip);
        addTest("display_merge_split_break", this::test_displayMergeSplitBreak);
        addTest("display_block_entity_save_load", this::test_displaySaveLoad);
        addTest("display_market_change_drops_viewport", this::test_displayMarketChangeDropsViewport); // SM-364
        addTest("display_client_market_change_drops_viewport", this::test_displayClientMarketChangeDropsViewport); // SM-364
        addTest("display_market_change_regroups", this::test_displayMarketChangeRegroups); // SM-360
        addTest("display_load_drops_untouched_default_view", this::test_displayLoadDropsUntouchedDefaultView); // SM-379
    }

    // ── Recipes ──

    /** Every recipe JSON StockMarket ships under data/stockmarket/recipe/ must be in the server's RecipeManager. */
    private TestResult test_recipesLoaded() {
        MinecraftServer server = getServer();
        if (server == null) return skip("no server");
        List<ResourceLocation> ids = new ArrayList<>(); // mc<=1.21.10
        //List<Identifier> ids = new ArrayList<>(); // mc>=1.21.11
        server.getResourceManager().listResources("recipe", p -> p.getPath().endsWith(".json"))
                .keySet().forEach(file -> {
                    if (!file.getNamespace().equals(StockMarketMod.MOD_ID)) return;
                    String path = file.getPath();
                    ids.add(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), // mc<=1.21.10
                    //ids.add(Identifier.fromNamespaceAndPath(file.getNamespace(), // mc>=1.21.11
                            path.substring("recipe/".length(), path.length() - ".json".length())));
                });
        TestResult r = assertTrue("no stockmarket recipe JSON found in the server resources (folder renamed?)", !ids.isEmpty());
        if (!r.passed()) return r;
        for (ResourceLocation id : ids) { // mc<=1.21.10
        //for (Identifier id : ids) { // mc>=1.21.11
            //r = assertTrue("recipe missing from RecipeManager: " + id, server.getRecipeManager().byKey(id).isPresent()); // mc<=1.21.1
            r = assertTrue("recipe missing from RecipeManager: " + id, server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id)).isPresent()); // mc>=1.21.2
            if (!r.passed()) return r;
        }
        return pass(ids.size() + " recipes loaded: " + ids);
    }

    // ── NeoForge mod bus ──

    /** NeoForgeSetup's mod-bus handlers ran (the bus registration changes between MC versions). */
    private TestResult test_neoForgeSetupEvents() {
        if (UtilitiesPlatform.getPlatformType() != UtilitiesPlatform.Type.NEOFORGE)
            return skip("NeoForge only");
        TestResult r = assertTrue("FMLCommonSetupEvent handler did not run", StockMarketMod.neoForgeCommonSetupFired);
        if (!r.passed()) return r;
        if (UtilitiesPlatform.isClient()) {
            r = assertTrue("FMLClientSetupEvent handler did not run", StockMarketMod.neoForgeClientSetupFired);
            if (!r.passed()) return r;
            return pass("common and client setup fired");
        }
        return pass("common setup fired (dedicated server, no client setup)");
    }

    // ── Villager sidecar ──

    /**
     * A fresh VillagerTradeManager saves villager_trades.nbt and a second one reads the stored offer back intact.
     * Works on the live world file, so its previous bytes are restored afterwards (or the file deleted if new).
     */
    private TestResult test_villagerSidecarRoundTrip() {
        MinecraftServer server = getServer();
        StockMarketModBackend.ServerInstances instances = StockMarketModBackend.getServerInstances();
        if (server == null || instances == null) return skip("no server instances");

        Path file = server.getWorldPath(LevelResource.ROOT).resolve(DataManager.BASE_PATH).resolve(VillagerTradeManager.DATA_FILE);
        byte[] backup;
        try {
            backup = Files.exists(file) ? Files.readAllBytes(file) : null;
        } catch (Exception e) {
            return fail("could not back up " + file + ": " + e);
        }
        try {
            UUID villager = UUID.randomUUID();
            MerchantOffer original = new MerchantOffer(new ItemCost(Items.EMERALD, 5), Optional.empty(),
                    new ItemStack(Items.IRON_INGOT, 3), 4, 16, 2, 0.05f, 0);

            VillagerTradeManager writer = new VillagerTradeManager(instances);
            writer.load(server);
            VillagerTradeData.VillagerEntry entry = writer.getSavedData(server)
                    .getOrCreateEntry(villager, server.overworld().getGameTime());
            entry.append(new VillagerTradeData.OriginalOffer(original, true));
            entry.get(0).setNoDiscount(true);
            entry.append(VillagerTradeData.OriginalOffer.PASSTHROUGH);
            entry.setPricedVersion(42);
            entry.flagClamped(0);
            writer.save(server);

            TestResult r = assertTrue("sidecar file not written: " + file, Files.isRegularFile(file));
            if (!r.passed()) return r;

            VillagerTradeManager reader = new VillagerTradeManager(instances);
            reader.load(server);
            VillagerTradeData.VillagerEntry loaded = reader.getSavedData(server).getEntry(villager);
            r = assertNotNull("villager entry missing after reload", loaded);
            if (!r.passed()) return r;
            r = assertEquals("offer count", 2, loaded.size());
            if (!r.passed()) return r;
            VillagerTradeData.OriginalOffer first = loaded.get(0);
            r = assertTrue("first offer must stay a rewritten snapshot", !first.isPassthrough() && first.isRewritten());
            if (!r.passed()) return r;
            r = assertTrue("first offer must keep its no-discount flag", first.isNoDiscount());
            if (!r.passed()) return r;
            MerchantOffer offer = first.offer();
            r = assertTrue("cost A must be 5 emeralds", offer.getBaseCostA().is(Items.EMERALD) && offer.getBaseCostA().getCount() == 5);
            if (!r.passed()) return r;
            r = assertTrue("result must be 3 iron ingots", offer.getResult().is(Items.IRON_INGOT) && offer.getResult().getCount() == 3);
            if (!r.passed()) return r;
            r = assertEquals("uses", 4, offer.getUses());
            if (!r.passed()) return r;
            r = assertEquals("max uses", 16, offer.getMaxUses());
            if (!r.passed()) return r;
            r = assertTrue("second offer must stay a pass-through marker", loaded.get(1).isPassthrough());
            if (!r.passed()) return r;
            r = assertEquals("priced version", 42L, loaded.pricedVersion());
            if (!r.passed()) return r;
            r = assertTrue("clamp flag of offer 0", loaded.isClampFlagged(0));
            if (!r.passed()) return r;
            return pass("villager_trades.nbt round trip kept the offer, flags and alignment");
        } finally {
            try {
                if (backup != null) Files.write(file, backup);
                else Files.deleteIfExists(file);
            } catch (Exception e) {
                StockMarketMod.LOGGER.error("[PortSmokeTestSuite] could not restore {}", file, e);
            }
        }
    }

    // ── Display block ──

    /** Three free positions in a row (along the display's right direction for NORTH = WEST) high above spawn. */
    private static BlockPos[] displayRow(ServerLevel level) {
        //BlockPos spawn = level.getSharedSpawnPos(); // mc<=1.21.8
        BlockPos spawn = level.getRespawnData().pos(); // mc>=1.21.9
        //BlockPos start = new BlockPos(spawn.getX(), level.getMaxBuildHeight() - 8, spawn.getZ()); // mc<=1.21.1
        BlockPos start = new BlockPos(spawn.getX(), level.getMaxY() - 7, spawn.getZ()); // mc>=1.21.2
        return new BlockPos[]{start, start.west(), start.west(2)};
    }

    private static StockMarketDisplayBlockEntity display(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof StockMarketDisplayBlockEntity d ? d : null;
    }

    /** Places three displays in a row (merge to one 3x1 group), removes the middle one (split), breaks the rest. */
    private TestResult test_displayMergeSplitBreak() {
        MinecraftServer server = getServer();
        if (server == null) return skip("no server");
        ServerLevel level = server.overworld();
        BlockPos[] row = displayRow(level);
        BlockState[] before = new BlockState[row.length];
        for (int i = 0; i < row.length; i++) before[i] = level.getBlockState(row[i]);
        BlockState display = StockMarketBlocks.STOCKMARKET_DISPLAY_BLOCK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        try {
            for (BlockPos p : row) level.setBlock(p, display, Block.UPDATE_ALL);

            // Merge: one group, controller at the left end (start), width 3
            for (int i = 0; i < row.length; i++) {
                StockMarketDisplayBlockEntity d = display(level, row[i]);
                TestResult r = assertNotNull("no display block entity at " + row[i], d);
                if (!r.passed()) return r;
                r = assertEquals("merged group width at " + i, 3, d.getGroupWidth());
                if (!r.passed()) return r;
                r = assertEquals("merged controller at " + i, row[0], d.getControllerPos());
                if (!r.passed()) return r;
                r = assertEquals("grid x at " + i, i, d.getGridX());
                if (!r.passed()) return r;
            }

            // Split: remove the middle, both ends become single-block controllers
            level.setBlock(row[1], before[1], Block.UPDATE_ALL);
            for (int i : new int[]{0, 2}) {
                StockMarketDisplayBlockEntity d = display(level, row[i]);
                TestResult r = assertTrue("split end " + i + " must be its own 1x1 controller",
                        d != null && d.isController() && d.getGroupWidth() == 1 && row[i].equals(d.getControllerPos()));
                if (!r.passed()) return r;
            }

            // Break: the block entity goes with the block
            level.setBlock(row[0], before[0], Block.UPDATE_ALL);
            TestResult r = assertNull("block entity left after break", display(level, row[0]));
            if (!r.passed()) return r;
            return pass("merge 3x1, split into two 1x1 and break behave");
        } finally {
            for (int i = 0; i < row.length; i++) level.setBlock(row[i], before[i], Block.UPDATE_ALL);
        }
    }

    /** A configured 2x1 display controller saves and loads into a fresh block entity with config and group intact. */
    private TestResult test_displaySaveLoad() {
        MinecraftServer server = getServer();
        if (server == null) return skip("no server");
        ServerLevel level = server.overworld();
        BlockPos[] row = displayRow(level);
        BlockState[] before = {level.getBlockState(row[0]), level.getBlockState(row[1])};
        BlockState display = StockMarketBlocks.STOCKMARKET_DISPLAY_BLOCK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        try {
            level.setBlock(row[0], display, Block.UPDATE_ALL);
            level.setBlock(row[1], display, Block.UPDATE_ALL);
            StockMarketDisplayBlockEntity ctrl = display(level, row[0]);
            TestResult r = assertTrue("left display must be the 2x1 controller",
                    ctrl != null && ctrl.isController() && ctrl.getGroupWidth() == 2);
            if (!r.passed()) return r;
            ItemID first = ItemID.of(new ItemStack(Items.DIAMOND));
            ItemID second = ItemID.of(new ItemStack(Items.IRON_INGOT));
            ctrl.setConfig(StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART, first, second);

            CompoundTag tag = ctrl.saveWithoutMetadata(level.registryAccess());
            StockMarketDisplayBlockEntity copy = new StockMarketDisplayBlockEntity(row[0], display);
            //copy.loadWithComponents(tag, level.registryAccess()); // mc<=1.21.5
            copy.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag)); // mc>=1.21.6

            r = assertEquals("display type", StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART, copy.getDisplayType());
            if (!r.passed()) return r;
            r = assertTrue("selected item", copy.getSelectedItemID() != null && copy.getSelectedItemID().getShort() == first.getShort());
            if (!r.passed()) return r;
            r = assertTrue("second item", copy.getSecondItemID() != null && copy.getSecondItemID().getShort() == second.getShort());
            if (!r.passed()) return r;
            r = assertTrue("group info", copy.isController() && copy.getGroupWidth() == 2 && copy.getGroupHeight() == 1
                    && row[0].equals(copy.getControllerPos()));
            if (!r.passed()) return r;
            r = assertNotNull("controller GUI rebuilt on load", copy.getGui());
            if (!r.passed()) return r;
            return pass("display block entity round trip kept type, items and group");
        } finally {
            level.setBlock(row[0], before[0], Block.UPDATE_ALL);
            level.setBlock(row[1], before[1], Block.UPDATE_ALL);
        }
    }

    /**
     * SM-360: two touching 1x1 displays show diamond and iron; switching the iron one to diamond (same display type)
     * merges them into one 2x1 group right away. setConfig used to regroup only when the display type changed.
     */
    private TestResult test_displayMarketChangeRegroups() {
        MinecraftServer server = getServer();
        if (server == null) return skip("no server");
        ServerLevel level = server.overworld();
        BlockPos[] row = displayRow(level);
        BlockState[] before = {level.getBlockState(row[0]), level.getBlockState(row[1])};
        BlockState display = StockMarketBlocks.STOCKMARKET_DISPLAY_BLOCK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        ItemID diamond = ItemID.of(new ItemStack(Items.DIAMOND));
        try {
            level.setBlock(row[0], display, Block.UPDATE_ALL);
            display(level, row[0]).setConfig(StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART, diamond);
            level.setBlock(row[1], display, Block.UPDATE_ALL);
            display(level, row[1]).setConfig(StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART,
                    ItemID.of(new ItemStack(Items.IRON_INGOT)));
            StockMarketDisplayBlockEntity iron = display(level, row[1]);
            TestResult r = assertTrue("diamond and iron displays must be two 1x1 controllers",
                    iron.isController() && iron.getGroupWidth() == 1 && display(level, row[0]).getGroupWidth() == 1);
            if (!r.passed()) return r;

            iron.setConfig(StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART, diamond);
            StockMarketDisplayBlockEntity left = display(level, row[0]);
            StockMarketDisplayBlockEntity right = display(level, row[1]);
            r = assertTrue("after the switch to diamond: one 2x1 group with the controller at the left end, got width "
                            + left.getGroupWidth() + ", controller of the right block " + right.getControllerPos(),
                    left.isController() && left.getGroupWidth() == 2 && row[0].equals(right.getControllerPos()));
            if (!r.passed()) return r;
            return pass("a market change merged the display with its same-market neighbour");
        } finally {
            level.setBlock(row[0], before[0], Block.UPDATE_ALL);
            level.setBlock(row[1], before[1], Block.UPDATE_ALL);
        }
    }

    // ── Display chart view (SM-364) ──

    private static final long CLIENT_WAIT_MS = 10_000;

    /** A chart view that no chart produces by itself (price range 123..130). */
    private static CompoundTag testViewport() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("viewX", 30);
        tag.putDouble("viewY", 123);
        tag.putDouble("viewW", 40);
        tag.putDouble("viewH", 7);
        tag.putDouble("zoom", 40);
        tag.putLong("candleDeltaMs", 60_000L);
        return tag;
    }

    /** @return the chart view a display block entity saves, or null if it saves none */
    private static @Nullable CompoundTag savedViewport(BlockEntity be, HolderLookup.Provider registries) {
        CompoundTag tag = be.saveWithoutMetadata(registries);
        return tag.contains("viewport") ? NbtCompat.getCompound(tag, "viewport") : null;
    }

    /**
     * SM-379: a display saved before SM-364 can hold the untouched default view of its never-connected server
     * chart (price range 0-500). Loading drops exactly that view, so the chart fits its market, and keeps a view a
     * player set, also one that differs from the default in a single value.
     */
    private TestResult test_displayLoadDropsUntouchedDefaultView() {
        MinecraftServer server = getServer();
        if (server == null) return skip("no server");
        ServerLevel level = server.overworld();
        BlockPos pos = displayRow(level)[0];
        BlockState before = level.getBlockState(pos);
        BlockState display = StockMarketBlocks.STOCKMARKET_DISPLAY_BLOCK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        try {
            level.setBlock(pos, display, Block.UPDATE_ALL);
            StockMarketDisplayBlockEntity ctrl = display(level, pos);
            TestResult r = assertTrue("1x1 display must be its own controller", ctrl != null && ctrl.isController());
            if (!r.passed()) return r;
            ctrl.setConfig(StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART, ItemID.of(new ItemStack(Items.DIAMOND)));
            // the server's chart never connects: its view is exactly what old worlds stored
            r = assertTrue("server chart of the configured display", ctrl.getChart() instanceof DisplayCandlestickChart);
            if (!r.passed()) return r;
            CompoundTag untouched = ((DisplayCandlestickChart) ctrl.getChart()).getViewportState();
            CompoundTag nearDefault = untouched.copy();
            nearDefault.putDouble("viewH", 480);
            CompoundTag tag = ctrl.saveWithoutMetadata(level.registryAccess());

            r = assertNull("view loaded from the untouched default " + untouched, loadedViewport(tag, untouched, pos, display, level));
            if (!r.passed()) return r;
            r = assertEquals("view loaded from a player's view", testViewport(), loadedViewport(tag, testViewport(), pos, display, level));
            if (!r.passed()) return r;
            r = assertEquals("view loaded from the default with another price range", nearDefault,
                    loadedViewport(tag, nearDefault, pos, display, level));
            if (!r.passed()) return r;
            return pass("the untouched default view is dropped on load, player views are kept");
        } finally {
            level.setBlock(pos, before, Block.UPDATE_ALL);
        }
    }

    /** @return the view a fresh display block entity keeps after loading {@code tag} with {@code view} stored */
    private static @Nullable CompoundTag loadedViewport(CompoundTag tag, CompoundTag view, BlockPos pos, BlockState state,
                                                        ServerLevel level) {
        CompoundTag withView = tag.copy();
        withView.put("viewport", view.copy());
        StockMarketDisplayBlockEntity copy = new StockMarketDisplayBlockEntity(pos, state);
        //copy.loadWithComponents(withView, level.registryAccess()); // mc<=1.21.5
        copy.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), withView)); // mc>=1.21.6
        return savedViewport(copy, level.registryAccess());
    }

    /**
     * SM-364, server side: a stored chart view survives a setConfig with the same market (which also rebuilds the
     * GUI; that used to replace the view with the never-connected server chart's default one) and is dropped when
     * the market changes, so the new market's chart fits its own candles.
     */
    private TestResult test_displayMarketChangeDropsViewport() {
        MinecraftServer server = getServer();
        if (server == null) return skip("no server");
        ServerLevel level = server.overworld();
        BlockPos pos = displayRow(level)[0];
        BlockState before = level.getBlockState(pos);
        BlockState display = StockMarketBlocks.STOCKMARKET_DISPLAY_BLOCK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        try {
            level.setBlock(pos, display, Block.UPDATE_ALL);
            StockMarketDisplayBlockEntity ctrl = display(level, pos);
            TestResult r = assertTrue("1x1 display must be its own controller", ctrl != null && ctrl.isController());
            if (!r.passed()) return r;
            ItemID diamond = ItemID.of(new ItemStack(Items.DIAMOND));
            ctrl.setConfig(StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART, diamond);
            CompoundTag view = testViewport();
            ctrl.applyViewport(view);
            r = assertEquals("view after applyViewport", view, savedViewport(ctrl, level.registryAccess()));
            if (!r.passed()) return r;

            ctrl.setConfig(StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART, diamond);
            r = assertEquals("view after a setConfig with the same market", view, savedViewport(ctrl, level.registryAccess()));
            if (!r.passed()) return r;

            ctrl.setConfig(StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART, ItemID.of(new ItemStack(Items.IRON_INGOT)));
            r = assertNull("view after the market changed to iron", savedViewport(ctrl, level.registryAccess()));
            if (!r.passed()) return r;
            return pass("same-market setConfig keeps the view, a market change drops it");
        } finally {
            level.setBlock(pos, before, Block.UPDATE_ALL);
        }
    }

    /**
     * SM-364, client side (needs a client in this JVM, sp cells): a client display controller showing diamond with
     * a stored view gets the update the server sends after a market change (iron, no view). It must end up with no
     * view, so the iron chart fits its own candles; it used to keep the old view or take the old chart's.
     */
    private TestResult test_displayClientMarketChangeDropsViewport() {
        MinecraftServer server = getServer();
        if (server == null) return skip("no server");
        if (StockMarketModBackend.getClientInstances() == null) return skip("no client in this JVM");
        ServerLevel level = server.overworld();
        BlockPos pos = displayRow(level)[0];
        BlockState before = level.getBlockState(pos);
        BlockState display = StockMarketBlocks.STOCKMARKET_DISPLAY_BLOCK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        CompoundTag diamondTag;
        ItemID iron = ItemID.of(new ItemStack(Items.IRON_INGOT));
        CompoundTag view = testViewport();
        try {
            // the update tag of a configured 1x1 controller with a view, as the server writes it
            level.setBlock(pos, display, Block.UPDATE_ALL);
            StockMarketDisplayBlockEntity ctrl = display(level, pos);
            TestResult r = assertTrue("1x1 display must be its own controller", ctrl != null && ctrl.isController());
            if (!r.passed()) return r;
            ctrl.setConfig(StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART, ItemID.of(new ItemStack(Items.DIAMOND)));
            ctrl.applyViewport(view);
            diamondTag = ctrl.saveWithoutMetadata(level.registryAccess());
        } finally {
            level.setBlock(pos, before, Block.UPDATE_ALL);
        }
        // the server's update after a market change: other market, no view (only the client side is under test)
        CompoundTag ironTag = diamondTag.copy();
        ironTag.putShort("selectedItemID", iron.getShort());
        ironTag.remove("viewport");
        String problem = await(ClientDisplayCheck.submit(pos, display, diamondTag, ironTag, view, iron), CLIENT_WAIT_MS);
        if (problem != null) return fail(problem);
        return pass("the client display dropped the old view on the market change");
    }

    /** Client-only part of the SM-364 client test, in its own class so a dedicated server never loads client types. */
    private static final class ClientDisplayCheck {
        /** Runs {@link #run} on the client thread. */
        static CompletableFuture<String> submit(BlockPos pos, BlockState state, CompoundTag diamondTag, CompoundTag ironTag,
                                                CompoundTag view, ItemID iron) {
            return net.minecraft.client.Minecraft.getInstance().submit(() -> run(pos, state, diamondTag, ironTag, view, iron));
        }

        /**
         * Loads both update tags into a fresh client-side display block entity (what an update packet does).
         *
         * @return null if the client kept the view for diamond and dropped it for iron, else what went wrong
         */
        private static @Nullable String run(BlockPos pos, BlockState state, CompoundTag diamondTag, CompoundTag ironTag,
                                            CompoundTag view, ItemID iron) {
            net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) return "the client is not in a world";
            StockMarketDisplayBlockEntity copy = new StockMarketDisplayBlockEntity(pos, state);
            copy.setLevel(level);
            try {
                //copy.loadWithComponents(diamondTag, level.registryAccess()); // mc<=1.21.5
                copy.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), diamondTag)); // mc>=1.21.6
                CompoundTag saved = savedViewport(copy, level.registryAccess());
                if (!view.equals(saved)) return "client view after loading diamond: expected " + view + ", got " + saved;

                //copy.loadWithComponents(ironTag, level.registryAccess()); // mc<=1.21.5
                copy.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), ironTag)); // mc>=1.21.6
                if (!(copy.getChart() instanceof DisplayCandlestickChart chart) || !iron.equals(chart.getTargetMarketID()))
                    return "no iron chart on the client after the market change: " + copy.getChart();
                saved = savedViewport(copy, level.registryAccess());
                if (saved != null) return "client still has a view after the market change to iron: " + saved;
                return null;
            } finally {
                copy.setRemoved();
            }
        }
    }
}
