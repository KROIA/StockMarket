package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.data.DataManager;
import net.kroia.stockmarket.minecraft.block.StockMarketBlocks;
import net.kroia.stockmarket.minecraft.entity.custom.StockMarketDisplayBlockEntity;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.villagertrading.VillagerTradeData;
import net.kroia.stockmarket.villagertrading.VillagerTradeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    }

    // ── Recipes ──

    /** Every recipe JSON StockMarket ships under data/stockmarket/recipe/ must be in the server's RecipeManager. */
    private TestResult test_recipesLoaded() {
        MinecraftServer server = getServer();
        if (server == null) return skip("no server");
        List<ResourceLocation> ids = new ArrayList<>();
        server.getResourceManager().listResources("recipe", p -> p.getPath().endsWith(".json"))
                .keySet().forEach(file -> {
                    if (!file.getNamespace().equals(StockMarketMod.MOD_ID)) return;
                    String path = file.getPath();
                    ids.add(ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
                            path.substring("recipe/".length(), path.length() - ".json".length())));
                });
        TestResult r = assertTrue("no stockmarket recipe JSON found in the server resources (folder renamed?)", !ids.isEmpty());
        if (!r.passed()) return r;
        for (ResourceLocation id : ids) {
            r = assertTrue("recipe missing from RecipeManager: " + id, server.getRecipeManager().byKey(id).isPresent());
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
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos start = new BlockPos(spawn.getX(), level.getMaxBuildHeight() - 8, spawn.getZ());
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
            copy.loadWithComponents(tag, level.registryAccess());

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
}
