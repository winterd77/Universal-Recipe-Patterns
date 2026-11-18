package org.gtlcore.universalpatterns.integration.ae2.handler;

import appeng.api.stacks.*;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem.Selection;
import java.util.*;

/** Filters existing encoded inputs. Never substitutes a fluid or changes its quantity. */
public final class ConsumedFluidFilter {
    private ConsumedFluidFilter() {}
    public static List<AEFluidKey> fluids(RecipePattern recipe) {
        if(recipe==null)return List.of();
        return recipe.inputSlots().stream().map(slot->slot.defaultKey())
                .filter(AEFluidKey.class::isInstance).map(AEFluidKey.class::cast).distinct().toList();
    }
    public static List<AEKey> inputs(RecipePattern recipe) {
        if(recipe==null)return List.of();
        return recipe.inputSlots().stream().map(slot->slot.defaultKey()).distinct().toList();
    }
    public static boolean matches(Selection selection,RecipePattern recipe) {
        var required=selection.requiredInputs();
        if(required.isEmpty())return true;
        var present=new HashSet<String>();for(var key:inputs(recipe))present.add(CatalystSupply.identity(key));
        return present.containsAll(required);
    }
}
