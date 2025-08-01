package net.kroia.stockmarket.screen.uiElements.botsetup;

/*
public class BotSetup_enabledFeatures extends BotSetupGuiElement{
    private static final String NAME = "bot_settings_setup_screen_enabled_features";
    public static final String PREFIX = "gui."+ StockMarketMod.MOD_ID+"."+NAME+".";

    public static final Component ENABLE_TARGET_PRICE = Component.translatable(PREFIX+"enable_target_price");
    public static final Component ENABLE_VOLUME_TRACKING = Component.translatable(PREFIX+"enable_volume_tracking");
    public static final Component ENABLE_RANDOM_WALK = Component.translatable(PREFIX+"enable_random_walk");

    private final CheckBox enableTargetPrice;
    private final CheckBox enableVolumeTracking;
    private final CheckBox enableRandomWalk;

    public BotSetup_enabledFeatures(ServerVolatilityBot.Settings settings) {
        super(settings);

        enableTargetPrice = new CheckBox(ENABLE_TARGET_PRICE.getString());
        enableVolumeTracking = new CheckBox(ENABLE_VOLUME_TRACKING.getString());
        enableRandomWalk = new CheckBox(ENABLE_RANDOM_WALK.getString());

        addChild(enableTargetPrice);
        addChild(enableVolumeTracking);
        addChild(enableRandomWalk);

        enableTargetPrice.setChecked(true);
        enableVolumeTracking.setChecked(true);
        enableRandomWalk.setChecked(true);
        enableTargetPrice.setTextAlignment(Alignment.TOP_LEFT);
        enableVolumeTracking.setTextAlignment(Alignment.TOP_LEFT);
        enableRandomWalk.setTextAlignment(Alignment.TOP_LEFT);
    }



    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        int padding = 5;

        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;

        int lineCount1 = ENABLE_TARGET_PRICE.getString().split("\n").length;
        int lineCount2 = ENABLE_VOLUME_TRACKING.getString().split("\n").length;
        int lineCount3 = ENABLE_RANDOM_WALK.getString().split("\n").length;
        enableTargetPrice.setBounds(padding, padding, width, (getFont().lineHeight+2)*lineCount1);
        enableVolumeTracking.setBounds(padding, enableTargetPrice.getBottom(), width, (getFont().lineHeight+2)*lineCount2);
        enableRandomWalk.setBounds(padding, enableVolumeTracking.getBottom(), width, (getFont().lineHeight+2)*lineCount3);

    }

    public boolean isTargetPriceEnabled() {
        return enableTargetPrice.isChecked();
    }
    public void setTargetPriceEnabled(boolean enabled) {
        enableTargetPrice.setChecked(enabled);
    }
    public boolean isVolumeTrackingEnabled() {
        return enableVolumeTracking.isChecked();
    }
    public void setVolumeTrackingEnabled(boolean enabled) {
        enableVolumeTracking.setChecked(enabled);
    }

    public boolean isRandomWalkEnabled() {
        return enableRandomWalk.isChecked();
    }
    public void setRandomWalkEnabled(boolean enabled) {
        enableRandomWalk.setChecked(enabled);
    }
}
*/