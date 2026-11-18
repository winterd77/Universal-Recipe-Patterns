package org.gtlcore.universalpatterns.integration.ae2.handler;

import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** Tracks petroleum fluids through registered routes in the current recipe manager. */
public final class PetrochemicalIndex {
    private static final TagKey<Fluid> FEEDSTOCKS = TagKey.create(Registries.FLUID, GTLCore.id("petrochemical_feedstocks"));
    private static final TagKey<Fluid> COMMON_FLUIDS = TagKey.create(Registries.FLUID, GTLCore.id("petrochemical_common_fluids"));
    private static final Set<GTRecipeType> REFINING = Set.of(GTLRecipeTypes.PETROCHEMICAL_PLANT_RECIPES,
            GTLRecipeTypes.DESULFURIZER_RECIPES, GTLRecipeTypes.FUEL_REFINING_RECIPES, GTRecipeTypes.CRACKING_RECIPES,
            GTRecipeTypes.DISTILLATION_RECIPES, GTRecipeTypes.DISTILLERY_RECIPES);
    private static final Set<GTRecipeType> REACTION = Set.of(GTRecipeTypes.CHEMICAL_RECIPES, GTRecipeTypes.LARGE_CHEMICAL_RECIPES);
    private static final Set<Fluid> EMPTY = Set.of();
    private static final Map<RecipeManager, Cached> CACHE = new WeakHashMap<>();

    private record Cached(long generation, PetrochemicalIndex index) {}
    private record Route(GTRecipe recipe, Set<Fluid> inputs, Set<Fluid> outputs) {}

    private final Set<Fluid> products;
    private final Map<GTRecipe, Set<Fluid>> feedsByRecipe;

    private PetrochemicalIndex(Set<Fluid> products, Map<GTRecipe, Set<Fluid>> feedsByRecipe) {
        this.products = products;
        this.feedsByRecipe = feedsByRecipe;
    }

    public static synchronized PetrochemicalIndex get(Level level) {
        RecipeManager manager = level.getRecipeManager();
        long generation = MERecipePatternHelper.getReloadGeneration();
        Cached cached = CACHE.get(manager);
        if (cached != null && cached.generation() == generation) return cached.index();
        PetrochemicalIndex index = build(manager);
        CACHE.put(manager, new Cached(generation, index));
        return index;
    }

    static boolean canClassify(GTRecipe recipe) {
        return REFINING.contains(recipe.recipeType) || REACTION.contains(recipe.recipeType);
    }

    public boolean isRoute(GTRecipe recipe) {
        return feedsByRecipe.containsKey(recipe);
    }

    public boolean isProduct(AEKey key) {
        return key instanceof AEFluidKey fluid && products.contains(fluid.getFluid());
    }

    boolean isFeed(GTRecipe recipe, AEKey key) {
        return key instanceof AEFluidKey fluid && feedsByRecipe.getOrDefault(recipe, EMPTY).contains(fluid.getFluid());
    }

    static boolean isMetal(@Nullable Material material) {
        // GT also assigns the ingot property to ingot-shaped plastics and rubbers.
        return material != null && material.hasProperty(PropertyKey.INGOT) &&
                !material.hasProperty(PropertyKey.POLYMER) && !material.hasProperty(PropertyKey.WOOD);
    }

    private static PetrochemicalIndex build(RecipeManager manager) {
        Set<GTRecipeType> types = new HashSet<>(REFINING);
        types.addAll(REACTION);
        List<Route> pending = new ArrayList<>();
        Set<Fluid> known = new HashSet<>();
        for (GTRecipeType type : types) {
            for (GTRecipe recipe : manager.getAllRecipesFor(type)) {
                Set<Fluid> inputs = fluids(recipe, true);
                Set<Fluid> outputs = fluids(recipe, false);
                outputs.removeIf(fluid -> isMetal(ChemicalHelper.getMaterial(fluid)));
                if (REACTION.contains(type)) {
                    // Shared reactors take part in the oil chain through the actual sulfuric fractions.
                    inputs.removeIf(fluid -> !isDesulfurizationFeed(fluid));
                }
                if (inputs.isEmpty() || outputs.isEmpty()) continue;
                inputs.stream().filter(fluid -> fluid.is(FEEDSTOCKS)).forEach(known::add);
                pending.add(new Route(recipe, inputs, outputs));
            }
        }

        Map<GTRecipe, Route> matched = new IdentityHashMap<>();
        Set<Fluid> products = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (var it = pending.iterator(); it.hasNext();) {
                Route route = it.next();
                if (route.recipe().recipeType != GTLRecipeTypes.PETROCHEMICAL_PLANT_RECIPES &&
                        Collections.disjoint(known, route.inputs())) continue;
                matched.put(route.recipe(), route);
                products.addAll(route.outputs());
                changed |= known.addAll(route.outputs());
                it.remove();
            }
        } while (changed);

        Map<GTRecipe, Set<Fluid>> feeds = new IdentityHashMap<>();
        matched.forEach((recipe, route) -> {
            var selected = new HashSet<>(route.inputs());
            if (recipe.recipeType != GTLRecipeTypes.PETROCHEMICAL_PLANT_RECIPES) selected.retainAll(known);
            feeds.put(recipe, Set.copyOf(selected));
        });
        return new PetrochemicalIndex(Set.copyOf(products), feeds);
    }

    private static boolean isDesulfurizationFeed(Fluid fluid) {
        Material material = ChemicalHelper.getMaterial(fluid);
        if (material == null) return false;
        return switch (material.getResourceLocation().toString()) {
            case "gtceu:sulfuric_heavy_fuel", "gtceu:sulfuric_light_fuel", "gtceu:sulfuric_naphtha",
                    "gtceu:sulfuric_gas", "gtceu:natural_gas" -> true;
            default -> false;
        };
    }

    private static Set<Fluid> fluids(GTRecipe recipe, boolean inputs) {
        var capability = FluidRecipeCapability.CAP;
        var lists = inputs ? List.of(recipe.getInputContents(capability), recipe.getTickInputContents(capability)) :
                List.of(recipe.getOutputContents(capability), recipe.getTickOutputContents(capability));
        var fluids = new HashSet<Fluid>();
        for (var contents : lists) {
            for (var content : contents) {
                if (content.chance <= 0 || !inputs && content.chance < content.maxChance) continue;
                for (var stack : capability.of(content.content).getStacks()) {
                    if (!stack.isEmpty() && !stack.getFluid().is(COMMON_FLUIDS)) fluids.add(stack.getFluid());
                }
            }
        }
        return fluids;
    }
}
