package org.gtlcore.universalpatterns.client.gui.widget;

import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternFilter;
import java.util.*;

/** The client translates distinct labels once per family. The server keeps recipe membership and paging. */
final class FamilyRecipeSearch {
    private record Row(List<String> ids, int[] labels) {}
    private final Map<Component,Integer> vocabulary = new LinkedHashMap<>();
    private final Map<String,Row> rows = new HashMap<>();

    void add(String recipeId, List<Component> labels, List<String> ids) {
        int[] indices = labels.stream().mapToInt(label -> vocabulary.computeIfAbsent(label, key -> vocabulary.size())).distinct().toArray();
        rows.put(recipeId, new Row(List.copyOf(ids),indices));
    }

    List<Component> labels() {
        return List.copyOf(vocabulary.keySet());
    }

    boolean matches(String recipeId, String query, BitSet localizedMatches) {
        var row = rows.get(recipeId);
        if (row == null) return false;
        if (MERecipePatternFilter.matchesText(recipeId,query)) return true;
        for (int index : row.labels()) if (localizedMatches.get(index)) return true;
        return row.ids().stream().anyMatch(id -> MERecipePatternFilter.matchesText(id,query));
    }
}
