package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.stacks.AEKey;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import java.util.Arrays;
/** Directional reference filters; both populated sides must match the same registered recipe. */
public final class RecipeSampleMatch {
    private RecipeSampleMatch() {}
    public static boolean matches(RecipePattern recipe, AEKey input, AEKey output) {
        return (input == null || recipe.inputSlots().stream().flatMap(slot -> slot.alternatives().stream()).anyMatch(input::equals)) &&
                (output == null || Arrays.stream(recipe.pattern().getOutputs()).anyMatch(stack -> output.equals(stack.what())));
    }
}
