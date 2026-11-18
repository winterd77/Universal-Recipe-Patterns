package org.gtlcore.universalpatterns.mixin;
import appeng.api.crafting.PatternDetailsHelper;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Slot recognition only: a many-recipe card must never decode as a fabricated single recipe. */
@Mixin(value=PatternDetailsHelper.class, remap=false)
public class UniversalPatternRecognitionMixin {
    @Inject(method="isEncodedPattern", at=@At("HEAD"), cancellable=true)
    private static void universalPatterns$recognize(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof UniversalPatternItem) cir.setReturnValue(true);
    }
}
