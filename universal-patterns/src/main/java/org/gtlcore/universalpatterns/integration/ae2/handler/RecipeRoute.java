package org.gtlcore.universalpatterns.integration.ae2.handler;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import com.google.common.hash.Hashing;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternFamily.Candidate;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps interchangeable material feeds together while distinguishing the registered processing requirements. */
public final class RecipeRoute {
    private RecipeRoute() {}

    public record Families(@Nullable Candidate preferred, @Nullable Candidate legacy) {}

    public static Families describe(RecipePattern recipe, @Nullable AEKey output, boolean preferOutput, Level level) {
        var legacy = UniversalPatternFamily.describe(recipe,output);
        if (legacy == null) legacy = UniversalPatternFamily.describePetrochemical(recipe,output,level);
        var preferred = RegisteredProcessFamilies.describe(recipe,output,preferOutput);
        if (preferred == null || preferred.family().length() > 512) preferred = legacy;
        if (preferred == null) preferred = RegisteredRecipeFamily.describe(recipe,output,level);
        return new Families(qualify(preferred),legacy);
    }

    public static @Nullable Candidate resolve(@Nullable Candidate routed, @Nullable Candidate legacy, String family) {
        var match = match(routed,family);
        if (match != null) return match;
        var casting = RegisteredProcessFamilies.legacyCasting(routed,family);
        if (casting != null) return casting;
        return legacy != null && legacy.family().equals(family) ? legacy : null;
    }

    public static @Nullable Candidate qualify(@Nullable Candidate base) {
        if (base == null) return null;
        var recipe = base.recipe();
        var slots = new ArrayList<String>();
        boolean transformsMaterial = base.material() != null && UniversalPatternFamily.materialOf(base.output()) == base.material() &&
                recipe.inputSlots().stream().anyMatch(input -> input.alternatives().stream()
                        .allMatch(key -> UniversalPatternFamily.materialOf(key) == base.material()));
        for (var slot : recipe.inputSlots()) {
            boolean fluid = slot.defaultKey() instanceof AEFluidKey;
            if (!fluid && !transformsMaterial) continue;
            String kind = slot.id().startsWith("tick_") ? "tick:" : "in:";
            slots.add(kind + alternatives(base, slot.alternatives(), true));
        }
        for (var keys : recipe.nonConsumedInputs()) slots.add("reusable" + alternatives(base, keys, false));
        Collections.sort(slots);
        String suffix = "|r:" + recipe.circuit() + (slots.isEmpty() ? "" : ":" + digest(slots.toString()));
        String family = base.family() + suffix;
        if (family.length() > 512) family = recipe.recipe().recipeType.registryName + "|route|exact:" + digest(base.family()) + suffix;
        return new Candidate(family, base.material(), base.inputs(), base.output(), recipe, base.selector(), base.family());
    }

    private static List<String> alternatives(Candidate base, List<AEKey> keys, boolean variableFeed) {
        return keys.stream().map(key -> {
            if (variableFeed && base.material() != null && UniversalPatternFamily.materialOf(key) == base.material()
                    && key.equals(key.dropSecondary())) {
                String form = UniversalPatternFamily.formOf(key);
                if (form != null) return "feed:" + form;
            }
            return key.getType().getId() + "/" + key.getId() + (key.equals(key.dropSecondary()) ? "" : "#" + CatalystSupply.identity(key));
        }).distinct().sorted().toList();
    }

    private static String digest(String value) {
        return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString();
    }

    public static @Nullable Candidate match(@Nullable Candidate routed, String family) {
        if (routed == null) return null;
        if (routed.family().equals(family)) return routed;
        if (routed.baseFamily().equals(family)) return new Candidate(family, routed.material(), routed.inputs(),
                routed.output(), routed.recipe(), routed.selector());
        return null;
    }
}
