package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.networking.general.RegisterItemIDPacket;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.banksystem.util.ItemStackNbt;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.marketmanager.IAsyncMarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.AsyncMarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.TestFixture;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.SlaveTrustCache;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * SM-273 (from BankSystem's security batch BS-186 / #143): a StockMarket item picker can still create a market for
 * an item BankSystem has no ItemID for yet.
 * <p>
 * The picker itself runs on the client ({@code CreateMarketTab}: {@code ItemID.getOrRegisterFromItemStackClientSide}
 * then {@code requestCreateMarket}); this suite drives the server side of that flow: the
 * {@link RegisterItemIDPacket} a client sends, received with a (non-admin) player as sender, then the market create.
 * <ul>
 *   <li>master (sp, mp): the item is minted locally, the create goes through the real market manager request handler
 *       as a StockMarket admin player;</li>
 *   <li>trusted slave: the slave asks the master to mint, gets the id back and creates the market on the master;</li>
 *   <li>untrusted slave: BankSystem #143 makes registration lookup-only, so the item must stay unregistered.</li>
 * </ul>
 * Cleanup: the market is deleted, the admin user removed; on the master the ItemID registry and item filters are
 * restored by a {@link TestFixture}. On a trusted slave the master keeps the plain item's ItemID (no deregister
 * exists and the master's registry cannot be restored from here; autotest worlds are fresh per cell).
 */
public class ItemPickerRegistrationTestSuite extends TestSuite {

    /** Wait for the master's answer (ARRS round trip plus the ItemID sync). */
    private static final long WAIT_MS = 10_000;
    /** How long an untrusted slave gets to (wrongly) learn an id. */
    private static final long REFUSAL_WAIT_MS = 3_000;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.PICKER_REGISTRATION;
    }

    @Override
    public void registerTests() {
        addTest("new_plain_item_registers_and_creates_market", this::test_newPlainItemRegistersAndCreatesMarket);
    }

    private TestResult test_newPlainItemRegistersAndCreatesMarket() {
        StockMarketModBackend.ServerInstances backend = StockMarketModBackend.getServerInstances();
        MinecraftServer server = getServer();
        if (backend == null || server == null)
            return skip("no server in this JVM");
        boolean slave = backend.MARKET_MANAGER.isSlave();
        if (slave && !MultiServerUtils.canInteractWithStockMarket())
            return skip("not connected to the master");
        Boolean trusted = slave ? SlaveTrustCache.get() : Boolean.TRUE;
        if (trusted == null)
            return skip("this slave does not know yet whether the master trusts it");
        ItemStack stack = unregisteredPlainItem();
        if (stack == null)
            return skip("every vanilla item already has an ItemID");

        TestFixture fixture = slave ? null : new TestFixture("PickerRegistration");
        if (fixture != null)
            fixture.setBackend(backend); // snapshots the ItemID registry and item filters before the mint
        UUID admin = UUID.randomUUID();
        ItemID marketID = null;
        boolean adminAdded = false;
        try {
            // what the server receives from the picker's client: a plain item from a normal (non-admin) player
            CompoundTag wire = (CompoundTag) ItemStackNbt.save(stack, server.registryAccess(), new CompoundTag());
            ItemIDManager.receiveRegisterItemIDPacket(new RegisterItemIDPacket(List.of(wire)), "", UUID.randomUUID());

            if (!trusted) {
                pumpUntil(() -> isRegistered(stack), REFUSAL_WAIT_MS);
                return assertFalse("untrusted slave: " + itemName(stack) + " is not registered", isRegistered(stack));
            }

            TestResult r = assertTrue(itemName(stack) + " got an ItemID" + (slave ? " from the master" : ""),
                    pumpUntil(() -> isRegistered(stack), WAIT_MS));
            if (!r.passed()) return r;
            marketID = ItemIDManager.getItemID(stack);

            IAsyncMarketManager creator;
            if (slave) {
                creator = backend.MARKET_MANAGER.getAsync(); // forwarded to the master
            } else {
                ServerMarketManager manager = backend.MARKET_MANAGER.getServerMarketManager();
                manager.onPlayerJoin(admin, "PickerTestAdmin");
                adminAdded = true;
                manager.setStockmarketAdminMode(admin, true);
                // the real request handler, as if the admin's client sent the create
                creator = AsyncMarketManager.createForTesting(input ->
                        AsyncMarketManager.Request.instance.handleOnMasterServer(input, "", admin));
            }
            r = assertNotNull("market created for " + itemName(stack), await(creator.createMarketAsync(marketID), WAIT_MS));
            if (!r.passed()) return r;
            r = assertEquals("market exists on the master", Boolean.TRUE,
                    await(backend.MARKET_MANAGER.getAsync().marketExistsAsync(marketID), WAIT_MS));
            if (!r.passed()) return r;
            return pass(itemName(stack) + " registered and its market created " + (slave ? "via the master" : "on the master"));
        } finally {
            if (marketID != null) {
                try {
                    await(backend.MARKET_MANAGER.getAsync().deleteMarketAsync(marketID), WAIT_MS);
                } catch (RuntimeException ignored) {
                    // best effort; the fixture restore below still runs
                }
            }
            if (adminAdded)
                backend.MARKET_MANAGER.getServerMarketManager().removeUser(admin);
            if (fixture != null)
                fixture.cleanup();
        }
    }

    /** @return a vanilla item's plain default stack this server has no ItemID for, or null if all have one */
    private static @Nullable ItemStack unregisteredPlainItem() {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || !"minecraft".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace()))
                continue;
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty() && !isRegistered(stack))
                return stack;
        }
        return null;
    }

    private static boolean isRegistered(ItemStack stack) {
        return ItemIDManager.getItemID(stack).isValid();
    }

    private static String itemName(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
