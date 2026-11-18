package org.gtlcore.universalpatterns.mixin;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalRecipeBinding;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets={"org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTraitBase$MEItemInputHandlerBase",
        "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTraitBase$MEFluidHandlerBase"}, remap=false)
public abstract class UniversalRecipeBindingMixin {
    @Shadow public abstract MEPatternBufferPartMachineBase getMachine();
    @Inject(method="meHandleRecipeInner", at=@At("HEAD"), cancellable=true)
    private void universalPatterns$match(GTRecipe recipe, Object2LongMap<?> left, boolean simulate, int slot, CallbackInfoReturnable<Boolean> cir) {
        if (getMachine() instanceof UniversalRecipeBinding binding && !binding.universalPatterns$matchesRecipe(slot, recipe)) cir.setReturnValue(false);
    }
    @Inject(method="meHandleRecipeInner", at=@At("RETURN"))
    private void universalPatterns$dirty(GTRecipe recipe, Object2LongMap<?> left, boolean simulate, int slot, CallbackInfoReturnable<Boolean> cir) {
        if (!simulate && getMachine() instanceof UniversalRecipeBinding) getMachine().markDirty();
    }
}
