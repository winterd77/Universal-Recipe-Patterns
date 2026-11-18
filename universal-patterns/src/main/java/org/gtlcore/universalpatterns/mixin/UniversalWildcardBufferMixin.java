package org.gtlcore.universalpatterns.mixin;
import appeng.api.crafting.IPatternDetails;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternExpansion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
@Mixin(targets="org.gtlcore.gtlcore.integration.wildcard.WildcardPatternCompatImpl", remap=false)
public class UniversalWildcardBufferMixin {
    @Inject(method="isWildcardPattern", at=@At("HEAD"), cancellable=true)
    private static void universalPatterns$accept(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof UniversalPatternItem) cir.setReturnValue(true);
    }
    @Inject(method="expandPatterns", at=@At("HEAD"), cancellable=true)
    private static void universalPatterns$expand(ItemStack stack, Level level, CallbackInfoReturnable<List<IPatternDetails>> cir) {
        if (stack.getItem() instanceof UniversalPatternItem) cir.setReturnValue(UniversalPatternExpansion.expand(stack, level));
    }
}
