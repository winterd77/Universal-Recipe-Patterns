package org.gtlcore.universalpatterns.client.gui.widget;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.ingredient.Target;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/** A reference slot with no inventory backing or ability to take items. */
final class RecipePatternSampleWidget extends WidgetGroup {
    private final Consumer<AEKey> changed;
    private final boolean searchSlot;
    private @Nullable AEKey sample;

    RecipePatternSampleWidget(int x, int y, Consumer<AEKey> changed, Component hint) {
        this(x, y, changed, hint, false);
    }

    RecipePatternSampleWidget(int x, int y, Consumer<AEKey> changed, Component hint, boolean searchSlot) {
        super(x, y, 18, 18);
        this.changed = changed;
        this.searchSlot = searchSlot;
        setClientSideWidget();
        setHoverTooltips(hint, Component.translatable("gtceu.machine.me_recipe_pattern_buffer." +
                (searchSlot ? "sample_search_controls" : "sample_container_hint")));
        setDraggingConsumer(value -> key(value) != null, null, null, value -> choose(key(value)));
        refresh();
    }

    @Nullable AEKey sample() {
        return sample;
    }

    void display(@Nullable AEKey value) {
        sample = value;
        refresh();
    }

    private void choose(@Nullable AEKey value) {
        display(value);
        changed.accept(value);
    }

    private void refresh() {
        clearAllWidgets();
        addWidget(sample == null ? new ImageWidget(0, 0, 18, 18, GuiTextures.SLOT) :
                RecipePatternBrowserWidget.stackWidget(new GenericStack(sample, 1), 0, 0, GuiTextures.SLOT));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isActive() || !isMouseOverElement(mouseX, mouseY) || gui == null) return false;
        boolean right = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        if (!right && button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (right && searchSlot) {
            if (sample != null) choose(null);
            return true;
        }

        // Refer to the declaring Minecraft class so Forge remaps this inherited method.
        AbstractContainerMenu menu = gui.getModularUIContainer();
        if (menu == null) return false;
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            if (right) choose(null);
            return right || searchSlot;
        }
        if (right || (searchSlot && Screen.hasShiftDown())) {
            // Inspect a copy even for containers whose fluid capability mutates the stack.
            var fluid = FluidUtil.getFluidContained(carried.copy()).orElse(FluidStack.EMPTY);
            if (!fluid.isEmpty()) choose(AEFluidKey.of(fluid.getFluid(), fluid.getTag()));
        } else {
            choose(AEItemKey.of(carried));
        }
        return true;
    }

    @Override
    public List<Target> getPhantomTargets(Object ingredient) {
        if (key(ingredient) == null) return List.of();
        return List.of(new Target() {
            @Override
            public Rect2i getArea() {
                return new Rect2i(getPosition().x, getPosition().y, 18, 18);
            }

            @Override
            public void accept(Object value) {
                AEKey accepted = key(value);
                if (isVisible() && isActive() && accepted != null) choose(accepted);
            }
        });
    }

    private static @Nullable AEKey key(Object ingredient) {
        if (LDLib.isJeiLoaded() && ingredient instanceof ITypedIngredient<?> typed) return key(typed.getIngredient());
        if (ingredient instanceof ItemStack stack && !stack.isEmpty()) return AEItemKey.of(stack);
        if (ingredient instanceof FluidStack stack && !stack.isEmpty()) return AEFluidKey.of(stack.getFluid(), stack.getTag());
        if (ingredient instanceof com.lowdragmc.lowdraglib.side.fluid.FluidStack stack && !stack.isEmpty())
            return AEFluidKey.of(stack.getFluid(), stack.getTag());
        if (ingredient instanceof GenericStack stack) return key(stack.what());
        return ingredient instanceof AEItemKey || ingredient instanceof AEFluidKey ? (AEKey) ingredient : null;
    }
}
