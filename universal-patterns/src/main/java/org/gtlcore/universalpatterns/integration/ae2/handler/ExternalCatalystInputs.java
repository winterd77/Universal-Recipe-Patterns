package org.gtlcore.universalpatterns.integration.ae2.handler;

import appeng.api.stacks.*;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import java.util.*;

/** Only proven reusable inputs may be delegated to the target inventory. */
public final class ExternalCatalystInputs {
    private ExternalCatalystInputs() {}
    public static List<List<GenericStack>> remove(List<List<GenericStack>> inputs, Object recipeBase) {
        GTRecipe recipe = recipeBase instanceof GTRecipe r ? r : recipeBase instanceof GTRecipeWrapper w ? w.recipe : null;
        if (recipe == null) return inputs;
        Set<AEKey> reusable = new HashSet<>(), consumed = new HashSet<>();
        for (var contents : List.of(recipe.getInputContents(ItemRecipeCapability.CAP), recipe.getTickInputContents(ItemRecipeCapability.CAP))) {
            for (var content : contents) {
                Set<AEKey> target = content.chance == 0 ? reusable : consumed;
                for (var stack : ItemRecipeCapability.CAP.of(content.content).getItems())
                    if (!stack.isEmpty()) target.add(AEItemKey.of(stack));
            }
        }
        for (var contents : List.of(recipe.getInputContents(FluidRecipeCapability.CAP), recipe.getTickInputContents(FluidRecipeCapability.CAP))) {
            for (var content : contents) {
                Set<AEKey> target = content.chance == 0 ? reusable : consumed;
                for (var stack : FluidRecipeCapability.CAP.of(content.content).getStacks())
                    if (!stack.isEmpty()) target.add(AEFluidKey.of(stack.getFluid(), stack.getTag()));
            }
        }
        reusable.removeAll(consumed);
        return inputs.stream().filter(slot -> slot.isEmpty() || !slot.stream().allMatch(stack -> stack != null && reusable.contains(payload(stack.what())))).toList();
    }
    private static AEKey payload(AEKey key) {
        if (key instanceof AEItemKey item) {
            var canonical = VirtualIngredientBehavior.canonicalStack(item.getReadOnlyStack());
            if (!canonical.isEmpty()) {
                AEKey material = VirtualIngredientBehavior.payloadItemKey(canonical);
                if (material == null) material = VirtualIngredientBehavior.payloadFluidKey(canonical);
                if (material != null) return material;
            }
        }
        return key;
    }
}
