package org.gtlcore.universalpatterns.integration.ae2.handler;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternFamily.Candidate;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Fallback for actual registered GT recipes, including recipe types supplied by addons. */
public final class RegisteredRecipeFamily {
    private RegisteredRecipeFamily() {}

    // GT freezes its material registry before any world can open a pattern editor.
    private static final class Names {
        static final Set<String> MATERIALS = GTCEuAPI.materialManager.getRegisteredMaterials().stream()
                .map(material -> material.getResourceLocation().getPath()).collect(Collectors.toUnmodifiableSet());
        static final Set<String> TIERS = Arrays.stream(GTValues.VN).map(tier -> tier.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        static final Set<String> PART_NAMES = partNames();

        private static Set<String> partNames() {
            var modifiers = new HashMap<String, Set<String>>();
            for (var id : BuiltInRegistries.ITEM.keySet()) {
                String path = id.getPath();
                String form = variant(path, TIERS, MATERIALS);
                if (form == null) continue;
                String noun = form.substring(form.indexOf(':') + 1);
                if (noun.contains("_")) modifiers.computeIfAbsent(noun, ignored -> new HashSet<>())
                        .add(path.substring(0, path.length() - noun.length() - 1));
            }
            return modifiers.entrySet().stream().filter(entry -> entry.getValue().size() > 1)
                    .map(entry -> entry.getKey()).collect(Collectors.toUnmodifiableSet());
        }
    }

    static @Nullable String variant(String path, Set<String> tiers, Set<String> materials) {
        // Longest registered prefix first, e.g. molybdenum_disilicide_coil_block.
        for (int split = path.lastIndexOf('_'); split > 0; split = path.lastIndexOf('_', split - 1)) {
            String prefix = path.substring(0, split);
            if (tiers.contains(prefix)) return "tiered:" + path.substring(split + 1);
            if (materials.contains(prefix)) return "material_part:" + path.substring(split + 1);
        }
        return null;
    }

    public static @Nullable Candidate describe(RecipePattern pattern, @Nullable AEKey output, Level level) {
        var raw = pattern.recipe();
        if (output == null || pattern.chancedInput() || level.getRecipeManager().byKey(raw.id).orElse(null) != raw) return null;
        var inputs = pattern.inputSlots().stream().flatMap(slot -> slot.alternatives().stream()).distinct().toList();
        if (inputs.isEmpty()) return null;
        String form = UniversalPatternFamily.formOf(output);
        String route = "components";
        if (form == null && output instanceof AEItemKey item && !item.getReadOnlyStack().hasTag()) {
            String path = output.getId().getPath();
            form = variant(path, Names.TIERS, Names.MATERIALS);
            for (int split = path.indexOf('_'); form == null && split > 0; split = path.indexOf('_', split + 1)) {
                String suffix = path.substring(split + 1);
                if (Names.PART_NAMES.contains(suffix)) form = "material_part:" + suffix;
            }
        }
        if (form == null) {
            // Keep unrecognised products usable without guessing which other products are equivalent.
            form = (output instanceof AEFluidKey ? "fluidid:" : "itemid:") + output.getId();
        } else if (!form.startsWith("tiered:") && !form.startsWith("material_part:")) {
            var roles = new TreeSet<String>();
            for (var input : inputs) {
                String role = UniversalPatternFamily.formOf(input);
                roles.add(role != null ? role : input instanceof AEItemKey ? "item" : "fluid");
            }
            route = String.join(",", roles);
        }
        String family = raw.recipeType.registryName + "|registered:" + route + "|" + form;
        return new Candidate(family, UniversalPatternFamily.materialOf(output), inputs, output, pattern, output);
    }
}
