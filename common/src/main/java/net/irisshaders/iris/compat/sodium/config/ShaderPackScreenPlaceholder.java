package net.irisshaders.iris.compat.sodium.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class ShaderPackScreenPlaceholder extends Screen {
    private Screen parent;
    private MultiLineLabel message;
    private Component confirmation = Component.literal("Switch");

    public ShaderPackScreenPlaceholder(Screen i) {
        super(Component.literal("Iris"));

        parent = i;
    }

    @Override
    protected void init() {
        super.init();
        this.message = MultiLineLabel.create(this.font, Component.literal("Iris cannot run when using Vulkan. Would you like to switch to OpenGL?\nThis will close your game."), this.width - 50);
        int textSize = (this.message.getLineCount() + 1) * 9;

        this.addRenderableWidget(
                Button.builder(this.confirmation, this::switchToVk)
                        .bounds(this.width / 2 - 155, 100 + textSize, 150, 20)
                        .build()
        );
        Button skipAndJoinButton = Button.builder(Component.literal("Return"), i -> onClose())
                .bounds(this.width / 2 - 155 + 160, 100 + textSize, 150, 20)
                .build();
        this.addRenderableWidget(skipAndJoinButton);
    }

    private void switchToVk(Button button) {
        Minecraft.getInstance().options.preferredGraphicsBackend().set(PreferredGraphicsApi.OPENGL);
        Minecraft.getInstance().options.save();

        if (Minecraft.getInstance().isLocalServer() && Minecraft.getInstance().getSingleplayerServer() != null) {
            Minecraft.getInstance().getSingleplayerServer().halt(true);
        }

        Minecraft.getInstance().disconnectWithSavingScreen();
        Minecraft.getInstance().stop();
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        ActiveTextCollector textRenderer = graphics.textRenderer();
        graphics.centeredText(this.font, this.title, this.width / 2, 50, -1);
        this.message.visitLines(TextAlignment.CENTER, this.width / 2, 70, 9, textRenderer);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
