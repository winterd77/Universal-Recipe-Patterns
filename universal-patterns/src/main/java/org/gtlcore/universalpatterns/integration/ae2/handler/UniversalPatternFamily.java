package org.gtlcore.universalpatterns.integration.ae2.handler;

import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKey;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.GTLCore;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/** Derives the material transformation from a real machine recipe. */
public final class UniversalPatternFamily {
    private static final String PREFIX = "gtlcore.universal_pattern.";
    private static final Set<String> BLENDING = Set.of("gtceu:mixer", "gtceu:alloy_smelter", "gtceu:alloy_blast_smelter",
            "gtlcore:dimensionally_transcendent_mixer", "gtceu:dimensionally_transcendent_mixer");
    private static final Set<String> SEPARATING = Set.of("gtceu:electrolyzer", "gtceu:centrifuge", "gtceu:distillation_tower",
            "gtlcore:rare_earth_centrifugal", "gtceu:rare_earth_centrifugal", "gtceu:electromagnetic_separator");
    private static final Set<String> HEATING = Set.of("gtceu:electric_furnace", "gtceu:electric_blast_furnace",
            "gtceu:primitive_blast_furnace", "gtceu:arc_furnace");
    private static final List<String> MAGNETIC_FORMS = List.of("rod", "longRod", "sphere");
    private static final List<TagKey<Item>> MAGNETIC_FORM_TAGS = List.of(
            TagKey.create(Registries.ITEM, GTLCore.id("magnetic_forms/rod")),
            TagKey.create(Registries.ITEM, GTLCore.id("magnetic_forms/long_rod")),
            TagKey.create(Registries.ITEM, GTLCore.id("magnetic_forms/sphere")));

    private UniversalPatternFamily() {}

    public record Candidate(String family, @Nullable Material material, List<AEKey> inputs, AEKey output,
                            MERecipePatternHelper.RecipePattern recipe, @Nullable AEKey selector, String baseFamily) {
        public Candidate(String family, @Nullable Material material, List<AEKey> inputs, AEKey output,
                         MERecipePatternHelper.RecipePattern recipe, @Nullable AEKey selector) {
            this(family, material, inputs, output, recipe, selector, family);
        }
        Candidate(String family, @Nullable Material material, List<AEKey> inputs, AEKey output,
                  MERecipePatternHelper.RecipePattern recipe) {
            this(family, material, inputs, output, recipe, null);
        }

        public ResourceLocation materialId() {
            if (selector != null) return selector.getId();
            return material == null ? inputs.getFirst().getId() : material.getResourceLocation();
        }

        public Component materialName() {
            if (selector != null) return selector.getDisplayName();
            return material == null ? inputs.getFirst().getDisplayName() : material.getLocalizedName();
        }

        public boolean hasMaterial(Predicate<Material> condition) {
            return material != null && condition.test(material);
        }

        boolean matchesSample(Predicate<AEKey> test) {
            if (selector != null) return test.test(selector);
            if (material == null) return test.test(inputs.getFirst());
            return material == materialOf(output) && test.test(output) ||
                    inputs.stream().anyMatch(key -> material == materialOf(key) && test.test(key));
        }

        public boolean hasRecipeSample(AEKey sample) {
            return matchesRecipeSample(sample::equals);
        }

        boolean matchesRecipeSample(Predicate<AEKey> test) {
            return recipe.inputSlots().stream().flatMap(slot -> slot.alternatives().stream()).anyMatch(test) ||
                    Arrays.stream(recipe.pattern().getOutputs()).anyMatch(output -> test.test(output.what()));
        }
    }

