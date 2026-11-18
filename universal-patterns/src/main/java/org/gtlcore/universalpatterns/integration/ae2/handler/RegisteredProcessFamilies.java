package org.gtlcore.universalpatterns.integration.ae2.handler;

import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternFamily.Candidate;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/** Additional groups whose roles are known from GT's recipe types and their actual IO. */
public final class RegisteredProcessFamilies {
    private static final Pattern GRADED_WATER = Pattern.compile("grade_([1-9][0-9]?)_purified_water");
    private static final Pattern FUEL_ROD = Pattern.compile("reactor_[a-z0-9_]+_(simple|dual|quad)");
    private static final List<String> CIRCUIT_TIERS = List.of("ulv", "lv", "mv", "hv", "ev", "iv", "luv", "zpm",
            "uv", "uhv", "uev", "uiv", "uxv", "opv", "max");
    private static final Set<String> ORE_STAGES = Set.of("ore_washer", "thermal_centrifuge", "integrated_ore_processor",
            "isa_mill", "flotating_beneficiation", "rare_earth_centrifugal");
    private static final TagKey<net.minecraft.world.item.Item> MILLED_ORES =
            TagKey.create(Registries.ITEM, GTLCore.id("ore_processing/milled"));
    private static final TagKey<net.minecraft.world.level.material.Fluid> FLOTATION_FROTH =
            TagKey.create(Registries.FLUID, GTLCore.id("ore_processing/flotation_froth"));
    private static final Set<String> ASSEMBLY = Set.of("circuit_assembler", "circuit_assembly_line", "assembly_line",
            "precision_assembler", "suprachronal_assembly_line", "component_assembly_line", "assembler");
    private static final Map<String, String> MACHINE_LINES = Map.ofEntries(
            Map.entry("heat_exchanger", "heat_exchange"),
            Map.entry("isa_mill", "fine_grinding"),
            Map.entry("flotating_beneficiation", "flotation"),
            Map.entry("dissolution_treatment", "leaching"),
            Map.entry("digestion_treatment", "leaching"),
            Map.entry("rare_earth_centrifugal", "rare_earth"),
            Map.entry("large_recycler", "recycling"),
            Map.entry("disassembly", "disassembly"),
            Map.entry("packer", "packing"),
            Map.entry("greenhouse", "cultivation"),
            Map.entry("fermenter", "fermentation"),
            Map.entry("brewery", "brewing"),
            Map.entry("fluid_solidifier", "casting"));
    private static final Set<String> INSPECTED_LINES = Set.of("water_purification", "plasma_condensation", "plasma_containment",
            "plasma_production", "powder_blending", "fission", "ore_beneficiation", "concentrate_drying", "electronic_assembly", "pcb_fabrication");
    private static final String CASTING_STAGE = "|stage:casting,";
    private static final String CASTING_FEED = "form:casting_fluid";

    private RegisteredProcessFamilies() {}

    public static boolean supportsLine(String name) {
        return INSPECTED_LINES.contains(name) || MACHINE_LINES.containsValue(name);
    }

    static Set<String> lineNames() {
        var names = new HashSet<>(INSPECTED_LINES);
        names.addAll(MACHINE_LINES.values());
        return names;
    }

    public static boolean matchesLine(Candidate candidate, String name) {
        RecipePattern recipe = candidate.recipe();
        String type = type(recipe);
        return switch (name) {
            case "powder_blending" -> type.equals("mixer") && powderBlendProfile(recipe,candidate.output())!=null;
            case "water_purification" -> purifiedOutput(recipe) != null;
            case "plasma_condensation" -> type.equals("plasma_condenser") && plasmaFeed(recipe) != null;
            case "plasma_containment" -> type.equals("canner") && plasmaFeed(recipe) != null && filledCell(candidate.output());
            case "plasma_production" -> !type.equals("plasma_condenser") && isPhase(candidate.output(), "plasma");
            case "fission" -> type.equals("fission_reactor") && fissionFeed(recipe, candidate.output()) != null;
            case "ore_beneficiation" -> ORE_STAGES.contains(type) ||
                    type.equals("vacuum_drying") && frothSlot(recipe) != null;
            case "concentrate_drying" -> type.equals("vacuum_drying") && frothSlot(recipe) != null;
            case "electronic_assembly" -> ASSEMBLY.contains(type) && circuitTier(candidate.output()) != null;
            case "pcb_fabrication" -> type.equals("pcb_factory");
            default -> name.equals(MACHINE_LINES.get(type));
        };
    }

