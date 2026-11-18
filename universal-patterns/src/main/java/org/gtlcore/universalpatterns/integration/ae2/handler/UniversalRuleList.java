package org.gtlcore.universalpatterns.integration.ae2.handler;

import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper;
import java.util.ArrayList;
import java.util.List;

/** Presents saved rules as clauses while keeping existing mixed expressions intact. */
public record UniversalRuleList(boolean requireAll, List<String> clauses) {

    public UniversalRuleList {
        clauses = List.copyOf(clauses);
    }

    public static UniversalRuleList read(String expression) {
        if (expression.isBlank()) return new UniversalRuleList(true, List.of());
        var clauses = new ArrayList<String>();
        char join = 0;
        int depth = 0, start = 0;
        for (int index = 0; index < expression.length(); index++) {
            char character = expression.charAt(index);
            if (character == '(') depth++;
            else if (character == ')') depth--;
            else if (depth == 0 && (character == '&' || character == '|')) {
                if (join != 0 && join != character) return new UniversalRuleList(true, List.of(expression));
                join = character;
                clauses.add(expression.substring(start, index).strip());
                if (index + 1 < expression.length() && expression.charAt(index + 1) == join) index++;
                start = index + 1;
            }
        }
        clauses.add(expression.substring(start).strip());
        return new UniversalRuleList(join != '|', clauses);
    }

    public String expression() {
        if (clauses.size() == 1) return clauses.getFirst();
        return String.join(requireAll ? " & " : " | ", clauses.stream().map(UniversalRuleList::group).toList());
    }

    private static String group(String term) {
        if (term.indexOf('&') < 0 && term.indexOf('|') < 0) return term;
        int depth = 0;
        for (int index = 0; index < term.length(); index++) {
            char character = term.charAt(index);
            if (character == '(') depth++;
            else if (character == ')') depth--;
            else if (depth == 0 && (character == '&' || character == '|')) return "(" + term + ")";
        }
        return term;
    }
}
