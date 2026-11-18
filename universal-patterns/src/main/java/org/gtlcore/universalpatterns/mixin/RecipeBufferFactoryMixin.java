package org.gtlcore.universalpatterns.mixin;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import org.gtlcore.universalpatterns.common.machine.multiblock.part.ae.MERecipePatternBufferPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import java.util.function.Function;

/** Replace only the named recipe-buffer factory; compiler-generated lambda numbers are irrelevant. */
@Mixin(targets="org.gtlcore.gtlcore.common.data.GTLMachines$GTAEMachines",remap=false)
public abstract class RecipeBufferFactoryMixin {
    @Unique private static int universalPatterns$factoryMatches;
    @ModifyArg(method="<clinit>", at=@At(value="INVOKE",
            target="Lcom/gregtechceu/gtceu/api/registry/registrate/GTRegistrate;machine(Ljava/lang/String;Ljava/util/function/Function;)Lcom/gregtechceu/gtceu/api/registry/registrate/MachineBuilder;"),
            index=1, require=1, remap=false)
    private static Function<IMachineBlockEntity, MetaMachine> universalPatterns$factory(String name, Function<IMachineBlockEntity, MetaMachine> original) {
        if ("me_recipe_pattern_buffer".equals(name)) {
            if (++universalPatterns$factoryMatches != 1)
                throw new IllegalStateException("Duplicate recipe-buffer registration; universal pattern integration aborted");
            return holder -> new MERecipePatternBufferPartMachine(holder, IO.BOTH);
        }
        return original;
    }
    @Inject(method="<clinit>", at=@At("RETURN"), require=1, remap=false)
    private static void universalPatterns$verifyFactory(CallbackInfo ci) {
        if (universalPatterns$factoryMatches != 1)
            throw new IllegalStateException("Recipe-buffer registration not found; incompatible GTLCore build");
    }
}