    public static boolean isPhase(AEKey key, String phase) {
        if (!(key instanceof AEFluidKey fluid)) return false;
        Material material = UniversalPatternFamily.materialOf(fluid);
        if (material == null) return false;
        var property = material.getProperty(PropertyKey.FLUID);
        if (property == null) return false;
        var storage = switch (phase) {
            case "plasma" -> FluidStorageKeys.PLASMA;
            case "molten" -> FluidStorageKeys.MOLTEN;
            case "gas" -> FluidStorageKeys.GAS;
            case "liquid" -> FluidStorageKeys.LIQUID;
            default -> throw new IllegalArgumentException(phase);
        };
        return fluid.getFluid() == property.get(storage);
    }

    public static boolean isPurifiedWater(AEKey key) {
        return waterGrade(key) > 0;
    }

    public static boolean isFilledPlasmaCell(AEKey key) {
        return filledCell(key);
    }

    public static @Nullable Candidate describe(RecipePattern recipe, @Nullable AEKey defaultOutput, boolean preferOutput) {
        String type = type(recipe);
        String blend=type.equals("mixer")?powderBlendProfile(recipe,defaultOutput):null;
        if(blend!=null)return group(recipe,defaultOutput,defaultOutput,"powder_blending",blend,UniversalPatternFamily.formOf(defaultOutput));
        AEKey cleanedWater = purifiedOutput(recipe);
        if (cleanedWater != null && (preferOutput || cleanedWater.equals(defaultOutput))) {
            int grade = waterGrade(cleanedWater);
            return group(recipe, cleanedWater, cleanedWater, "water_purification", signature(recipe),
                    "water:" + (grade == 1 ? "distilled" : Integer.toString(grade - 1)));
        }
        if (defaultOutput == null) return null;
        Candidate ore = describeOreLine(recipe, defaultOutput, type);
        if (ore != null) return ore;
        if (type.equals("fission_reactor")) {
            AEKey fuel = fissionFeed(recipe, defaultOutput);
            if (fuel == null) return null;
            String path = fuel.getId().getPath();
            return group(recipe, fuel, defaultOutput, "fission", "fuel:" + path.substring(path.lastIndexOf('_') + 1), "fuel:depleted");
        }
        if (type.equals("plasma_condenser") || type.equals("canner")) {
            AEKey feed = plasmaFeed(recipe);
            if (feed != null && (type.equals("plasma_condenser") || filledCell(defaultOutput))) {
                String line = type.equals("canner") ? "plasma_containment" : "plasma_condensation";
                return group(recipe, feed, defaultOutput, line, signature(recipe), outputProfile(recipe));
            }
        }
        String tier = ASSEMBLY.contains(type) ? circuitTier(defaultOutput) : null;
        if (tier != null) return group(recipe, defaultOutput, defaultOutput, "electronic_assembly", signature(recipe), "tier:" + tier);
        if (type.equals("pcb_factory") && defaultOutput instanceof AEItemKey) {
            return group(recipe, defaultOutput, defaultOutput, "pcb_fabrication", signature(recipe), "form:pcb");
        }
        String line = MACHINE_LINES.get(type);
        if (line != null) {
            if (line.equals("casting")) {
                var casting = describeCasting(recipe, defaultOutput);
                if (casting != null) return casting;
            }
            AEKey anchor = type.equals("large_recycler") || type.equals("disassembly") || type.equals("packer") ?
                    recipe.inputSlots().stream().findFirst().map(MERecipePatternHelper.InputSlot::defaultKey).orElse(defaultOutput) : defaultOutput;
            String result = type.equals("greenhouse") && defaultOutput.isTagged(ItemTags.LOGS) ? "form:logs" : outputProfile(recipe);
            return group(recipe, anchor, defaultOutput, line, signature(recipe), result);
        }
        if (isPhase(defaultOutput, "plasma")) {
            return group(recipe, defaultOutput, defaultOutput, "plasma_production", signature(recipe), "fluid:plasma");
        }
        return null;
    }

