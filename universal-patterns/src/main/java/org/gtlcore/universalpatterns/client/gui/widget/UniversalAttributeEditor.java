package org.gtlcore.universalpatterns.client.gui.widget;

import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.api.gui.RecipePatternLabelWidget;
import org.gtlcore.universalpatterns.integration.ae2.handler.MaterialExpression;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalRuleList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class UniversalAttributeEditor extends WidgetGroup {
    static final int MAX_ATTRIBUTES = 512;
    private static final int PAGE_SIZE = 5;
    private static final int START_Y = 98;
    private static final int ROW_HEIGHT = 21;
    private static final int TEXT = 0xFF333333;
    private static final String PREFIX = "gtceu.machine.me_recipe_pattern_buffer.";

    private final RecipePatternSampleWidget sample;
    private final WidgetGroup options = new WidgetGroup(0, 0, 332, 268).setClientSideWidget();
    private final WidgetGroup conditions = new WidgetGroup(0, 0, 332, 268).setClientSideWidget();
    private final WidgetGroup editing = new WidgetGroup(0, 0, 332, 268).setClientSideWidget();
    private final WidgetGroup manualEditor = new WidgetGroup(0, 0, 332, 268).setClientSideWidget();
    private final List<Runnable> pagerVisibility = new ArrayList<>();
    private final List<String> allow = new ArrayList<>(), deny = new ArrayList<>();
    private final Consumer<String> search;
    private final BiConsumer<String, String> save;
    private final TextFieldWidget manualField;
    private List<String> attributes = List.of();
    private Component feedback = Component.empty();
    private boolean recipes, blacklist, allowAll, denyAll, waiting;
    private int attributePage, conditionPage;
    private String manual = "";
    private @Nullable String editingClause;
    private String sourceAllow = "", sourceDeny = "";
    private boolean hasDraft;
    private String attributeQuery = "";
    private int category;
    private String draftContext = "";

    void setDraftContext(String context) {
        if (!draftContext.equals(context)) {
            hasDraft = false;
            draftContext = context;
        }
    }

    UniversalAttributeEditor(Consumer<AEKey> inspect, Consumer<String> search, BiConsumer<String, String> save) {
        super(0, 0, 332, 268);
        this.search = search;
        this.save = save;
        setClientSideWidget();
        addWidget(new ButtonWidget(0, 0, 332, 268, GuiTextures.BACKGROUND, click -> {}));
        addWidget(line(14, 10, 270, () -> text(recipes ? "sample_find_title" : "pattern_rules")));
        addWidget(button(300, 8, 18, 18, () -> "×", this::close));
        sample = new RecipePatternSampleWidget(14, 32, value -> setSample(value, inspect), tooltip("sample_rule_hint"));
        addWidget(sample);
        addWidget(line(40, 34, 276, () -> sample.sample() == null ? text("sample_drop") : sample.sample().getDisplayName().getString()));
        addWidget(button(14, 74, 48, 18, () -> text("attribute_category." + category), () -> {
            category = (category + 1) % 6;
            attributePage = 0;
            refreshRows();
        }));
        addWidget(new TextFieldWidget(66, 74, 90, 18, () -> attributeQuery, value -> {
            attributeQuery = value;
            attributePage = 0;
            refreshRows();
        }).setMaxStringLength(128).setBordered(false).setTextColor(0xFFFFFFFF).setBackground(GuiTextures.SLOT_DARK)
                .setHoverTooltips(tooltip("attribute_search")));
        addWidget(panelLine(14, START_Y, 142, () -> waiting ? text("sample_loading") :
                attributes.isEmpty() ? text("sample_no_attributes") : ""));
        addWidget(line(14, 54, 142, () -> text("available_attributes")));
        addWidget(options);
        addPager(this, 14, 204, 142, () -> attributePage, () -> filteredAttributes().size(), delta -> {
            attributePage = page(attributePage + delta, filteredAttributes().size());
            refreshRows();
        });

        editing.addWidget(button(176, 74, 68, 18, () -> text(blacklist ? "pattern_to_deny_short" : "pattern_to_allow_short"), () -> {
            blacklist = !blacklist;
            conditionPage = 0;
            resetManual();
            refreshRows();
        }).setHoverTooltips(tooltip("sample_list_hint")));
        editing.addWidget(button(248, 74, 70, 18, () -> text(requireAll() ? "sample_match_all" : "sample_match_any"), () -> {
            if (blacklist) denyAll = !denyAll;
            else allowAll = !allowAll;
            persistRules();
        }).setHoverTooltips(tooltip("sample_join_hint")));
        editing.addWidget(panelLine(176, START_Y, 142, () -> active().isEmpty() ? text("sample_no_rules") : ""));
        editing.addWidget(line(176, 54, 142, () -> text("selected_rules")));
        editing.addWidget(conditions);
        addPager(editing, 176, 204, 142, () -> conditionPage, () -> active().size(), delta -> {
            conditionPage = page(conditionPage + delta, active().size());
            refreshRows();
        });
        editing.addWidget(button(14, 246, 104, 16, () -> text("advanced_expression"), this::openManual));
        editing.addWidget(button(214, 246, 104, 16, () -> text("pattern_rule_save"), () -> {
            save.accept(expression(allow, allowAll), expression(deny, denyAll));
            close();
        }));
        addWidget(editing);
        addWidget(line(14, 228, 304, () -> feedback.getString().isEmpty() ? text(category==3?"source_short_hint":"rule_click_hint") : feedback.getString()));
        manualEditor.addWidget(new ButtonWidget(0, 0, 332, 268, GuiTextures.BACKGROUND, click -> {}));
        manualEditor.addWidget(line(14, 10, 270, () -> text("advanced_expression")));
        manualEditor.addWidget(button(300, 8, 18, 18, () -> "×", this::closeManual));
        manualEditor.addWidget(line(14, 54, 304, () -> text(blacklist ? "advanced_target_deny" : "advanced_target_allow")));
        manualField = new TextFieldWidget(14, 82, 304, 20, () -> manual, value -> manual = value)
                .setMaxStringLength(MaterialExpression.MAX_LENGTH).setBordered(false).setTextColor(0xFFFFFFFF)
                .setBackground(GuiTextures.SLOT_DARK);
        manualField.setHoverTooltips(tooltip("sample_manual_hint"));
        manualEditor.addWidget(manualField);
        manualEditor.addWidget(line(14, 138, 304, () -> text("more_options_hint")));
        manualEditor.addWidget(line(14, 114, 304, () -> feedback.getString().isEmpty() ? text("advanced_expression_hint") : feedback.getString()));
        manualEditor.addWidget(button(14, 240, 100, 18, () -> text("advanced_back"), this::closeManual));
        manualEditor.addWidget(button(218, 240, 100, 18, () -> text(editingClause == null ? "sample_add" : "sample_update"), () -> {
            if (put(manualField.getCurrentString().strip(), editingClause)) {
                resetManual();
                closeManual();
            }
        }).setHoverTooltips(tooltip("sample_manual_hint")));
        addWidget(manualEditor);
        closeManual();
        close();
    }

    void open(boolean recipes, @Nullable AEKey reference, List<String> discovered, String savedAllow, String savedDeny) {
        closeManual();
        this.recipes = recipes;
        blacklist = false;
        if (recipes || !hasDraft || !sourceAllow.equals(savedAllow) || !sourceDeny.equals(savedDeny)) {
            allowAll = read(savedAllow, allow);
            denyAll = !savedDeny.isBlank() && read(savedDeny, deny);
            if (savedDeny.isBlank()) deny.clear();
            sourceAllow = savedAllow;
            sourceDeny = savedDeny;
        }
        hasDraft = !recipes;
        sample.display(reference);
        waiting = false;
        attributes = discovered;
        attributePage = 0;
        conditionPage = 0;
        feedback = Component.empty();
        resetManual();
        editing.setVisible(!recipes).setActive(!recipes);
        setVisible(true).setActive(true);
        refreshRows();
    }

    void close() {
        closeManual();
        setVisible(false).setActive(false);
    }

    private void openManual() {
        manualEditor.setVisible(true).setActive(true);
    }

    private void closeManual() {
        manualEditor.setVisible(false).setActive(false);
    }

    void closeTopLayer() {
        if (manualEditor.isVisible()) closeManual();
        else close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (manualEditor.isVisible()) return manualEditor.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (manualEditor.isVisible()) return manualEditor.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    boolean isRecipePicker() {
        return recipes;
    }

    void showAttributes(@Nullable AEKey reference, List<String> discovered) {
        if (!isVisible() || !java.util.Objects.equals(reference, sample.sample())) return;
        waiting = false;
        attributes = discovered;
        attributePage = 0;
        refreshRows();
    }

    void showFeedback(Component message) {
        feedback = message;
    }

    private void setSample(@Nullable AEKey value, Consumer<AEKey> inspect) {
        attributes = List.of();
        attributePage = 0;
        waiting = value != null;
        refreshRows();
        inspect.accept(value);
    }

    private boolean requireAll() {
        return blacklist ? denyAll : allowAll;
    }

    private List<String> active() {
        return blacklist ? deny : allow;
    }

    private boolean put(String clause, @Nullable String replaced) {
        if (clause.isEmpty()) return false;
        if (clause.equals(replaced)) return true;
        var terms = active();
        if (terms.contains(clause)) return false;
        int index = replaced == null ? -1 : terms.indexOf(replaced);
        if (index < 0) terms.add(clause);
        else terms.set(index, clause);
        if (expression(active(), requireAll()).length() > MaterialExpression.MAX_LENGTH) {
            if (index < 0) terms.removeLast();
            else terms.set(index, replaced);
            feedback = tooltip("sample_rule_limit");
            return false;
        }
        if (index < 0) conditionPage = page(Integer.MAX_VALUE, terms.size());
        feedback = Component.empty();
        refreshRows();
        persistRules();
        return true;
    }

    private void refreshRows() {
        options.clearAllWidgets();
        conditions.clearAllWidgets();
        var visibleAttributes = filteredAttributes();
        for (int row = 0; row < PAGE_SIZE; row++) {
            int y = START_Y + row * ROW_HEIGHT;
            int attribute = attributePage * PAGE_SIZE + row;
            if (attribute < visibleAttributes.size()) {
                String term = visibleAttributes.get(attribute);
                options.addWidget(button(14, y, 142, 18, () -> label(term), () -> {
                    if (recipes) {
                        search.accept(term);
                        close();
                    } else put(term, null);
                }).setHoverTooltips(Component.literal(fullLabel(term)), Component.literal(term), tooltip(term.startsWith("source:direct:") ? "source_direct_hint" : term.startsWith("source:other:") ? "source_other_hint" : term.startsWith("process:") ? "source_explanation" : term.startsWith("prop:") ? "property_explanation" : recipes ? "sample_search_attribute" : "sample_choose_attribute")));
            }
            int condition = conditionPage * PAGE_SIZE + row;
            if (recipes || condition >= active().size()) continue;
            String term = active().get(condition);
            conditions.addWidget(button(176, y, 120, 18, () -> label(term), () -> {
                editingClause = term;
                setManual(term);
                openManual();
            })
                    .setHoverTooltips(Component.literal(fullLabel(term)), Component.literal(term), tooltip("sample_copy_rule")));
            conditions.addWidget(button(300, y, 18, 18, () -> "×", () -> {
                active().remove(condition);
                persistRules();
                if (term.equals(editingClause)) resetManual();
                conditionPage = page(conditionPage, active().size());
                refreshRows();
            }).setHoverTooltips(tooltip("sample_remove_rule")));
        }
        pagerVisibility.forEach(Runnable::run);
    }

    static String processLabel(String name) {
        String path=name.contains(":")?name.substring(name.indexOf(':')+1):name;
        // GT recipe types use this translation key in JEI, including addon-registered processes.
        String fallback=Component.translatableWithFallback("gtceu."+path,name).getString();
        return Component.translatableWithFallback(PREFIX+"sample_process."+name,
                Component.translatableWithFallback(PREFIX+"sample_process."+path,fallback).getString()).getString();
    }

    private List<String> filteredAttributes() {
        String query = attributeQuery.toLowerCase(java.util.Locale.ROOT);
        return attributes.stream().filter(term -> switch (category) {
            case 1 -> term.contains("#");
            case 2 -> term.startsWith("prop:") || term.startsWith("flag:");
            case 3 -> term.startsWith("source:") || term.startsWith("process:");
            case 4 -> term.startsWith("use:");
            case 5 -> term.startsWith("line:") || term.startsWith("recipe:") || term.startsWith("product:") || term.startsWith("input:");
            default -> true;
        }).filter(term -> term.contains(query) || label(term).toLowerCase(java.util.Locale.ROOT).contains(query)).toList();
    }

    private void setManual(String value) {
        manual = value;
        manualField.setCurrentString(value);
    }

    private void persistRules() {
        if (!recipes) save.accept(expression(allow, allowAll), expression(deny, denyAll));
    }

    private void resetManual() {
        editingClause = null;
        setManual("");
    }

    private static boolean read(String saved, List<String> target) {
        var stored = UniversalRuleList.read(saved);
        target.clear();
        target.addAll(stored.clauses());
        return stored.requireAll();
    }

    private static String expression(List<String> rules, boolean all) {
        return new UniversalRuleList(all, rules).expression();
    }

    private void addPager(WidgetGroup group, int x, int y, int width, Supplier<Integer> current,
                          Supplier<Integer> count, Consumer<Integer> change) {
        WidgetGroup pager = new WidgetGroup(x, y, width, 13).setClientSideWidget();
        pager.addWidget(button(0, 0, 16, 13, () -> "<", () -> change.accept(-1)));
        pager.addWidget(panelLine(23, 0, width - 46, () -> text("page", current.get() + 1, page(Integer.MAX_VALUE, count.get()) + 1)));
        pager.addWidget(button(width - 16, 0, 16, 13, () -> ">", () -> change.accept(1)));
        Runnable visibility = () -> pager.setVisible(count.get() > PAGE_SIZE).setActive(count.get() > PAGE_SIZE);
        pagerVisibility.add(visibility);
        visibility.run();
        group.addWidget(pager);
    }

    private static int page(int requested, int count) {
        return Math.clamp(requested, 0, Math.max(0, count - 1) / PAGE_SIZE);
    }

    private static Widget button(int x, int y, int width, int height, Supplier<String> label, Runnable action) {
        return RecipePatternBrowserWidget.labeledButton(x, y, width, height, label, action,
                ResourceBorderTexture.BUTTON_COMMON, ResourceBorderTexture.BUTTON_COMMON);
    }

    private static ImageWidget line(int x, int y, int width, Supplier<String> label) {
        return new RecipePatternLabelWidget(x, y, width, 13, label, TEXT, false);
    }

    private static ImageWidget panelLine(int x, int y, int width, Supplier<String> label) {
        return new RecipePatternLabelWidget(x, y, width, 13, label, 0xFF30373B, false);
    }

    private static String fullLabel(String term) {
        if(!term.startsWith("source:other:"))return label(term);
        String[] parts=term.split(":",5);
        if(parts.length!=5)return label(term);
        String form=parts[2].endsWith("key")?text("source_exact_form"):
                Component.translatableWithFallback("gtlcore.universal_pattern.form."+parts[3],parts[3]).getString();
        return label(term)+" · "+text("source_reference",form);
    }

    private static String label(String term) {
        if (term.startsWith("sample:") || term.startsWith("recipe_io:")) {
            String value = term.substring(term.indexOf(':') + 1);
            if (value.startsWith("#") || value.contains("*") || value.contains("?")) return value;
            return text("sample_exact", value);
        }
        int delimiter = term.indexOf(':');
        if (delimiter <= 0) return term;
        String kind = term.substring(0, delimiter);
        String name = term.substring(delimiter + 1);
        return switch (kind) {
            case "source" -> {
                String[] parts=term.split(":",5);
                if(parts.length!=5)yield term;
                String form=parts[2].endsWith("key")?text("source_exact_form"):
                        Component.translatableWithFallback("gtlcore.universal_pattern.form."+parts[3],parts[3]).getString();
                yield text(parts[1].equals("direct")?"source_direct":"source_other",processLabel(parts[4]),form);
            }
            case "prop" -> Component.translatableWithFallback(PREFIX + "preset_" + name, term).getString();
            case "line" -> Component.translatableWithFallback("gtlcore.universal_pattern.stage." + name, term).getString();
            case "recipe" -> text("sample_recipe_type", Component.translatableWithFallback(name.replace(":", ".recipe."), name).getString());
            case "process", "use" -> text(kind.equals("process") ? "sample_produced_by" : "sample_used_by",
                    processLabel(name));
            default -> term;
        };
    }

    private static Component tooltip(String key) {
        return Component.translatable(PREFIX + key);
    }

    private static String text(String key, Object... args) {
        return Component.translatable(PREFIX + key, args).getString();
    }
}
