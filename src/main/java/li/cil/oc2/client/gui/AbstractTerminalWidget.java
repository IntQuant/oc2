package li.cil.oc2.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import li.cil.oc2.client.gui.terminal.TerminalInput;
import li.cil.oc2.client.gui.widget.ToggleImageButton;
import li.cil.oc2.common.Constants;
import li.cil.oc2.common.vm.Terminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fml.client.gui.GuiUtils;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static li.cil.oc2.common.util.TooltipUtils.withColor;

public abstract class AbstractTerminalWidget extends AbstractGui {
    public static final int TERMINAL_WIDTH = Terminal.WIDTH * Terminal.CHAR_WIDTH / 2;
    public static final int TERMINAL_HEIGHT = Terminal.HEIGHT * Terminal.CHAR_HEIGHT / 2;

    public static final int MARGIN_SIZE = 8;
    public static final int TERMINAL_X = MARGIN_SIZE;
    public static final int TERMINAL_Y = MARGIN_SIZE;

    public static final int WIDTH = TERMINAL_WIDTH + MARGIN_SIZE * 2;
    public static final int HEIGHT = TERMINAL_HEIGHT + MARGIN_SIZE * 2;

    private static final int CONTROLS_TOP = 8;
    private static final int ENERGY_TOP = CONTROLS_TOP + Sprites.SIDEBAR_2.height + 4;

    private static boolean isInputCaptureEnabled;

    ///////////////////////////////////////////////////////////////////

    private final Screen parent;
    private final Terminal terminal;
    private int windowLeft, windowTop;
    private boolean isMouseOverTerminal;

    private int currentEnergy, maxEnergy, energyConsumption;

    ///////////////////////////////////////////////////////////////////

    protected AbstractTerminalWidget(final Screen parent, final Terminal terminal) {
        this.parent = parent;
        this.terminal = terminal;
    }

    public void setEnergyInfo(final int current, final int capacity, final int consumption) {
        this.currentEnergy = current;
        this.maxEnergy = capacity;
        this.energyConsumption = consumption;
    }

    public void renderBackground(final MatrixStack matrixStack, final int mouseX, final int mouseY) {
        RenderSystem.color4f(1f, 1f, 1f, 1f);

        isMouseOverTerminal = isMouseOverTerminal(mouseX, mouseY);

        Sprites.SIDEBAR_2.draw(matrixStack, windowLeft - Sprites.SIDEBAR_2.width, windowTop + CONTROLS_TOP);

        if (maxEnergy > 0) {
            final int x = windowLeft - Sprites.SIDEBAR_2.width;
            final int y = windowTop + ENERGY_TOP;
            Sprites.SIDEBAR_2.draw(matrixStack, x, y);
            Sprites.ENERGY_BASE.draw(matrixStack, x + 4, y + 4);
        }

        Sprites.TERMINAL_SCREEN.draw(matrixStack, windowLeft, windowTop);

        if (shouldCaptureInput()) {
            Sprites.TERMINAL_FOCUSED.draw(matrixStack, windowLeft, windowTop);
        }
    }

    public void render(final MatrixStack matrixStack, final int mouseX, final int mouseY, @Nullable final ITextComponent error) {
        if (isRunning()) {
            final MatrixStack stack = new MatrixStack();
            stack.translate(windowLeft + TERMINAL_X, windowTop + TERMINAL_Y, getClient().getItemRenderer().blitOffset);
            stack.scale(TERMINAL_WIDTH / (float) terminal.getWidth(), TERMINAL_HEIGHT / (float) terminal.getHeight(), 1f);
            terminal.render(stack);
        } else {
            final FontRenderer font = getClient().font;
            if (error != null) {
                final int textWidth = font.width(error);
                final int textOffsetX = (TERMINAL_WIDTH - textWidth) / 2;
                final int textOffsetY = (TERMINAL_HEIGHT - font.lineHeight) / 2;
                font.drawShadow(matrixStack,
                        error,
                        windowLeft + TERMINAL_X + textOffsetX,
                        windowTop + TERMINAL_Y + textOffsetY,
                        0xEE3322);
            }
        }

        if (maxEnergy > 0) {
            Sprites.ENERGY_BAR.drawFillY(matrixStack, windowLeft - Sprites.SIDEBAR_2.width + 4, windowTop + ENERGY_TOP + 4, currentEnergy / (float) maxEnergy);

            if (isMouseOver(mouseX, mouseY, -Sprites.SIDEBAR_2.width + 4, ENERGY_TOP + 4, Sprites.ENERGY_BAR.width, Sprites.ENERGY_BAR.height)) {
                final List<? extends ITextProperties> tooltip = Arrays.asList(
                        new TranslationTextComponent(Constants.TOOLTIP_ENERGY, withColor(currentEnergy + "/" + maxEnergy, TextFormatting.GREEN)),
                        new TranslationTextComponent(Constants.TOOLTIP_ENERGY_CONSUMPTION, withColor(String.valueOf(energyConsumption), TextFormatting.GREEN))
                );
                GuiUtils.drawHoveringText(matrixStack, tooltip, mouseX, mouseY, parent.width, parent.height, 200, getClient().font);
            }
        }
    }

