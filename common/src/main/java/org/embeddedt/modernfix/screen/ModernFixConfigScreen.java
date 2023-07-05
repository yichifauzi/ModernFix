package org.embeddedt.modernfix.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

public class ModernFixConfigScreen extends Screen {
    private OptionList optionList;
    private Screen lastScreen;

    public boolean madeChanges = false;
    private Button doneButton;
    public ModernFixConfigScreen(Screen lastScreen) {
        super(Component.translatable("modernfix.config"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        this.optionList = new OptionList(this, this.minecraft);
        this.addWidget(this.optionList);
        this.doneButton = new Button.Builder(CommonComponents.GUI_DONE, (arg) -> {
            this.onClose();
        }).pos(this.width / 2 - 100, this.height - 29).size(200, 20).build();
        this.addRenderableWidget(this.doneButton);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(lastScreen);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        this.optionList.render(poseStack, mouseX, mouseY, partialTicks);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 8, 16777215);
        this.doneButton.setMessage(madeChanges ? Component.translatable("modernfix.config.done_restart") : CommonComponents.GUI_DONE);
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public void renderComponentHoverEffect(PoseStack matrixStack, @Nullable Style style, int mouseX, int mouseY) {
        super.renderComponentHoverEffect(matrixStack, style, mouseX, mouseY);
    }
}
