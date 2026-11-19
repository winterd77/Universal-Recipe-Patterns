package org.gtlcore.universalpatterns.integration.ae2.handler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.InputSlot;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem.Selection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

/** A preference belongs to the set of registered item alternatives, independent of recipe, slot order or count. */
public final class ItemSubstitutions {
    private ItemSubstitutions() {}

    private record Group(String id, Map<String,AEKey> items) {}
    private static final Map<List<AEKey>,Group> groups = new WeakHashMap<>();

    private static synchronized Group group(InputSlot slot) {
        return groups.computeIfAbsent(slot.alternatives(), alternatives -> {
            var items = new TreeMap<String,AEKey>();
            for (var key : alternatives) items.put(CatalystSupply.identity(key), key);
            try {
                var digest = MessageDigest.getInstance("SHA-256");
                for (String item : items.keySet()) digest.update(item.getBytes(StandardCharsets.US_ASCII));
                return new Group(HexFormat.of().formatHex(digest.digest()), Map.copyOf(items));
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        });
    }

    public static boolean selectable(InputSlot slot) {
        return slot.defaultKey() instanceof AEItemKey && slot.alternatives().size() > 1;
    }

    public static Map<String,String> change(Map<String,String> previous, InputSlot slot, AEKey choice) {
        if (!selectable(slot) || choice != null && (!(choice instanceof AEItemKey) || !slot.alternatives().contains(choice)))
            throw new IllegalArgumentException("Item is not a registered alternative");
        var values = new HashMap<>(previous);
        var id = group(slot).id();
        if (choice == null) values.remove(id);
        else values.put(id, CatalystSupply.identity(choice));
        if (values.size() > org.gtlcore.universalpatterns.common.item.UniversalPatternItem.MAX_MATERIALS)
            throw new IllegalArgumentException("Too many item preferences");
        return Map.copyOf(values);
    }

    public static AEKey resolve(Selection policy, InputSlot slot, AEKey fallback) {
        if (policy.itemChoices().isEmpty() || !selectable(slot)) return fallback;
        var group = group(slot);
        var choice = policy.itemChoices().get(group.id());
        return choice == null ? fallback : group.items().getOrDefault(choice, fallback);
    }

    public static List<GenericStack> inputs(Selection policy, RecipePattern recipe, Function<InputSlot,AEKey> fallback) {
        var consumed = new ArrayList<GenericStack>();
        for (var slot : recipe.inputSlots()) {
            var base = fallback.apply(slot);
            if (!policy.ignoredInputs().isEmpty() && policy.ignoredInputs().contains(CatalystSupply.identity(base))) continue;
            consumed.add(new GenericStack(resolve(policy, slot, base), slot.amount()));
        }
        // Ignore edits follow the original ingredient. Explicit extra stacks keep their exact item.
        var extras = DeliveryPolicy.extras(policy);
        if (!policy.ignoredInputs().isEmpty())
            extras = extras.stream().filter(stack -> !policy.ignoredInputs().contains(CatalystSupply.identity(stack.what()))).toList();
        return DeliveryPolicy.batchAmounts(consumed, extras, Set.of(), policy.batchMultiplier());
    }
}
