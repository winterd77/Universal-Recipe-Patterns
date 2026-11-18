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
                var candidate = RegisteredProcessFamilies.describe(recipe, recipe.primaryOutput(), true);
                if (candidate == null || !candidate.family().equals(selection.family())) {
                    candidate = RegisteredProcessFamilies.legacyCasting(candidate, selection.family());
                    if (candidate == null) candidate = UniversalPatternFamily.describe(recipe, recipe.primaryOutput());
                    if (candidate == null) candidate = UniversalPatternFamily.describePetrochemical(recipe, recipe.primaryOutput(), level);
                }
                if (candidate == null || !candidate.family().equals(selection.family())) continue;
                var id = candidate.materialId();
                Boolean override=selection.recipeOverrides().get(recipe.id());
                if(Boolean.FALSE.equals(override))continue;
                if (override==null&&(selection.excluded().contains(id) || deny.test(candidate))) continue;
                String chosen = selection.recipes().get(id);
                if (override==null&&chosen != null && !chosen.equals(recipe.id())) continue;
                if (Boolean.TRUE.equals(override)||selection.materials().contains(id) || allow.test(candidate)) {
                    var delivered=DeliveryPolicy.apply(selection.forMaterial(id.toString()),recipe.pattern(),level);
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
