package org.gtlcore.universalpatterns.integration.ae2.handler;

import appeng.api.crafting.IPatternDetails;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem.Selection;
import java.util.*;

/** Expands applied card settings only. Physical cards (including their drafts) are never rewritten. */
public final class UniversalPatternExpansion {
    private static final Map<Level, Cache> CACHES = new WeakHashMap<>();
    public static void invalidate() { CACHES.clear(); }
    public static MERecipePatternHelper.RecipePattern recipe(Level level, String id) {
        var cache=CACHES.get(level);
        return cache==null ? null : cache.byId.get(id);
    }
    private static final class Cache {
        final long generation = MERecipePatternHelper.getReloadGeneration();
        final Map<Selection, List<IPatternDetails>> selections = new LinkedHashMap<>();
        final Map<String, List<MERecipePatternHelper.RecipePattern>> types = new HashMap<>();
        final Map<String, MERecipePatternHelper.RecipePattern> byId = new HashMap<>();
        final Map<String, RecipeRoute.Families> families = new HashMap<>();
    }
    public static List<IPatternDetails> expand(ItemStack stack, Level level) {
        if (level == null || !(stack.getItem() instanceof UniversalPatternItem)) return List.of();
        Selection selection = UniversalPatternItem.read(stack);
        if (selection.family().isEmpty()) return List.of();
        Cache cache = CACHES.get(level);
        if (cache == null || cache.generation != MERecipePatternHelper.getReloadGeneration()) {
            cache = new Cache(); CACHES.put(level, cache);
        }
        var existing = cache.selections.get(selection);
        if (existing != null) return existing;
        String typeId = selection.family().split("\\|", 2)[0];
        var location = ResourceLocation.tryParse(typeId);
        if (location == null) return List.of();
        var type = GTRegistries.RECIPE_TYPES.get(location);
        if (type == null) return List.of();
        var recipes = cache.types.computeIfAbsent(typeId, ignored -> {
            var result = new ArrayList<MERecipePatternHelper.RecipePattern>();
            for (var recipe : MERecipePatternHelper.getRecipes(level.getRecipeManager(), Set.of(type))) {
                var pattern = MERecipePatternHelper.createPattern(recipe, level);
                if (pattern != null && !pattern.chancedInput() && pattern.primaryOutput() != null) result.add(pattern);
            }
            return List.copyOf(result);
        });
        var result = new LinkedHashSet<IPatternDetails>();
        for (var recipe : recipes) cache.byId.putIfAbsent(recipe.id(), recipe);
        try {
            var allow = UniversalMaterialRules.compile(selection.allow(), level);
            var deny = UniversalMaterialRules.compile(selection.deny(), level);
            for (var recipe : recipes) {
                if(!ConsumedFluidFilter.matches(selection,recipe))continue;
                var families = cache.families.computeIfAbsent(recipe.id(), ignored ->
                        RecipeRoute.describe(recipe,recipe.primaryOutput(),true,level));
                var candidate = RecipeRoute.resolve(families.preferred(),families.legacy(),selection.family());
                if (candidate == null) continue;
                var id = candidate.materialId();
                var matched = candidate;
                if (selection.recipeReason(recipe.id(), id, () -> allow.test(matched), () -> deny.test(matched)) <= 2) {
                    var delivered=DeliveryPolicy.apply(selection.forMaterial(id.toString()),recipe,candidate.output(),level);
                    if(delivered!=null)delivered=DirectCatalystSupply.apply(selection,recipe,delivered,level);
                    if(delivered!=null)result.add(delivered);
                }
            }
        } catch (IllegalArgumentException invalidRule) {
            // Fail closed after a tag/recipe reload; never publish an unfiltered family.
            result.clear();
        }
        var answer = List.copyOf(result);
        if (cache.selections.size() >= 256) cache.selections.remove(cache.selections.keySet().iterator().next());
        cache.selections.put(selection, answer);
        return answer;
    }
}