    public static @Nullable Candidate describe(MERecipePatternHelper.RecipePattern recipe, @Nullable AEKey output) {
        String type = recipe.recipe().recipeType.registryName.toString();
        if (output == null) return null;
        if (output instanceof AEItemKey && isMagnetization(recipe)) {
            var magnetic = describeMagnetization(recipe, output, type);
            if (magnetic != null) return magnetic;
        }
        if (SEPARATING.contains(type)) {
            var separated = describeSeparation(recipe, output, type);
            if (separated != null) return separated;
        }
        Material material = materialOf(output);
        if (material == null) return null;
        String outputForm = formOf(output, material);
        if (outputForm == null) return null;
        var forms = new TreeSet<String>();
        var materialInputs = new ArrayList<AEKey>();
        for (var slot : recipe.inputSlots()) {
            // Alternatives are taken from the real consumed ingredient. Every alternative must
            // represent the same form before this slot can establish the material of the route.
            String form = formOf(slot.defaultKey(), material);
            if (form == null || slot.alternatives().stream().anyMatch(key -> !form.equals(formOf(key, material)))) continue;
            forms.add(form);
            materialInputs.addAll(slot.alternatives());
        }
        if (!forms.isEmpty()) {
            return candidate(recipe, output, material, materialInputs, type, String.join(",", forms), outputForm);
        }
        var demagnetized = describeDemagnetization(recipe, output, material, type, outputForm);
        if (demagnetized != null) return demagnetized;
        if (!BLENDING.contains(type) || recipe.inputSlots().size() < 2) return null;
        for (var slot : recipe.inputSlots()) {
            String form = formOf(slot.defaultKey());
            if (form == null || slot.alternatives().stream().anyMatch(key -> !form.equals(formOf(key)))) return null;
            forms.add(form);
            materialInputs.addAll(slot.alternatives());
        }
        return candidate(recipe, output, material, materialInputs, type, "blend," + String.join(",", forms), outputForm);
    }

    static boolean isMagnetization(MERecipePatternHelper.RecipePattern recipe) {
        return recipe.recipe().recipeType.registryName.toString().equals("gtceu:polarizer");
    }

    private static @Nullable Candidate describeMagnetization(MERecipePatternHelper.RecipePattern recipe, AEKey output, String type) {
        if (recipe.inputSlots().size() != 1) return null;
        var slot = recipe.inputSlots().getFirst();
        if (!(slot.defaultKey() instanceof AEItemKey) || slot.alternatives().isEmpty()) return null;
        String form = magneticForm(slot.defaultKey());
        if (slot.alternatives().stream().anyMatch(key -> !form.equals(magneticForm(key)))) return null;
        return candidate(recipe, output, materialOf(slot.defaultKey()), slot.alternatives(), type,
                "magnetization," + form, magneticForm(output));
    }

    private static String magneticForm(AEKey key) {
        String recognized = formOf(key);
        if (recognized != null) return recognized;
        if (key instanceof AEItemKey) {
            for (int i = 0; i < MAGNETIC_FORM_TAGS.size(); i++) {
                if (key.isTagged(MAGNETIC_FORM_TAGS.get(i))) return "item:" + MAGNETIC_FORMS.get(i);
            }
        }
        return "itemid:" + key.getId();
    }

    private static @Nullable Candidate describeDemagnetization(MERecipePatternHelper.RecipePattern recipe, AEKey output,
                                                                 Material base, String type, String outputForm) {
        var slot = demagnetizationInput(recipe, base, type);
        if (slot == null) return null;
        return candidate(recipe, output, materialOf(slot.defaultKey()), slot.alternatives(), type,
                "demagnetization," + magneticForm(slot.defaultKey()), outputForm);
    }

    static boolean isDemagnetization(Candidate candidate) {
        return demagnetizationInput(candidate.recipe(), materialOf(candidate.output()),
                candidate.recipe().recipe().recipeType.registryName.toString()) != null;
    }

    private static @Nullable MERecipePatternHelper.InputSlot demagnetizationInput(MERecipePatternHelper.RecipePattern recipe,
                                                                                 @Nullable Material base, String type) {
        if (base == null || !HEATING.contains(type)) return null;
        var property = base.getProperty(PropertyKey.INGOT);
        if (property == null || property.getMagneticMaterial() == null) return null;
        Material magnetic = property.getMagneticMaterial();
        for (var slot : recipe.inputSlots()) {
            if (!(slot.defaultKey() instanceof AEItemKey) || materialOf(slot.defaultKey()) != magnetic) continue;
            String form = magneticForm(slot.defaultKey());
            if (!slot.alternatives().isEmpty() && slot.alternatives().stream().allMatch(key ->
                    key instanceof AEItemKey && materialOf(key) == magnetic && form.equals(magneticForm(key)))) return slot;
        }
        return null;
    }

