package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.config.*;
import appeng.api.crafting.*;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.common.data.GTLItems;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import java.util.*;

/** Only used by hatches whose runtime supports virtual ingredients, never by plain providers. */
public final class UniversalVirtualSupply {
    public static Map<AEKey,AEItemKey> relevant(List<IPatternDetails> patterns, Level level, Map<AEKey,AEItemKey> supplies) {
        if(supplies.isEmpty())return supplies;
        var relevant=new HashMap<AEKey,AEItemKey>();
        for(var pattern:patterns) {
            var tag=pattern.getDefinition().getReadOnlyStack().getTag();
            var recipe=tag==null?null:UniversalPatternExpansion.recipe(level,tag.getString("gtlcoreRecipe"));
            if(recipe!=null)for(var alternatives:recipe.nonConsumedInputs())for(var key:alternatives)
                if(supplies.containsKey(key))relevant.put(key,supplies.get(key));
        }
        return Map.copyOf(relevant);
    }
    public static Map<AEKey, AEItemKey> available(IGrid grid, IActionSource source) {
        if(grid==null)return Map.of();
        var supplies=new HashMap<AEKey,AEItemKey>();
        var storage=grid.getStorageService();
        for(var entry:storage.getCachedInventory().findFuzzy(AEItemKey.of(GTLItems.VIRTUAL_INGREDIENT.asStack()),FuzzyMode.IGNORE_ALL)) {
            if(entry.getLongValue()<=0 || !(entry.getKey() instanceof AEItemKey token))continue;
            var stack=VirtualIngredientBehavior.canonicalStack(token.getReadOnlyStack());
            if(stack.isEmpty() || storage.getInventory().extract(token,1,Actionable.SIMULATE,source)<=0)continue;
            AEKey payload=VirtualIngredientBehavior.payloadItemKey(stack);
            if(payload==null)payload=VirtualIngredientBehavior.payloadFluidKey(stack);
            if(payload==null)continue;
            if(!supplies.containsKey(payload) || token.equals(AEItemKey.of(stack)))supplies.put(payload,token);
        }
        return Map.copyOf(supplies);
    }
    public static List<IPatternDetails> decorate(List<IPatternDetails> patterns, Level level, Map<AEKey,AEItemKey> supplies) {
        if(supplies.isEmpty())return patterns;
        var result=new ArrayList<IPatternDetails>();
        for(var pattern:patterns) {
            var tag=pattern.getDefinition().getReadOnlyStack().getTag();
            var recipe=tag==null?null:UniversalPatternExpansion.recipe(level,tag.getString("gtlcoreRecipe"));
            if(recipe==null || !(pattern instanceof AEProcessingPattern processing)){result.add(pattern);continue;}
            var tokens=new LinkedHashSet<AEItemKey>();
            for(var alternatives:recipe.nonConsumedInputs())for(var key:alternatives) {
                var token=supplies.get(key);if(token!=null){tokens.add(token);break;}
            }
            if(tokens.isEmpty()){result.add(pattern);continue;}
            var inputs=new ArrayList<GenericStack>();
            for(var token:tokens)inputs.add(new GenericStack(token,1));
            Arrays.stream(processing.getSparseInputs()).filter(Objects::nonNull).forEach(inputs::add);
            var encoded=PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new),processing.getSparseOutputs());
            encoded.getOrCreateTag().putString("gtlcoreRecipe",recipe.id());
            encoded.getOrCreateTag().putInt("gtlcoreRecipeCircuit",recipe.circuit());
            var decoded=PatternDetailsHelper.decodePattern(encoded,level);
            if(decoded!=null)result.add(decoded);
        }
        return List.copyOf(result);
    }
}
