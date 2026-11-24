package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistry;
import net.kroia.stockmarket.screen.PluginManagementScreen;
import net.kroia.stockmarket.screen.uiElements.MarketItemButton;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.testkit.TestCategory;
import net.kroia.testkit.gui.Gui;
import net.kroia.testkit.gui.GuiNode;
import net.kroia.testkit.gui.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Predicate;

/**
 * SM-361: the picture {@code stockmarket/dev/example-plugin.png} of the developer docs ("Your first plugin", Step 4):
 * Plugin Management with a new instance of the example plugin's {@code PriceFloorPlugin}, made through the page's own
 * steps 4-7: Add Plugin, PriceFloorPlugin, tick Enabled, + diamond, click the diamond icon, type a Min Price, Apply.
 * Diamond is on the chart, like the other plugin entry shots of {@link DocsScreenshotSuite}.
 * <p>
 * Needs the example plugin jar (StockMarketExamplePlugin) next to StockMarket: without it both tests skip. Opt-in, its
 * own category, so it runs only when named ({@code --only stockmarket/sm_docs_plugin}, or in a launcher instance
 * {@code -Dmodutilities.tests.include=stockmarket/sm_docs_plugin}), never with the other docs shots.
 * Needs the integrated server (singleplayer) and the English (en_us) GUI texts.
 */
public class DocsPluginShotSuite extends DocsScreenshotSuite {

