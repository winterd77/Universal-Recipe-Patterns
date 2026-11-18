package org.gtlcore.universalpatterns.api.gui;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory.HeldItemHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.api.gui.RecipePatternUiTextures;
import org.gtlcore.universalpatterns.client.gui.widget.RecipePatternBrowserWidget;
import org.gtlcore.universalpatterns.integration.ae2.handler.WildcardPatternEditor;
import java.util.Set;

/** Binds the session to the actual stack, not merely an equal item in the same hand. */
public final class UniversalPatternHeldEditor {
    public static final class Holder extends HeldItemHolder {
        private final int selectedSlot;
        public Holder(Player player, InteractionHand hand) {
            super(player,hand);
            selectedSlot=player.getInventory().selected;
        }
        @Override public boolean isInvalid() {
            return getPlayer().getItemInHand(getHand()) != getHeld() || getHeld().isEmpty() ||
                    (getHand()==InteractionHand.MAIN_HAND && getPlayer().getInventory().selected!=selectedSlot);
        }
        @Override public void markAsDirty() { getPlayer().getInventory().setChanged(); }
    }
    public static ModularUI create(Player player, HeldItemHolder original) {
        var holder=new Holder(player,original.getHand());
        var cards=new ItemStackTransfer(1) {
            @Override public ItemStack getStackInSlot(int slot) {
                return slot==0 && !holder.isInvalid() ? holder.getHeld() : ItemStack.EMPTY;
            }
            @Override public void setStackInSlot(int slot, ItemStack stack) {
                if(slot!=0 || holder.isInvalid() || holder.isRemote())return;
                // Keep the original stack reference so ordinary draft saves do not invalidate the session.
                holder.getHeld().setTag(stack.getTag()==null ? null : stack.getTag().copy());
                holder.markAsDirty();
            }
        };
        var editor=new WildcardPatternEditor(new WildcardPatternEditor.Context() {
            public net.minecraft.world.level.Level getLevel() { return player.level(); }
            public boolean isRemote() { return holder.isRemote(); }
            public boolean isValid() { return !holder.isInvalid(); }
            public Set<com.gregtechceu.gtceu.api.recipe.GTRecipeType> recipeTypes() { return Set.of(); }
            public void markDirty() { holder.markAsDirty(); }
        },cards,()->{});
        var browser=new RecipePatternBrowserWidget(8,16,editor,RecipePatternBrowserWidget.Page.UNIVERSAL,true);
        var inventory=new WidgetGroup(0,220,348,94) {
            @Override public void drawInBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTicks) {
                if(!browser.hasModalOpen())super.drawInBackground(graphics,mouseX,mouseY,partialTicks);
            }
            @Override public void drawInForeground(GuiGraphics graphics,int mouseX,int mouseY,float partialTicks) {
                if(!browser.hasModalOpen())super.drawInForeground(graphics,mouseX,mouseY,partialTicks);
            }
            @Override public boolean mouseClicked(double x,double y,int button) {
                return !browser.hasModalOpen() && super.mouseClicked(x,y,button);
            }
        };
        inventory.addWidget(new LabelWidget(93,1,"container.inventory").setTextColor(0xFF303541).setDropShadow(false));
        for(int index=0;index<36;index++) {
            final int slot=index;
            int x=93+(index%9)*18;
            int y=index<9 ? 72 : 14+((index-9)/9)*18;
            inventory.addWidget(new SlotWidget(player.getInventory(),slot,x,y,true,true) {
                private boolean available() {
                    return !holder.isInvalid() && !browser.hasModalOpen() &&
                            !(holder.getHand()==InteractionHand.MAIN_HAND && slot==holder.selectedSlot);
                }
                @Override public boolean canTakeStack(Player who) { return available() && super.canTakeStack(who); }
                @Override public boolean canPutStack(ItemStack stack) {
                    // Also blocks number-key/offhand swapping the edited card into another inventory slot.
                    return available() && stack!=holder.getHeld() && super.canPutStack(stack);
                }
                @Override public boolean isEnabled() { return !browser.hasModalOpen() && super.isEnabled(); }
            }.setLocationInfo(true,index<9).setBackgroundTexture(GuiTextures.SLOT));
        }
        return new ModularUI(348,314,holder,player)
                .background(RecipePatternUiTextures.BACKGROUND)
                .widget(new LabelWidget(8,4,"item.gtlcore.universal_pattern").setTextColor(0xFF303541).setDropShadow(false))
                .widget(inventory)
                .widget(browser);
    }
}
