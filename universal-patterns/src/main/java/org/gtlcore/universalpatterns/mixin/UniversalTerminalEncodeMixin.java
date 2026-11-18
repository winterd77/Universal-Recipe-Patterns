package org.gtlcore.universalpatterns.mixin;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.me.items.PatternEncodingTermMenu;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Also protect already-inserted cards from encoding/clearing after an upgrade. */
@Mixin(value=PatternEncodingTermMenu.class,remap=false)
public abstract class UniversalTerminalEncodeMixin {
 @Shadow @Final private RestrictedInputSlot encodedPatternSlot;
 @Inject(method={"encode","clearPattern"},at=@At("HEAD"),cancellable=true)
 private void universalPatterns$protect(CallbackInfo ci){
  if(encodedPatternSlot.getItem().getItem() instanceof UniversalPatternItem)ci.cancel();
 }
}