    /** Registry ID of the example plugin: {@code PriceFloorPlugin.class.getName()} in its {@code ExamplePlugins}. */
    static final String PRICE_FLOOR = "com.example.stockmarketplugin.plugin.PriceFloorPlugin";
    /** The plugin's name in the Add Plugin list and on its entry. */
    private static final String NAME = "PriceFloorPlugin";
    private static final String NOT_LOADED = NAME + " is not registered (install the StockMarket example plugin jar)";

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.DOCS_PLUGIN;
    }

    @Override
    public void registerTests() {
        addTest("fixture", () -> loaded() ? test_fixture() : skip(NOT_LOADED));
        addTest("example_plugin", () -> loaded() ? shot("stockmarket/dev/example-plugin.png", this::examplePlugin) : skip(NOT_LOADED));
    }

    private static boolean loaded() {
        return PluginRegistry.findPlugin(PRICE_FLOOR) != null;
    }

    /** The page's steps 4-7 on Plugin Management; returns null when the screen is ready for the picture. */
    private @Nullable String examplePlugin() {
        pluginManagement();

        // Add Plugin -> PriceFloorPlugin (a rerun in the same world reuses the instance it made before)
        if (entries().isEmpty()) {
            Gui.click("Add Plugin");
            GuiNode type = waitFor(n -> "Button".equals(n.type()) && NAME.equals(n.text()), "the " + NAME + " button of Add Plugin");
            scrollTo(type);
            Gui.click(waitFor(n -> "Button".equals(n.type()) && NAME.equals(n.text()), NAME));
            if (!Gui.waitUntil(() -> Minecraft.getInstance().screen instanceof PluginManagementScreen, 100))
                return "Add Plugin did not return to Plugin Management";
            waitFor(n -> isEntry(n), "the new " + NAME + " entry");
            Gui.waitTicks(10);
        }

        // tick Enabled: only its box (the right end) takes the click; the list is rebuilt after the server answers
        scrollTo(entry());
        GuiNode enabled = findIn(entry(), n -> n.handle() instanceof CheckBox box && "Enabled".equals(box.getText()));
        if (enabled == null) return "no Enabled box in the " + NAME + " entry";
        if (!((CheckBox) enabled.handle()).isChecked()) {
            Gui.click(enabled.x() + enabled.width() - enabled.height() / 2, enabled.centerY(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
            Gui.waitTicks(DATA_TICKS); // the box ticks at once; the rebuilt entry shows the server's state
            waitFor(n -> isEntry(n) && findIn(n, c -> c.handle() instanceof CheckBox box
                    && "Enabled".equals(box.getText()) && box.isChecked()) != null, NAME + " enabled");
        }

        // + diamond: the market list opens, the diamond row toggles the subscription, Close goes back
        scrollTo(entry());
        if (findIn(entry(), n -> shows(n, Items.DIAMOND)) == null) {
            GuiNode plus = findIn(entry(), n -> "+".equals(n.text()));
            if (plus == null) return "no + in the " + NAME + " entry";
            Gui.click(plus);
            GuiNode row = waitFor(n -> "MarketToggleEntry".equals(n.type()) && findIn(n, c -> shows(c, Items.DIAMOND)) != null,
                    "the diamond row of the market list");
            scrollTo(row);
            row = Gui.find(n -> "MarketToggleEntry".equals(n.type()) && findIn(n, c -> shows(c, Items.DIAMOND)) != null, "diamond row");
            Gui.click(row.x() + row.width() * 3 / 4, row.centerY(), GLFW.GLFW_MOUSE_BUTTON_LEFT); // on its name, not the icon
            Gui.waitTicks(DATA_TICKS);
            Gui.click("Close");
            if (!Gui.waitUntil(() -> Minecraft.getInstance().screen instanceof PluginManagementScreen, 100))
                return "Close did not return to Plugin Management";
            waitFor(n -> isEntry(n) && findIn(n, c -> shows(c, Items.DIAMOND)) != null, "diamond on the " + NAME + " entry");
        }

        // the diamond icon: diamond on the chart, the form shows its settings (a second click would deselect it)
        scrollTo(entry());
        if (!diamondSelected()) Gui.click(findIn(entry(), n -> n.visible() && shows(n, Items.DIAMOND)));
        Gui.waitTicks(DATA_TICKS);
        if (!diamondSelected()) return "the diamond icon is not selected after the click";

        // Min Price: clear the box, type a floor below the live price, Apply
        String minPrice = minPrice();
        GuiNode box = findIn(entry(), n -> n.visible() && n.handle() instanceof TextBox);
        if (box == null) return "no Min Price box in the " + NAME + " entry";
        TextBox textBox = (TextBox) box.handle();
        int len = Gui.onClient(() -> textBox.getText().length());
        Gui.click(box);
        for (int i = 0; i < len; i++) Gui.key(Keys.RIGHT);
        for (int i = 0; i < len; i++) Gui.key(Keys.BACKSPACE);
        Gui.type(minPrice);
        String typed = Gui.onClient(textBox::getText);
        if (!minPrice.equals(typed)) return "Min Price box reads '" + typed + "' after typing " + minPrice;
        GuiNode apply = findIn(entry(), n -> n.visible() && "Apply".equals(n.text()));
        if (apply == null) return "no Apply in the " + NAME + " entry";
        Gui.click(apply);
        Gui.waitTicks(DATA_TICKS);
        return null;
    }

    /** A floor at 90 % of diamond's live price (whole coins), so the plugin leaves the price alone in the picture. */
    private String minPrice() {
        StockMarketModBackend.ServerInstances b = StockMarketModBackend.getServerInstances();
        IAsyncMarket market = await(b.MARKET_MANAGER.getAsync().getMarketAsync(fixture.market(Items.DIAMOND)), WAIT_MS);
        long raw = market == null ? 0 : await(market.getCurrentMarketPriceAsync(), WAIT_MS);
        return String.valueOf(Math.max(1, (long) Math.floor(MarketManager.convertToRealAmountStatic(raw) * 0.9)));
    }

    /** Whether the diamond icon of the entry is selected (diamond on the chart). */
    private static boolean diamondSelected() {
        GuiNode icon = findIn(entry(), n -> shows(n, Items.DIAMOND));
        return icon != null && icon.handle() instanceof MarketItemButton b && Gui.onClient(b::isSelected);
    }

    private static boolean isEntry(GuiNode n) {
        return "PluginEntryWidget".equals(n.type()) && findIn(n, c -> "Label".equals(c.type()) && NAME.equals(c.text())) != null;
    }

    private static List<GuiNode> entries() {
        return Gui.findAll(DocsPluginShotSuite::isEntry);
    }

    /** The PriceFloorPlugin entry (found again after each step: the list rebuilds its entries on every server answer). */
    private static GuiNode entry() {
        return Gui.find(DocsPluginShotSuite::isEntry, "plugin entry " + NAME);
    }

    /** Waits up to 100 ticks for a match (polled from the test thread); throws if none appears. */
    private static GuiNode waitFor(Predicate<GuiNode> filter, String what) {
        for (int i = 0; i < 100; i++) {
            List<GuiNode> found = Gui.findAll(filter);
            if (!found.isEmpty()) return found.get(0);
            Gui.waitTicks(1);
        }
        throw new IllegalStateException("no " + what + " after 100 ticks");
    }
}
