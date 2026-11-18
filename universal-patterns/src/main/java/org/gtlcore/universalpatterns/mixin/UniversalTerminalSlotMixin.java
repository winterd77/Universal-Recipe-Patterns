package org.gtlcore.universalpatterns.mixin;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Reject insertion here, without changing pattern-provider recognition. */
@Mixin(value=RestrictedInputSlot.class,remap=false)
public abstract class UniversalTerminalSlotMixin extends appeng.menu.slot.AppEngSlot {
 protected UniversalTerminalSlotMixin(appeng.api.inventories.InternalInventory inventory,int index){super(inventory,index);}
 @Inject(method={"mayPlace(Lnet/minecraft/world/item/ItemStack;)Z","m_5857_(Lnet/minecraft/world/item/ItemStack;)Z"},at=@At("HEAD"),cancellable=true)
 private void universalPatterns$reject(ItemStack stack,CallbackInfoReturnable<Boolean> cir){
  if(stack.getItem() instanceof UniversalPatternItem && getMenu() instanceof PatternEncodingTermMenu)cir.setReturnValue(false);
 }
}
