package org.gtlcore.universalpatterns.integration.ae2.handler;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;

import java.util.*;

/** A planning multiplier applies to ordinary registered products, before manual delivery edits. */
public final class RecipeYield {
    private RecipeYield() {}

    static long scaledAmount(long base, long variable, int factor) {
        if (factor < 1 || variable < 0 || variable > base) throw new IllegalArgumentException("Invalid yield");
        return Math.addExact(base, Math.multiplyExact(variable, factor - 1L));
    }

    public static List<GenericStack> outputs(RecipePattern recipe, int factor, Level level) {
        return outputs(recipe,factor,level,key -> true);
    }

    static List<GenericStack> outputs(RecipePattern recipe, int factor, Level level, java.util.function.Predicate<AEKey> expected) {
        var base = Arrays.stream(recipe.pattern().getOutputs()).filter(s -> expected.test(s.what())).toList();
        if (factor == 1) return base;
        var raw = recipe.recipe();
        var itemOutputs = raw.getOutputContents(ItemRecipeCapability.CAP);
        boolean mixed = !raw.getTickOutputContents(ItemRecipeCapability.CAP).isEmpty()
                || !raw.getTickOutputContents(FluidRecipeCapability.CAP).isEmpty()
                || itemOutputs.stream().anyMatch(c -> "recipe_cycle_container".equals(c.slotName));
        if (!mixed) return base.stream().map(stack ->
                new GenericStack(stack.what(), scaledAmount(stack.amount(), stack.amount(), factor))).toList();

        // The same AE key can occur as a product, returned container and per-tick output.
        // Project only scalable slots through GTLCore's original chance/count conversion.
        Map<RecipeCapability<?>, List<Content>> ordinary = new HashMap<>();
        ordinary.put(ItemRecipeCapability.CAP, itemOutputs.stream()
                .filter(c -> !"recipe_cycle_container".equals(c.slotName)).toList());
        ordinary.put(FluidRecipeCapability.CAP, raw.getOutputContents(FluidRecipeCapability.CAP));
        var productRecipe = new GTRecipe(raw.recipeType, raw.id, raw.inputs, ordinary, raw.tickInputs, Map.of(),
                raw.inputChanceLogics, raw.outputChanceLogics, raw.tickInputChanceLogics, Map.of(),
                raw.conditions, raw.ingredientActions, raw.data, raw.duration, raw.isFuel);
        var products = MERecipePatternHelper.createPattern(productRecipe, level);
        if (products == null) return base;
        var variable = new HashMap<AEKey, Long>();
        for (var stack : products.pattern().getOutputs()) variable.merge(stack.what(), stack.amount(), Math::addExact);
        return base.stream().map(stack -> new GenericStack(stack.what(),
                scaledAmount(stack.amount(), variable.getOrDefault(stack.what(), 0L), factor))).toList();
    }
}
