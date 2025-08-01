package net.kroia.stockmarket.screen.uiElements.botsetup;

/*
public class BotSetup_volatility extends BotSetupGuiElement{

    private static final String NAME = "bot_settings_setup_screen_volatility";
    public static final String PREFIX = "gui."+ StockMarketMod.MOD_ID+"."+NAME+".";

    private static final Component QUESTION = Component.translatable(PREFIX+"question");
    private static final Component LOW = Component.translatable(PREFIX+"low");
    private static final Component HIGH = Component.translatable(PREFIX+"high");



    private final Label questionLabel;
    private final Label lowLabel;
    private final Label highLabel;

    private final Slider slider;

    public BotSetup_volatility(ServerVolatilityBot.Settings settings) {
        super(settings);

        questionLabel = new Label(QUESTION.getString());
        lowLabel = new Label(LOW.getString());
        highLabel = new Label(HIGH.getString());
        slider = new HorizontalSlider();

        questionLabel.setAlignment(GuiElement.Alignment.TOP_LEFT);

        lowLabel.setAlignment(GuiElement.Alignment.LEFT);
        highLabel.setAlignment(GuiElement.Alignment.RIGHT);

        addChild(questionLabel);
        addChild(lowLabel);
        addChild(highLabel);
        addChild(slider);


        slider.setSliderValue(0.5);
    }



    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        int padding = 5;

        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;

        questionLabel.setBounds(padding, padding, width, height-40);
        lowLabel.setBounds(padding, questionLabel.getBottom(), width/2, 20);
        highLabel.setBounds(lowLabel.getRight(), questionLabel.getBottom(), width/2, 20);
        slider.setBounds(padding, lowLabel.getBottom(), width, 20);
    }

    public float getVolatility() {
        return (float)slider.getSliderValue();
    }
    public void setTooltipSupplyer(Supplier<String> tooltipSupplier) {
        slider.setTooltipSupplier(tooltipSupplier);
    }
}
*/