    private static @Nullable Candidate describeSeparation(MERecipePatternHelper.RecipePattern recipe, AEKey output, String type) {
        if (recipe.inputSlots().size() != 1) return null;
        var products = recipe.pattern().getOutputs();
        if (products.length < 2) return null;
        var input = recipe.inputSlots().getFirst();
        Material material = materialOf(input.defaultKey());
        if (material == null) return null;
        String form = formOf(input.defaultKey(), material);
        if (form == null || input.alternatives().stream().anyMatch(key -> !form.equals(formOf(key, material)))) return null;
        var productForms = new ArrayList<String>();
        for (GenericStack product : products) {
            String productForm = formOf(product.what());
            if (productForm == null) return null;
            productForms.add(productForm);
        }
        productForms.sort(String::compareTo);
        return candidate(recipe, output, material, input.alternatives(), type, "split," + form, String.join(",", productForms));
    }

    public static @Nullable Candidate describePetrochemical(MERecipePatternHelper.RecipePattern recipe,
                                                           @Nullable AEKey output, Level level) {
        GTRecipeRoute route = petrochemicalRoute(recipe, output, level);
        if (route == null) return null;
        var productForms = new ArrayList<String>();
        for (GenericStack product : recipe.pattern().getOutputs()) {
            String form = formOf(product.what());
            productForms.add(form != null ? form : "reagent:" + product.what().getId());
        }
        productForms.sort(String::compareTo);
        return candidate(recipe, output, route.material(), route.feeds(), recipe.recipe().recipeType.registryName.toString(),
                "petrochemical," + String.join(",", route.forms()), String.join(",", productForms));
    }

    private record GTRecipeRoute(Material material, List<AEKey> feeds, Set<String> forms) {}

    private static @Nullable GTRecipeRoute petrochemicalRoute(MERecipePatternHelper.RecipePattern recipe,
                                                              @Nullable AEKey output, Level level) {
        var registered = recipe.recipe();
        if (output == null || !PetrochemicalIndex.canClassify(registered)) return null;
        var index = PetrochemicalIndex.get(level);
        if (!index.isRoute(registered)) return null;

        var feeds = new ArrayList<AEKey>();
        var forms = new TreeSet<String>();
        Material singleFeed = null;
        int feedSlots = 0;
        for (var slot : recipe.inputSlots()) {
            AEKey primary = slot.defaultKey();
            Material material = materialOf(primary);
            String form = material == null ? null : formOf(primary, material);
            if (form != null && index.isFeed(registered, primary) &&
                    slot.alternatives().stream().allMatch(key -> index.isFeed(registered, key) && form.equals(formOf(key, material)))) {
                feedSlots++;
                singleFeed = material;
                feeds.addAll(slot.alternatives());
                forms.add(form);
            } else {
                // Steam and hydrogen routes stay distinct even when their material forms coincide.
                for (AEKey key : slot.alternatives()) forms.add("reagent:" + key.getId());
            }
        }
        Material anchor = feedSlots == 1 ? singleFeed : materialOf(output);
        return feedSlots == 0 || anchor == null ? null : new GTRecipeRoute(anchor, feeds, forms);
    }

    private static Candidate candidate(MERecipePatternHelper.RecipePattern recipe, AEKey output, @Nullable Material material,
                                       List<AEKey> inputs, String type, String inputForms, String outputForms) {
        return new Candidate(type + "|" + inputForms + "|" + outputForms, material, List.copyOf(inputs), output, recipe);
    }

    static @Nullable Material materialOf(@Nullable AEKey key) {
        if (key instanceof AEItemKey item) {
            var entry = ChemicalHelper.getUnificationEntry(item.getItem());
            return entry == null ? null : entry.material;
        }
        if (key instanceof AEFluidKey fluid) return ChemicalHelper.getMaterial(fluid.getFluid());
        return null;
    }

    private static @Nullable String formOf(AEKey key, Material material) {
        if (key instanceof AEItemKey item) {
            var entry = ChemicalHelper.getUnificationEntry(item.getItem());
            if (entry != null && entry.material == material && entry.tagPrefix != null) return "item:" + entry.tagPrefix.name();
        }
        if (key instanceof AEFluidKey fluid) {
            var source = fluid.getFluid();
            var property = material.getProperty(PropertyKey.FLUID);
            if (property != null) {
                if (source == property.get(FluidStorageKeys.MOLTEN)) return "fluid:molten";
                for (var storage : FluidStorageKey.allKeys()) {
                    if (source == property.get(storage)) return "fluid:" + storage.getResourceLocation().getPath();
                }
            }
        }
        return null;
    }

