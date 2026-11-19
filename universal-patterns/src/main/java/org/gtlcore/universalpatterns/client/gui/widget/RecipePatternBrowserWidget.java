package org.gtlcore.universalpatterns.client.gui.widget;

import org.gtlcore.gtlcore.api.gui.RecipePatternLabelWidget;
import org.gtlcore.gtlcore.api.gui.RecipePatternUiTextures;
import org.gtlcore.gtlcore.client.ae2.wireless.UniversalSearch;
import org.gtlcore.universalpatterns.integration.ae2.handler.PatternEditorHost;
import org.gtlcore.universalpatterns.integration.ae2.handler.RegisteredProcessFamilies;
import org.gtlcore.universalpatterns.integration.ae2.handler.RecipeYield;
import org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryPolicy;
import org.gtlcore.universalpatterns.integration.ae2.handler.ItemSubstitutions;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternFilter;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternFilter.Rule;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternFilter.Target;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.InputSlot;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternFamily;
import org.gtlcore.universalpatterns.integration.ae2.handler.MaterialExpression;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem.Selection;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalMaterialRules;
import net.minecraft.resources.ResourceLocation;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import com.google.common.primitives.Ints;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Only paged search results and the selected pattern are sent to the client, never the entire recipe map. */
public class RecipePatternBrowserWidget extends WidgetGroup {

    private static final String PREFIX = "gtceu.machine.me_recipe_pattern_buffer.";
    private int resultPageSize() { return compact ? 3 : 5; }
    private static final int RECIPE_LIST_PAGE_SIZE = 4;
    private static final int RULE_PAGE_SIZE = 4;
    private static final int DIFFERENT_FAMILY_REASON = 3;
    private int stackPageSize() { return page == Page.UNIVERSAL ? 3 : 9; }
    private static final int TEXT_COLOR = RecipePatternUiTextures.TEXT_COLOR;
    private static final int MUTED_COLOR = RecipePatternUiTextures.MUTED_COLOR;
    private static final int NATIVE_PANEL_TEXT = 0xFF30373B;
    private static final int PANEL_COLOR = RecipePatternUiTextures.PANEL_COLOR;
    private static final int PANEL_BORDER = 0xFF7E7E7E;
    private static final int MAX_LOCALIZED_MATERIALS = 8192;
    private static final int SNAPSHOT = 10;
    private static final int SEARCH = 20;
    private static final int SEARCH_PAGE = 21;
    private static final int SELECT = 22;
    private static final int BLACKLIST = 23;
    private static final int PRIMARY = 24;
    private static final int ADD_RULE = 25;
    private static final int REMOVE_RULE = 26;
    private static final int RECIPE_LIST_PAGE = 27;
    private static final int RULE_PAGE = 28;
    private static final int WHITELIST = 29;
    private static final int FILTER_MODE = 30;
    private static final int SEARCH_VISIBILITY = 31;
    private static final int INPUT_OPTIONS = 32;
    private static final int INPUT_CHOICE = 33;
    private static final int INPUT_OPTIONS_PAGE = 34;
    private static final int CLOSE_INPUT_OPTIONS = 35;
    private static final int EDIT_UNIVERSAL = 36;
    private static final int UNIVERSAL_SLOT = 37;
    private static final int UNIVERSAL_RULES = 38;
    private static final int RECIPE_SAMPLE = 39;
    private static final int INSPECT_SAMPLE = 40;
    private static final int RECIPE_ATTRIBUTE = 41;
    private static final int SAMPLE_ATTRIBUTES = 11;

    private enum PatternEdit { ADD, REMOVE, BIND, ADD_RESULTS, REMOVE_RESULTS, FOLLOW, CHOOSE_RECIPE, ALL_RECIPES, APPLY, REVERT, EXTERNAL_CATALYSTS, UNBIND, EXPAND_CASTING }

    private static final int INPUT_OPTION_PAGE_SIZE = 18;

    public enum Page {
        OVERVIEW,
        SEARCH,
        FILTERS,
        UNIVERSAL
    }

    private enum FilterMode {
        BLACKLIST,
        WHITELIST
    }

    private enum SearchVisibility {

        ALL,
        PUBLISHED,
        BLOCKED,
        ON_FAMILY,
        ON_PATTERN;

        private boolean matches(int status) {
            return switch (this) {
                case ALL -> true;
                case PUBLISHED -> status == 0;
                case BLOCKED -> status != 0 && status != 4 && status != 7;
                case ON_FAMILY, ON_PATTERN -> true;
            };
        }
    }

    private record InputView(String id, GenericStack stack, int alternatives, boolean customised, AEKey deliveryKey) {}

    private final PatternEditorHost machine;
    private final Page page;
    private final boolean compact;
    private boolean browsingFamily;
    private static final int FAMILY_QUERY = 64;
    private String familyQuery = "";
    private BitSet matchingFamilyLabels = new BitSet();
    private FamilyRecipeSearch familyIndex;
    private String indexedFamily = "";
    private List<RecipePattern> indexedRecipes = List.of();
    private List<Component> familyLabels = List.of();
    private int familyIndexVersion, sentFamilyIndexVersion = -1;
    private long indexedGeneration = -1;
    private TextFieldWidget familyField;
    private Widget familyPlaceholder;
    private int familyQueryDelay;

    private static final int CARD_ITEM_CHOICE = 63;
    private int substitutionSlotPage;

    private int searchResetRevision, receivedSearchResetRevision;
    private static final int SHOW_FAMILY = 42;
    private static final int SET_CATALYST_MODE = 43;
    private static final int SET_REQUIRED_FLUID = 44;
    private static final int REPLACE_FIXED_INPUT = 45;
    private static final int TOGGLE_VARIANT_GROUPS = 46;
    private static final int SET_MATERIAL_SELECTED = 47;
    private static final int SET_DELIVERY_IGNORE=48, SET_DELIVERY_EXTRA=49, RESET_DELIVERY=50, SET_OUTPUT_IGNORE=51, SET_OUTPUT_EXTRA=52, SYNC_OUTPUT=53, SET_OUTPUT_PRIMARY=54, SYNC_INPUT=55, REMOVE_EXTRA_INPUT=56, REMOVE_EXTRA_OUTPUT=57, SYNC_PENDING=58, SET_CATALYST_GROUP=59;
    private Set<String> ignoredDelivery=Set.of();
    private List<GenericStack> extraDelivery=List.of();
    private Set<String> ignoredOutputs=Set.of();
    private List<GenericStack> extraOutputs=List.of();
    private AEKey activeDeliveryKey,lastRemovedExtra;
    private List<org.gtlcore.universalpatterns.integration.ae2.handler.PendingPatternSync.Change> pendingChanges=List.of();
    private int pendingSyncPage;
    private boolean pendingSyncOpen;
    private boolean activeDeliveryOutput;
    private String activeDeliveryRecipe="";
    private AEKey deliverySample;
    private String deliveryAmount="1";
    private String deliveryMessage="";
    private boolean groupVariants=true;
    private static final int SET_MULTIPLIERS = 60;
    private static final int SET_CARD_DEFAULTS = 61;
    private static final int SET_PERSONAL_SUPPLY = 62;
    private boolean keepByproducts, canNarrowRoute;
    private int defaultSupplyMode = -1, personalSupplyMode = -1;
    private int outputMultiplier = 1;
    private int batchMultiplier = 1;
    private String focusToggledRecipe = "";
    private final Map<String,Integer> variantGroupSizes=new HashMap<>();
    private List<GenericStack> familyFluids=List.of();
    private final Map<AEKey,List<GenericStack>> constraintCandidates=new HashMap<>();
    private AEKey constraintAnchor=null;
    private String requiredFluid="";
    private Set<String> fixedInputs=Set.of();
    private String constraintSearch="";
    private final WidgetGroup constraintRows=new WidgetGroup(66,70,200,104).setClientSideWidget();
    private List<GenericStack> constraintOptions=List.of();
    private long constraintRevision;
    private int constraintSlot;
    private int fluidChoicePage;
    private FilterMode filterMode = FilterMode.BLACKLIST;
    private SearchVisibility searchVisibility = SearchVisibility.ALL;
    private final WidgetGroup recipeRows = new WidgetGroup(8, 48, 136, 5 * 28).setClientSideWidget();
    private final WidgetGroup inputSlots = new WidgetGroup(160, 90, 162, 18).setClientSideWidget();
    private final WidgetGroup outputSlots = new WidgetGroup(160, 140, 162, 18).setClientSideWidget();
    private final WidgetGroup ruleRows = new WidgetGroup(10, 78, 148, RULE_PAGE_SIZE * 28).setClientSideWidget();
    private final WidgetGroup recipeListRows = new WidgetGroup(176, 78, 146, RECIPE_LIST_PAGE_SIZE * 28).setClientSideWidget();
    private String search = "";
    private Set<String> localizedMaterials = Set.of();
    private Set<ResourceLocation> localizedTypes = Set.of();
    private List<RecipePattern> searchResults = List.of();
    private String selectedId = "";
    private List<GenericStack> selectedInputs = List.of();
    private List<InputView> selectedInputViews = List.of();
    private final WidgetGroup inputChooser = new WidgetGroup(156, 76, 170, 94).setClientSideWidget();
    private final WidgetGroup inputOptionSlots = new WidgetGroup(4, 22, 162, 36).setClientSideWidget();
    private String selectedInputId = "";
    private int inputOptionPage, inputOptionCount;
    private AEKey inputDefault, inputChoice;
    private List<GenericStack> inputOptions = List.of();
    private List<GenericStack> selectedOutputs = List.of();
    private AEKey selectedDisplay;
    private AEKey selectedPrimary;
    private int selectedStatus;
    private boolean selectedWhitelisted;
    private boolean selectedInFamily;
    private boolean canExpandCasting;
    private boolean catalogReady;
    private int universalPatternSize = -1;
    private int universalSlot;
    private String universalFamily = "";
    private long patternRevision;
    private long draftBaseRevision = -1, lastDraftWriteRevision = -1;

    private boolean acceptsDraftRevision(long revision) {
        long current = machine.getConfigurationRevision();
        return revision == current || current == lastDraftWriteRevision && revision >= draftBaseRevision && revision <= current;
    }

    private final Map<Integer, Selection> drafts = new HashMap<>();
    private final Map<Integer, ItemStack> originals = new HashMap<>();
    private int selectedVariants;
    private String selectedReasonLabel = "";
    private boolean draftChanged;
    private String draftMessage = "";

    private Selection draft() {
        ItemStack current = machine.universalCardSnapshot(universalSlot);
        if (!originals.containsKey(universalSlot) || !ItemStack.matches(originals.get(universalSlot), current)) {
            originals.put(universalSlot, current);
            var saved = UniversalPatternItem.readDraft(current);
            if (gui != null && UniversalPatternItem.needsSupplyPreference(current))
                saved = saved.withDefaults(saved.keepByproducts(), UniversalPatternItem.personalSupplyMode(gui.entityPlayer.getPersistentData()));
            if (gui != null && current.getItem() instanceof UniversalPatternItem) {
                var inherited = new HashMap<>(UniversalPatternItem.personalItemChoices(gui.entityPlayer.getPersistentData()));
                saved.itemChoices().forEach(inherited::put);
                if (inherited.size() <= UniversalPatternItem.MAX_MATERIALS) saved = saved.withItemChoices(inherited);
            }
            drafts.put(universalSlot, saved);
        }
        return drafts.get(universalSlot);
    }

    private boolean storeDraft(Selection selection) {
        long beforeRevision = machine.getConfigurationRevision();
        if (beforeRevision != lastDraftWriteRevision) draftBaseRevision = beforeRevision;
        if (machine.storeUniversalDraft(universalSlot, originals.get(universalSlot), selection)) {
            lastDraftWriteRevision = machine.getConfigurationRevision();
            originals.put(universalSlot, machine.universalCardSnapshot(universalSlot));
            drafts.put(universalSlot, selection);
            return true;
        }
        ruleFeedback = Component.translatable(PREFIX + "draft_conflict");
        return false;
    }

    private UniversalPatternFamily.Candidate draftCandidate(String id) {
        var bound = machine.candidateForFamily(id, draft().family());
        return bound == null ? machine.universalCandidate(id) : bound;
    }

    private boolean storeItemEdit(Selection selection,AEKey key,boolean output){
        var candidate=machine.candidateForFamily(selectedId,selection.family());if(candidate==null)return false;
        try{storeDraft(org.gtlcore.universalpatterns.integration.ae2.handler.PendingPatternSync.enqueue(selection,candidate,key,output));return true;}
        catch(IllegalArgumentException|ArithmeticException invalid){ruleFeedback=Component.translatable(PREFIX+"delivery.invalid");return false;}
    }
    private int reason(String id) {
        return machine.universalReason(draft(), id);
    }

    private boolean validMultipliers(Selection selection) {
        if (selection.batchMultiplier() == 1 && selection.outputMultiplier() == 1) return true;
        try {
            for (var recipe : machine.familyRecipes(selection.family())) {
                var candidate = machine.candidateForFamily(recipe.id(), selection.family());
                if (candidate == null || machine.universalReason(selection,recipe.id()) > 2) continue;
                var policy = selection.forMaterial(candidate.materialId().toString());
                ItemSubstitutions.inputs(policy, recipe, InputSlot::defaultKey);
                DeliveryPolicy.outputs(policy,recipe,candidate.output(),machine.getLevel());
            }
            return true;
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            ruleFeedback = Component.translatable(PREFIX+"output.multiplier_invalid");
            return false;
        }
    }

    private void editDraft(PatternEdit edit, List<String> ids) {
        Selection before = draft();
        ruleFeedback = Component.empty();
        if (edit == PatternEdit.EXTERNAL_CATALYSTS) return; // Legacy whole-card action is no longer exposed.
        if (edit == PatternEdit.APPLY) {
            try {
                UniversalMaterialRules.compile(before.allow(), machine.getLevel());
                UniversalMaterialRules.compile(before.deny(), machine.getLevel());
            } catch (IllegalArgumentException invalid) {
                ruleFeedback = Component.translatable(PREFIX + "pattern_invalid", invalid.getMessage());
                return;
            }
            if (!validMultipliers(before)) return;
            if (machine.applyUniversalDraft(universalSlot, originals.get(universalSlot), before)) {
                originals.put(universalSlot, machine.universalCardSnapshot(universalSlot));
                ruleFeedback = Component.translatable(PREFIX + "draft_applied");
            } else ruleFeedback = Component.translatable(PREFIX + "draft_conflict");
            return;
        }
        if (edit == PatternEdit.REVERT) {
            if (!machine.discardUniversalDraft(universalSlot, originals.get(universalSlot))) {
                ruleFeedback = Component.translatable(PREFIX + "draft_conflict");
                return;
            }
            drafts.remove(universalSlot);
            originals.remove(universalSlot);
            draft();
            ruleFeedback = Component.translatable(PREFIX + "draft_reverted");
            return;
        }
        if (edit == PatternEdit.UNBIND) {
            storeDraft(Selection.empty().withSupplyFrom(before));
            browsingFamily = false;
            searchVisibility = SearchVisibility.ALL;
            searchPage = 0;
            return;
        }
        if (edit == PatternEdit.EXPAND_CASTING) {
            var expanded = expandedCasting(before.family(), selectedId);
            if (expanded == null) return;
            storeDraft(before.withFamily(expanded.family()));
            showFamily();
            return;
        }
        if (edit == PatternEdit.BIND) {
            var candidate = machine.universalCandidate(selectedId);
            if (candidate == null || !editableStatus(machine.getRecipeStatus(selectedId))) return;
            if (!before.family().isEmpty() && machine.candidateForFamily(selectedId,before.family())!=null) {
                if (before.family().equals(candidate.baseFamily())) storeDraft(before.withFamily(candidate.family()));
                showFamily(); return;
            }
            if (!candidate.family().equals(before.family()))
                storeDraft(new Selection(candidate.family(), Set.of(), Set.of(), "all", "").withSupplyFrom(before));
            showFamily();
            return;
        }
        if(edit==PatternEdit.ADD||edit==PatternEdit.REMOVE||edit==PatternEdit.ADD_RESULTS||edit==PatternEdit.REMOVE_RESULTS||edit==PatternEdit.FOLLOW){
            var updated=before;
            for(String id:ids)if(machine.candidateForFamily(id,before.family())!=null)
                updated=updated.withRecipeOverride(id,edit==PatternEdit.FOLLOW?null:edit==PatternEdit.ADD||edit==PatternEdit.ADD_RESULTS);
            storeDraft(updated);return;
        }
        var included = new HashSet<>(before.materials());
        var excluded = new HashSet<>(before.excluded());
        var choices = new HashMap<>(before.recipes());
        for (String id : ids) {
            var candidate = machine.candidateForFamily(id, before.family());
            if (candidate == null) continue;
            var material = candidate.materialId();
            switch (edit) {
                case ADD, ADD_RESULTS -> { included.add(material); excluded.remove(material); }
                case REMOVE, REMOVE_RESULTS -> { included.remove(material); excluded.add(material); }
                case FOLLOW -> { included.remove(material); excluded.remove(material); }
                case CHOOSE_RECIPE -> choices.put(material, id);
                case ALL_RECIPES -> choices.remove(material);
                default -> {}
            }
        }
        if (included.size() <= 2048 && excluded.size() <= 2048 && choices.size() <= 2048)
            storeDraft(new Selection(before.family(), included, excluded, before.allow(), before.deny(), choices, before.externalCatalysts(), before.catalystModes(), before.requiredFluid(),before.fixedInputs()).withDeliveryFrom(before));
    }

    private String savedAllow = "", savedDeny = "";
    private boolean externalCatalysts;
    private List<GenericStack> selectedCatalysts = List.of();
    private int catalystPage;
    private List<Integer> selectedCatalystModes = List.of();
    private final WidgetGroup ingredientArea = new WidgetGroup(154,28,174,118).setClientSideWidget();
    private final WidgetGroup catalystSlots = new WidgetGroup(6,71,76,42).setClientSideWidget();
    private Component ruleFeedback = Component.empty();
    private Component searchFeedback = Component.empty();
    private final UniversalAttributeEditor ruleEditor;
    private RecipePatternSampleWidget recipeSample, outputSample;
    private boolean inspectingOutput;
    private @Nullable AEKey serverOutputSample;
    private TextFieldWidget searchField;
    private @Nullable AEKey serverRecipeSample;
    private @Nullable AEKey lastInspectedSample;
    private boolean lastInspectedForRecipes;
    private List<String> lastAttributes = List.of();
    private Component selectedMaterial;
    private ImageWidget familySummary;
    private boolean whitelistActive;
    private int searchPage, recipeListPage, rulePage, inputPage, outputPage;
    private int searchCount, recipeListCount, ruleCount, publishedCount, skippedCount;
    private int blockedCount, chancedPrimaryCount, ruleFilteredCount, activeBlacklistCount;
    private int chancedInputCount;
    private int whitelistFilteredCount;
    private Target ruleTarget = Target.RECIPE_ID;
    private String ruleExpression = "";
    private long lastRevision = -1;
    private boolean dirty = true;

    public RecipePatternBrowserWidget(int x, int y, PatternEditorHost machine, Page page) {
        this(x,y,machine,page,false);
    }

    public RecipePatternBrowserWidget(int x, int y, PatternEditorHost machine, Page page, boolean compact) {
        super(x, y, page == Page.OVERVIEW ? 232 : 332, page == Page.OVERVIEW ? 84 : page == Page.UNIVERSAL ? 268 : 226);
        this.machine = machine;
        this.page = page;
        this.compact = compact;
        ruleEditor = page == Page.UNIVERSAL ? new UniversalAttributeEditor(this::inspectSample, this::searchAttribute, this::saveRules) : null;
        switch (page) {
            case OVERVIEW -> addWidget(createOverview());
            case SEARCH, UNIVERSAL -> addWidget(createSearchPage());
            case FILTERS -> addWidget(createRecipeListPage());
        }
    }

    private WidgetGroup createOverview() {
        WidgetGroup view = new WidgetGroup(0, 0, 232, 84).setClientSideWidget();
        addCounter(view, 0, "published_label", () -> publishedCount, RecipePatternUiTextures.COUNTER_PUBLISHED_COLOR);
        addCounter(view, 80, "blocked_label", () -> blockedCount, RecipePatternUiTextures.COUNTER_ERROR_COLOR);
        addCounter(view, 160, "skipped_label", () -> skippedCount, RecipePatternUiTextures.COUNTER_MUTED_COLOR);
        view.addWidget(line(0, 48, 232, 14, this::blockedCounts, 0xFF616978));
        view.addWidget(line(0, 68, 232, 14, () -> text("navigation_hint"), 0xFF616978));
        return view;
    }