    public void tick() {
        final ByteBuffer input = terminal.getInput();
        if (input != null) {
            sendTerminalInputToServer(input);
        }
    }

    public boolean charTyped(final char ch, final int modifier) {
        terminal.putInput((byte) ch);
        return true;
    }

    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!shouldCaptureInput() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }

        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
            final String value = getClient().keyboardHandler.getClipboard();
            for (final char ch : value.toCharArray()) {
                terminal.putInput((byte) ch);
            }
        } else {
            final byte[] sequence = TerminalInput.getSequence(keyCode, modifiers);
            if (sequence != null) {
                for (int i = 0; i < sequence.length; i++) {
                    terminal.putInput(sequence[i]);
                }
            }
        }

        return true;
    }

    public void init() {
        this.windowLeft = (parent.width - WIDTH) / 2;
        this.windowTop = (parent.height - HEIGHT) / 2;

        getClient().keyboardHandler.setSendRepeatsToGui(true);

        addButton(new ToggleImageButton(
                parent, windowLeft - Sprites.SIDEBAR_2.width + 4, windowTop + CONTROLS_TOP + 4,
                12, 12,
                new TranslationTextComponent(Constants.COMPUTER_SCREEN_POWER_CAPTION),
                new TranslationTextComponent(Constants.COMPUTER_SCREEN_POWER_DESCRIPTION),
                Sprites.POWER_BUTTON_BASE,
                Sprites.POWER_BUTTON_PRESSED,
                Sprites.POWER_BUTTON_ACTIVE
        ) {
            @Override
            public void onPress() {
                super.onPress();
                sendPowerStateToServer(!isRunning());
            }

            @Override
            public boolean isToggled() {
                return isRunning();
            }
        });

        addButton(new ToggleImageButton(
                parent, windowLeft - Sprites.SIDEBAR_2.width + 4, windowTop + CONTROLS_TOP + 18,
                12, 12,
                new TranslationTextComponent(Constants.COMPUTER_SCREEN_CAPTURE_INPUT_CAPTION),
                new TranslationTextComponent(Constants.COMPUTER_SCREEN_CAPTURE_INPUT_DESCRIPTION),
                Sprites.INPUT_BUTTON_BASE,
                Sprites.INPUT_BUTTON_PRESSED,
                Sprites.INPUT_BUTTON_ACTIVE
        ) {
            @Override
            public void onPress() {
                super.onPress();
                isInputCaptureEnabled = !isInputCaptureEnabled;
            }

            @Override
            public boolean isToggled() {
                return isInputCaptureEnabled;
            }
        });
    }

    public void onClose() {
        getClient().keyboardHandler.setSendRepeatsToGui(false);
    }

    ///////////////////////////////////////////////////////////////////

    protected abstract boolean isRunning();

    protected void addButton(final Widget widget) {
    }

    protected abstract void sendPowerStateToServer(boolean value);

    protected abstract void sendTerminalInputToServer(final ByteBuffer input);

    ///////////////////////////////////////////////////////////////////

    private Minecraft getClient() {
        return parent.getMinecraft();
    }

    private boolean shouldCaptureInput() {
        return isMouseOverTerminal && isInputCaptureEnabled && isRunning();
    }

    private boolean isMouseOverTerminal(final int mouseX, final int mouseY) {
        return isMouseOver(mouseX, mouseY,
                AbstractTerminalWidget.TERMINAL_X, AbstractTerminalWidget.TERMINAL_Y,
                AbstractTerminalWidget.TERMINAL_WIDTH, AbstractTerminalWidget.TERMINAL_HEIGHT);
    }

    private boolean isMouseOver(final int mouseX, final int mouseY, final int x, final int y, final int width, final int height) {
        final int localMouseX = mouseX - windowLeft;
        final int localMouseY = mouseY - windowTop;
        return localMouseX >= x &&
               localMouseX < x + width &&
               localMouseY >= y &&
               localMouseY < y + height;
    }
}