    private static @Nullable Candidate describeCasting(RecipePattern recipe, AEKey output) {
        if (!(output instanceof AEItemKey) || recipe.inputSlots().size() != 1 || recipe.circuit() >= 0 ||
                recipe.pattern().getOutputs().length != 1 || recipe.nonConsumedInputs().size() != 1) return null;
        var molds = recipe.nonConsumedInputs().getFirst();
        if (molds.size() != 1 || !(molds.getFirst() instanceof AEItemKey mold) || mold.getReadOnlyStack().hasTag() ||
                !mold.getId().getNamespace().equals("gtceu") || !mold.getId().getPath().endsWith("_casting_mold")) return null;
        var material = UniversalPatternFamily.materialOf(output);
        String outputForm = UniversalPatternFamily.formOf(output);
        if (material == null || outputForm == null) return null;
        var feed = recipe.inputSlots().getFirst();
        if (feed.alternatives().isEmpty() || !(feed.defaultKey() instanceof AEFluidKey) ||
                feed.alternatives().stream().anyMatch(key ->
                        !(key instanceof AEFluidKey) || UniversalPatternFamily.materialOf(key) != material ||
                                !(isPhase(key, "liquid") || isPhase(key, "molten")))) return null;
        return group(recipe, output, output, "casting", CASTING_FEED + ",mold:" + mold.getId(), outputForm);
    }

    /** Resolves a casting card saved before the liquid was made variable. */
    public static @Nullable Candidate legacyCasting(@Nullable Candidate preferred, String family) {
        if (preferred == null || !family.contains(CASTING_STAGE + "fluidid:") ||
                !preferred.family().contains(CASTING_STAGE + CASTING_FEED + ",")) return null;
        var recipe = preferred.recipe();
        String exact = recipe.recipe().recipeType.registryName + CASTING_STAGE + signature(recipe) + "|" + outputProfile(recipe);
        return exact.equals(family) ? new Candidate(exact, preferred.material(), preferred.inputs(),
                preferred.output(), recipe, preferred.selector()) : null;
    }

    static @Nullable String powderBlendProfile(RecipePattern recipe, @Nullable AEKey output) {
        if(output==null || !isPowder(output) || recipe.inputSlots().size()<2)return null;
        var reagents=new TreeSet<String>();boolean powder=false;
        for(var slot:recipe.inputSlots()) {
            if(slot.alternatives().isEmpty())return null;
            if(slot.alternatives().stream().allMatch(RegisteredProcessFamilies::isPowder)) {powder=true;continue;}
            // Wet blending may vary amounts and repeated slots, but different fluid reagents
            // remain different processes. Never collapse petrochemical/metal fluid routes.
            if(slot.alternatives().stream().anyMatch(key->!(key instanceof AEFluidKey)))return null;
            reagents.add(slot.alternatives().stream().map(key->key.getId().toString()).distinct().sorted().reduce((a,b)->a+"/"+b).orElse(""));
        }
        return !powder?null:reagents.isEmpty()?"dry_powders":"wet_powders,"+String.join(",",reagents);
    }
    private static boolean isPowder(AEKey key) {
        String form=UniversalPatternFamily.formOf(key);
        return key instanceof AEItemKey && ("item:dust".equals(form)||"item:dustSmall".equals(form)||"item:dustTiny".equals(form));
    }