    private void addCounter(WidgetGroup view, int x, String label, Supplier<Integer> count, int color) {
        view.addWidget(new ImageWidget(x, 0, 72, 40, RecipePatternUiTextures.COUNTER_BACKGROUND));
        view.addWidget(line(x + 6, 4, 60, 12, () -> text(label), RecipePatternUiTextures.COUNTER_TEXT_COLOR));
        view.addWidget(new ImageWidget(x + 6, 19, 60, 16,
                new TextTexture(() -> Integer.toString(count.get())).setColor(color).setDropShadow(false).setWidth(60))
                .setHoverTooltips(Component.translatable(PREFIX + label)));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (page == Page.UNIVERSAL && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (inputChooser.isVisible()) { send(CLOSE_INPUT_OPTIONS,b -> {}); return true; }
            if (supplyChooser.isVisible()) { closeSupplyChooser(); return true; }
            if (universalOptions.isVisible()) { closeUniversalOptions(); return true; }
            if (ruleEditor.isVisible()) { ruleEditor.closeTopLayer(); return true; }
        }
        if (page == Page.UNIVERSAL && supplyChooser.isVisible()) return supplyChooser.keyPressed(keyCode,scanCode,modifiers);
        if (page == Page.UNIVERSAL && universalOptions.isVisible()) return true;
        if (page == Page.UNIVERSAL && ruleEditor.isVisible()) return ruleEditor.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double delta) {
        if (supplyChooser.isVisible()) return true;
        if (hasModalOpen()) return super.mouseWheelMove(mouseX, mouseY, delta);
        if (delta != 0 && (page == Page.UNIVERSAL || page == Page.SEARCH)) {
            int step=delta>0 ? -1 : 1;
            if (recipeRows.isMouseOverElement(mouseX,mouseY) && searchCount>resultPageSize()) {
                sendPage(SEARCH_PAGE,step);return true;
            }
            if (catalystSlots.isMouseOverElement(mouseX,mouseY) && selectedCatalysts.size()>1) {
                catalystPage=movePage(catalystPage,step,selectedCatalysts.size(),1);refreshCatalystSlots();return true;
            }
            if (inputSlots.isMouseOverElement(mouseX,mouseY) && selectedInputs.size()>stackPageSize()) {
                changeStackPage(false,step);return true;
            }
            if (outputSlots.isMouseOverElement(mouseX,mouseY) && selectedOutputs.size()>stackPageSize()) {
                changeStackPage(true,step);return true;
            }
        }
        return super.mouseWheelMove(mouseX,mouseY,delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (page == Page.UNIVERSAL && supplyChooser.isVisible()) return supplyChooser.charTyped(codePoint,modifiers);
        if (page == Page.UNIVERSAL && universalOptions.isVisible()) return true;
        if (page == Page.UNIVERSAL && ruleEditor.isVisible()) return ruleEditor.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    private WidgetGroup createSearchPage() {
        WidgetGroup view = new WidgetGroup(0, 0, 332, page == Page.UNIVERSAL ? 268 : 226).setClientSideWidget();
        boolean universal = page == Page.UNIVERSAL;
        if (universal) view.addWidget(new ImageWidget(0, 0, 332, 268, GuiTextures.BACKGROUND));
        TextFieldWidget field = new TextFieldWidget(universal ? 104 : 4, 4, universal ? 91 : 210, 18, () -> search, value -> search = value) {

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (isFocus() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
                    submitSearch();
                    return true;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }.setMaxStringLength(MERecipePatternFilter.MAX_EXPRESSION_LENGTH)
                .setBordered(false).setTextColor(0xFFFFFFFF).setBackground(universal ? GuiTextures.SLOT_DARK : RecipePatternUiTextures.INPUT_BACKGROUND);
        field.setHoverTooltips(Component.translatable(PREFIX + (page == Page.UNIVERSAL ? "pattern_search_hint" : "search_hint")));
        searchField = field;
        if (universal) {
            view.addWidget(line(4, 4, 24, 18, () -> text("sample_input"), NATIVE_PANEL_TEXT));
            view.addWidget(line(54, 4, 24, 18, () -> text("sample_output"), NATIVE_PANEL_TEXT));
            recipeSample = new RecipePatternSampleWidget(30, 4, value -> useRecipeSample(value, false),
                    Component.translatable(PREFIX + "sample_input_hint"), true);
            outputSample = new RecipePatternSampleWidget(80, 4, value -> useRecipeSample(value, true),
                    Component.translatable(PREFIX + "sample_output_hint"), true);
            view.addWidget(outputSample);
            view.addWidget(recipeSample);
            view.addWidget(button(239, 4, 89, 18, () -> text("sample_button"), this::openRecipeAttributes)
                    .setHoverTooltips(Component.translatable(PREFIX + "sample_find_title")));
        } else enableMaterialDrop(field, value -> search = value);
        view.addWidget(field);
        view.addWidget(button(universal ? 198 : 220, 4, universal ? 38 : 48, 18, () -> text("search"), this::submitSearch));
        if (!universal) view.addWidget(button(273, 4, 55, 18, () -> text("visibility." + searchVisibility.name().toLowerCase(Locale.ROOT)),
                () -> send(SEARCH_VISIBILITY, buffer -> buffer.writeEnum(nextVisibility())))
                .setHoverTooltips(Component.translatable(PREFIX + "visibility_hint")));
        view.addWidget(universal ? new ImageWidget(4, 28, 144, 178, GuiTextures.BACKGROUND) : panel(4, 28, 144, 178));
        view.addWidget(universal ? new ImageWidget(154, 28, 174, 178, GuiTextures.BACKGROUND) : panel(154, 28, 174, 178));
        int panelText = universal ? NATIVE_PANEL_TEXT : TEXT_COLOR;
        int panelDetail = universal ? NATIVE_PANEL_TEXT : MUTED_COLOR;
        view.addWidget(line(8, 31, universal ? 70 : 136, 12, () -> searchFeedback.getString().isEmpty() ?
                text(browsingFamily ? "family_results_label" : "results_label", searchCount) : searchFeedback.getString(), panelDetail));
        if (universal) {
            familyField = new TextFieldWidget(79,30,65,14,() -> familyQuery, value -> {
                if (!value.equals(familyQuery)) {
                    familyQuery = value;
                    familyQueryDelay = 4;
                }
            }).setMaxStringLength(96).setBordered(false).setTextColor(0xFFFFFFFF).setBackground(GuiTextures.SLOT_DARK);
            familyField.setHoverTooltips(Component.translatable(PREFIX+"family_search_hint"));
            view.addWidget(familyField);
            familyPlaceholder = line(82,31,60,12,() -> text("family_search"),0xFFB8B8B8);
            view.addWidget(familyPlaceholder);
            familyPlaceholder.setVisible(false).setActive(false);
            familyField.setVisible(false).setActive(false);
        }
        view.addWidget(recipeRows);
        addPager(view, 8, 190, 136, () -> searchPage, () -> pages(searchCount, resultPageSize()), delta -> sendPage(SEARCH_PAGE, delta));
        if (!universal) {
        view.addWidget(line(160, 31, 162, 12, () -> selectedMaterial != null ? selectedMaterial.getString() :
                selectedDisplay == null ? text("select_recipe") : selectedDisplay.getDisplayName().getString(), panelText));
        if (!universal) view.addWidget(line(160, 45, 162, 12, () -> selectedId, panelDetail));
        view.addWidget(line(160, 59, 162, 12, () -> selectedId.isEmpty() ? "" : text("status." + selectedStatus), panelDetail));
        view.addWidget(new ImageWidget(160, 73, 162, 1, new ColorRectTexture(PANEL_BORDER)));
            view.addWidget(line(160, 76, 162, 12, () -> text("inputs"), TEXT_COLOR)
                    .setHoverTooltips(Component.translatable(PREFIX + "input_choice_hint")));
            view.addWidget(line(160, 126, 162, 12, () -> text("outputs_short"), TEXT_COLOR)
                    .setHoverTooltips(Component.translatable(PREFIX + "primary_hint")));
        view.addWidget(inputSlots);
        view.addWidget(outputSlots);
        addPager(view, 160, 110, compact ? 72 : 162, () -> inputPage, () -> pages(selectedInputs.size(), stackPageSize()), delta -> changeStackPage(false, delta));
        addPager(view, 160, 160, compact ? 72 : 162, () -> outputPage, () -> pages(selectedOutputs.size(), stackPageSize()), delta -> changeStackPage(true, delta));
        } else addInlineIngredients(view);
        if (page == Page.UNIVERSAL) {
            addUniversalButtons(view);
            view.addWidget(ruleEditor);
        } else {
            addHatchButtons(view);
            addFooter(view);
        }
        createInputChooser();
        view.addWidget(inputChooser);
        if (compact) compactLayout(view);
        return view;
    }

    private void addHatchButtons(WidgetGroup view) {
        view.addWidget(button(160, 181, 50, 18, () -> text(selectedStatus == 1 ? "unblacklist_short" : "blacklist_short"), () -> {
            if (!selectedId.isEmpty()) sendBlacklist(selectedId, selectedStatus != 1);
        }).setHoverTooltips(Component.translatable(PREFIX + "blacklist_hint")));
        view.addWidget(button(214, 181, 50, 18, () -> text(selectedWhitelisted ? "unwhitelist_short" : "whitelist_short"), () -> {
            if (!selectedId.isEmpty()) sendWhitelist(selectedId, !selectedWhitelisted);
        }).setHoverTooltips(Component.translatable(PREFIX + "whitelist_hint")));
        view.addWidget(button(268, 181, 54, 18, () -> text("reset_primary_short"), () -> {
            if (!selectedId.isEmpty()) sendPrimary(selectedId, null);
        }).setHoverTooltips(Component.translatable(PREFIX + "primary_hint")));
    }

    private final WidgetGroup universalOptions = new WidgetGroup(0, 0, 332, 268).setClientSideWidget();
    private final WidgetGroup supplyChooser = new WidgetGroup(0, 0, 332, 268).setClientSideWidget();
    private final List<Runnable> pagerVisibility = new ArrayList<>();

    private void closeUniversalOptions() {
        universalOptions.setVisible(false).setActive(false);
    }

    private void addUniversalButtons(WidgetGroup view) {
        familySummary = line(8, 209, 174, 12, () -> universalPatternSize < 0 ? text("pattern_missing") :
                UniversalPatternFamily.compactLabel(universalFamily).getString(), NATIVE_PANEL_TEXT);
        view.addWidget(familySummary);
        view.addWidget(button(190,207,66,16,()->text("sync.main",pendingChanges.size()),this::openPendingSync));
        view.addWidget(line(260, 209, 64, 12, () -> universalPatternSize < 0 ? "" : catalogReady ? text("pattern_count", universalPatternSize) : "--", NATIVE_PANEL_TEXT));
        if (machine.universalPatternSlots() == 1) view.addWidget(button(8, 226, 66, 18, () -> text(universalFamily.isEmpty() ? "clear_search" : "show_family"), () -> {
            if (!universalFamily.isEmpty()) { send(SHOW_FAMILY, buffer -> {}); return; }
            recipeSample.display(null); outputSample.display(null);
            useRecipeSample(null, false); useRecipeSample(null, true);
            search = "";
            searchField.setCurrentString("");
            submitSearch();
        }).setHoverTooltips(Component.translatable(PREFIX + "clear_search_hint")));
        else view.addWidget(button(8, 226, 66, 18, () -> text("pattern_slot", universalSlot + 1), () -> {
            ruleEditor.close();
            send(UNIVERSAL_SLOT, buffer -> buffer.writeVarInt((universalSlot + 1) % machine.universalPatternSlots()));
        }).setHoverTooltips(Component.translatable(PREFIX + "pattern_slot_hint")));
        view.addWidget(button(80, 226, 76, 18, () -> text("pattern_rules"), this::openRuleEditor)
                .setHoverTooltips(Component.translatable(PREFIX + "pattern_rules_help")));
        view.addWidget(button(162, 226, 66, 18, () -> text("more_options"), () -> {
            ruleEditor.close();
            universalOptions.setVisible(true).setActive(true);
        }));
        view.addWidget(button(234, 226, 90, 18, () -> text(draftChanged?"draft_apply":"draft_saved"), () -> {if(draftChanged)editPattern(PatternEdit.APPLY);})
                .setHoverTooltips(Component.translatable(PREFIX + "draft_apply_hint")));
        view.addWidget(line(8, 249, 316, 13, () -> draftMessage.isEmpty() ? text(draftChanged ? "draft_pending" : "draft_clean") : draftMessage, NATIVE_PANEL_TEXT));
        // Full-screen input shield prevents clicks through the expanded panel.
        universalOptions.addWidget(new ButtonWidget(0, 0, 332, 268, new ColorRectTexture(0x88000000), click -> {
            if (click.isRemote) closeUniversalOptions();
        }));
        universalOptions.addWidget(new ButtonWidget(20, 36, 292, 222, GuiTextures.BACKGROUND, click -> {}));
        universalOptions.addWidget(line(30, 44, 142, 14, () -> text("more_options"), NATIVE_PANEL_TEXT));
        var expandCasting = button(178, 43, 100, 16, () -> text("pattern_expand_casting"), () -> {
            editPattern(PatternEdit.EXPAND_CASTING);
            closeUniversalOptions();
        }).setHoverTooltips(Component.translatable(PREFIX + "pattern_expand_casting_hint"));
        expandCasting.setVisible(false).setActive(false);
        universalOptions.addWidget(expandCasting);
        pagerVisibility.add(() -> expandCasting.setVisible(canExpandCasting).setActive(canExpandCasting));
        var narrow = button(178,43,100,16,() -> text("route.narrow"),() -> {
            editPattern(PatternEdit.BIND); closeUniversalOptions();
        });
        narrow.setVisible(false).setActive(false);
        universalOptions.addWidget(narrow);
        pagerVisibility.add(() -> narrow.setVisible(canNarrowRoute).setActive(canNarrowRoute));
        universalOptions.addWidget(button(284, 43, 18, 16, () -> "X", this::closeUniversalOptions));
        universalOptions.addWidget(line(30, 63, 272, 12, () -> text("variant_section", selectedVariants), NATIVE_PANEL_TEXT));
        universalOptions.addWidget(button(30, 79, 132, 18, () -> text("variant_choose", selectedVariants), () -> {
            editPattern(PatternEdit.CHOOSE_RECIPE); closeUniversalOptions();
        }));
        universalOptions.addWidget(button(170, 79, 132, 18, () -> text("variant_all"), () -> {
            editPattern(PatternEdit.ALL_RECIPES); closeUniversalOptions();
        }));
        universalOptions.addWidget(button(30,100,132,17,() -> text(keepByproducts ? "outputs.all" : "outputs.primary"),
                () -> setCardDefaults(!keepByproducts,defaultSupplyMode)));
        universalOptions.addWidget(button(170, 100, 132, 17, () -> text("multipliers.summary", batchMultiplier, outputMultiplier),
                () -> { closeUniversalOptions(); openMultiplier(); }));
        universalOptions.addWidget(button(30,119,132,17,() -> text("supply.default",text("ingredients.supply_mode."+defaultSupplyMode)),
                () -> setCardDefaults(keepByproducts, defaultSupplyMode == 3 ? -1 : defaultSupplyMode+1))
                .setHoverTooltips(Component.translatable(PREFIX+"supply.default_hint")));
        universalOptions.addWidget(button(170,119,132,17,() -> text("supply.personal",text("ingredients.supply_mode."+personalSupplyMode)),
                () -> send(SET_PERSONAL_SUPPLY,b -> b.writeVarInt(personalSupplyMode == 3 ? -1 : personalSupplyMode+1)))
                .setHoverTooltips(Component.translatable(PREFIX+"supply.personal_hint")));
        universalOptions.addWidget(line(30, 138, 272, 12, () -> text("bulk_section", searchCount), NATIVE_PANEL_TEXT));
        universalOptions.addWidget(button(30, 151, 132, 18, () -> text("pattern_bulk"), () -> {
            editPattern(PatternEdit.ADD_RESULTS); closeUniversalOptions();
        }).setHoverTooltips(Component.translatable(PREFIX + "pattern_bulk_hint")));
        universalOptions.addWidget(button(170, 151, 132, 18, () -> text("pattern_bulk_remove"), () -> {
            editPattern(PatternEdit.REMOVE_RESULTS); closeUniversalOptions();
        }).setHoverTooltips(Component.translatable(PREFIX + "pattern_bulk_hint")));
        universalOptions.addWidget(button(30, 173, 132, 18, () -> text("visibility." + searchVisibility.name().toLowerCase(Locale.ROOT)), () -> {
            send(SEARCH_VISIBILITY, buffer -> buffer.writeEnum(nextVisibility()));
        }).setHoverTooltips(Component.translatable(PREFIX + "visibility_hint")));
        universalOptions.addWidget(button(170, 173, 132, 18, () -> text("draft_revert"), () -> {
            ruleEditor.setDraftContext(""); ruleEditor.close();
            editPattern(PatternEdit.REVERT); closeUniversalOptions();
        }));
        universalOptions.addWidget(button(30,195,132,18,()->text(groupVariants?"variants.expand":"variants.collapse"),()->{send(TOGGLE_VARIANT_GROUPS,b->{});closeUniversalOptions();}));
        universalOptions.addWidget(button(170,195,132,18,()->text("ingredients.reset_all"),()->{send(SET_REQUIRED_FLUID,b->{b.writeVarLong(patternRevision);b.writeVarInt(universalSlot);writeSample(b,null);b.writeBoolean(false);});closeUniversalOptions();}));
        universalOptions.addWidget(button(30,224,132,18,()->text("material_restore"),()->{editPattern(PatternEdit.FOLLOW);closeUniversalOptions();}));
        universalOptions.addWidget(button(170,224,132,18,()->text("delivery.reset"),()->{send(RESET_DELIVERY,b->{b.writeVarLong(patternRevision);b.writeVarInt(universalSlot);});closeUniversalOptions();}));
        closeUniversalOptions();
        view.addWidget(universalOptions);
        closeSupplyChooser();
        view.addWidget(supplyChooser);
    }

    private void closeSupplyChooser() { pendingSyncOpen=false;activeDeliveryKey=null;supplyChooser.setVisible(false).setActive(false); }

    private void setCardDefaults(boolean keep, int mode) {
        send(SET_CARD_DEFAULTS,b -> {
            b.writeVarLong(patternRevision); b.writeVarInt(universalSlot); b.writeBoolean(keep); b.writeVarInt(mode);
        });
    }

    private List<GenericStack> familyFluidOptions() {
        var family=draft().family();
        if(family.isEmpty())return List.of();
        return machine.cachedRecipes().stream().filter(recipe->machine.candidateForFamily(recipe.id(),family)!=null)
                .flatMap(recipe->org.gtlcore.universalpatterns.integration.ae2.handler.ConsumedFluidFilter.inputs(recipe).stream())
                .distinct().sorted(Comparator.comparing(key->key.getId().toString()+key.toTag()))
                .map(key->new GenericStack(key,1)).toList();
    }

    private String requiredFluidLabel() {
        if(fixedInputs.isEmpty())return text("ingredients.fluid_any");
        var names=familyFluids.stream().filter(stack->fixedInputs.contains(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(stack.what()))).map(stack->stack.what().getDisplayName().getString()).distinct().toList();
        return names.isEmpty()?text("ingredients.fixed_unresolved",fixedInputs.size()):text("ingredients.fixed_names",String.join(", ",names));
    }

    private void chooseRequiredFluid(AEKey key,long revision,int slot) {
        boolean fixed=key!=null; // Choosing an already-selected candidate is idempotent; reset is explicit.
        if(constraintAnchor!=null)send(REPLACE_FIXED_INPUT,buffer->{buffer.writeVarLong(revision);buffer.writeVarInt(slot);buffer.writeUtf(selectedId);writeSample(buffer,constraintAnchor);writeSample(buffer,fixed?key:null);});
        else send(SET_REQUIRED_FLUID,buffer->{buffer.writeVarLong(revision);buffer.writeVarInt(slot);writeSample(buffer,key);buffer.writeBoolean(fixed);});
        closeSupplyChooser();
    }

    private void refreshConstraintRows() {
        String query=constraintSearch.strip().toLowerCase(Locale.ROOT);
        var visible=new LinkedHashMap<AEKey,GenericStack>();
        if(constraintAnchor==null){
            var groups=new HashMap<AEKey,List<AEKey>>();constraintCandidates.forEach((k,v)->groups.put(k,v.stream().map(GenericStack::what).toList()));
            for(var key:org.gtlcore.universalpatterns.integration.ae2.handler.InputConstraintOptions.overview(selectedInputs.stream().map(GenericStack::what).toList(),familyFluids.stream().map(GenericStack::what).toList(),groups,fixedInputs))visible.put(key,new GenericStack(key,1));
        } else for(var stack:constraintCandidates.getOrDefault(constraintAnchor,List.of(new GenericStack(constraintAnchor,1))))visible.putIfAbsent(stack.what(),stack);
        constraintOptions=visible.values().stream().filter(stack->query.isEmpty() || stack.what().getDisplayName().getString().toLowerCase(Locale.ROOT).contains(query) || stack.what().getId().toString().contains(query))
                .sorted(Comparator.comparingInt(stack->fixedInputs.contains(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(stack.what()))?0:1)).toList();
        fluidChoicePage=clampPage(fluidChoicePage,constraintOptions.size(),4);
        constraintRows.clearAllWidgets();
        var options=page(constraintOptions,fluidChoicePage,4);
        for(int i=0;i<options.size();i++) {
            var stack=options.get(i);int y=i*27;
            boolean fixed=fixedInputs.contains(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(stack.what()));
            boolean conflict=constraintAnchor==null && constraintCandidates.getOrDefault(stack.what(),List.of(stack)).stream().filter(k->fixedInputs.contains(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(k.what()))).count()>1;
            constraintRows.addWidget(new ImageWidget(0,y,200,23,GuiTextures.BUTTON));
            constraintRows.addWidget(stackWidget(stack,3,y+2,GuiTextures.SLOT));
            constraintRows.addWidget(line(27,y+3,124,17,()->stack.what().getDisplayName().getString(),NATIVE_PANEL_TEXT));
            constraintRows.addWidget(line(153,y+3,44,17,()->text(conflict?"ingredients.conflict":fixed?"ingredients.fixed":constraintAnchor==null?"ingredients.wildcard":"ingredients.pick"),NATIVE_PANEL_TEXT));
            constraintRows.addWidget(new ButtonWidget(0,y,200,23,IGuiTexture.EMPTY,click->{if(click.isRemote){if(constraintAnchor==null){openInputDelivery(stack);}else chooseRequiredFluid(stack.what(),constraintRevision,constraintSlot);}})
                    .setHoverTooltips(stack.what().getDisplayName(),Component.translatable(PREFIX+"ingredients.fluid_hint")));
        }
        if(options.isEmpty())constraintRows.addWidget(line(0,4,200,36,()->text("ingredients.fluid_empty"),NATIVE_PANEL_TEXT));
    }

    private void openFluidChooser() {
        activeDeliveryKey=null;
        constraintRevision=patternRevision;constraintSlot=universalSlot;
        supplyChooser.clearAllWidgets();int shieldHeight=compact?294:268;supplyChooser.setSizeHeight(shieldHeight);
        supplyChooser.addWidget(new ButtonWidget(0,0,332,shieldHeight,new ColorRectTexture(0x88000000),click->{if(click.isRemote)closeSupplyChooser();}));
        supplyChooser.addWidget(new ButtonWidget(54,8,224,248,GuiTextures.BACKGROUND,click->{}));
        supplyChooser.addWidget(line(66,17,174,14,()->text(constraintAnchor==null?"ingredients.fluid_title":"ingredients.choose_one"),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(248,16,18,16,()->constraintAnchor==null?"X":"<",()->{if(constraintAnchor==null)closeSupplyChooser();else{constraintAnchor=null;constraintSearch="";fluidChoicePage=0;openFluidChooser();}}));
        supplyChooser.addWidget(line(66,34,200,12,()->constraintAnchor==null?text("ingredients.fluid_scope"):constraintAnchor.getDisplayName().getString(),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(new TextFieldWidget(66,49,200,16,()->constraintSearch,value->{constraintSearch=value;fluidChoicePage=0;refreshConstraintRows();}).setMaxStringLength(128));
        supplyChooser.addWidget(constraintRows);refreshConstraintRows();
        supplyChooser.addWidget(button(66,183,24,16,()->"<",()->{fluidChoicePage=movePage(fluidChoicePage,-1,constraintOptions.size(),4);refreshConstraintRows();}));
        supplyChooser.addWidget(line(96,183,140,16,()->text("page",fluidChoicePage+1,pages(constraintOptions.size(),4)),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(242,183,24,16,()->">",()->{fluidChoicePage=movePage(fluidChoicePage,1,constraintOptions.size(),4);refreshConstraintRows();}));
        supplyChooser.addWidget(button(66,208,200,19,()->text(constraintAnchor==null?"ingredients.fluid_any":"ingredients.make_wildcard"),()->{if(constraintAnchor==null)chooseRequiredFluid(null,constraintRevision,constraintSlot);else {send(REPLACE_FIXED_INPUT,b->{b.writeVarLong(constraintRevision);b.writeVarInt(constraintSlot);b.writeUtf(selectedId);writeSample(b,constraintAnchor);writeSample(b,null);});closeSupplyChooser();}}));
        supplyChooser.addWidget(line(66,236,200,12,()->text("ingredients.supply_draft"),NATIVE_PANEL_TEXT));
        supplyChooser.setVisible(true).setActive(true);
    }

    private void openSupplyChooser(GenericStack stack, int current) {
        activeDeliveryKey=null;
        final long revision=patternRevision;
        final int slot=universalSlot;
        final String recipe=selectedId;
        final boolean[] together={false};
        supplyChooser.clearAllWidgets();
        int shieldHeight=compact ? 294 : 268;
        supplyChooser.setSizeHeight(shieldHeight);
        supplyChooser.addWidget(new ButtonWidget(0,0,332,shieldHeight,new ColorRectTexture(0x88000000),click->{
            if(click.isRemote)closeSupplyChooser();
        }));
        supplyChooser.addWidget(new ButtonWidget(54,28,224,226,GuiTextures.BACKGROUND,click->{}));
        supplyChooser.addWidget(line(66,38,172,14,()->text("ingredients.supply_title"),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(248,37,18,16,()->"X",this::closeSupplyChooser));
        supplyChooser.addWidget(stackWidget(stack,66,59,GuiTextures.SLOT));
        supplyChooser.addWidget(line(90,59,176,18,()->stack.what().getDisplayName().getString(),NATIVE_PANEL_TEXT)
                .setHoverTooltips(stack.what().getDisplayName()));
        for(int mode=0;mode<4;mode++) {
            final int choice=mode;
            supplyChooser.addWidget(button(66,85+mode*27,200,21,
                    ()->text("ingredients.supply_mode."+choice)+(current==choice?text("ingredients.supply_current"):""),()->{
                send(together[0]?SET_CATALYST_GROUP:SET_CATALYST_MODE,buffer->{
                    buffer.writeVarLong(revision);buffer.writeVarInt(slot);buffer.writeUtf(recipe,1024);
                    writeSample(buffer,stack.what());buffer.writeVarInt(choice);
                });
                closeSupplyChooser();
            }).setHoverTooltips(Component.translatable(PREFIX+"ingredients.supply_hint."+mode)));
        }
        supplyChooser.addWidget(button(66,198,200,21,()->text(together[0]?"ingredients.group_on":"ingredients.group_off"),()->together[0]=!together[0]).setHoverTooltips(Component.translatable(PREFIX+"ingredients.group_hint")));
        supplyChooser.addWidget(button(66,225,200,18,()->text("supply.follow"),()->{
            send(together[0]?SET_CATALYST_GROUP:SET_CATALYST_MODE,b -> {
                b.writeVarLong(revision); b.writeVarInt(slot); b.writeUtf(recipe,1024); writeSample(b,stack.what()); b.writeVarInt(-1);
            });
            closeSupplyChooser();
        }));
        supplyChooser.setVisible(true).setActive(true);
    }

    private boolean deliveryIgnored(AEKey key){return ignoredDelivery.contains(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(key));}
    private boolean outputIgnored(AEKey key){return ignoredOutputs.contains(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(key));}
    private void openInputDelivery(GenericStack stack){
        if (!stack.what().equals(activeDeliveryKey)) substitutionSlotPage = 0;
        activeDeliveryKey=stack.what();activeDeliveryOutput=false;activeDeliveryRecipe=selectedId;
        final long revision=patternRevision;final int slot=universalSlot;AEKey key=stack.what();
        supplyChooser.clearAllWidgets();int height=compact?294:268;supplyChooser.setSizeHeight(height);
        supplyChooser.addWidget(new ButtonWidget(0,0,332,height,new ColorRectTexture(0x88000000),click->{if(click.isRemote)closeSupplyChooser();}));
        supplyChooser.addWidget(new ButtonWidget(54,16,224,240,GuiTextures.BACKGROUND,click->{}));
        supplyChooser.addWidget(line(66,28,172,18,()->text("delivery.title"),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(248,28,18,18,()->"X",this::closeSupplyChooser));
        supplyChooser.addWidget(stackWidget(stack,66,52,GuiTextures.SLOT));
        supplyChooser.addWidget(line(90,52,176,18,()->key.getDisplayName().getString(),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(66,78,200,20,()->text("delivery.candidates"),()->{constraintAnchor=key;constraintSearch="";fluidChoicePage=0;openFluidChooser();}));
        supplyChooser.addWidget(button(66,104,200,20,()->text(deliveryIgnored(key)?"delivery.restore":"delivery.ignore"),()->{
            send(SET_DELIVERY_IGNORE,b->{b.writeVarLong(revision);b.writeVarInt(slot);writeSample(b,key);b.writeBoolean(!deliveryIgnored(key));});closeSupplyChooser();
        }));
        supplyChooser.addWidget(button(66,130,200,20,()->text("delivery.extra"),()->openExtraDelivery(key)));
        var candidates = selectedInputViews.stream().filter(input -> selectedInFamily && input.stack().what().equals(key)
                && input.stack().what() instanceof AEItemKey && input.alternatives() > 1).toList();
        if (!candidates.isEmpty()) {
            substitutionSlotPage = clampPage(substitutionSlotPage, candidates.size(), 1);
            var input = candidates.get(substitutionSlotPage);
            String recipe = selectedId;
            supplyChooser.addWidget(button(66,156,candidates.size() > 1 ? 162 : 200,20,
                    () -> text("delivery.substitute", input.deliveryKey().getDisplayName().getString()), () -> {
                closeSupplyChooser();
                send(INPUT_OPTIONS, b -> { b.writeUtf(recipe,1024); b.writeUtf(input.id(),64); });
            }).setHoverTooltips(Component.literal(input.id() + " · " + input.deliveryKey().getId()),
                    Component.translatable(PREFIX+"delivery.substitute_hint")));
            if (candidates.size() > 1) supplyChooser.addWidget(button(232,156,34,20,
                    () -> (substitutionSlotPage + 1) + "/" + candidates.size(), () -> {
                substitutionSlotPage = (substitutionSlotPage + 1) % candidates.size();
                openInputDelivery(stack);
            }));
        }
        if(extraDelivery.stream().anyMatch(v->v.what().equals(key)))supplyChooser.addWidget(button(66,182,200,20,()->text("delivery.delete_added"),()->removeExtra(key,false,revision,slot)));
        supplyChooser.addWidget(line(66,214,200,28,()->text(key.equals(lastRemovedExtra)&&extraDelivery.stream().noneMatch(v->v.what().equals(key))?"delivery.removed_sync":"delivery.scope_hint"),NATIVE_PANEL_TEXT));
        supplyChooser.setVisible(true).setActive(true);
    }
    private void openOutputDelivery(GenericStack stack){
        activeDeliveryKey=stack.what();activeDeliveryOutput=true;activeDeliveryRecipe=selectedId;
        final long revision=patternRevision;final int slot=universalSlot;AEKey key=stack.what();
        supplyChooser.clearAllWidgets();int height=compact?294:268;supplyChooser.setSizeHeight(height);
        supplyChooser.addWidget(new ButtonWidget(0,0,332,height,new ColorRectTexture(0x88000000),click->{if(click.isRemote)closeSupplyChooser();}));
        supplyChooser.addWidget(new ButtonWidget(54,16,224,240,GuiTextures.BACKGROUND,click->{}));
        supplyChooser.addWidget(line(66,28,172,18,()->text("output.title"),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(248,28,18,18,()->"X",this::closeSupplyChooser));
        supplyChooser.addWidget(stackWidget(stack,66,52,GuiTextures.SLOT));
        supplyChooser.addWidget(line(90,52,176,18,()->key.getDisplayName().getString(),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(66,82,200,20,()->text(outputIgnored(key)?"output.restore":"output.ignore"),()->{
            send(SET_OUTPUT_IGNORE,b->{b.writeVarLong(revision);b.writeVarInt(slot);writeSample(b,key);b.writeBoolean(!outputIgnored(key));});closeSupplyChooser();
        }));
        supplyChooser.addWidget(button(66,110,200,20,()->text(key.equals(selectedPrimary)?"output.is_primary":"output.make_primary"),()->{
            send(SET_OUTPUT_PRIMARY,b->{b.writeVarLong(revision);b.writeVarInt(slot);b.writeUtf(selectedId);writeSample(b,key);});closeSupplyChooser();
        }));
        supplyChooser.addWidget(button(66,138,200,20,()->text("multipliers.summary",batchMultiplier,outputMultiplier),this::openMultiplier));
        if(extraOutputs.stream().anyMatch(v->v.what().equals(key)))supplyChooser.addWidget(button(66,166,200,20,()->text("output.delete_added"),()->removeExtra(key,true,revision,slot)));
        supplyChooser.addWidget(line(66,202,200,40,()->text(key.equals(lastRemovedExtra)&&extraOutputs.stream().noneMatch(v->v.what().equals(key))?"delivery.removed_sync":"output.scope_hint"),NATIVE_PANEL_TEXT));
        supplyChooser.setVisible(true).setActive(true);
    }
    private void openMultiplier() {
        activeDeliveryKey = null;
        long revision = patternRevision;
        int slot = universalSlot;
        String family = universalFamily;
        String[] factor = {Integer.toString(outputMultiplier)};
        String[] batch = {Integer.toString(batchMultiplier)};
        String[] feedback = {""};
        supplyChooser.clearAllWidgets();
        int height = compact ? 294 : 268;
        supplyChooser.setSizeHeight(height);
        supplyChooser.addWidget(new ButtonWidget(0,0,332,height,new ColorRectTexture(0x88000000),click -> {
            if (click.isRemote) closeSupplyChooser();
        }));
        supplyChooser.addWidget(new ButtonWidget(42,30,248,206,GuiTextures.BACKGROUND,click -> {}));
        supplyChooser.addWidget(line(54,42,194,16,() -> text("multipliers.title"),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(260,40,18,18,() -> "X",this::closeSupplyChooser));
        supplyChooser.addWidget(line(54,70,135,18,() -> text("multipliers.batch"),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(new TextFieldWidget(195,70,81,18,() -> batch[0],value -> batch[0]=value)
                .setMaxStringLength(10));
        supplyChooser.addWidget(line(54,92,222,14,() -> text("multipliers.batch_hint"),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(line(54,118,135,18,() -> text("multipliers.yield"),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(new TextFieldWidget(195,118,81,18,() -> factor[0],value -> factor[0]=value)
                .setMaxStringLength(10));
        supplyChooser.addWidget(line(54,140,222,14,() -> text("multipliers.yield_hint"),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(54,166,222,20,() -> text("delivery.set"),() -> {
            try {
                int parsed = Integer.parseInt(factor[0].strip());
                int batches = Integer.parseInt(batch[0].strip());
                if (parsed < 1 || batches < 1 || family.isEmpty()) throw new IllegalArgumentException();
                send(SET_MULTIPLIERS,b -> {
                    b.writeVarLong(revision); b.writeVarInt(slot); b.writeUtf(family,512);
                    b.writeVarInt(parsed); b.writeVarInt(batches);
                });
                closeSupplyChooser();
            } catch (IllegalArgumentException invalid) { feedback[0] = text("output.multiplier_invalid"); }
        }));
        supplyChooser.addWidget(line(54,198,222,20,() -> feedback[0],NATIVE_PANEL_TEXT));
        supplyChooser.setVisible(true).setActive(true);
    }
    private void removeExtra(AEKey key,boolean output,long revision,int slot){
        String recipeId=selectedId;lastRemovedExtra=key;
        send(output?REMOVE_EXTRA_OUTPUT:REMOVE_EXTRA_INPUT,b->{b.writeVarLong(revision);b.writeVarInt(slot);b.writeUtf(recipeId);writeSample(b,key);});
        closeSupplyChooser();
    }
    private void openExtraDelivery(AEKey initial){
        activeDeliveryKey=null;
        final long revision=patternRevision;final int slot=universalSlot;
        deliverySample=initial;deliveryMessage="";deliveryAmount=extraDelivery.stream().filter(s->s.what().equals(initial)).map(s->Long.toString(s.amount())).findFirst().orElse("1");
        supplyChooser.clearAllWidgets();int height=compact?294:268;supplyChooser.setSizeHeight(height);
        supplyChooser.addWidget(new ButtonWidget(0,0,332,height,new ColorRectTexture(0x88000000),click->{if(click.isRemote)closeSupplyChooser();}));
        supplyChooser.addWidget(new ButtonWidget(54,28,224,204,GuiTextures.BACKGROUND,click->{}));
        supplyChooser.addWidget(line(66,40,172,18,()->text("delivery.extra"),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(248,40,18,18,()->"X",this::closeSupplyChooser));
        var sample=new RecipePatternSampleWidget(66,66,key->{deliverySample=key;deliveryAmount=extraDelivery.stream().filter(v->v.what().equals(key)).map(v->Long.toString(v.amount())).findFirst().orElse("1");},Component.translatable(PREFIX+"delivery.drag"));sample.display(initial);supplyChooser.addWidget(sample);
        supplyChooser.addWidget(line(90,66,176,18,()->deliverySample==null?text("delivery.drag"):deliverySample.getDisplayName().getString(),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(line(66,90,200,12,()->text(deliverySample instanceof AEFluidKey?"delivery.mb":"delivery.items"),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(new TextFieldWidget(66,108,200,18,()->deliveryAmount,value->deliveryAmount=value).setMaxStringLength(19));
        supplyChooser.addWidget(button(66,142,200,20,()->text("delivery.set"),()->{
            try{long amount=Long.parseLong(deliveryAmount.strip());if(deliverySample==null||amount<=0)throw new IllegalArgumentException();
                send(SET_DELIVERY_EXTRA,b->{b.writeVarLong(revision);b.writeVarInt(slot);writeSample(b,deliverySample);b.writeVarLong(amount);});lastRemovedExtra=null;closeSupplyChooser();
            }catch(IllegalArgumentException invalid){deliveryMessage=text("delivery.invalid");}
        }));
        supplyChooser.addWidget(button(66,170,200,20,()->text("delivery.remove_extra"),()->{if(deliverySample!=null){removeExtra(deliverySample,false,revision,slot);}}));
        supplyChooser.addWidget(line(66,202,200,14,()->deliveryMessage.isEmpty()?text("delivery.per_craft"):deliveryMessage,NATIVE_PANEL_TEXT));
        supplyChooser.setVisible(true).setActive(true);
    }

    private void refreshCatalystSlots() {
        catalystPage=clampPage(catalystPage,selectedCatalysts.size(),1);
        catalystSlots.clearAllWidgets();
        var stacks=page(selectedCatalysts,catalystPage,1);
        for(int i=0;i<stacks.size();i++) {
            var stack=stacks.get(i);int index=catalystPage+i;
            int mode=index<selectedCatalystModes.size() ? selectedCatalystModes.get(index) : 0;
            catalystSlots.addWidget(stackWidget(stack,i*38,0,GuiTextures.SLOT));
            catalystSlots.addWidget(new ButtonWidget(i*38,0,18,18,new ColorRectTexture(0x00000000),click->{
                if(click.isRemote)openSupplyChooser(stack,mode);
            }).setHoverTooltips(stack.what().getDisplayName(),Component.translatable(PREFIX+"ingredients.click_supply")));
            catalystSlots.addWidget(line(i*38+22,0,30,18,()->text("ingredients.supply_short."+mode),NATIVE_PANEL_TEXT));
        }
    }

    private Widget typeChoiceButton;

    private void addInlineIngredients(WidgetGroup view) {
        ingredientArea.addWidget(line(8,6,158,16,()->selectedMaterial==null?text("select_recipe"):selectedMaterial.getString(),NATIVE_PANEL_TEXT));
        ingredientArea.addWidget(line(8,26,52,16,()->text("inputs_short"),NATIVE_PANEL_TEXT)
                .setHoverTooltips(Component.translatable(PREFIX+"ingredients.click_input")));
        ingredientArea.addWidget(button(64,26,16,16,()->"+",()->openExtraDelivery(null)));
        ingredientArea.addWidget(line(94,26,72,16,()->inlinePageLabel("universal_outputs_label",outputPage,selectedOutputs.size()),NATIVE_PANEL_TEXT)
                .setHoverTooltips(Component.translatable(PREFIX+"universal_output_edit_hint")));
        inputSlots.setSelfPosition(8,46);inputSlots.setSizeWidth(54);
        outputSlots.setSelfPosition(94,46);outputSlots.setSizeWidth(54);
        ingredientArea.addWidget(inputSlots);ingredientArea.addWidget(outputSlots);
        addInlinePager(ingredientArea,64,46,()->inputPage,()->selectedInputs.size(),delta->changeStackPage(false,delta));
        addInlinePager(ingredientArea,150,46,()->outputPage,()->selectedOutputs.size(),delta->changeStackPage(true,delta));
        ingredientArea.addWidget(line(8,66,158,10,this::requiredFluidLabel,NATIVE_PANEL_TEXT));
        ingredientArea.addWidget(line(8,78,76,10,()->text("ingredients.reusable_short")+(selectedCatalysts.size()>1?" "+(catalystPage+1)+"/"+pages(selectedCatalysts.size(),1):""),NATIVE_PANEL_TEXT));
        catalystSlots.setSelfPosition(8,92);catalystSlots.setSizeHeight(18);ingredientArea.addWidget(catalystSlots);
        ingredientArea.addWidget(line(8,94,54,12,()->selectedId.isEmpty()?"":selectedCatalysts.isEmpty()?text("ingredients.none"):"",NATIVE_PANEL_TEXT));
        addInlinePager(ingredientArea,64,76,()->catalystPage,()->selectedCatalysts.size(),1,delta->{
            catalystPage=movePage(catalystPage,delta,selectedCatalysts.size(),1);refreshCatalystSlots();pagerVisibility.forEach(Runnable::run);
        });
        ingredientArea.addWidget(line(94,78,72,10,()->selectedReasonLabel,NATIVE_PANEL_TEXT));
        typeChoiceButton=button(94,92,72,18,()->text(selectedInFamily||selectedId.isEmpty()&&!universalFamily.isEmpty()?"pattern_unbind":universalFamily.isEmpty()?"pattern_bind":"pattern_rebind"),()->editPattern(selectedInFamily||selectedId.isEmpty()&&!universalFamily.isEmpty()?PatternEdit.UNBIND:PatternEdit.BIND));
        ingredientArea.addWidget(typeChoiceButton);
        view.addWidget(ingredientArea);
    }

    private String inlinePageLabel(String key,int current,int count) {
        return text(key)+(count>3?" "+(current+1)+"/"+pages(count,3):"");
    }

    private void addInlinePager(WidgetGroup parent,int x,int y,Supplier<Integer> current,Supplier<Integer> count,Consumer<Integer> change) {
        addInlinePager(parent,x,y,current,count,3,change);
    }
    private void addInlinePager(WidgetGroup parent,int x,int y,Supplier<Integer> current,Supplier<Integer> count,int pageSize,Consumer<Integer> change) {
        WidgetGroup pager=new WidgetGroup(x,y,16,18).setClientSideWidget();
        pager.addWidget(button(0,0,16,9,()->"<",()->change.accept(-1)));
        pager.addWidget(button(0,9,16,9,()->">",()->change.accept(1)));
        Runnable update=()->pager.setVisible(count.get()>pageSize).setActive(count.get()>pageSize);
        pagerVisibility.add(update);update.run();parent.addWidget(pager);
    }

    @Override
    public java.util.List<com.lowdragmc.lowdraglib.gui.ingredient.Target> getPhantomTargets(Object ingredient) {
        if (page == Page.UNIVERSAL) {
            if (supplyChooser.isVisible()) return supplyChooser.getPhantomTargets(ingredient);
            if (ruleEditor.isVisible()) return ruleEditor.getPhantomTargets(ingredient);
            if (universalOptions.isVisible()) return universalOptions.getPhantomTargets(ingredient);
        }
        return super.getPhantomTargets(ingredient);
    }

    private static List<GenericStack> deliveryPreview(Selection selection) {
        try { return org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryPolicy.extras(selection); }
        catch (IllegalArgumentException invalid) { return List.of(); }
    }
    private Selection outputSelection(Selection selection) {
        var candidate=machine.candidateForFamily(selectedId,selection.family());
        return selection.forMaterial(candidate==null?"":candidate.materialId().toString());
    }
    private void openPendingSync(){
        activeDeliveryKey=null;pendingSyncOpen=true;pendingSyncPage=clampPage(pendingSyncPage,pendingChanges.size(),2);
        supplyChooser.clearAllWidgets();supplyChooser.setSizeHeight(compact?294:268);
        supplyChooser.addWidget(new ButtonWidget(0,0,332,compact?294:268,new ColorRectTexture(0x88000000),click->{if(click.isRemote)closeSupplyChooser();}));
        supplyChooser.addWidget(new ButtonWidget(20,8,292,252,GuiTextures.BACKGROUND,click->{}));
        supplyChooser.addWidget(line(32,20,232,18,()->text("sync.title",pendingChanges.size()),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(282,20,18,18,()->"X",this::closeSupplyChooser));
        supplyChooser.addWidget(line(32,46,268,12,()->text(pendingChanges.isEmpty()?"sync.empty":"sync.persisted"),NATIVE_PANEL_TEXT));
        var entries=page(pendingChanges,pendingSyncPage,2);
        for(int i=0;i<entries.size();i++){
            var change=entries.get(i);int y=66+i*66;
            supplyChooser.addWidget(stackWidget(new GenericStack(change.key(),1),32,y,GuiTextures.SLOT));
            supplyChooser.addWidget(line(56,y,244,14,()->text(change.output()?"sync.output":"sync.input")+change.key().getDisplayName().getString(),NATIVE_PANEL_TEXT));
            supplyChooser.addWidget(line(56,y+16,244,12,()->text("sync.detail",change.material(),change.amount()==0?text("sync.clear_extra"):text("sync.extra_amount",change.amount()),text(change.ignored()?"sync.ignored":"sync.retained")),NATIVE_PANEL_TEXT));
            for(int mode=0;mode<2;mode++){boolean fixed=mode==1;final long revision=patternRevision;final int slot=universalSlot;
                supplyChooser.addWidget(button(32+mode*138,y+34,130,20,()->text(fixed?"sync.fixed":"sync.wildcard"),()->send(SYNC_PENDING,b->{b.writeVarLong(revision);b.writeVarInt(slot);b.writeUtf(change.id(),2048);b.writeBoolean(fixed);})));}
        }
        supplyChooser.addWidget(button(32,208,24,18,()->"<",()->{pendingSyncPage=movePage(pendingSyncPage,-1,pendingChanges.size(),2);openPendingSync();}));
        supplyChooser.addWidget(line(62,208,208,18,()->text("page",pendingSyncPage+1,pages(pendingChanges.size(),2)),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(276,208,24,18,()->">",()->{pendingSyncPage=movePage(pendingSyncPage,1,pendingChanges.size(),2);openPendingSync();}));
        supplyChooser.addWidget(line(32,238,268,12,()->draftMessage.isEmpty()?text("sync.apply_hint"):draftMessage,NATIVE_PANEL_TEXT));
        supplyChooser.setVisible(true).setActive(true);
    }
    private void openOutputSync(AEKey key) {
        activeDeliveryKey=null;
        final long revision=patternRevision;final int slot=universalSlot;final String recipeId=selectedId;
        supplyChooser.clearAllWidgets();int height=compact?294:268;supplyChooser.setSizeHeight(height);
        supplyChooser.addWidget(new ButtonWidget(0,0,332,height,new ColorRectTexture(0x88000000),click->{if(click.isRemote)closeSupplyChooser();}));
        supplyChooser.addWidget(new ButtonWidget(54,28,224,208,GuiTextures.BACKGROUND,click->{}));
        supplyChooser.addWidget(line(66,40,172,18,()->text("output.sync"),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(248,40,18,18,()->"X",this::closeSupplyChooser));
        supplyChooser.addWidget(line(66,68,200,24,()->text("output.sync_hint"),NATIVE_PANEL_TEXT));
        for(int mode=0;mode<2;mode++){final boolean fixed=mode==1;
            supplyChooser.addWidget(button(66,102+mode*30,200,22,()->text(fixed?"output.sync_fixed":"output.sync_material"),()->{
                send(SYNC_OUTPUT,b->{b.writeVarLong(revision);b.writeVarInt(slot);b.writeUtf(recipeId);writeSample(b,key);b.writeBoolean(fixed);});closeSupplyChooser();
            }));
        }
        supplyChooser.addWidget(line(66,174,200,40,()->text("output.sync_note"),NATIVE_PANEL_TEXT));
        supplyChooser.setVisible(true).setActive(true);
    }
    private void openInputSync(AEKey key) {
        activeDeliveryKey=null;
        final long revision=patternRevision;final int slot=universalSlot;final String recipeId=selectedId;
        supplyChooser.clearAllWidgets();int height=compact?294:268;supplyChooser.setSizeHeight(height);
        supplyChooser.addWidget(new ButtonWidget(0,0,332,height,new ColorRectTexture(0x88000000),click->{if(click.isRemote)closeSupplyChooser();}));
        supplyChooser.addWidget(new ButtonWidget(54,28,224,208,GuiTextures.BACKGROUND,click->{}));
        supplyChooser.addWidget(line(66,40,172,18,()->text("delivery.sync"),NATIVE_PANEL_TEXT));
        supplyChooser.addWidget(button(248,40,18,18,()->"X",this::closeSupplyChooser));
        supplyChooser.addWidget(line(66,68,200,24,()->text("delivery.sync_hint"),NATIVE_PANEL_TEXT));
        for(int mode=0;mode<2;mode++){final boolean fixed=mode==1;
            supplyChooser.addWidget(button(66,102+mode*30,200,22,()->text(fixed?"delivery.sync_fixed":"delivery.sync_material"),()->{
                send(SYNC_INPUT,b->{b.writeVarLong(revision);b.writeVarInt(slot);b.writeUtf(recipeId);writeSample(b,key);b.writeBoolean(fixed);});closeSupplyChooser();
            }));
        }
        supplyChooser.addWidget(line(66,174,200,40,()->text("delivery.sync_note"),NATIVE_PANEL_TEXT));
        supplyChooser.setVisible(true).setActive(true);
    }
    private static List<GenericStack> outputPreview(Selection selection) {
        try { return org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryPolicy.outputExtras(selection); }
        catch (IllegalArgumentException invalid) { return List.of(); }
    }

    public boolean hasModalOpen() {
        return page == Page.UNIVERSAL && (inputChooser.isVisible() || ruleEditor.isVisible() || universalOptions.isVisible() || supplyChooser.isVisible());
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (familyField == null) return;
        familyPlaceholder.setVisible(browsingFamily && familyQuery.isEmpty() && !familyField.isFocus());
        if (browsingFamily && familyQueryDelay > 0 && --familyQueryDelay == 0) {
            String query = familyQuery.strip();
            var matched = new BitSet(familyLabels.size());
            if (!query.isEmpty()) {
                for (int i = 0; i < familyLabels.size(); i++)
                    if (matchesLocalizedName(familyLabels.get(i).getString(),query)) matched.set(i);
            }
            send(FAMILY_QUERY, buffer -> {
                buffer.writeVarInt(universalSlot);
                buffer.writeVarInt(familyIndexVersion);
                buffer.writeUtf(query,96);
                buffer.writeLongArray(matched.toLongArray());
            });
        }
    }

    private void prepareFamilySearch(List<RecipePattern> recipes) {
        String family = draft().family();
        long generation = org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.getReloadGeneration();
        if (family.equals(indexedFamily) && generation == indexedGeneration && recipes.equals(indexedRecipes)) return;
        familyIndex = new FamilyRecipeSearch();
        for (var recipe : recipes) {
            var candidate = machine.candidateForFamily(recipe.id(),family);
            if (candidate == null) continue;
            var labels = new ArrayList<Component>();
            var ids = new ArrayList<String>();
            labels.add(candidate.materialName());
            ids.add(candidate.materialId().toString());
            var route = routeDescription(recipe);
            if (!route.getSiblings().isEmpty()) labels.add(route);
            for (var output : recipe.pattern().getOutputs()) {
                labels.add(output.what().getDisplayName());
                ids.add(output.what().getId().toString());
            }
            familyIndex.add(recipe.id(),labels,ids);
        }
        indexedFamily = family;
        indexedGeneration = generation;
        indexedRecipes = List.copyOf(recipes);
        familyLabels = familyIndex.labels();
        matchingFamilyLabels.clear();
        familyIndexVersion++;
    }

    private void showFamily() {
        if (draft().family().isEmpty()) return;
        browsingFamily = true;
        familyQuery = "";
        matchingFamilyLabels.clear();
        serverRecipeSample = null;
        serverOutputSample = null;
        search = "";
        localizedMaterials = Set.of();
        localizedTypes = Set.of();
        searchPage = 0;
        searchVisibility = draft().requiredInputs().isEmpty()?SearchVisibility.ON_FAMILY:SearchVisibility.ON_PATTERN;
        searchResetRevision++;
    }

    /** Compact held editor: three material rows and paired input/output previews. Modals keep their full page. */
    private void compactLayout(WidgetGroup view) {
        for (Widget widget : view.widgets) {
            if (widget == ruleEditor || widget == universalOptions || widget == supplyChooser || widget == ingredientArea || widget == inputChooser) continue;
            int x=widget.getSelfPositionX(), y=widget.getSelfPositionY();
            if (x==0 && y==0) widget.setSize(332,204);
            else if ((x==4 || x==154) && y==28) widget.setSizeHeight(118);
            else if (widget==recipeRows) { widget.setSelfPositionY(46); widget.setSizeHeight(84); }
            else if (x==8 && y==190) widget.setSelfPositionY(130);
            else if (x==160 && y==73) widget.setSelfPositionY(64);
            else if (x==160 && (y==76 || y==126)) {
                widget.setSelfPosition(y==76?160:246,68); widget.setSizeWidth(76);
            } else if (widget==inputSlots || widget==outputSlots) {
                widget.setSelfPosition(widget==inputSlots?160:246,82); widget.setSizeWidth(72);
            } else if (x==160 && (y==110 || y==160)) {
                widget.setSelfPosition(y==110?160:246,104); widget.setSizeWidth(72);
                var children=((WidgetGroup)widget).widgets;
                children.get(1).setSelfPositionX(56);
                children.get(2).setSizeWidth(32);
            } else if (y==181) widget.setSelfPositionY(124);
            else if (y==209) widget.setSelfPositionY(149);
            else if (y==207) widget.setSelfPositionY(147);
            else if (y==226) widget.setSelfPositionY(167);
            else if (y==249) widget.setSelfPositionY(188);
        }
    }

    private void openRuleEditor() {
        if (universalPatternSize < 0 || universalFamily.isEmpty()) return;
        ruleEditor.setDraftContext(universalSlot + "|" + universalFamily);
        ruleEditor.open(false, null, List.of(), savedAllow, savedDeny);
    }

    private void openRecipeAttributes() {
        AEKey reference = (inspectingOutput ? outputSample : recipeSample).sample();
        boolean cached = lastInspectedForRecipes && Objects.equals(reference, lastInspectedSample);
        ruleEditor.open(true, reference, cached ? lastAttributes : List.of(), "", "");
        if (reference != null && !cached) inspectSample(reference);
    }

    private void useRecipeSample(@Nullable AEKey reference, boolean output) {
        inspectingOutput = output;
        if (reference != null) {
            search = "";
            searchField.setCurrentString("");
        }
        send(RECIPE_SAMPLE, buffer -> { buffer.writeBoolean(output); writeSample(buffer, reference); });
    }

    private void inspectSample(@Nullable AEKey reference) {
        if (ruleEditor.isRecipePicker()) {
            (inspectingOutput ? outputSample : recipeSample).display(reference);
            useRecipeSample(reference, inspectingOutput);
        } else send(INSPECT_SAMPLE, buffer -> writeSample(buffer, reference));
    }

    private static void writeSample(FriendlyByteBuf buffer, @Nullable AEKey sample) {
        buffer.writeBoolean(sample != null);
        if (sample != null) GenericStack.writeBuffer(new GenericStack(sample, 1), buffer);
    }

    private static @Nullable AEKey readSample(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? GenericStack.readBuffer(buffer).what() : null;
    }

    private void searchAttribute(String expression) {
        search = expression;
        searchField.setCurrentString(expression);
        send(RECIPE_ATTRIBUTE, buffer -> buffer.writeUtf(expression, MERecipePatternFilter.MAX_EXPRESSION_LENGTH));
    }

    private void saveRules(String allow, String deny) {
        if (allow.length() > MaterialExpression.MAX_LENGTH || deny.length() > MaterialExpression.MAX_LENGTH) {
            ruleEditor.showFeedback(Component.translatable(PREFIX + "sample_rule_limit"));
            return;
        }
        send(UNIVERSAL_RULES, buffer -> {
            buffer.writeVarLong(patternRevision);
            buffer.writeVarInt(universalSlot);
            buffer.writeUtf(allow, MaterialExpression.MAX_LENGTH);
            buffer.writeUtf(deny, MaterialExpression.MAX_LENGTH);
        });
    }

    private SearchVisibility nextVisibility() {
        if (page == Page.UNIVERSAL) {
            return switch (searchVisibility) {
                case ALL -> SearchVisibility.ON_FAMILY;
                case ON_FAMILY -> SearchVisibility.ON_PATTERN;
                default -> SearchVisibility.ALL;
            };
        }
        return switch (searchVisibility) {
            case ALL -> SearchVisibility.PUBLISHED;
            case PUBLISHED -> SearchVisibility.BLOCKED;
            default -> SearchVisibility.ALL;
        };
    }

    private void createInputChooser() {
        inputChooser.addWidget(new ButtonWidget(0, 0, 170, 94, RecipePatternUiTextures.BACKGROUND, click -> {}));
        inputChooser.addWidget(line(4, 4, 138, 14, () -> text("choose_input"), TEXT_COLOR));
        inputChooser.addWidget(button(148, 4, 16, 12, () -> "X", () -> send(CLOSE_INPUT_OPTIONS, buffer -> {})));
        inputChooser.addWidget(inputOptionSlots);
        inputChooser.addWidget(line(4, 61, 162, 12, () -> inputChoice == null ? "" : inputChoice.getDisplayName().getString(), MUTED_COLOR));
        inputChooser.addWidget(button(4, 76, 82, 12, () -> text(page == Page.UNIVERSAL ? "delivery.reset_choice" : "reset_input"), () -> sendInputChoice(selectedId, selectedInputId, null)));
        addPager(inputChooser, 92, 76, 72, () -> inputOptionPage, () -> pages(inputOptionCount, INPUT_OPTION_PAGE_SIZE),
                delta -> send(INPUT_OPTIONS_PAGE, buffer -> {
                    buffer.writeUtf(selectedId, 1024);
                    buffer.writeUtf(selectedInputId, 64);
                    buffer.writeInt(delta);
                }));
        inputChooser.setVisible(false).setActive(false);
    }

    private void sendInputChoice(String recipeId, String slotId, @Nullable AEKey choice) {
        send(page == Page.UNIVERSAL ? CARD_ITEM_CHOICE : INPUT_CHOICE, buffer -> {
            if (page == Page.UNIVERSAL) {
                buffer.writeVarLong(patternRevision);
                buffer.writeVarInt(universalSlot);
            }
            buffer.writeUtf(recipeId, 1024);
            buffer.writeUtf(slotId, 64);
            buffer.writeBoolean(choice != null);
            if (choice != null) GenericStack.writeBuffer(new GenericStack(choice, 1), buffer);
        });
    }

    private WidgetGroup createRecipeListPage() {
        WidgetGroup view = new WidgetGroup(0, 0, 332, page == Page.UNIVERSAL ? 268 : 226).setClientSideWidget();
        view.addWidget(button(4, 0, 16, 12, () -> "<", () -> changeFilterMode(-1))
                .setHoverTooltips(Component.translatable(PREFIX + "lists_hint")));
        view.addWidget(button(312, 0, 16, 12, () -> ">", () -> changeFilterMode(1))
                .setHoverTooltips(Component.translatable(PREFIX + "lists_hint")));
        view.addWidget(new ImageWidget(24, 0, 284, 16,
                new TextTexture(() -> text("list_mode", text(isWhitelistMode() ? "whitelist_tab" : "blacklist_tab"), filterMode.ordinal() + 1))
                        .setColor(TEXT_COLOR).setDropShadow(false).setWidth(284))
                .setHoverTooltips(Component.translatable(PREFIX + "lists_hint")));
        view.addWidget(line(4, 18, 324, 12,
                () -> isWhitelistMode() ? text(whitelistActive ? "whitelist_active" : "whitelist_inactive", whitelistFilteredCount) : blockedCounts(), MUTED_COLOR));
        view.addWidget(button(4, 32, 74, 18, () -> Component.translatable(ruleTarget.translationKey()).getString(),
                () -> ruleTarget = Target.values()[(ruleTarget.ordinal() + 1) % Target.values().length]));
        TextFieldWidget field = new TextFieldWidget(82, 32, 206, 18, () -> ruleExpression, value -> ruleExpression = value)
                .setMaxStringLength(MERecipePatternFilter.MAX_EXPRESSION_LENGTH)
                .setBordered(false).setTextColor(0xFFFFFFFF).setBackground(RecipePatternUiTextures.INPUT_BACKGROUND);
        field.setHoverTooltips(Component.translatable(PREFIX + "rule_hint"), Component.translatable(PREFIX + "lists_hint"));
        enableMaterialDrop(field, value -> ruleExpression = value);
        view.addWidget(field);
        view.addWidget(button(292, 32, 36, 18, () -> text("add_rule"), () -> {
            if (ruleExpression.isBlank()) return;
            send(ADD_RULE, buffer -> {
                buffer.writeEnum(filterMode);
                buffer.writeEnum(ruleTarget);
                buffer.writeUtf(ruleExpression, MERecipePatternFilter.MAX_EXPRESSION_LENGTH);
            });
        }));
        view.addWidget(panel(4, 58, 160, 148));
        view.addWidget(panel(170, 58, 158, 148));
        view.addWidget(line(10, 61, 148, 12, () -> text(isWhitelistMode() ? "whitelist_rules_label" : "rules_label", ruleCount), TEXT_COLOR)
                .setHoverTooltips(Component.translatable(PREFIX + "rule_hint"), Component.translatable(PREFIX + "lists_hint")));
        view.addWidget(line(176, 61, 146, 12, () -> text(isWhitelistMode() ? "whitelist_label" : "blacklist_label", recipeListCount), TEXT_COLOR)
                .setHoverTooltips(Component.translatable(PREFIX + "individual_lists_hint"), Component.translatable(PREFIX + "lists_hint")));
        view.addWidget(ruleRows);
        view.addWidget(recipeListRows);
        addPager(view, 10, 190, 148, () -> rulePage, () -> pages(ruleCount, RULE_PAGE_SIZE), delta -> sendPage(RULE_PAGE, delta));
        addPager(view, 176, 190, 146, () -> recipeListPage, () -> pages(recipeListCount, RECIPE_LIST_PAGE_SIZE), delta -> sendPage(RECIPE_LIST_PAGE, delta));
        addFooter(view);
        return view;
    }

    private String blockedCounts() {
        return text("blocked_counts", chancedPrimaryCount, chancedInputCount, activeBlacklistCount, ruleFilteredCount, whitelistFilteredCount);
    }

    private List<String> getRecipeList() {
        return isWhitelistMode() ? machine.getWhitelistedRecipes() : machine.getBlacklistedRecipes();
    }

    private List<Rule> getRules() {
        return isWhitelistMode() ? machine.getWhitelistRules() : machine.getFilterRules();
    }

    private boolean isWhitelistMode() {
        return filterMode == FilterMode.WHITELIST;
    }

    private void changeFilterMode(int delta) {
        FilterMode target = FilterMode.values()[Math.floorMod(filterMode.ordinal() + Integer.signum(delta), FilterMode.values().length)];
        send(FILTER_MODE, buffer -> buffer.writeEnum(target));
    }

    private static Widget panel(int x, int y, int width, int height) {
        return new ImageWidget(x, y, width, height, RecipePatternUiTextures.BACKGROUND);
    }

    private static ImageWidget line(int x, int y, int width, int height, Supplier<String> label, int color) {
        return new RecipePatternLabelWidget(x, y, width, height, label, color, false);
    }

    private void addFooter(WidgetGroup view) {
        view.addWidget(line(4, 211, 324, 12, () -> text("recipe_counts", publishedCount, blockedCount, skippedCount), MUTED_COLOR));
    }

    private void addPager(WidgetGroup view, int x, int y, int width, Supplier<Integer> current, Supplier<Integer> total, Consumer<Integer> change) {
        WidgetGroup pager = new WidgetGroup(x, y, width, 12).setClientSideWidget();
        pager.addWidget(button(0, 0, 16, 12, () -> "<", () -> change.accept(-1)));
        pager.addWidget(button(width - 16, 0, 16, 12, () -> ">", () -> change.accept(1)));
        pager.addWidget(new ImageWidget(20, 0, width - 40, 12,
                new TextTexture(() -> text("page", current.get() + 1, total.get()))
                        .setColor(page == Page.UNIVERSAL ? NATIVE_PANEL_TEXT : MUTED_COLOR).setDropShadow(false).setWidth(width - 40)));
        if (page == Page.UNIVERSAL) {
            Runnable visibility = () -> pager.setVisible(total.get() > 1).setActive(total.get() > 1);
            pagerVisibility.add(visibility);
            visibility.run();
        }
        view.addWidget(pager);
    }

    private Widget button(int x, int y, int width, int height, Supplier<String> label, Runnable action) {
        return labeledButton(x, y, width, height, label, action,
                page == Page.UNIVERSAL ? ResourceBorderTexture.BUTTON_COMMON : RecipePatternUiTextures.BUTTON,
                page == Page.UNIVERSAL ? ResourceBorderTexture.BUTTON_COMMON : RecipePatternUiTextures.BUTTON_HOVER);
    }

    static Widget labeledButton(int x, int y, int width, int height, Supplier<String> label, Runnable action,
                                IGuiTexture background, IGuiTexture hover) {
        return new ButtonWidget(x, y, width, height,
                new GuiTextureGroup(background, buttonLabel(label, width)),
                click -> {
                    if (click.isRemote) action.run();
                }) {

            @Override
            protected void drawTooltipTexts(int mouseX, int mouseY) {
                if (!isMouseOverElement(mouseX, mouseY) || getHoverElement(mouseX, mouseY) != this ||
                        gui == null || gui.getModularUIGui() == null)
                    return;
                List<Component> hints = new ArrayList<>(tooltipTexts);
                String fullText = label.get();
                if (!fullText.isBlank() && hints.stream().noneMatch(hint -> hint.getString().equals(fullText))) {
                    hints.add(0, Component.literal(fullText));
                }
                if (!hints.isEmpty()) gui.getModularUIGui().setHoverTooltip(List.copyOf(hints), ItemStack.EMPTY, null, null);
            }
        }.setHoverTexture(new GuiTextureGroup(hover, buttonLabel(label, width)));
    }

    private static TextTexture buttonLabel(Supplier<String> label, int width) {
        return new TextTexture(label).setColor(RecipePatternUiTextures.BUTTON_TEXT_COLOR).setDropShadow(true)
                .setWidth(width - 4).setType(TextTexture.TextType.HIDE);
    }

    private void enableMaterialDrop(TextFieldWidget field, Consumer<String> responder) {
        field.setDraggingConsumer(value -> materialId(value) != null, null, null, value -> {
            String id = materialId(value);
            if (id != null) {
                responder.accept(id);
                field.setCurrentString(id);
            }
        });
    }

    private static String materialId(Object value) {
        if (value instanceof ItemStack item && !item.isEmpty()) return BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        if (value instanceof FluidStack fluid && !fluid.isEmpty()) return BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
        if (value instanceof net.minecraftforge.fluids.FluidStack fluid && !fluid.isEmpty()) return BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
        if (value instanceof GenericStack stack) return materialId(stack.what());
        if (value instanceof AEItemKey item) return BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        if (value instanceof AEFluidKey fluid) return BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
        return null;
    }

    private static Set<Integer> findLocalizedMaterials(String query) {
        if (query.isBlank()) return Set.of();
        // Registry indices keep even broad localized searches within one small client action packet.
        Set<Integer> ids = new LinkedHashSet<>();
        for (var item : BuiltInRegistries.ITEM) {
            if (ids.size() >= MAX_LOCALIZED_MATERIALS) break;
            if (matchesLocalizedName(item.getDescription().getString(), query)) ids.add(BuiltInRegistries.ITEM.getId(item) << 1);
        }
        for (var fluid : BuiltInRegistries.FLUID) {
            if (ids.size() >= MAX_LOCALIZED_MATERIALS) break;
            if (matchesLocalizedName(fluid.getFluidType().getDescription().getString(), query)) ids.add((BuiltInRegistries.FLUID.getId(fluid) << 1) | 1);
        }
        return ids;
    }

    private static boolean matchesLocalizedName(String name, String query) {
        return MERecipePatternFilter.matchesText(name, query) ||
                (query.indexOf('*') < 0 && query.indexOf('?') < 0 && UniversalSearch.contains(name, query.strip()));
    }

    private void send(int action, Consumer<FriendlyByteBuf> writer) {
        writeClientAction(action, writer);
    }

    private void submitSearch() {
        Set<Integer> materials = org.gtlcore.universalpatterns.integration.ae2.handler.UniversalMaterialRules.isExpression(search)
                ? Set.of() : findLocalizedMaterials(search);
        send(SEARCH, buffer -> {
            buffer.writeUtf(search, MERecipePatternFilter.MAX_EXPRESSION_LENGTH);
            buffer.writeVarInt(materials.size());
            materials.forEach(buffer::writeVarInt);
            var types = search.isBlank() || search.contains(":") || UniversalMaterialRules.isExpression(search)
                    ? List.<ResourceLocation>of() : GTRegistries.RECIPE_TYPES.values().stream()
                    .map(type -> type.registryName).filter(type -> matchesLocalizedName(
                            UniversalPatternFamily.typeLabel(type).getString(), search)).limit(1024).toList();
            buffer.writeVarInt(types.size());
            types.forEach(buffer::writeResourceLocation);
        });
    }

    private void sendPage(int action, int delta) {
        send(action, buffer -> {
            if (action == RECIPE_LIST_PAGE || action == RULE_PAGE) buffer.writeEnum(filterMode);
            buffer.writeInt(delta);
        });
    }

    private void sendBlacklist(String id, boolean excluded) {
        send(BLACKLIST, buffer -> {
            buffer.writeUtf(id, 1024);
            buffer.writeBoolean(excluded);
        });
    }

    private void sendWhitelist(String id, boolean allowed) {
        send(WHITELIST, buffer -> {
            buffer.writeUtf(id, 1024);
            buffer.writeBoolean(allowed);
        });
    }

    private void sendPrimary(String id, AEKey primary) {
        send(PRIMARY, buffer -> {
            buffer.writeUtf(id, 1024);
            buffer.writeBoolean(primary != null);
            if (primary != null) GenericStack.writeBuffer(new GenericStack(primary, 1), buffer);
        });
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        // Card-only hosts do not accept machine-global settings, including forged packets.
        if (machine.cardEditorOnly() && (id == BLACKLIST || id == WHITELIST || id == PRIMARY ||
                id == INPUT_CHOICE || id == ADD_RULE || id == REMOVE_RULE || id == FILTER_MODE)) return;
        // Actions retain IDs, complete rules and list modes, even if the view changes before they arrive.
        switch (id) {
            case SYNC_PENDING -> {
                long revision=buffer.readVarLong();int slot=buffer.readVarInt();String idKey=buffer.readUtf(2048);boolean fixed=buffer.readBoolean();
                if(page!=Page.UNIVERSAL||slot!=universalSlot||!acceptsDraftRevision(revision))return;
                var before=draft();String value=before.pendingSync().get(idKey);if(value==null)return;
                var change=org.gtlcore.universalpatterns.integration.ae2.handler.PendingPatternSync.parse(idKey,value);
                if(change==null||!change.family().equals(before.family()))return;
                var source=machine.candidateForFamily(change.recipeId(),change.family());if(source==null||!source.materialId().toString().equals(change.material())){ruleFeedback=Component.translatable(PREFIX+"sync.source_missing");return;}
                if(!fixed&&change.output()&&!change.key().equals(source.output())){ruleFeedback=Component.translatable(PREFIX+"output.no_match");return;}
                var family=machine.cachedRecipes().stream().filter(r->machine.candidateForFamily(r.id(),before.family())!=null).toList();
                var index=new org.gtlcore.universalpatterns.integration.ae2.handler.InputConstraintOptions.Index(family);
                var next=before;var seen=new HashSet<String>();int count=0,skipped=0;
                try{
                    for(var recipe:family){
                        if(machine.universalReason(before.withDeliveryFrom(Selection.empty()),recipe.id())>2)continue;
                        var target=machine.candidateForFamily(recipe.id(),before.family());
                        AEKey key=fixed?change.key():change.output()?target.output():org.gtlcore.universalpatterns.integration.ae2.handler.InputSyncMatcher.match(source,target,change.key(),index);
                        if(key==null){skipped++;continue;}
                        String identity=target.materialId()+"\n"+org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(key);if(!seen.add(identity))continue;
                        next=org.gtlcore.universalpatterns.integration.ae2.handler.PendingPatternSync.apply(next,change,target.materialId().toString(),key);count++;
                    }
                    if(skipped==0&&count>0)next=next.withPendingSync(idKey,null);
                    storeDraft(next);ruleFeedback=Component.translatable(PREFIX+"sync.result",count,skipped);
                }catch(IllegalArgumentException|ArithmeticException invalid){ruleFeedback=Component.translatable(PREFIX+"delivery.invalid");}
            }
            case REMOVE_EXTRA_INPUT, REMOVE_EXTRA_OUTPUT -> {
                long revision=buffer.readVarLong();int slot=buffer.readVarInt();String recipeId=buffer.readUtf(1024);AEKey key=readSample(buffer);
                if(page!=Page.UNIVERSAL||slot!=universalSlot||!acceptsDraftRevision(revision)||!recipeId.equals(selectedId)||key==null)return;
                var before=draft();var candidate=machine.candidateForFamily(recipeId,before.family());if(candidate==null)return;
                boolean output=id==REMOVE_EXTRA_OUTPUT;String material=candidate.materialId().toString();
                var next=org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryEdits.setExtra(before,material,key,0,output);
                boolean nativeKey=output?Arrays.stream(candidate.recipe().pattern().getOutputs()).anyMatch(v->v.what().equals(key)):org.gtlcore.universalpatterns.integration.ae2.handler.ConsumedFluidFilter.inputs(candidate.recipe()).contains(key);
                if(!nativeKey){String identity=material+"\n"+org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(key);
                    next=output?next.withIgnoredOutput(identity,false):next.withIgnoredInput(identity,false);
                    if(output&&next.primaryOutputs().containsKey(material)&&org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryEdits.sameKey(next.primaryOutputs().get(material),key))next=next.withPrimaryOutput(material,"");
                }
                if(storeItemEdit(next,key,output))ruleFeedback=Component.translatable(PREFIX+"delivery.deleted");
            }
            case SET_PERSONAL_SUPPLY -> {
                int mode = buffer.readVarInt();
                if (page != Page.UNIVERSAL || gui == null || !(gui.entityPlayer instanceof net.minecraft.server.level.ServerPlayer player)
                        || mode < -1 || mode > 3) return;
                UniversalPatternItem.setPersonalSupplyMode(player.getPersistentData(),mode);
            }
            case SET_CARD_DEFAULTS -> {
                long revision=buffer.readVarLong(); int slot=buffer.readVarInt();
                boolean keep=buffer.readBoolean(); int mode=buffer.readVarInt();
                if (page!=Page.UNIVERSAL || slot!=universalSlot || !acceptsDraftRevision(revision) || mode < -1 || mode > 3) return;
                storeDraft(draft().withDefaults(keep,mode));
            }
            case SET_MULTIPLIERS -> {
                long revision = buffer.readVarLong();
                int slot = buffer.readVarInt();
                String family = buffer.readUtf(512);
                int factor = buffer.readVarInt();
                int batches = buffer.readVarInt();
                if (page != Page.UNIVERSAL || slot != universalSlot || !acceptsDraftRevision(revision)
                        || factor < 1 || batches < 1 || family.isEmpty() || !family.equals(draft().family())) return;
                var next = draft().withMultipliers(factor,batches);
                if (validMultipliers(next)) storeDraft(next);
            }
            case SET_OUTPUT_PRIMARY -> {
                long revision=buffer.readVarLong();int slot=buffer.readVarInt();String recipeId=buffer.readUtf(1024);AEKey key=readSample(buffer);
                if(page!=Page.UNIVERSAL||slot!=universalSlot||!acceptsDraftRevision(revision)||!recipeId.equals(selectedId)||key==null)return;
                var before=draft();var candidate=machine.candidateForFamily(recipeId,before.family());if(candidate==null)return;
                var recipe=machine.getRecipePattern(recipeId);var effective=outputSelection(before);
                if(Arrays.stream(recipe.pattern().getOutputs()).noneMatch(v->v.what().equals(key))&&outputPreview(effective).stream().noneMatch(v->v.what().equals(key)))return;
                String scope=candidate.materialId()+"\n";
                var next=before.withPrimaryOutput(candidate.materialId().toString(),org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryPolicy.encode(key)).withIgnoredOutput(scope+org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(key),false);
                storeItemEdit(next,key,true);
            }
            case SYNC_INPUT -> {
                long revision=buffer.readVarLong();int slot=buffer.readVarInt();String recipeId=buffer.readUtf(1024);AEKey key=readSample(buffer);boolean fixed=buffer.readBoolean();
                if(page!=Page.UNIVERSAL||slot!=universalSlot||!acceptsDraftRevision(revision)||!recipeId.equals(selectedId)||key==null)return;
                var before=draft();var source=machine.candidateForFamily(recipeId,before.family());if(source==null)return;
                var current=before.forMaterial(source.materialId().toString());
                boolean ignored=current.ignoredInputs().contains(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(key));
                long amount;try{amount=deliveryPreview(current).stream().filter(v->v.what().equals(key)).mapToLong(GenericStack::amount).reduce(0L,Math::addExact);}catch(ArithmeticException invalid){ruleFeedback=Component.translatable(PREFIX+"delivery.invalid");return;}
                var family=machine.cachedRecipes().stream().filter(r->machine.candidateForFamily(r.id(),before.family())!=null).toList();
                var index=new org.gtlcore.universalpatterns.integration.ae2.handler.InputConstraintOptions.Index(family);
                var next=before;int count=0,skipped=0;var seen=new HashSet<String>();
                try {
                    for(var recipe:family){
                        var target=machine.candidateForFamily(recipe.id(),before.family());
                        if(machine.universalReason(before.withDeliveryFrom(Selection.empty()),recipe.id())>2)continue;
                        AEKey input=fixed?key:org.gtlcore.universalpatterns.integration.ae2.handler.InputSyncMatcher.match(source,target,key,index);
                        if(input==null){skipped++;continue;}
                        String scope=target.materialId()+"\n";String identity=scope+org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(input);
                        if(!seen.add(identity))continue;
                        next=org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryEdits.setExtra(next,target.materialId().toString(),input,amount,false).withIgnoredInput(identity,ignored);count++;
                    }
                    storeDraft(next);ruleFeedback=Component.translatable(PREFIX+"delivery.synced",count,skipped);
                }catch(IllegalArgumentException invalid){ruleFeedback=Component.translatable(PREFIX+"delivery.invalid");}
            }
            case SYNC_OUTPUT -> {
                long revision=buffer.readVarLong();int slot=buffer.readVarInt();String recipeId=buffer.readUtf(1024);AEKey key=readSample(buffer);boolean fixed=buffer.readBoolean();
                if(page!=Page.UNIVERSAL||slot!=universalSlot||!acceptsDraftRevision(revision)||!recipeId.equals(selectedId)||key==null)return;
                var before=draft();var source=machine.candidateForFamily(recipeId,before.family());if(source==null)return;
                if(!fixed&&!key.equals(source.output())){ruleFeedback=Component.translatable(PREFIX+"output.no_match");return;}
                var current=before.forMaterial(source.materialId().toString());
                boolean ignored=current.ignoredOutputs().contains(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(key));
                long amount=current.extraOutputs().getOrDefault(org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryPolicy.encode(key),0L);
                var next=before;int count=0;var seen=new HashSet<String>();
                try {
                    for(var recipe:machine.cachedRecipes()) {
                        var target=machine.candidateForFamily(recipe.id(),before.family());if(target==null)continue;
                        int state=machine.universalReason(before.withDeliveryFrom(Selection.empty()),recipe.id());if(state>2)continue;
                        AEKey output=fixed?key:target.output();if(output==null)continue;
                        String scope=target.materialId()+"\n";String identity=scope+org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(output);
                        if(!seen.add(identity))continue;
                        next=org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryEdits.setExtra(next,target.materialId().toString(),output,0,true).withIgnoredOutput(identity,ignored);
                        if(current.primaryOutputs().containsValue(org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryPolicy.encode(key)))next=next.withPrimaryOutput(target.materialId().toString(),org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryPolicy.encode(output));count++;
                    }
                    storeDraft(next);ruleFeedback=Component.translatable(PREFIX+"output.synced",count);
                }catch(IllegalArgumentException invalid){ruleFeedback=Component.translatable(PREFIX+"output.invalid");}
            }
            case SET_OUTPUT_IGNORE, SET_OUTPUT_EXTRA -> {
                long revision=buffer.readVarLong();int slot=buffer.readVarInt();AEKey key=readSample(buffer);
                boolean ignore=id==SET_OUTPUT_IGNORE&&buffer.readBoolean();long amount=id==SET_OUTPUT_EXTRA?buffer.readVarLong():0;
                if(page!=Page.UNIVERSAL||slot!=universalSlot||!acceptsDraftRevision(revision)||key==null||draft().family().isEmpty()||amount<0||(id==SET_OUTPUT_EXTRA&&amount>0))return;
                var recipe=machine.getRecipePattern(selectedId);
                if(recipe==null||machine.candidateForFamily(selectedId,draft().family())==null||!(key instanceof AEItemKey||key instanceof AEFluidKey))return;
                if(id==SET_OUTPUT_IGNORE&&Arrays.stream(recipe.pattern().getOutputs()).noneMatch(v->v.what().equals(key))&&outputPreview(outputSelection(draft())).stream().noneMatch(v->v.what().equals(key)))return;
                try {
                    var before=draft();String scope=machine.candidateForFamily(selectedId,before.family()).materialId()+"\n";var identity=scope+org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(key);
                    if(id==SET_OUTPUT_IGNORE)storeItemEdit(before.withIgnoredOutput(identity,ignore),key,true);
                    else {
                        var next=org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryEdits.setExtra(before,scope.substring(0,scope.length()-1),key,amount,true);
                        if(amount>0)next=next.withIgnoredOutput(identity,false);
                        storeItemEdit(next,key,true);
                    }
                }catch(IllegalArgumentException invalid){ruleFeedback=Component.translatable(PREFIX+"output.invalid");}
            }
            case RESET_DELIVERY -> {
                long revision=buffer.readVarLong();int slot=buffer.readVarInt();
                if(page!=Page.UNIVERSAL||slot!=universalSlot||!acceptsDraftRevision(revision))return;
                var before = draft();
                storeDraft(before.withDeliveryFrom(Selection.empty()).withDefaults(before.keepByproducts(),before.defaultSupplyMode()).withItemChoices(before.itemChoices()));
            }
            case SET_DELIVERY_IGNORE, SET_DELIVERY_EXTRA -> {
                long revision=buffer.readVarLong();int slot=buffer.readVarInt();AEKey key=readSample(buffer);
                boolean ignore=id==SET_DELIVERY_IGNORE&&buffer.readBoolean();long amount=id==SET_DELIVERY_EXTRA?buffer.readVarLong():0;
                if(page!=Page.UNIVERSAL||slot!=universalSlot||!acceptsDraftRevision(revision)||!(key instanceof AEItemKey||key instanceof AEFluidKey)||draft().family().isEmpty())return;
                try{
                    var candidate=machine.candidateForFamily(selectedId,draft().family());if(candidate==null)return;
                    var before=draft().scopeInputs(candidate.materialId().toString());String scope=candidate.materialId()+"\n";
                    if(id==SET_DELIVERY_IGNORE){
                        if(org.gtlcore.universalpatterns.integration.ae2.handler.ConsumedFluidFilter.inputs(machine.getRecipePattern(selectedId)).stream().noneMatch(v->v.equals(key))&&org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryPolicy.extras(before.forMaterial(candidate.materialId().toString())).stream().noneMatch(v->v.what().equals(key)))return;
                        storeItemEdit(before.withIgnoredInput(scope+org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(key),ignore),key,false);
                    }else{
                        if(amount<0)return;
                        var next=org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryEdits.setExtra(before,candidate.materialId().toString(),key,amount,false);
                        if(amount>0)next=next.withIgnoredInput(scope+org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(key),false);
                        storeItemEdit(next,key,false);
                    }
                }catch(IllegalArgumentException invalid){ruleFeedback=Component.translatable(PREFIX+"delivery.invalid");}
            }
            case SET_MATERIAL_SELECTED -> {
                long revision=buffer.readVarLong();int slot=buffer.readVarInt();String recipeId=buffer.readUtf(1024);boolean selected=buffer.readBoolean();
                if(page!=Page.UNIVERSAL || slot!=universalSlot || !acceptsDraftRevision(revision))return;
                var candidate=machine.candidateForFamily(recipeId,draft().family());
                int state=reason(recipeId);
                if(candidate==null || !(state>=0 && state<=7 && state!=3))return;
                editDraft(selected?PatternEdit.ADD:PatternEdit.REMOVE,List.of(recipeId));
                selectedId = recipeId;
                selectedInputId = "";
                focusToggledRecipe = recipeId;
                if (!selected && searchVisibility==SearchVisibility.ON_PATTERN) searchVisibility=SearchVisibility.ON_FAMILY;
            }
            case TOGGLE_VARIANT_GROUPS -> {if(page!=Page.UNIVERSAL)return;groupVariants=!groupVariants;searchPage=0;}
            case REPLACE_FIXED_INPUT -> {
                long revision=buffer.readVarLong();int slot=buffer.readVarInt();String recipeId=buffer.readUtf(1024);AEKey anchor=readSample(buffer),key=readSample(buffer);
                if(page!=Page.UNIVERSAL || slot!=universalSlot || !acceptsDraftRevision(revision) || !recipeId.equals(selectedId) || anchor==null || draft().family().isEmpty())return;
                var current=machine.getRecipePattern(recipeId);
                var family=machine.cachedRecipes().stream().filter(r->machine.candidateForFamily(r.id(),draft().family())!=null).toList();
                var candidates=org.gtlcore.universalpatterns.integration.ae2.handler.InputConstraintOptions.candidates(current,family,anchor);
                if(candidates.isEmpty() || (key!=null && !candidates.contains(key)))return;
                if(familyFluidOptions().stream().noneMatch(stack->stack.what().equals(anchor)))return;
                try {
                    var next=draft();
                    var replacement=org.gtlcore.universalpatterns.integration.ae2.handler.InputConstraintOptions.replace(next.requiredInputs(),candidates,key);
                    for(var previous:next.requiredInputs())if(!replacement.contains(previous))next=next.withFixedInput(previous,false);
                    for(var value:replacement)next=next.withFixedInput(value,true);
                    storeDraft(next);
                }catch(IllegalArgumentException invalid){return;}
                showFamily();searchVisibility=SearchVisibility.ON_PATTERN;
            }
            case SET_REQUIRED_FLUID -> {
                long revision=buffer.readVarLong();int slot=buffer.readVarInt();AEKey key=readSample(buffer);boolean fixed=buffer.readBoolean();
                if(page!=Page.UNIVERSAL || slot!=universalSlot || !acceptsDraftRevision(revision) || draft().family().isEmpty())return;
                if(key!=null && familyFluidOptions().stream().noneMatch(stack->stack.what().equals(key)))return;
                try { storeDraft(key==null?draft().withoutFixedInputs():draft().withFixedInput(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(key),fixed)); }
                catch(IllegalArgumentException invalid){return;}
                showFamily();
                if(key!=null)searchVisibility=SearchVisibility.ON_PATTERN;
            }
            case SET_CATALYST_MODE -> {
                long revision=buffer.readVarLong();int slot=buffer.readVarInt();
                String recipeId=buffer.readUtf(1024);AEKey key=readSample(buffer);int mode=buffer.readVarInt();
                if(page!=Page.UNIVERSAL || slot!=universalSlot || !acceptsDraftRevision(revision) || key==null || mode< -1 || mode>3)return;
                var recipe=machine.getRecipePattern(recipeId);
                if(recipe==null || !recipeId.equals(selectedId) || recipe.nonConsumedInputs().stream().flatMap(Collection::stream).noneMatch(key::equals))return;
                try { storeDraft(draft().withCatalystMode(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(key),mode)); }
                catch(IllegalArgumentException invalid){ruleFeedback=Component.translatable(PREFIX+"pattern_invalid",invalid.getMessage());}
            }
            case SET_CATALYST_GROUP -> {
                long revision=buffer.readVarLong();int slot=buffer.readVarInt();
                String recipeId=buffer.readUtf(1024);AEKey key=readSample(buffer);int mode=buffer.readVarInt();
                if(page!=Page.UNIVERSAL || slot!=universalSlot || !acceptsDraftRevision(revision) || key==null || mode< -1 || mode>3)return;
                var recipe=machine.getRecipePattern(recipeId);
                if(recipe==null || !recipeId.equals(selectedId) || recipe.nonConsumedInputs().stream().flatMap(Collection::stream).noneMatch(key::equals))return;
                try { var before=draft();var source=machine.candidateForFamily(recipeId,before.family());
                    var targets=machine.cachedRecipes().stream().map(r->machine.candidateForFamily(r.id(),before.family())).filter(Objects::nonNull).toList();
                    storeDraft(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystGroupSupply.apply(before,source,targets,key,mode)); }
                catch(IllegalArgumentException invalid){ruleFeedback=Component.translatable(PREFIX+"pattern_invalid",invalid.getMessage());}
            }
            case SHOW_FAMILY -> {
                if (page != Page.UNIVERSAL) return;
                showFamily();
            }
            case FAMILY_QUERY -> {
                int slot = buffer.readVarInt();
                int version = buffer.readVarInt();
                String query = buffer.readUtf(96);
                if (page != Page.UNIVERSAL || !browsingFamily || slot != universalSlot || familyIndex == null || version != familyIndexVersion) return;
                matchingFamilyLabels = BitSet.valueOf(buffer.readLongArray(null, (familyLabels.size() + 63) / 64));
                familyQuery = query;
                searchPage = 0;
                focusToggledRecipe = "";
                selectedInputId = "";
            }
            case SEARCH -> {
                browsingFamily = false;
                search = buffer.readUtf(MERecipePatternFilter.MAX_EXPRESSION_LENGTH);
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_LOCALIZED_MATERIALS) return;
                Set<String> ids = new HashSet<>();
                for (int index = 0; index < count; index++) {
                    int registryId = buffer.readVarInt();
                    if (registryId < 0) continue;
                    if ((registryId & 1) == 0) {
                        var item = BuiltInRegistries.ITEM.byId(registryId >>> 1);
                        ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
                    } else {
                        var fluid = BuiltInRegistries.FLUID.byId(registryId >>> 1);
                        ids.add(BuiltInRegistries.FLUID.getKey(fluid).toString());
                    }
                }
                localizedMaterials = ids;
                int typeCount = buffer.readVarInt();
                if (typeCount < 0 || typeCount > 1024) return;
                var types = new HashSet<ResourceLocation>();
                for (int i = 0; i < typeCount; i++) types.add(buffer.readResourceLocation());
                localizedTypes = Set.copyOf(types);
                if (page == Page.UNIVERSAL) searchVisibility = SearchVisibility.ALL;
                searchPage = 0;
                selectedId = "";
                selectedInputId = "";
            }
            case SEARCH_VISIBILITY -> {
                SearchVisibility visibility = buffer.readEnum(SearchVisibility.class);
                if (page != Page.SEARCH && page != Page.UNIVERSAL) return;
                if (page == Page.SEARCH && (visibility == SearchVisibility.ON_FAMILY || visibility == SearchVisibility.ON_PATTERN)) return;
                searchVisibility = visibility;
                if (page == Page.UNIVERSAL && (visibility == SearchVisibility.ON_FAMILY || visibility == SearchVisibility.ON_PATTERN)) {
                    showFamily(); searchVisibility = visibility;
                }
                searchPage = 0;
                selectedId = "";
                selectedInputId = "";
            }
            case SEARCH_PAGE -> searchPage = movePage(searchPage, buffer.readInt(), searchCount, resultPageSize());
            case SELECT -> {
                String recipeId = buffer.readUtf(1024);
                if (machine.getRecipePattern(recipeId) != null) selectedId = recipeId;
                selectedInputId = "";
            }
            case INPUT_OPTIONS -> {
                String recipeId = buffer.readUtf(1024);
                String slotId = buffer.readUtf(64);
                RecipePattern pattern = machine.getRecipePattern(recipeId);
                if ((page != Page.SEARCH && page != Page.UNIVERSAL) || !recipeId.equals(selectedId) || pattern == null) return;
                InputSlot slot = machine.getInputSlot(pattern, slotId);
                if (slot == null || slot.alternatives().size() <= 1 || page == Page.UNIVERSAL && !ItemSubstitutions.selectable(slot)) return;
                selectedInputId = slotId;
                inputOptionPage = 0;
            }
            case CARD_ITEM_CHOICE -> {
                long revision = buffer.readVarLong();
                int cardSlot = buffer.readVarInt();
                String recipeId = buffer.readUtf(1024);
                String slotId = buffer.readUtf(64);
                AEKey choice = readSample(buffer);
                if (page != Page.UNIVERSAL || gui == null || !(gui.entityPlayer instanceof net.minecraft.server.level.ServerPlayer player)
                        || cardSlot != universalSlot || !acceptsDraftRevision(revision) || !recipeId.equals(selectedId)
                        || !slotId.equals(selectedInputId) || machine.candidateForFamily(recipeId,draft().family()) == null) return;
                var recipe = machine.getRecipePattern(recipeId);
                var slot = recipe == null ? null : machine.getInputSlot(recipe, slotId);
                if (slot == null) return;
                try {
                    var next = draft().withItemChoices(ItemSubstitutions.change(draft().itemChoices(), slot, choice));
                    var data = player.getPersistentData();
                    var personal = ItemSubstitutions.change(UniversalPatternItem.personalItemChoices(data), slot, choice);
                    if (storeDraft(next)) {
                        UniversalPatternItem.savePersonalItemChoices(data, personal);
                        selectedInputId = "";
                    }
                } catch (IllegalArgumentException invalid) {
                    ruleFeedback = Component.translatable(PREFIX + "delivery.invalid");
                }
            }
            case INPUT_CHOICE -> {
                String recipeId = buffer.readUtf(1024);
                String slotId = buffer.readUtf(64);
                GenericStack choice = buffer.readBoolean() ? GenericStack.readBuffer(buffer) : null;
                if (page != Page.SEARCH || !recipeId.equals(selectedId)) return;
                machine.setInputChoice(recipeId, slotId, choice == null ? null : choice.what());
                selectedInputId = "";
            }
            case INPUT_OPTIONS_PAGE -> {
                String recipeId = buffer.readUtf(1024);
                String slotId = buffer.readUtf(64);
                int delta = buffer.readInt();
                RecipePattern pattern = machine.getRecipePattern(recipeId);
                if ((page != Page.SEARCH && page != Page.UNIVERSAL) || !recipeId.equals(selectedId) || !slotId.equals(selectedInputId) || pattern == null) return;
                InputSlot slot = machine.getInputSlot(pattern, slotId);
                if (slot == null) return;
                inputOptionPage = movePage(inputOptionPage, delta, slot.alternatives().size(), INPUT_OPTION_PAGE_SIZE);
            }
            case CLOSE_INPUT_OPTIONS -> selectedInputId = "";
            case BLACKLIST -> machine.setRecipeBlacklisted(buffer.readUtf(1024), buffer.readBoolean());
            case WHITELIST -> machine.setRecipeWhitelisted(buffer.readUtf(1024), buffer.readBoolean());
            case EDIT_UNIVERSAL -> {
                long editRevision = buffer.readVarLong();
                int slot = buffer.readVarInt();
                PatternEdit edit = buffer.readEnum(PatternEdit.class);
                String recipeId = buffer.readUtf(1024);
                if (page != Page.UNIVERSAL || slot != universalSlot || !machine.hasUniversalPattern(slot)) return;
                if (!acceptsDraftRevision(editRevision)) {
                    ruleFeedback = Component.translatable(PREFIX + "draft_conflict");
                    dirty = true;
                    return;
                }
                if (edit == PatternEdit.ADD_RESULTS || edit == PatternEdit.REMOVE_RESULTS) {
                    refreshView();
                    editDraft(edit, searchResults.stream().map(RecipePattern::id).toList());
                } else if (edit == PatternEdit.APPLY || edit == PatternEdit.REVERT || recipeId.equals(selectedId)) {
                    editDraft(edit, List.of(recipeId));
                }
            }
            case UNIVERSAL_SLOT -> {
                int slot = buffer.readVarInt();
                if (page != Page.UNIVERSAL || slot < 0 || slot >= machine.universalPatternSlots()) return;
                universalSlot = slot;
                originals.remove(slot);
                familyQuery = ""; matchingFamilyLabels.clear(); searchResetRevision++;
                searchVisibility = org.gtlcore.universalpatterns.common.item.UniversalPatternItem.readDraft(machine.universalCardSnapshot(slot)).family().isEmpty() ? SearchVisibility.ALL : SearchVisibility.ON_FAMILY;
                searchPage = 0;
                selectedId = "";
                selectedInputId = "";
                ruleFeedback = Component.empty();
            }
            case RECIPE_SAMPLE, INSPECT_SAMPLE -> {
                if (page != Page.UNIVERSAL) return;
                boolean output = id == RECIPE_SAMPLE && buffer.readBoolean();
                AEKey reference = readSample(buffer);
                if (reference != null && !(reference instanceof AEItemKey || reference instanceof AEFluidKey)) return;
                if (id == RECIPE_SAMPLE) {
                    browsingFamily = false;
                    if (output) serverOutputSample = reference; else serverRecipeSample = reference;
                    if (reference != null) {
                        search = "";
                        localizedMaterials = Set.of();
                        localizedTypes = Set.of();
                    }
                    searchPage = 0;
                    selectedId = "";
                    selectedInputId = "";
                    searchVisibility = SearchVisibility.ALL;
                }
                boolean forRecipes = id == RECIPE_SAMPLE;
                List<String> attributes = reference == null ? List.of() : machine.universalSampleAttributes(reference, forRecipes).stream()
                        .filter(term -> term.length() <= MERecipePatternFilter.MAX_EXPRESSION_LENGTH).limit(UniversalAttributeEditor.MAX_ATTRIBUTES).toList();
                writeUpdateInfo(SAMPLE_ATTRIBUTES, reply -> {
                    reply.writeBoolean(forRecipes);
                    writeSample(reply, reference);
                    reply.writeVarInt(attributes.size());
                    attributes.forEach(term -> reply.writeUtf(term, MaterialExpression.MAX_LENGTH));
                });
            }
            case RECIPE_ATTRIBUTE -> {
                if (page != Page.UNIVERSAL || (serverRecipeSample == null && serverOutputSample == null)) return;
                search = buffer.readUtf(MERecipePatternFilter.MAX_EXPRESSION_LENGTH);
                searchPage = 0;
                selectedId = "";
                selectedInputId = "";
            }
            case UNIVERSAL_RULES -> {
                long revision = buffer.readVarLong();
                int slot = buffer.readVarInt();
                String allow = buffer.readUtf(MaterialExpression.MAX_LENGTH);
                String deny = buffer.readUtf(MaterialExpression.MAX_LENGTH);
                if (page != Page.UNIVERSAL || slot != universalSlot || !machine.hasUniversalPattern(slot)) return;
                if (!acceptsDraftRevision(revision)) {
                    ruleFeedback = Component.translatable(PREFIX + "pattern_changed");
                } else {
                    try {
                        var before = draft();
                        storeDraft(new Selection(before.family(), before.materials(), before.excluded(), allow, deny, before.recipes(), before.externalCatalysts(), before.catalystModes(), before.requiredFluid(),before.fixedInputs()).withDeliveryFrom(before));
                        UniversalMaterialRules.compile(allow, machine.getLevel());
                        UniversalMaterialRules.compile(deny, machine.getLevel());
                        ruleFeedback = Component.translatable(PREFIX + "pattern_rules_saved");
                    } catch (IllegalArgumentException error) {
                        ruleFeedback = Component.translatable(PREFIX + "pattern_invalid", error.getMessage());
                    }
                }
            }
            case PRIMARY -> {
                String recipeId = buffer.readUtf(1024);
                GenericStack primary = buffer.readBoolean() ? GenericStack.readBuffer(buffer) : null;
                machine.setPrimaryOutput(recipeId, primary == null ? null : primary.what());
            }
            case ADD_RULE -> {
                FilterMode mode = buffer.readEnum(FilterMode.class);
                Target target = buffer.readEnum(Target.class);
                String expression = buffer.readUtf(MERecipePatternFilter.MAX_EXPRESSION_LENGTH);
                if (page != Page.FILTERS) return;
                if (mode == FilterMode.WHITELIST) machine.addWhitelistRule(target, expression);
                else machine.addFilterRule(target, expression);
            }
            case REMOVE_RULE -> {
                FilterMode mode = buffer.readEnum(FilterMode.class);
                Rule rule = new Rule(buffer.readEnum(Target.class), buffer.readUtf(MERecipePatternFilter.MAX_EXPRESSION_LENGTH));
                if (page != Page.FILTERS) return;
                if (mode == FilterMode.WHITELIST) machine.removeWhitelistRule(rule);
                else machine.removeFilterRule(rule);
            }
            case RECIPE_LIST_PAGE -> {
                FilterMode mode = buffer.readEnum(FilterMode.class);
                int delta = buffer.readInt();
                if (page == Page.FILTERS && mode == filterMode) recipeListPage = movePage(recipeListPage, delta, recipeListCount, RECIPE_LIST_PAGE_SIZE);
            }
            case RULE_PAGE -> {
                FilterMode mode = buffer.readEnum(FilterMode.class);
                int delta = buffer.readInt();
                if (page == Page.FILTERS && mode == filterMode) rulePage = movePage(rulePage, delta, ruleCount, RULE_PAGE_SIZE);
            }
            case FILTER_MODE -> {
                FilterMode mode = buffer.readEnum(FilterMode.class);
                if (page == Page.FILTERS && mode != filterMode) {
                    filterMode = mode;
                    recipeListPage = 0;
                    rulePage = 0;
                }
            }
            default -> {
                super.handleClientAction(id, buffer);
                return;
            }
        }
        dirty = true;
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        sentFamilyIndexVersion = -1;
        if (page == Page.UNIVERSAL) {
            for (int slot = 0; slot < machine.universalPatternSlots(); slot++) {
                if (machine.hasUniversalPattern(slot)) {
                    universalSlot = slot;
                    if (!org.gtlcore.universalpatterns.common.item.UniversalPatternItem.readDraft(machine.universalCardSnapshot(slot)).family().isEmpty()) showFamily();
                    else searchVisibility = SearchVisibility.ALL;
                    break;
                }
            }
        }
        refreshView();
        writeSnapshot(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        readSnapshot(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        // All child widgets are client-side views. Their updates are supplied by this bounded snapshot.
        if (dirty || lastRevision != machine.getConfigurationRevision()) {
            refreshView();
            writeUpdateInfo(SNAPSHOT, this::writeSnapshot);
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == SNAPSHOT) readSnapshot(buffer);
        else if (id == SAMPLE_ATTRIBUTES && page == Page.UNIVERSAL) {
            lastInspectedForRecipes = buffer.readBoolean();
            AEKey reference = readSample(buffer);
            int count = buffer.readVarInt();
            if (count < 0 || count > UniversalAttributeEditor.MAX_ATTRIBUTES) return;
            var discovered = new ArrayList<String>(count);
            for (int index = 0; index < count; index++) discovered.add(buffer.readUtf(MaterialExpression.MAX_LENGTH));
            lastInspectedSample = reference;
            lastAttributes = List.copyOf(discovered);
            if (ruleEditor.isRecipePicker() == lastInspectedForRecipes) ruleEditor.showAttributes(reference, lastAttributes);
        } else super.readUpdateInfo(id, buffer);
    }

    private void refreshView() {
        if (page == Page.SEARCH || page == Page.UNIVERSAL) {
            searchFeedback = Component.empty();
            try {
                boolean emptyQuery = page == Page.UNIVERSAL && !browsingFamily && search.isBlank() && serverRecipeSample == null && serverOutputSample == null;
                boolean expression = page == Page.UNIVERSAL && UniversalMaterialRules.isExpression(search);
                var matches = emptyQuery ? List.<RecipePattern>of() : browsingFamily ? machine.familyRecipes(draft().family()) : machine.searchRecipes(expression ? "" : search, localizedMaterials, page == Page.UNIVERSAL ? universalSlot : -1);
                if (browsingFamily) {
                    prepareFamilySearch(matches);
                    if (!familyQuery.isBlank()) matches = matches.stream().filter(recipe ->
                            familyIndex.matches(recipe.id(),familyQuery,matchingFamilyLabels)).toList();
                }
                if (!browsingFamily && !expression && !search.isBlank() && !localizedTypes.isEmpty()) {
                    var combined = new LinkedHashMap<String,RecipePattern>();
                    for (var match : matches) combined.put(match.id(),match);
                    for (var recipe : machine.cachedRecipes())
                        if (localizedTypes.contains(recipe.recipe().recipeType.registryName)) combined.putIfAbsent(recipe.id(),recipe);
                    matches = List.copyOf(combined.values());
                }
                if (expression) {
                    var predicate = UniversalMaterialRules.compile(search, machine.getLevel());
                    matches = matches.stream().filter(pattern -> {
                        var candidate = draftCandidate(pattern.id());
                        return candidate != null && predicate.test(candidate);
                    }).toList();
                }
                if (page == Page.UNIVERSAL && !emptyQuery) {
                    matches = matches.stream().filter(pattern ->
                            org.gtlcore.universalpatterns.integration.ae2.handler.RecipeSampleMatch.matches(pattern, serverRecipeSample, serverOutputSample)).toList();
                }
                searchResults = matches.stream()
                        .filter(pattern -> searchVisibility.matches(machine.getRecipeStatus(pattern.id())))
                        .toList();
            } catch (IllegalArgumentException invalid) {
                searchResults = List.of();
                searchFeedback = Component.translatable(PREFIX + "pattern_invalid", invalid.getMessage());
            }
            if (page == Page.UNIVERSAL) {
                searchResults = universalResults(searchResults);
                variantGroupSizes.clear();
                if(groupVariants){
                    var groups=new LinkedHashMap<String,List<RecipePattern>>();
                    var overrides = draft().recipeOverrides();
                    for (var recipe : searchResults) {
                        var candidate = draftCandidate(recipe.id());
                        var routed = machine.universalCandidate(recipe.id());
                        String group = overrides.containsKey(recipe.id()) ? recipe.id() : (routed == null ? candidate.family() : routed.family())+"|"+candidate.materialId();
                        groups.computeIfAbsent(group,k -> new ArrayList<>()).add(recipe);
                    }
                    var representatives=new ArrayList<RecipePattern>();
                    for(var bucket:groups.values())for(var variants:org.gtlcore.universalpatterns.integration.ae2.handler.RecipeVariantGroups.groups(bucket)){
                        var representative=variants.stream().min(Comparator.comparingInt((RecipePattern r)->org.gtlcore.universalpatterns.integration.ae2.handler.ConsumedFluidFilter.matches(draft(),r)?0:1).thenComparingInt(r->reason(r.id())<=2?0:1).thenComparingInt(r->r.id().equals(selectedId)?0:1)).orElseThrow();
                        representatives.add(representative);variantGroupSizes.put(representative.id(),variants.size());
                    }
                    searchResults=List.copyOf(representatives);
                }
            }
            searchCount = searchResults.size();
            if (!focusToggledRecipe.isEmpty()) {
                for (int i=0; i<searchResults.size(); i++) if (searchResults.get(i).id().equals(focusToggledRecipe)) {
                    searchPage = i / resultPageSize(); break;
                }
                focusToggledRecipe = "";
            }
            searchPage = clampPage(searchPage, searchCount, resultPageSize());
            if (searchResults.stream().noneMatch(pattern -> pattern.id().equals(selectedId))) {
                selectedId = searchResults.isEmpty() ? "" : searchResults.get(searchPage * resultPageSize()).id();
                selectedInputId = "";
            }
        } else if (page == Page.FILTERS) {
            recipeListCount = getRecipeList().size();
            recipeListPage = clampPage(recipeListPage, recipeListCount, RECIPE_LIST_PAGE_SIZE);
            ruleCount = getRules().size();
            rulePage = clampPage(rulePage, ruleCount, RULE_PAGE_SIZE);
        }
        var statistics = machine.getRecipeStatistics();
        publishedCount = statistics.published();
        skippedCount = statistics.skipped();
        blockedCount = statistics.blocked();
        chancedPrimaryCount = statistics.chancedPrimary();
        chancedInputCount = statistics.chancedInput();
        activeBlacklistCount = statistics.blacklisted();
        ruleFilteredCount = statistics.ruleFiltered();
        whitelistFilteredCount = statistics.whitelistFiltered();
        whitelistActive = machine.isWhitelistActive();
        lastRevision = machine.getConfigurationRevision();
        dirty = false;
    }

    private List<RecipePattern> universalResults(List<RecipePattern> matches) {
        String family = draft().family();
        if (searchVisibility == SearchVisibility.ALL) {
            return matches.stream().filter(pattern -> draftCandidate(pattern.id()) != null).toList();
        }
        return matches.stream().filter(pattern -> {
            var candidate = draftCandidate(pattern.id());
            return candidate != null && family.equals(candidate.family()) &&
                    (searchVisibility != SearchVisibility.ON_PATTERN || reason(pattern.id()) <= 2);
        }).toList();
    }

    private static boolean editableStatus(int status) {
        return status == 0 || status == 7;
    }

    private @Nullable UniversalPatternFamily.Candidate expandedCasting(String family, String id) {
        var preferred = machine.universalCandidate(id);
        return RegisteredProcessFamilies.legacyCasting(preferred, family) != null && editableStatus(machine.getRecipeStatus(id))
                ? preferred : null;
    }

    private void editPattern(PatternEdit edit) {
        if (universalPatternSize < 0) return;
        boolean allowed = switch (edit) {
            case BIND -> !selectedId.isEmpty();
            case EXPAND_CASTING -> canExpandCasting;
            case APPLY, REVERT, EXTERNAL_CATALYSTS, UNBIND -> true;
            case FOLLOW, CHOOSE_RECIPE, ALL_RECIPES -> selectedInFamily;
            case ADD -> selectedInFamily;
            case REMOVE -> selectedInFamily;
            case ADD_RESULTS, REMOVE_RESULTS -> !universalFamily.isEmpty() && searchCount > 0;
        };
        if (!allowed) return;
        send(EDIT_UNIVERSAL, buffer -> {
            buffer.writeVarLong(patternRevision);
            buffer.writeVarInt(universalSlot);
            buffer.writeEnum(edit);
            buffer.writeUtf(selectedId, 1024);
        });
    }

    private void writeSnapshot(FriendlyByteBuf buffer) {
        buffer.writeVarInt(publishedCount);
        buffer.writeVarInt(skippedCount);
        buffer.writeVarInt(blockedCount);
        buffer.writeVarInt(chancedPrimaryCount);
        buffer.writeVarInt(chancedInputCount);
        buffer.writeVarInt(activeBlacklistCount);
        buffer.writeVarInt(ruleFilteredCount);
        buffer.writeVarInt(whitelistFilteredCount);
        buffer.writeBoolean(whitelistActive);
        switch (page) {
            case SEARCH, UNIVERSAL -> writeSearchSnapshot(buffer);
            case FILTERS -> writeRecipeListSnapshot(buffer);
        }
    }

    private void writeSearchSnapshot(FriendlyByteBuf buffer) {
        buffer.writeEnum(searchVisibility);
        buffer.writeVarInt(searchPage);
        buffer.writeVarInt(searchCount);
        buffer.writeComponent(searchFeedback);
        if (page == Page.UNIVERSAL) {
            var selection = draft();
            buffer.writeVarInt(universalSlot);
            buffer.writeVarInt(searchResetRevision);
            buffer.writeBoolean(browsingFamily);buffer.writeBoolean(groupVariants);
            boolean sendLabels = browsingFamily && familyIndexVersion != sentFamilyIndexVersion;
            buffer.writeBoolean(sendLabels);
            if (sendLabels) {
                buffer.writeVarInt(familyIndexVersion);
                buffer.writeVarInt(familyLabels.size());
                for (var label : familyLabels) buffer.writeComponent(label);
                sentFamilyIndexVersion = familyIndexVersion;
            }
            buffer.writeBoolean(machine.hasRecipeCatalog());
            buffer.writeVarInt(machine.hasUniversalPattern(universalSlot) ? (int) machine.cachedRecipes().stream()
                    .filter(pattern -> reason(pattern.id()) <= 2).count() : -1);
            buffer.writeBoolean(!selection.equals(org.gtlcore.universalpatterns.common.item.UniversalPatternItem.read(originals.get(universalSlot))));
            buffer.writeUtf(selection.family(), 512);
            buffer.writeVarLong(machine.getConfigurationRevision());
            buffer.writeUtf(selection.allow(), MaterialExpression.MAX_LENGTH);
            buffer.writeUtf(selection.deny(), MaterialExpression.MAX_LENGTH);
            buffer.writeBoolean(selection.externalCatalysts());
            buffer.writeVarInt(selection.outputMultiplier());
            buffer.writeVarInt(selection.batchMultiplier());
            buffer.writeBoolean(selection.keepByproducts());
            buffer.writeVarInt(selection.defaultSupplyMode());
            buffer.writeVarInt(gui == null ? -1 : UniversalPatternItem.personalSupplyMode(gui.entityPlayer.getPersistentData()));
            buffer.writeVarInt(outputSelection(selection).ignoredInputs().size());for(var value:outputSelection(selection).ignoredInputs())buffer.writeUtf(value,64);
            writeStacks(buffer,deliveryPreview(outputSelection(selection)));
            buffer.writeVarInt(outputSelection(selection).ignoredOutputs().size());for(var value:outputSelection(selection).ignoredOutputs())buffer.writeUtf(value,64);
            writeStacks(buffer,outputPreview(outputSelection(selection)));
            buffer.writeUtf(selection.requiredFluid(),64);
            buffer.writeVarInt(selection.requiredInputs().size());for(String key:selection.requiredInputs())buffer.writeUtf(key,64);
            var allInputs=familyFluidOptions();writeStacks(buffer,allInputs);
            var current=machine.getRecipePattern(selectedId);
            var anchors=new LinkedHashSet<AEKey>(org.gtlcore.universalpatterns.integration.ae2.handler.ConsumedFluidFilter.inputs(current));
            for(var stack:allInputs)if(selection.requiredInputs().contains(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.identity(stack.what())))anchors.add(stack.what());
            var family=machine.cachedRecipes().stream().filter(r->machine.candidateForFamily(r.id(),selection.family())!=null).toList();
            var inputIndex=new org.gtlcore.universalpatterns.integration.ae2.handler.InputConstraintOptions.Index(family);
            buffer.writeVarInt(anchors.size());for(var anchor:anchors){writeSample(buffer,anchor);writeStacks(buffer,inputIndex.candidates(current,anchor).stream().map(k->new GenericStack(k,1)).toList());}
            buffer.writeVarInt(selection.pendingSync().size());for(var entry:selection.pendingSync().entrySet()){buffer.writeUtf(entry.getKey(),2048);buffer.writeUtf(entry.getValue(),12288);}
            buffer.writeComponent(ruleFeedback);
        }
        buffer.writeUtf(selectedId);
        List<RecipePattern> rows = page(searchResults, searchPage, resultPageSize());
        buffer.writeVarInt(rows.size());
        for (RecipePattern pattern : rows) {
            buffer.writeUtf(pattern.id());
            buffer.writeVarInt(machine.getRecipeStatus(pattern.id()));
            GenericStack rowOutput = machine.getPreviewPattern(pattern).getPrimaryOutput();
            if (page == Page.UNIVERSAL) {
                var candidate = draftCandidate(pattern.id());
                int rowReason = reason(pattern.id());
                buffer.writeBoolean(rowReason <= 2);
                buffer.writeComponent(candidate.materialName());
                buffer.writeComponent(routeDescription(pattern));
                buffer.writeVarInt(rowReason);
                if (rowReason == DIFFERENT_FAMILY_REASON) buffer.writeUtf(candidate.family());
                buffer.writeVarInt(variantGroupSizes.getOrDefault(pattern.id(), 1));
                var card = draft();
                if (candidate.family().equals(card.family())) {
                    try {
                        var effective = card.forMaterial(candidate.materialId().toString());
                        var products = DeliveryPolicy.outputs(effective,pattern,candidate.output(),machine.getLevel());
                        if (!products.isEmpty()) rowOutput = products.getFirst();
                    } catch (IllegalArgumentException | ArithmeticException invalid) {
                        // Keep the registered product visible so this draft can be edited.
                    }
                }
            }
            GenericStack.writeBuffer(rowOutput, buffer);
        }
        RecipePattern selected = machine.getRecipePattern(selectedId);
        if (selected != null) {
            buffer.writeVarInt(machine.getRecipeStatus(selectedId));
            buffer.writeBoolean(machine.isRecipeWhitelisted(selectedId));
            if (page == Page.UNIVERSAL) {
                var candidate = draftCandidate(selectedId);
                var card = draft();
                int selectedReason = reason(selectedId);
                buffer.writeBoolean(candidate != null && card.family().equals(candidate.family()));
                buffer.writeBoolean(expandedCasting(card.family(), selectedId) != null);
                var routed = machine.universalCandidate(selectedId);
                buffer.writeBoolean(routed != null && card.family().equals(routed.baseFamily()) && editableStatus(machine.getRecipeStatus(selectedId)));
                buffer.writeComponent(candidate == null ? Component.empty() : candidate.materialName());
                buffer.writeVarInt(selectedReason);
                if (selectedReason == DIFFERENT_FAMILY_REASON) {
                    buffer.writeUtf(candidate == null ? selected.recipe().recipeType.registryName.toString() : candidate.family());
                }
                buffer.writeVarInt(candidate == null ? 0 : (int) machine.cachedRecipes().stream()
                        .map(pattern -> machine.candidateForFamily(pattern.id(), card.family())).filter(Objects::nonNull)
                        .filter(other -> other.materialId().equals(candidate.materialId())).count());
            }
            AEKey primary = machine.getPrimaryOutput(selected);
            if(page==Page.UNIVERSAL){
                var candidate=draftCandidate(selectedId);
                if(candidate!=null)primary=candidate.output();
                try{
                    var policy=outputSelection(draft());
                    var override=DeliveryPolicy.primary(policy);
                    if (override!=null && (Arrays.stream(selected.pattern().getOutputs()).anyMatch(s->s.what().equals(override)) ||
                            outputPreview(policy).stream().anyMatch(s->s.what().equals(override)))) primary=override;
                }catch(IllegalArgumentException invalid){}
            }
            buffer.writeBoolean(primary != null);
            if (primary != null) GenericStack.writeBuffer(new GenericStack(primary, 1), buffer);
            IPatternDetails preview = page==Page.UNIVERSAL?selected.pattern():machine.getPreviewPattern(selected);
            List<AEItemKey> virtualInputs = page == Page.UNIVERSAL ? List.of() : machine.getVirtualInputKeys(selected);
            buffer.writeVarInt(selected.inputSlots().size() + virtualInputs.size());
            for (InputSlot slot : selected.inputSlots()) {
                AEKey choice = page == Page.UNIVERSAL ? slot.defaultKey() : machine.getInputChoice(selected, slot);
                buffer.writeUtf(slot.id(), 64);
                GenericStack.writeBuffer(new GenericStack(choice, slot.amount()), buffer);
                buffer.writeVarInt(slot.alternatives().size());
                buffer.writeBoolean(!choice.equals(slot.defaultKey()));
                if (page == Page.UNIVERSAL) writeSample(buffer, ItemSubstitutions.resolve(draft(), slot, choice));
            }
            for (AEItemKey virtual : virtualInputs) {
                buffer.writeUtf("", 64);
                GenericStack.writeBuffer(new GenericStack(virtual, 1), buffer);
                buffer.writeVarInt(1);
                buffer.writeBoolean(false);
            }
            List<GenericStack> outputView = Arrays.asList(preview.getOutputs());
            if (page == Page.UNIVERSAL && draft().outputMultiplier() != 1 &&
                    machine.candidateForFamily(selectedId,draft().family()) != null) {
                try { outputView = RecipeYield.outputs(selected,draft().outputMultiplier(),machine.getLevel()); }
                catch (IllegalArgumentException | ArithmeticException invalid) { outputView = List.of(); }
            }
            writeStacks(buffer, outputView);
            if (page == Page.UNIVERSAL) {
                var catalysts=selected.nonConsumedInputs().stream().flatMap(Collection::stream).distinct().toList();
                writeStacks(buffer,catalysts.stream().map(key->new GenericStack(key,1)).toList());
                for(var key:catalysts)buffer.writeVarInt(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.mode(draft(),key));
            }
        }
        InputSlot slot = selected == null ? null : machine.getInputSlot(selected, selectedInputId);
        buffer.writeBoolean(slot != null && slot.alternatives().size() > 1);
        if (slot != null && slot.alternatives().size() > 1) {
            inputOptionCount = slot.alternatives().size();
            inputOptionPage = clampPage(inputOptionPage, inputOptionCount, INPUT_OPTION_PAGE_SIZE);
            buffer.writeUtf(slot.id(), 64);
            buffer.writeVarInt(inputOptionCount);
            buffer.writeVarInt(inputOptionPage);
            GenericStack.writeBuffer(new GenericStack(slot.defaultKey(), 1), buffer);
            GenericStack.writeBuffer(new GenericStack(page == Page.UNIVERSAL ? ItemSubstitutions.resolve(draft(), slot, slot.defaultKey()) : machine.getInputChoice(selected, slot), 1), buffer);
            writeStacks(buffer, page(slot.alternatives(), inputOptionPage, INPUT_OPTION_PAGE_SIZE).stream()
                    .map(key -> new GenericStack(key, 1)).toList());
        }
    }

    private void writeRecipeListSnapshot(FriendlyByteBuf buffer) {
        buffer.writeEnum(filterMode);
        buffer.writeVarInt(rulePage);
        buffer.writeVarInt(ruleCount);
        List<Rule> rules = page(getRules(), rulePage, RULE_PAGE_SIZE);
        buffer.writeVarInt(rules.size());
        for (Rule rule : rules) {
            buffer.writeEnum(rule.target());
            buffer.writeUtf(rule.expression());
        }
        buffer.writeVarInt(recipeListPage);
        buffer.writeVarInt(recipeListCount);
        List<String> entries = page(getRecipeList(), recipeListPage, RECIPE_LIST_PAGE_SIZE);
        buffer.writeVarInt(entries.size());
        entries.forEach(buffer::writeUtf);
    }

    private void readSnapshot(FriendlyByteBuf buffer) {
        publishedCount = buffer.readVarInt();
        skippedCount = buffer.readVarInt();
        blockedCount = buffer.readVarInt();
        chancedPrimaryCount = buffer.readVarInt();
        chancedInputCount = buffer.readVarInt();
        activeBlacklistCount = buffer.readVarInt();
        ruleFilteredCount = buffer.readVarInt();
        whitelistFilteredCount = buffer.readVarInt();
        whitelistActive = buffer.readBoolean();
        switch (page) {
            case SEARCH, UNIVERSAL -> readSearchSnapshot(buffer);
            case FILTERS -> readRecipeListSnapshot(buffer);
        }
        pagerVisibility.forEach(Runnable::run);
    }

    private void readSearchSnapshot(FriendlyByteBuf buffer) {
        searchVisibility = buffer.readEnum(SearchVisibility.class);
        searchPage = buffer.readVarInt();
        searchCount = buffer.readVarInt();
        searchFeedback = buffer.readComponent();
        if (page == Page.UNIVERSAL) {
            universalSlot = buffer.readVarInt();
            int resetRevision = buffer.readVarInt();
            browsingFamily = buffer.readBoolean();groupVariants=buffer.readBoolean();
            if (buffer.readBoolean()) {
                familyIndexVersion = buffer.readVarInt();
                int count = buffer.readVarInt();
                var labels = new ArrayList<Component>(count);
                for (int i = 0; i < count; i++) labels.add(buffer.readComponent());
                familyLabels = List.copyOf(labels);
                if (!familyQuery.isEmpty()) familyQueryDelay = 1;
            }
            familyField.setVisible(browsingFamily).setActive(browsingFamily);
            if (!browsingFamily || receivedSearchResetRevision != resetRevision) {
                familyQuery = "";
                familyField.setCurrentString("");
                familyQueryDelay = 0;
            }
            if (receivedSearchResetRevision != resetRevision) {
                receivedSearchResetRevision = resetRevision;
                recipeSample.display(null); outputSample.display(null);
                search = ""; searchField.setCurrentString("");
            }
            catalogReady = buffer.readBoolean();
            universalPatternSize = buffer.readVarInt();
            draftChanged = buffer.readBoolean();
            String updatedFamily = buffer.readUtf(512);
            universalFamily = updatedFamily;
            familySummary.setHoverTooltips(universalPatternSize < 0 ?
                    Component.translatable(PREFIX + "pattern_missing") : UniversalPatternFamily.label(universalFamily));
            patternRevision = buffer.readVarLong();
            savedAllow = buffer.readUtf(MaterialExpression.MAX_LENGTH);
            savedDeny = buffer.readUtf(MaterialExpression.MAX_LENGTH);
            externalCatalysts = buffer.readBoolean();
            outputMultiplier = buffer.readVarInt();
            batchMultiplier = buffer.readVarInt();
            keepByproducts = buffer.readBoolean();
            defaultSupplyMode = buffer.readVarInt();
            personalSupplyMode = buffer.readVarInt();
            var ignored=new HashSet<String>();int ignoredCount=buffer.readVarInt();for(int i=0;i<ignoredCount;i++)ignored.add(buffer.readUtf(64));ignoredDelivery=Set.copyOf(ignored);extraDelivery=readStacks(buffer);
            var ignoredOut=new HashSet<String>();int outputCount=buffer.readVarInt();for(int i=0;i<outputCount;i++)ignoredOut.add(buffer.readUtf(64));ignoredOutputs=Set.copyOf(ignoredOut);extraOutputs=readStacks(buffer);
            requiredFluid=buffer.readUtf(64);
            var constraints=new HashSet<String>();int constraintCount=buffer.readVarInt();for(int i=0;i<constraintCount;i++)constraints.add(buffer.readUtf(64));fixedInputs=Set.copyOf(constraints);
            familyFluids=readStacks(buffer);
            constraintCandidates.clear();int anchors=buffer.readVarInt();for(int i=0;i<anchors;i++)constraintCandidates.put(readSample(buffer),readStacks(buffer));
            var pending=new ArrayList<org.gtlcore.universalpatterns.integration.ae2.handler.PendingPatternSync.Change>();int pendingCount=buffer.readVarInt();
            for(int i=0;i<pendingCount;i++){var change=org.gtlcore.universalpatterns.integration.ae2.handler.PendingPatternSync.parse(buffer.readUtf(2048),buffer.readUtf(12288));if(change!=null&&change.family().equals(universalFamily))pending.add(change);}
            pending.sort(Comparator.comparing(org.gtlcore.universalpatterns.integration.ae2.handler.PendingPatternSync.Change::id));pendingChanges=List.copyOf(pending);
            ruleFeedback = buffer.readComponent();
            draftMessage = ruleFeedback.getString();
            ruleEditor.showFeedback(ruleFeedback);
        }
        String previous = selectedId;
        selectedId = buffer.readUtf();
        if (!previous.equals(selectedId)) {
            inputPage = 0;
            outputPage = 0;
        }
        recipeRows.clearAllWidgets();
        int rowCount = buffer.readVarInt();
        for (int row = 0; row < rowCount; row++) {
            String id = buffer.readUtf();
            int status = buffer.readVarInt();
            boolean included = page == Page.UNIVERSAL && buffer.readBoolean();
            Component materialName = page == Page.UNIVERSAL ? buffer.readComponent() : null;
            Component route = page == Page.UNIVERSAL ? buffer.readComponent() : Component.empty();
            int rowReason = page == Page.UNIVERSAL ? buffer.readVarInt() : 0;
            boolean differentFamily = rowReason == DIFFERENT_FAMILY_REASON;
            String rowFamily = differentFamily ? buffer.readUtf() : "";
            int variants = page == Page.UNIVERSAL ? buffer.readVarInt() : 1;
            Component rowStatus = differentFamily ? Component.literal(recipeProcessLabel(rowFamily)) :
                    Component.translatable(PREFIX + (page == Page.UNIVERSAL ? "reason." + rowReason : "status." + status));
            String variantLabel = variants > 1 ? text("variants.count", variants) + " / " : "";
            String rowSubtitle = page != Page.UNIVERSAL ? shortRecipeId(id) :
                    differentFamily ? rowStatus.getString() : variantLabel + rowStatus.getString();
            Component rowTooltip = differentFamily ? Component.literal(variantLabel).append(rowStatus)
                    .append(" · ").append(UniversalPatternFamily.label(rowFamily)) : rowStatus;
            GenericStack stack = GenericStack.readBuffer(buffer);
            int y = row * 28;
            IGuiTexture rowTexture = page == Page.UNIVERSAL ? new ColorRectTexture(id.equals(selectedId) ? 0xFFB8CDC5 : 0xFFD8DDDC) :
                    id.equals(selectedId) ? RecipePatternUiTextures.ROW_SELECTED : RecipePatternUiTextures.ROW;
            Widget entry = new ButtonWidget(0, y, page==Page.UNIVERSAL?114:136, 27, rowTexture,
                    click -> {
                        if (click.isRemote) send(SELECT, packet -> packet.writeUtf(id, 1024));
                    }).setHoverTexture(page == Page.UNIVERSAL ? new ColorRectTexture(0xFFC8D8D2) : RecipePatternUiTextures.ROW_HOVER)
                    .setHoverTooltips(Component.literal(id), rowTooltip, route);
            recipeRows.addWidget(entry);
            recipeRows.addWidget(new ImageWidget(0, y, 2, 27, new ColorRectTexture((page == Page.UNIVERSAL ? included : status == 0) ? RecipePatternUiTextures.COUNTER_PUBLISHED_COLOR : RecipePatternUiTextures.COUNTER_ERROR_COLOR)));
            recipeRows.addWidget(stackWidget(stack, 4, y + 4, page == Page.UNIVERSAL ? GuiTextures.SLOT : RecipePatternUiTextures.SLOT));
            recipeRows.addWidget(line(26, y + 2, page==Page.UNIVERSAL?86:106, 12,
                    () -> materialName == null ? stack.what().getDisplayName().getString() : materialName.getString() + (route.getString().isEmpty() ? "" : " · " + route.getString()), TEXT_COLOR));
            recipeRows.addWidget(line(26, y + 15, page == Page.UNIVERSAL ? 86 : 106, 10,
                    () -> rowSubtitle, page == Page.UNIVERSAL ? 0xFF414A4E : MUTED_COLOR));
            if(page==Page.UNIVERSAL){
                final long revision=patternRevision;final int cardSlot=universalSlot;
                boolean editable = rowReason >= 0 && rowReason <= 7 && !differentFamily;
                var icon = included ? com.lowdragmc.lowdraglib.gui.editor.Icons.CHECKBOX_MARKED : com.lowdragmc.lowdraglib.gui.editor.Icons.CHECKBOX_BLANK;
                var checkbox=new ButtonWidget(116,y+5,16,16,new GuiTextureGroup(new ColorRectTexture(included ? 0xFF266845 : 0xFF505A60),icon),click->{
                    if (click.isRemote) send(SET_MATERIAL_SELECTED,packet->{packet.writeVarLong(revision);packet.writeVarInt(cardSlot);packet.writeUtf(id,1024);packet.writeBoolean(!included);});
                });
                checkbox.setActive(editable);checkbox.setHoverTooltips(Component.translatable(PREFIX+(editable?(included?"material_uncheck":"material_check"):"material_locked")));
                recipeRows.addWidget(checkbox);
            }
        }
        if (rowCount == 0) recipeRows.addWidget(line(4, 4, 128, 14, () -> text("no_results"),
                page == Page.UNIVERSAL ? NATIVE_PANEL_TEXT : MUTED_COLOR));
        if (!selectedId.isEmpty()) {
            selectedStatus = buffer.readVarInt();
            selectedWhitelisted = buffer.readBoolean();
            if (page == Page.UNIVERSAL) {
                selectedInFamily = buffer.readBoolean();
                canExpandCasting = buffer.readBoolean();
                canNarrowRoute = buffer.readBoolean();
                selectedMaterial = buffer.readComponent();
                int selectedReason = buffer.readVarInt();
                selectedReasonLabel = selectedReason == DIFFERENT_FAMILY_REASON ?
                        recipeProcessLabel(buffer.readUtf()) : text("reason." + selectedReason);
                selectedVariants = buffer.readVarInt();
            }
            selectedPrimary = buffer.readBoolean() ? GenericStack.readBuffer(buffer).what() : null;
            int inputCount = buffer.readVarInt();
            List<InputView> inputs = new ArrayList<>(inputCount);
            for (int index = 0; index < inputCount; index++) {
                String id = buffer.readUtf(64);
                GenericStack stack = GenericStack.readBuffer(buffer);
                int count = buffer.readVarInt();
                boolean customised = buffer.readBoolean();
                inputs.add(new InputView(id,stack,count,customised,page == Page.UNIVERSAL ? readSample(buffer) : stack.what()));
            }
            selectedInputViews = inputs;
            selectedInputs = inputs.stream().map(InputView::stack).toList();
            selectedOutputs = readStacks(buffer);
            if (page == Page.UNIVERSAL) {
                int batches = selectedInFamily ? batchMultiplier : 1;
                selectedInputs = editableStacks(selectedInputs, extraDelivery, batches);
                selectedOutputs = editableStacks(selectedOutputs, extraOutputs, batches);
            }
            if (page == Page.UNIVERSAL) {
                selectedCatalysts = readStacks(buffer);
                var modes=new ArrayList<Integer>();for(int i=0;i<selectedCatalysts.size();i++)modes.add(buffer.readVarInt());
                selectedCatalystModes=List.copyOf(modes);
            }
            selectedDisplay = selectedOutputs.isEmpty() ? null : selectedOutputs.get(0).what();
        } else {
            selectedStatus = 0;
            selectedWhitelisted = false;
            selectedInFamily = false;
            canExpandCasting = false;
            canNarrowRoute = false;
            selectedPrimary = null;
            selectedInputs = List.of();
            selectedInputViews = List.of();
            selectedOutputs = List.of();
            selectedCatalysts = List.of();
            selectedCatalystModes=List.of();
            selectedDisplay = null;
            selectedMaterial = null;
            selectedReasonLabel = "";
        }
        boolean choosing = buffer.readBoolean();
        if (choosing) {
            selectedInputId = buffer.readUtf(64);
            inputOptionCount = buffer.readVarInt();
            inputOptionPage = buffer.readVarInt();
            inputDefault = GenericStack.readBuffer(buffer).what();
            inputChoice = GenericStack.readBuffer(buffer).what();
            inputOptions = readStacks(buffer);
        } else {
            selectedInputId = "";
            inputOptions = List.of();
        }
        inputChooser.setVisible(choosing).setActive(choosing);
        displayInputOptions();
        displayStacks();
        if(typeChoiceButton!=null)typeChoiceButton.setVisible(!selectedId.isEmpty()||!universalFamily.isEmpty()).setActive(!selectedId.isEmpty()||!universalFamily.isEmpty());
        if(pendingSyncOpen&&supplyChooser.isVisible())openPendingSync();
        if(activeDeliveryKey!=null&&supplyChooser.isVisible()){
            if(!activeDeliveryRecipe.equals(selectedId))closeSupplyChooser();
            else {var key=activeDeliveryKey;if(activeDeliveryOutput)openOutputDelivery(new GenericStack(key,1));else openInputDelivery(new GenericStack(key,1));}
        }
    }

    private void displayInputOptions() {
        inputOptionSlots.clearAllWidgets();
        String recipeId = selectedId;
        String slotId = selectedInputId;
        for (int index = 0; index < inputOptions.size(); index++) {
            GenericStack stack = inputOptions.get(index);
            int x = index % 9 * 18;
            int y = index / 9 * 18;
            inputOptionSlots.addWidget(stackWidget(stack, x, y));
            Widget button = new ButtonWidget(x, y, 18, 18, IGuiTexture.EMPTY, click -> {
                if (click.isRemote) sendInputChoice(recipeId, slotId, stack.what());
            }).setHoverTooltips(stack.what().getDisplayName(), Component.literal(materialId(stack)),
                    Component.translatable(PREFIX + (stack.what().equals(inputDefault) ? "input_default" : "select_input")));
            if (stack.what().equals(inputChoice)) button.setBackground(new ColorBorderTexture(1, 0xFF70D090));
            inputOptionSlots.addWidget(button);
        }
    }

    private void readRecipeListSnapshot(FriendlyByteBuf buffer) {
        filterMode = buffer.readEnum(FilterMode.class);
        FilterMode mode = filterMode;
        rulePage = buffer.readVarInt();
        ruleCount = buffer.readVarInt();
        ruleRows.clearAllWidgets();
        int rules = buffer.readVarInt();
        for (int row = 0; row < rules; row++) {
            Rule rule = new Rule(buffer.readEnum(Target.class), buffer.readUtf());
            int y = row * 28;
            ruleRows.addWidget(new ImageWidget(0, y, 148, 27, RecipePatternUiTextures.RULE_BACKGROUND));
            ruleRows.addWidget(line(4, y + 2, 124, 12, () -> Component.translatable(rule.target().translationKey()).getString(), TEXT_COLOR));
            ruleRows.addWidget(line(4, y + 15, 124, 10, rule::expression, MUTED_COLOR)
                    .setHoverTooltips(Component.literal(rule.expression())));
            ruleRows.addWidget(button(130, y + 5, 16, 16, () -> "X", () -> send(REMOVE_RULE, packet -> {
                packet.writeEnum(mode);
                packet.writeEnum(rule.target());
                packet.writeUtf(rule.expression(), MERecipePatternFilter.MAX_EXPRESSION_LENGTH);
            })).setHoverTooltips(Component.translatable(PREFIX + "remove")));
        }
        if (rules == 0) ruleRows.addWidget(line(4, 4, 140, 14, () -> text("no_rules"), MUTED_COLOR));
        recipeListPage = buffer.readVarInt();
        recipeListCount = buffer.readVarInt();
        recipeListRows.clearAllWidgets();
        int excluded = buffer.readVarInt();
        for (int row = 0; row < excluded; row++) {
            String id = buffer.readUtf();
            int y = row * 28;
            recipeListRows.addWidget(new ImageWidget(0, y, 146, 27, new ColorRectTexture(row % 2 == 0 ? RecipePatternUiTextures.ROW_COLOR : PANEL_COLOR)));
            recipeListRows.addWidget(line(4, y + 2, 122, 12, () -> shortRecipeId(id), TEXT_COLOR).setHoverTooltips(Component.literal(id)));
            recipeListRows.addWidget(line(4, y + 15, 122, 10, () -> id.substring(0, Math.max(0, id.indexOf('/'))), MUTED_COLOR));
            recipeListRows.addWidget(button(128, y + 5, 16, 16, () -> "X", () -> {
                if (mode == FilterMode.WHITELIST) sendWhitelist(id, false);
                else sendBlacklist(id, false);
            })
                    .setHoverTooltips(Component.literal(id), Component.translatable(PREFIX + "remove")));
        }
        if (excluded == 0) recipeListRows.addWidget(line(4, 4, 138, 14, () -> text(isWhitelistMode() ? "no_whitelist" : "no_blacklist"), MUTED_COLOR));
    }

    private static String recipeProcessLabel(String family) {
        int separator = family.indexOf('|');
        return UniversalAttributeEditor.processLabel(separator < 0 ? family : family.substring(0, separator));
    }

    private static Component routeDescription(RecipePattern recipe) {
        var result = Component.empty();
        if (recipe.circuit() >= 0) result.append(Component.translatable("gtlcore.universal_pattern.route_circuit", recipe.circuit()));
        for (var fluid : org.gtlcore.universalpatterns.integration.ae2.handler.ConsumedFluidFilter.fluids(recipe)) {
            if (!result.getSiblings().isEmpty()) result.append(" + ");
            result.append(fluid.getDisplayName());
        }
        return result;
    }

    private static String shortRecipeId(String id) {
        return id.substring(id.lastIndexOf('/') + 1);
    }

    private void changeStackPage(boolean output, int delta) {
        if (output) outputPage = movePage(outputPage, delta, selectedOutputs.size(), stackPageSize());
        else inputPage = movePage(inputPage, delta, selectedInputs.size(), stackPageSize());
        displayStacks();
    }

    /** Ignored ingredients stay visible for editing, with a strike through their icons. */
    private List<GenericStack> editableStacks(List<GenericStack> base, List<GenericStack> extras, int batches) {
        try {
            return DeliveryPolicy.batchAmounts(base, extras, Set.of(), batches);
        } catch (ArithmeticException invalid) {
            // Keep the offending ingredient accessible so an oversized draft can be corrected.
            draftMessage = text("output.multiplier_invalid");
            var unscaled = new LinkedHashMap<AEKey, GenericStack>();
            for (var stack : base) unscaled.put(stack.what(),stack);
            for (var stack : extras) unscaled.putIfAbsent(stack.what(),stack);
            return List.copyOf(unscaled.values());
        }
    }

    private void displayStacks() {
        if (page == Page.UNIVERSAL) refreshCatalystSlots();
        inputPage = clampPage(inputPage, selectedInputs.size(), stackPageSize());
        outputPage = clampPage(outputPage, selectedOutputs.size(), stackPageSize());
        displayStacks(inputSlots, page(selectedInputs, inputPage, stackPageSize()), false);
        displayStacks(outputSlots, page(selectedOutputs, outputPage, stackPageSize()), true);
    }

    private void displayStacks(WidgetGroup group, List<GenericStack> stacks, boolean output) {
        group.clearAllWidgets();
        for (int index = 0; index < stacks.size(); index++) {
            GenericStack stack = stacks.get(index);
            int x = (index % 9) * 18;
            int y = (index / 9) * 18;
            GenericStack icon = stack;
            if (page == Page.UNIVERSAL && !output && extraDelivery.stream().noneMatch(extra -> extra.what().equals(stack.what()))) {
                var replacements = selectedInputViews.stream().filter(input -> input.stack().what().equals(stack.what()))
                        .map(InputView::deliveryKey).distinct().toList();
                if (replacements.size() == 1) icon = new GenericStack(replacements.getFirst(),stack.amount());
            }
            group.addWidget(stackWidget(icon, x, y, page == Page.UNIVERSAL ? GuiTextures.SLOT : RecipePatternUiTextures.SLOT));
            if (page == Page.UNIVERSAL) {
                if(output ? outputIgnored(stack.what()) || selectedInFamily && !keepByproducts && !stack.what().equals(selectedPrimary) : deliveryIgnored(stack.what()))
                    group.addWidget(new ImageWidget(x,y+8,18,2,new ColorRectTexture(0xFFC54C4C)));
                group.addWidget(new ButtonWidget(x,y,18,18,IGuiTexture.EMPTY,click->{
                    if(click.isRemote){if(output)openOutputDelivery(stack);else openInputDelivery(stack);}
                }).setHoverTooltips(icon.what().getDisplayName(),Component.literal(icon.what().getId().toString()),Component.translatable(PREFIX+(output?"output.title":"delivery.title"))));
                continue;
            }
            if (!output) {
                InputView input = selectedInputViews.get(inputPage * stackPageSize() + index);
                if (!input.id().isEmpty() && input.alternatives() > 1) {
                    String recipeId = selectedId;
                    Widget button = new ButtonWidget(x, y, 18, 18, IGuiTexture.EMPTY, click -> {
                        if (!click.isRemote) return;
                        if (click.button == 1) sendInputChoice(recipeId, input.id(), null);
                        else send(INPUT_OPTIONS, buffer -> {
                            buffer.writeUtf(recipeId, 1024);
                            buffer.writeUtf(input.id(), 64);
                        });
                    }).setHoverTooltips(stack.what().getDisplayName(), Component.literal(materialId(stack) + " X " + stack.amount()),
                            Component.translatable(PREFIX + "input_alternatives", input.alternatives()),
                            Component.translatable(PREFIX + "input_choice_hint"));
                    if (input.customised()) button.setBackground(new ColorBorderTexture(1, 0xFF70D090));
                    group.addWidget(button);
                }
            }
            if (output && !machine.cardEditorOnly()) {
                String recipeId = selectedId;
                Widget button = new ButtonWidget(x, y, 18, 18, new ColorRectTexture(0), click -> {
                    if (click.isRemote) sendPrimary(recipeId, stack.what());
                }).setHoverTooltips(stack.what().getDisplayName(), Component.literal(materialId(stack) + " X " + stack.amount()),
                        Component.translatable(PREFIX + (stack.what().equals(selectedPrimary) ? "is_primary" : "make_primary")));
                if (stack.what().equals(selectedPrimary)) button.setBackground(new ColorBorderTexture(1, 0xFF70D090));
                group.addWidget(button);
            }
        }
    }

    private static Widget stackWidget(GenericStack stack, int x, int y) {
        return stackWidget(stack, x, y, RecipePatternUiTextures.SLOT);
    }

    static Widget stackWidget(GenericStack stack, int x, int y, IGuiTexture background) {
        if (stack.what() instanceof AEFluidKey fluid) {
            FluidStorage storage = new FluidStorage(FluidStack.create(fluid.getFluid(), stack.amount(), fluid.getTag()));
            return new TankWidget(storage, x, y, false, false).setBackground(background).setClientSideWidget();
        }
        ItemStackTransfer transfer = new ItemStackTransfer(1);
        if (stack.what() instanceof AEItemKey item) transfer.setStackInSlot(0, item.toStack(Ints.saturatedCast(stack.amount())));
        return new SlotWidget(transfer, 0, x, y, false, false).setBackgroundTexture(background).setClientSideWidget();
    }

    private static void writeStacks(FriendlyByteBuf buffer, List<GenericStack> stacks) {
        buffer.writeVarInt(stacks.size());
        stacks.forEach(stack -> GenericStack.writeBuffer(stack, buffer));
    }

    private static List<GenericStack> readStacks(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<GenericStack> stacks = new ArrayList<>(size);
        for (int index = 0; index < size; index++) stacks.add(GenericStack.readBuffer(buffer));
        return stacks;
    }

    private static <T> List<T> page(List<T> values, int page, int size) {
        int from = Math.min(values.size(), Math.max(0, page) * size);
        return values.subList(from, Math.min(values.size(), from + size));
    }

    private static int pages(int count, int size) {
        return Math.max(1, (count + size - 1) / size);
    }

    private static int clampPage(int page, int count, int size) {
        return Math.max(0, Math.min(page, pages(count, size) - 1));
    }

    private static int movePage(int page, int delta, int count, int size) {
        return clampPage(page + Integer.signum(delta), count, size);
    }

    private static String text(String key, Object... args) {
        return Component.translatable(PREFIX + key, args).getString();
    }
}
