package net.kroia.stockmarket.pluginsystem.plugins.screen;

import net.kroia.modutilities.gui.elements.*;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.minecraft.network.chat.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * Inline settings GUI for the DefaultOrderbookVolumeDistributionPlugin.
 * Displays volume scale and convergence speed inputs with an Apply button.
 */
public class VolumeDistributionGuiElement extends PluginGuiElement {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".volume_distribution.";
        public static final Component VOLUME_SCALE = Component.translatable(PREFIX + "volume_scale");
        public static final Component SPEED = Component.translatable(PREFIX + "speed");
        public static final Component APPLY = Component.translatable(PREFIX + "apply");
    }

    private final Label volumeScaleLabel;
    private final TextBox volumeScaleTextBox;
    private final Label speedLabel;
    private final TextBox speedTextBox;
    private final Button applyButton;

    public VolumeDistributionGuiElement() {
        volumeScaleLabel = new Label(Texts.VOLUME_SCALE.getString());
        volumeScaleTextBox = new TextBox();
        volumeScaleTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        speedLabel = new Label(Texts.SPEED.getString());
        speedTextBox = new TextBox();
        speedTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        applyButton = new Button(Texts.APPLY.getString(), this::onApply);

        addChild(volumeScaleLabel);
        addChild(volumeScaleTextBox);
        addChild(speedLabel);
        addChild(speedTextBox);
        addChild(applyButton);
    }

    @Override
    protected void onPluginSyncDataReceived(PluginSyncData data) {
        byte[] settings = data.getCustomSettings();
        if (settings != null) {
            try {
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(settings));
                volumeScaleTextBox.setText(dis.readFloat());
                speedTextBox.setText(dis.readFloat());
            } catch (Exception e) {
                // Keep defaults
            }
        }
    }

    private void onApply() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeFloat((float) volumeScaleTextBox.getDouble());
            dos.writeFloat((float) speedTextBox.getDouble());
            dos.flush();
            sendCustomSettings(baos.toByteArray());
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    protected void layoutChanged() {
        int w = getWidth();
        int h = getHeight();
        int eh = Math.min(defaultElementHeight, h / 4);
        int labelW = w / 3;

        // Row 1: volume scale label + textbox
        volumeScaleLabel.setBounds(0, 0, labelW, eh);
        volumeScaleTextBox.setBounds(labelW + spacing, 0, w - labelW - spacing, eh);

        // Row 2: speed label + textbox
        int row2Y = eh + spacing;
        speedLabel.setBounds(0, row2Y, labelW, eh);
        speedTextBox.setBounds(labelW + spacing, row2Y, w - labelW - spacing, eh);

        // Row 3: apply button
        applyButton.setBounds(0, row2Y + eh + spacing, w, eh);
    }

    @Override
    protected void render() {}
}