    private static @Nullable Candidate describeOreLine(RecipePattern recipe, AEKey output, String type) {
        if (type.equals("isa_mill") && output instanceof AEItemKey && output.isTagged(MILLED_ORES)) {
            for (var slot : recipe.inputSlots()) {
                if (!(slot.defaultKey() instanceof AEItemKey)) continue;
                String tag = declaredTag(recipe, slot);
                String feed = tag != null && tag.startsWith("forge:ores/") ? "form:ore" : form(slot.defaultKey());
                return group(recipe, output, output, "fine_grinding", signature(recipe, slot, feed, true) + ",circuit:" + recipe.circuit(),
                        "form:milled_ore");
            }
        }
        if (type.equals("flotating_beneficiation") && output instanceof AEFluidKey && output.isTagged(FLOTATION_FROTH)) {
            for (var slot : recipe.inputSlots()) {
                if (slot.defaultKey() instanceof AEItemKey && slot.defaultKey().isTagged(MILLED_ORES) &&
                        slot.alternatives().stream().allMatch(key -> key.isTagged(MILLED_ORES))) {
                    return group(recipe, slot.defaultKey(), output, "flotation", signature(recipe, slot, "form:milled_ore", true),
                            "form:flotation_froth");
                }
            }
        }
        if (type.equals("vacuum_drying")) {
            var feed = frothSlot(recipe);
            if (feed != null) return group(recipe, feed.defaultKey(), output, "concentrate_drying",
                    signature(recipe, feed, "form:flotation_froth", true), outputProfile(recipe));
        }
        return null;
    }

    private static @Nullable MERecipePatternHelper.InputSlot frothSlot(RecipePattern recipe) {
        for (var slot : recipe.inputSlots()) {
            if (slot.defaultKey() instanceof AEFluidKey && slot.defaultKey().isTagged(FLOTATION_FROTH) &&
                    slot.alternatives().stream().allMatch(key -> key.isTagged(FLOTATION_FROTH))) return slot;
        }
        return null;
    }

    private static Candidate group(RecipePattern recipe, AEKey anchor, AEKey output, String line, String inputForm, String outputForm) {
        List<AEKey> inputs = recipe.inputSlots().stream().flatMap(slot -> slot.alternatives().stream()).toList();
        String family = recipe.recipe().recipeType.registryName + "|stage:" + line + "," + inputForm + "|" + outputForm;
        // Scope each selector to the actual item/fluid variant, even where several share a GT material.
        return new Candidate(family, UniversalPatternFamily.materialOf(anchor), inputs, output, recipe, anchor);
    }

    private static @Nullable AEKey purifiedOutput(RecipePattern recipe) {
        int inputGrade = recipe.inputSlots().stream().flatMap(slot -> slot.alternatives().stream())
                .mapToInt(RegisteredProcessFamilies::waterGrade).max().orElse(-1);
        if (inputGrade < 0) return null;
        AEKey best = null;
        int grade = inputGrade;
        for (GenericStack result : recipe.pattern().getOutputs()) {
            int candidate = waterGrade(result.what());
            if (candidate > grade) {
                grade = candidate;
                best = result.what();
            }
        }
        return best;
    }

    private static int waterGrade(AEKey key) {
        if (!(key instanceof AEFluidKey)) return -1;
        ResourceLocation id = key.getId();
        if (id.toString().equals("minecraft:water")) return 0;
        if (!id.getNamespace().equals("gtceu")) return -1;
        if (id.getPath().equals("distilled_water")) return 1;
        var match = GRADED_WATER.matcher(id.getPath());
        // Graded purification steps follow distilled water in ascending order.
        return match.matches() ? 1 + Integer.parseInt(match.group(1)) : -1;
    }

    private static @Nullable AEKey plasmaFeed(RecipePattern recipe) {
        for (var slot : recipe.inputSlots()) {
            if (isPhase(slot.defaultKey(), "plasma") || filledCell(slot.defaultKey())) return slot.defaultKey();
        }
        return null;
    }

    private static boolean filledCell(AEKey key) {
        return key instanceof AEItemKey && key.isTagged(TagKey.create(Registries.ITEM, GTLCore.id("plasma_containers/filled")));
    }