    static @Nullable String formOf(AEKey key) {
        Material material = materialOf(key);
        return material == null ? null : formOf(key, material);
    }

    public static Component typeLabel(ResourceLocation type) {
        return Component.translatableWithFallback(type.getNamespace()+"."+type.getPath(),
                Component.translatableWithFallback("gtceu."+type.getPath(), type.getPath()).getString());
    }

    public static Component label(String family) {
        if (family.isEmpty()) return Component.translatable(PREFIX + "unset");
        int route = family.lastIndexOf("|r:");
        if (route >= 0) {
            var base = label(family.substring(0, route));
            String circuit = family.substring(route + 3).split(":", 2)[0];
            return circuit.equals("-1") ? base : base.copy().append(" · ").append(Component.translatable(PREFIX + "route_circuit", circuit));
        }
        String[] parts = family.split("\\|", -1);
        if (parts.length != 3) return Component.translatable(PREFIX + "unset");
        String inputs = parts[1];
        if (inputs.startsWith("registered:")) {
            var type = ResourceLocation.tryParse(parts[0]);
            if (type == null) return Component.translatable(PREFIX + "unset");
            var product = formLabel(parts[2]);
            var roles = inputs.substring("registered:".length());
            if (!roles.equals("components")) product = Component.translatable(PREFIX + "family", formLabels(roles), product);
            return Component.translatable(PREFIX + "registered_family", typeLabel(type), product);
        }
        String key = "family";
        if (inputs.startsWith("stage:")) {
            int split = inputs.indexOf(',');
            if (split > 6) {
                String stage = inputs.substring(6, split);
                return Component.translatable(PREFIX + "stage_family",
                        Component.translatableWithFallback(PREFIX + "stage." + stage, stage),
                        formLabels(inputs.substring(split + 1)), formLabels(parts[2]));
            }
        }
        if (inputs.startsWith("blend,")) {
            key = "blend_family";
            inputs = inputs.substring(6);
        } else if (inputs.startsWith("split,")) {
            key = "split_family";
            inputs = inputs.substring(6);
        } else if (inputs.startsWith("petrochemical,")) {
            key = "petrochemical_family";
            inputs = inputs.substring("petrochemical,".length());
        } else if (inputs.startsWith("magnetization,")) {
            key = "magnetization_family";
            inputs = inputs.substring("magnetization,".length());
        } else if (inputs.startsWith("demagnetization,")) {
            key = "demagnetization_family";
            inputs = inputs.substring("demagnetization,".length());
        }
        return Component.translatable(PREFIX + key, formLabels(inputs), formLabels(parts[2]));
    }

    public static Component compactLabel(String family) {
        String[] parts = family.split("\\|", -1);
        if (parts.length == 3 && parts[1].startsWith("stage:")) {
            int split = parts[1].indexOf(',');
            if (split > 6) {
                String stage = parts[1].substring(6, split);
                if (stage.equals("fission")) return formLabels(parts[1].substring(split + 1));
                if (stage.equals("water_purification")) return formLabels(parts[2]);
                return Component.translatableWithFallback(PREFIX + "stage." + stage, stage);
            }
        }
        return label(family);
    }

    private static Component formLabels(String forms) {
        var label = Component.empty();
        for (String form : forms.split(",")) {
            if (!label.getSiblings().isEmpty()) label.append(" + ");
            label.append(formLabel(form));
        }
        return label;
    }

    private static Component formLabel(String form) {
        int separator = form.indexOf(':');
        String name = separator < 0 ? form : form.substring(separator + 1);
        if (form.startsWith("tiered:") || form.startsWith("material_part:"))
            return Component.translatableWithFallback(PREFIX + "part." + name, name.replace('_', ' '));
        if (form.startsWith("water:")) {
            return name.equals("distilled") ? Component.translatable(PREFIX + "distilled_water") :
                    Component.translatable(PREFIX + "water_grade", name);
        }
        if (form.startsWith("tier:")) return Component.literal(name.toUpperCase(java.util.Locale.ROOT));
        if (form.startsWith("mold:")) {
            return Component.translatableWithFallback("item." + name.replace(':', '.'), name);
        }
        return Component.translatableWithFallback(PREFIX + "form." + name, name);
    }
}