    private static @Nullable AEKey fissionFeed(RecipePattern recipe, AEKey output) {
        if (!(output instanceof AEItemKey) || recipe.inputSlots().size() != 1) return null;
        var slot = recipe.inputSlots().getFirst();
        AEKey key = slot.defaultKey();
        ResourceLocation id = key.getId();
        if (!(key instanceof AEItemKey) || !id.getNamespace().equals("kubejs") || !FUEL_ROD.matcher(id.getPath()).matches() ||
                !output.getId().toString().equals(id.getNamespace() + ":depleted_" + id.getPath())) return null;
        return slot.alternatives().stream().allMatch(key::equals) ? key : null;
    }

    private static @Nullable String circuitTier(AEKey key) {
        if (!(key instanceof AEItemKey)) return null;
        for (String tier : CIRCUIT_TIERS) {
            if (key.isTagged(TagKey.create(Registries.ITEM, ResourceLocation.tryParse("gtceu:circuits/" + tier)))) return tier;
        }
        return null;
    }

    private static String signature(RecipePattern recipe) {
        return signature(recipe, null, "", false);
    }

    private static String signature(RecipePattern recipe, @Nullable MERecipePatternHelper.InputSlot variable,
                                    String substitution, boolean keepReagents) {
        var forms = new ArrayList<String>();
        for (var slot : recipe.inputSlots()) {
            if (slot == variable) {
                forms.add(substitution);
                continue;
            }
            String tag = declaredTag(recipe, slot);
            if (tag != null) {
                forms.add("tag:" + tag);
                continue;
            }
            var alternatives = slot.alternatives().stream()
                    .map(key -> keepReagents ? "id:" + key.getId() : inputForm(key)).distinct().sorted().toList();
            if (alternatives.size() == 1) forms.add(alternatives.getFirst());
            else if (!alternatives.isEmpty()) forms.add(String.join("/", alternatives));
        }
        forms.sort(String::compareTo);
        return forms.isEmpty() ? "form:empty" : String.join(",", forms);
    }

    private static @Nullable String declaredTag(RecipePattern recipe, MERecipePatternHelper.InputSlot slot) {
        if (slot.alternatives().size() <= 1) return null;
        int separator = slot.id().lastIndexOf(':');
        if (separator < 0) return null;
        String kind = slot.id().substring(0, separator);
        var capability = ItemRecipeCapability.CAP;
        var contents = switch (kind) {
            case "item" -> recipe.recipe().getInputContents(capability);
            case "tick_item" -> recipe.recipe().getTickInputContents(capability);
            default -> null;
        };
        if (contents == null) return null;
        int index = Integer.parseInt(slot.id().substring(separator + 1));
        if (index < 0 || index >= contents.size()) return null;
        var ingredient = capability.of(contents.get(index).content);
        while (true) {
            if (ingredient instanceof SizedIngredient sized) ingredient = sized.getInner();
            else if (ingredient instanceof IntProviderIngredient provider) ingredient = provider.getInner();
            else break;
        }
        if (!ingredient.isVanilla()) return null;
        var json = ingredient.toJson();
        if (!json.isJsonObject()) return null;
        var tag = json.getAsJsonObject().get("tag");
        return tag != null && tag.isJsonPrimitive() ? tag.getAsString() : null;
    }

    private static String inputForm(AEKey key) {
        if (key instanceof AEFluidKey && !isPhase(key, "plasma")) return "fluidid:" + key.getId();
        return form(key);
    }

    private static String outputProfile(RecipePattern recipe) {
        return String.join(",", Arrays.stream(recipe.pattern().getOutputs()).map(stack -> form(stack.what())).sorted().toList());
    }

    private static String form(AEKey key) {
        if (filledCell(key)) return "form:plasma_cell";
        if (key instanceof AEItemKey && key.isTagged(ItemTags.LOGS)) return "form:logs";
        String unit = UniversalPatternFamily.formOf(key);
        return unit == null ? "id:" + key.getId() : unit;
    }

    private static String type(RecipePattern recipe) {
        ResourceLocation id = recipe.recipe().recipeType.registryName;
        return switch (id.getNamespace()) {
            case "gtceu", "gtlcore" -> id.getPath();
            default -> "";
        };
    }
}
