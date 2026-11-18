package org.gtlcore.universalpatterns.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.*;
import org.gtlcore.universalpatterns.api.gui.RecipePatternMachineUIWidget;
import org.gtlcore.universalpatterns.api.gui.RecipePatternManagementPage;
import org.gtlcore.gtlcore.api.machine.trait.IMERecipeHandlerTrait;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternTrait;
import org.gtlcore.universalpatterns.client.gui.widget.RecipePatternBrowserWidget;
import org.gtlcore.universalpatterns.client.gui.widget.RecipePatternBrowserWidget.Page;
import org.gtlcore.gtlcore.common.data.GTLItems;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem.Selection;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternFilter;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternFilter.Descriptor;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternFilter.Rule;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternFilter.Target;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.InputSlot;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternFamily;
import org.gtlcore.universalpatterns.integration.ae2.handler.RegisteredProcessFamilies;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternFamily.Candidate;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalMaterialRules;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.*;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Publishes actual processing recipes from formed controllers, optionally restricted by a portable pattern. */
public class MERecipePatternBufferPartMachine extends MEPatternBufferPartMachineBase implements org.gtlcore.universalpatterns.integration.ae2.handler.PatternEditorHost {

    private static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MERecipePatternBufferPartMachine.class, MEPatternBufferPartMachineBase.MANAGED_FIELD_HOLDER);
    private static final String SLOT_DATA_KEY = "recipePatternSlots";
    public static final int UNIVERSAL_PATTERN_SLOTS = 4;

    private final MERecipePatternBufferRecipeHandlerTrait recipeHandler;
    private final List<RecipePattern> recipePatterns = new ObjectArrayList<>();
    private final List<IPatternDetails> availablePatterns = new ObjectArrayList<>();
    private final Map<String, RecipePattern> patternById = new LinkedHashMap<>();
    private final Map<String, IPatternDetails> previewPatterns = new HashMap<>();
    private final Set<String> invalidPatterns = new HashSet<>();
    // Bounded by the current recipe catalog; never persisted and cleared on reload.
    private record PreviewKey(IPatternDetails original, AEKey primary, Map<String,AEKey> inputs, List<AEItemKey> virtual) {}
    private record PreviewEntry(PreviewKey key, IPatternDetails pattern) {}
    private record ProcessedEntry(IPatternDetails preview, boolean byproducts, IPatternDetails pattern) {}
    private record FamilyEntry(AEKey output, boolean explicit, Candidate legacy, Candidate preferred) {}
    private final Map<String,PreviewEntry> previewCache = new HashMap<>();
    private final Map<String,ProcessedEntry> processedCache = new HashMap<>();
    private final Map<String,FamilyEntry> familyCache = new HashMap<>();
    private Map<AEKey, AEItemKey> virtualSupplies = Map.of();
    private Map<String, List<AEItemKey>> virtualInputKeys = Map.of();
    private final Map<String, Selection> catalystPolicies = new HashMap<>();
    private final Map<String, Descriptor> searchMetadata = new HashMap<>();
    private final Set<String> blacklist = new TreeSet<>();
    private final List<Rule> filterRules = new ArrayList<>();
    private final Set<String> ruleExcluded = new HashSet<>();
    private final Set<String> whitelist = new TreeSet<>();
    private final List<Rule> whitelistRules = new ArrayList<>();
    private final Set<String> ruleAllowed = new HashSet<>();
    private final Map<String, AEKey> primaryOverrides = new HashMap<>();
    private final Map<String, Map<String, AEKey>> inputOverrides = new HashMap<>();
    @Persisted
    private final ItemStackTransfer universalPatternSlot = new ItemStackTransfer(UNIVERSAL_PATTERN_SLOTS);
    @Persisted
    @DescSynced
    private boolean universalPatternMode;
    private List<Selection> universalSelections = Collections.nCopies(UNIVERSAL_PATTERN_SLOTS, Selection.empty());
    private List<Set<ResourceLocation>> effectiveUniversalSelections = Collections.nCopies(UNIVERSAL_PATTERN_SLOTS, Set.of());
    private final Map<String, Candidate> publishedUniversalRecipes = new HashMap<>();
    private final Map<String, Candidate> universalCandidates = new HashMap<>();
    private final Map<String, Candidate> legacyUniversalCandidates = new HashMap<>();
    private final Set<GTRecipe> registeredRecipes = Collections.newSetFromMap(new IdentityHashMap<>());

    private long configurationRevision;
    @Persisted
    private boolean keepByProduct = false;
    private final Object2IntMap<IPatternDetails> patternToSlot = new Object2IntOpenHashMap<>();
    // Allocate material buffers only for patterns that have actually received a job.
    private final Int2ReferenceMap<InternalSlot> internalSlots = new Int2ReferenceOpenHashMap<>();
    private final Int2ReferenceMap<GTRecipe> recipeCache = new Int2ReferenceOpenHashMap<>();
    private final IntSet activeSlots = new IntOpenHashSet();
    private IntConsumer removeSlotFromMap = slot -> {};
    private ListTag pendingSlotData = new ListTag();
    private Set<GTRecipeType> activeRecipeTypes = Set.of();
    private RecipeManager recipeManager;
    private long reloadGeneration = -1;
    private TickableSubscription modeSubscription;
    private int skippedRecipes;
    private int unrepresentableChancedPrimaryRecipes;
    private int unrepresentableChancedInputRecipes;

    public record RecipeStatistics(int published, int blacklisted, int ruleFiltered, int whitelistFiltered, int chancedPrimary, int chancedInput, int skipped) {

        public int blocked() {
            return blacklisted + ruleFiltered + whitelistFiltered + chancedPrimary + chancedInput;
        }
    }

    public MERecipePatternBufferPartMachine(IMachineBlockEntity holder, IO io) {
        super(holder, io);
        universalPatternSlot.setFilter(stack -> stack.getItem() instanceof UniversalPatternItem);
        patternToSlot.defaultReturnValue(-1);
        recipeHandler = new MERecipePatternBufferRecipeHandlerTrait(this, io);
        getMainNode().addService(IGridTickable.class, new Ticker());
    }

    void addBufferedFluid(AEFluidKey key, long amount) { buffer.addTo(key, amount); }

    public long getConfigurationRevision() {
        return configurationRevision;
    }

    @Override
    public void onMachineRemoved() {
        super.onMachineRemoved();
        clearInventory(universalPatternSlot);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            modeSubscription = subscribeServerTick(modeSubscription, () -> {
                if (getOffsetTimer() % 20 == 0) {
                    refreshPatterns();
                    refreshVirtualInputs(true);
                }
            });
        }
    }

    @Override
    public void onUnload() {
        if (modeSubscription != null) {
            modeSubscription.unsubscribe();
            modeSubscription = null;
        }
        super.onUnload();
    }

    @Override
    public void addedToController(@NotNull IMultiController controller) {
        super.addedToController(controller);
        // GT finishes forming the controller after attaching its parts; inspect it on the next server tick.
        reloadGeneration = -1;
    }

    @Override
    public void removedFromController(@NotNull IMultiController controller) {
        super.removedFromController(controller);
        if (!isRemote()) refreshPatterns();
    }

    private void refreshPatterns() {
        if (getLevel() == null || isRemote()) return;
        Set<GTRecipeType> types = MERecipePatternHelper.getActiveRecipeTypes(getControllers());
        RecipeManager manager = getLevel().getRecipeManager();
        long generation = MERecipePatternHelper.getReloadGeneration();
        if (types.equals(activeRecipeTypes) && manager == recipeManager && generation == reloadGeneration) return;

        List<RecipePattern> expanded = new ObjectArrayList<>();
        registeredRecipes.clear();
        for (GTRecipeType type : types) registeredRecipes.addAll(manager.getAllRecipesFor(type));
        int skipped = 0;
        int chancedPrimary = 0;
        int chancedInput = 0;
        for (GTRecipe recipe : MERecipePatternHelper.getRecipes(manager, types)) {
            RecipePattern pattern = MERecipePatternHelper.createPattern(recipe, getLevel());
            if (pattern != null) expanded.add(pattern);
            else if (MERecipePatternHelper.hasChancedInput(recipe)) chancedInput++;
            else if (MERecipePatternHelper.hasChancedPrimaryOutput(recipe)) chancedPrimary++;
            else skipped++;
        }

        ListTag saved = collectSlotData();
        for (int index : recipeCache.keySet()) {
            removeSlotFromMap.accept(index);
            notifyProxySlotRemoved(index);
        }
        internalSlots.clear();
        activeSlots.clear();
        recipeCache.clear();
        recipePatterns.clear();
        previewCache.clear();processedCache.clear();familyCache.clear();
        patternById.clear();
        searchMetadata.clear();
        recipePatterns.addAll(expanded);
        for (RecipePattern recipePattern : recipePatterns) {
            patternById.put(recipePattern.id(), recipePattern);
            searchMetadata.put(recipePattern.id(), MERecipePatternFilter.describe(recipePattern.recipe()));
        }
        rebuildRuleMatches();
        refreshVirtualInputs(false);
        refreshCraftingPatterns();
        configurationRevision++;

        activeRecipeTypes = types;
        recipeManager = manager;
        reloadGeneration = generation;
        skippedRecipes = skipped;
        unrepresentableChancedPrimaryRecipes = chancedPrimary;
        unrepresentableChancedInputRecipes = chancedInput;
        // A freshly loaded part may not have rejoined its controller yet. Keep saved jobs until its mode is known.
        if (types.isEmpty() && availablePatterns.isEmpty() && !pendingSlotData.isEmpty() && saved.isEmpty()) {
            needPatternSync = true;
            return;
        }
        saved.addAll(pendingSlotData);
        pendingSlotData = new ListTag();
        restoreSlotData(saved);
        notifyInputsChanged();
        needPatternSync = true;
    }

    private void refreshCraftingPatterns() {
        if (isRemote() || getLevel() == null) return;
        availablePatterns.clear();
        patternToSlot.clear();
        previewPatterns.clear();
        invalidPatterns.clear();
        refreshUniversalSelections();
        for (int index = 0; index < recipePatterns.size(); index++) {
            RecipePattern recipePattern = recipePatterns.get(index);
            var previewKey = new PreviewKey(recipePattern.pattern(),getPrimaryOutput(recipePattern),
                    Map.copyOf(inputOverrides.getOrDefault(recipePattern.id(),Map.of())),List.copyOf(effectiveVirtualInputs(recipePattern.id())));
            var cachedPreview=previewCache.get(recipePattern.id());
            IPatternDetails preview = cachedPreview!=null && cachedPreview.key().equals(previewKey) ? cachedPreview.pattern() : buildPreviewPattern(recipePattern);
            previewCache.put(recipePattern.id(),new PreviewEntry(previewKey,preview));
            if (preview == null) {
                invalidPatterns.add(recipePattern.id());
                continue;
            }
            previewPatterns.put(recipePattern.id(), preview);
            if (getRecipeStatus(recipePattern.id()) != 0) continue;
            var cachedProcessed=processedCache.get(recipePattern.id());
            if(cachedProcessed!=null && cachedProcessed.preview()==preview && cachedProcessed.byproducts()==keepByProduct){
                if(cachedProcessed.pattern()!=null){availablePatterns.add(cachedProcessed.pattern());patternToSlot.put(cachedProcessed.pattern(),index);}
                else invalidPatterns.add(recipePattern.id());
                continue;
            }
            ItemStack complete = preview.getDefinition().toStack();
            IPatternDetails processed = realPatternHelper.processPatternWithCircuit(complete, circuit -> {}, getLevel(), keepByProduct);
            if (processed == null) {
                processedCache.put(recipePattern.id(),new ProcessedEntry(preview,keepByProduct,null));
                invalidPatterns.add(recipePattern.id());
                continue;
            }
            ItemStack encoded = processed.getDefinition().toStack();
            // MEBufferPatternHelper rebuilds the ordinary AE2 IO; retain the generated recipe's unique binding.
            encoded.getOrCreateTag().putString("gtlcoreRecipe", complete.getOrCreateTag().getString("gtlcoreRecipe"));
            encoded.getOrCreateTag().putInt("gtlcoreRecipeCircuit", recipePattern.circuit());
            IPatternDetails pattern = PatternDetailsHelper.decodePattern(encoded, getLevel());
            processedCache.put(recipePattern.id(),new ProcessedEntry(preview,keepByProduct,pattern));
            if (pattern != null) {
                availablePatterns.add(pattern);
                patternToSlot.put(pattern, index);
            } else {
                invalidPatterns.add(recipePattern.id());
            }
        }
        needPatternSync = true;
        markDirty();
    }

    private void refreshUniversalSelections() {
        List<Selection> selections = new ArrayList<>(UNIVERSAL_PATTERN_SLOTS);
        Set<String> installedFamilies = new HashSet<>();
        for (int slot = 0; slot < UNIVERSAL_PATTERN_SLOTS; slot++) {
            Selection selection = UniversalPatternItem.read(universalPatternSlot.getStackInSlot(slot));
            selections.add(selection);
            installedFamilies.add(selection.family());
        }
        universalCandidates.clear();
        legacyUniversalCandidates.clear();
        for (RecipePattern pattern : recipePatterns) {
            if (!registeredRecipes.contains(pattern.recipe())) continue;
            AEKey override = configuredPrimaryOutput(pattern);
            var output = override == null ? pattern.primaryOutput() : override;
            var family=familyCache.get(pattern.id());
            if(family==null || !Objects.equals(family.output(),output) || family.explicit()!=(override!=null)){
                Candidate legacy = UniversalPatternFamily.describe(pattern, output);
                if (legacy == null) legacy = UniversalPatternFamily.describePetrochemical(pattern, output, getLevel());
                Candidate candidate = RegisteredProcessFamilies.describe(pattern, output, override == null);
                if (candidate == null || candidate.family().length() > 512) candidate = legacy;
                family=new FamilyEntry(output,override!=null,legacy,candidate);familyCache.put(pattern.id(),family);
            }
            Candidate legacy=family.legacy(), candidate=family.preferred();
            if (candidate != legacy && legacy != null && installedFamilies.contains(legacy.family())) {
                legacyUniversalCandidates.put(pattern.id(), legacy);
            }
            if (candidate != null && candidate.family().length() <= 512) universalCandidates.put(pattern.id(), candidate);
        }
        List<Set<ResourceLocation>> effective = new ArrayList<>(UNIVERSAL_PATTERN_SLOTS);
        publishedUniversalRecipes.clear();
        catalystPolicies.clear();
        for (Selection selection : selections) {
            Set<ResourceLocation> materials = evaluateSelection(selection);
            effective.add(materials);
        }
        universalSelections = List.copyOf(selections);
        effectiveUniversalSelections = List.copyOf(effective);
    }

    private Set<ResourceLocation> evaluateSelection(Selection selection) {
        if (selection.family().isEmpty()) return Set.of();
        Predicate<Candidate> allow = savedRule(selection.allow(), false);
        Predicate<Candidate> deny = savedRule(selection.deny(), true);
        var materials = new HashSet<ResourceLocation>();
        universalCandidates.forEach((recipeId, preferred) -> {
            Candidate candidate = candidateForFamily(recipeId, selection.family());
            if (candidate == null || !org.gtlcore.universalpatterns.integration.ae2.handler.ConsumedFluidFilter.matches(selection,getRecipePattern(recipeId))) return;
            var id = candidate.materialId();
            Boolean override=selection.recipeOverrides().get(recipeId);
            if(Boolean.FALSE.equals(override))return;
            if (override==null&&(selection.excluded().contains(id) || deny.test(candidate))) return;
            String chosen = selection.recipes().get(id);
            if (override==null&&chosen != null && !chosen.equals(recipeId)) return;
            if (Boolean.TRUE.equals(override)||selection.materials().contains(id) || allow.test(candidate)) {
                materials.add(id);
                catalystPolicies.putIfAbsent(recipeId,selection);
                if (candidate == preferred) publishedUniversalRecipes.put(recipeId, candidate);
                else publishedUniversalRecipes.putIfAbsent(recipeId, candidate);
            }
        });
        return Set.copyOf(materials);
    }

    private Predicate<Candidate> savedRule(String rule, boolean denyOnInvalid) {
        try {
            return UniversalMaterialRules.compile(rule, getLevel());
        } catch (IllegalArgumentException invalidAfterReload) {
            return candidate -> denyOnInvalid;
        }
    }

    private void refreshVirtualInputs(boolean republish) {
        if (isRemote() || getLevel() == null) return;
        Map<AEKey, AEItemKey> supplies = new HashMap<>();
        var grid = getMainNode().getGrid();
        if (grid != null && !recipePatterns.isEmpty()) {
            var storage = grid.getStorageService();
            // AE2's fuzzy index visits only virtual wrappers, regardless of the number of ordinary network items.
            var wrappers = storage.getCachedInventory().findFuzzy(Objects.requireNonNull(AEItemKey.of(GTLItems.VIRTUAL_INGREDIENT.asStack())), FuzzyMode.IGNORE_ALL);
            for (var entry : wrappers) {
                if (entry.getLongValue() <= 0 || !(entry.getKey() instanceof AEItemKey wrapper)) continue;
                ItemStack canonical = VirtualIngredientBehavior.canonicalStack(wrapper.getReadOnlyStack());
                if (canonical.isEmpty() || storage.getInventory().extract(wrapper, 1, Actionable.SIMULATE, actionSource) <= 0) continue;
                AEKey payload = VirtualIngredientBehavior.payloadItemKey(canonical);
                if (payload == null) payload = VirtualIngredientBehavior.payloadFluidKey(canonical);
                if (payload == null) continue;
                AEItemKey canonicalKey = AEItemKey.of(canonical);
                AEItemKey current = supplies.get(payload);
                // Prefer the provider's canonical token; otherwise retain an available previously selected token.
                if (current == null || wrapper.equals(canonicalKey) ||
                        (wrapper.equals(virtualSupplies.get(payload)) && !current.equals(canonicalKey)))
                    supplies.put(payload, wrapper);
            }
        }
        virtualSupplies = supplies;
        Map<String, List<AEItemKey>> inputs = new HashMap<>();
        for (RecipePattern recipePattern : recipePatterns) {
            Set<AEItemKey> tokens = new LinkedHashSet<>();
            for (List<AEKey> alternatives : recipePattern.nonConsumedInputs()) {
                for (AEKey material : alternatives) {
                    AEItemKey token = supplies.get(material);
                    if (token != null) {
                        tokens.add(token);
                        break;
                    }
                }
            }
            if (!tokens.isEmpty()) inputs.put(recipePattern.id(), List.copyOf(tokens));
        }
        if (!inputs.equals(virtualInputKeys)) {
            virtualInputKeys = inputs;
            if (republish) configurationChanged();
        }
    }

    private void rebuildRuleMatches() {
        ruleExcluded.clear();
        ruleAllowed.clear();
        searchMetadata.forEach((id, metadata) -> {
            if (filterRules.stream().anyMatch(rule -> rule.matches(metadata))) ruleExcluded.add(id);
            if (whitelistRules.stream().anyMatch(rule -> rule.matches(metadata))) ruleAllowed.add(id);
        });
    }

    public RecipeStatistics getRecipeStatistics() {
        int blacklisted = 0;
        int ruleFiltered = 0;
        int whitelistFiltered = 0;
        int chancedPrimary = 0;
        int chancedInput = 0;
        for (RecipePattern pattern : recipePatterns) {
            switch (getRecipeStatus(pattern.id())) {
                case 1 -> blacklisted++;
                case 2, 7 -> ruleFiltered++;
                case 3 -> chancedPrimary++;
                case 5 -> whitelistFiltered++;
                case 6 -> chancedInput++;
            }
        }
        int skipped = skippedRecipes + recipePatterns.size() - availablePatterns.size() - blacklisted - ruleFiltered - whitelistFiltered - chancedPrimary - chancedInput;
        return new RecipeStatistics(availablePatterns.size(), blacklisted, ruleFiltered, whitelistFiltered,
                chancedPrimary + unrepresentableChancedPrimaryRecipes, chancedInput + unrepresentableChancedInputRecipes, skipped);
    }

    public List<RecipePattern> searchRecipes(String query, Set<String> localizedMaterials) {
        return searchRecipes(query, localizedMaterials, -1);
    }

    public List<RecipePattern> findUniversalRecipes(AEKey sample, String expression, int slot) {
        Predicate<Candidate> rule = expression.isBlank() ? candidate -> true : UniversalMaterialRules.compile(expression, getLevel());
        return recipePatterns.stream().filter(pattern -> {
            Candidate candidate = universalCandidate(slot, pattern.id());
            return candidate != null && candidate.hasRecipeSample(sample) && rule.test(candidate);
        }).toList();
    }

    public List<String> universalSampleAttributes(AEKey sample, boolean forRecipes) {
        return UniversalMaterialRules.sampleAttributes(sample, getLevel(), universalCandidates.values(), forRecipes);
    }

    public List<RecipePattern> searchRecipes(String query, Set<String> localizedMaterials, int universalSlot) {
        if (UniversalMaterialRules.isExpression(query)) {
            var rule = UniversalMaterialRules.compile(query, getLevel());
            return recipePatterns.stream().filter(pattern -> {
                Candidate candidate = universalSlot < 0 ? universalCandidate(pattern.id()) : universalCandidate(universalSlot, pattern.id());
                return candidate != null && rule.test(candidate);
            }).toList();
        }
        for (var prefix : Map.of("in:", Target.INPUT, "out:", Target.OUTPUT, "id:", Target.RECIPE_ID).entrySet()) {
            if (query.regionMatches(true, 0, prefix.getKey(), 0, prefix.getKey().length())) {
                var rule = new Rule(prefix.getValue(), query.substring(prefix.getKey().length()));
                if (rule.expression().isBlank()) return List.of();
                return recipePatterns.stream().filter(pattern -> rule.matches(searchMetadata.get(pattern.id()))).toList();
            }
        }
        return recipePatterns.stream().filter(pattern -> searchMetadata.get(pattern.id()).matchesSearch(query, localizedMaterials)).toList();
    }

    public @Nullable RecipePattern getRecipePattern(String id) {
        return patternById.get(id);
    }

    public List<String> getBlacklistedRecipes() {
        return List.copyOf(blacklist);
    }

    public List<Rule> getFilterRules() {
        return List.copyOf(filterRules);
    }

    public List<String> getWhitelistedRecipes() {
        return List.copyOf(whitelist);
    }

    public boolean isRecipeWhitelisted(String id) {
        return whitelist.contains(id);
    }

    public List<Rule> getWhitelistRules() {
        return List.copyOf(whitelistRules);
    }

    public boolean isWhitelistActive() {
        return !whitelist.isEmpty() || !whitelistRules.isEmpty();
    }

    /**
     * 0 = published, 1 = blacklisted, 2 = blacklist rule, 3 = chanced primary, 4 = AE2 limits, 5 = outside whitelist,
     * 6 = chanced input, 7 = absent from the installed universal pattern.
     */
    public int getRecipeStatus(String id) {
        if (blacklist.contains(id)) return 1;
        if (ruleExcluded.contains(id)) return 2;
        // Blacklist entries and rules always win, even when the same recipe matches the whitelist.
        if (isWhitelistActive() && !whitelist.contains(id) && !ruleAllowed.contains(id)) return 5;
        RecipePattern pattern = patternById.get(id);
        // Neither a whitelist entry nor a primary-output override can enable probabilistic consumption.
        if (pattern != null && pattern.chancedInput()) return 6;
        if (pattern == null || getPrimaryOutput(pattern) == null) return 3;
        if (invalidPatterns.contains(id)) return 4;
        return (universalPatternMode || hasAnyUniversalPattern()) && !publishedUniversalRecipes.containsKey(id) ? 7 : 0;
    }

    public @Nullable AEKey getPrimaryOutput(RecipePattern pattern) {
        AEKey override = configuredPrimaryOutput(pattern);
        if (override != null) return override;
        if (universalPatternMode || hasAnyUniversalPattern()) {
            Candidate candidate = publishedUniversalRecipes.getOrDefault(pattern.id(), universalCandidates.get(pattern.id()));
            if (candidate != null) return candidate.output();
        }
        return pattern.primaryOutput();
    }

    private @Nullable AEKey configuredPrimaryOutput(RecipePattern pattern) {
        AEKey override = primaryOverrides.get(pattern.id());
        if (override != null && Arrays.stream(pattern.pattern().getOutputs()).anyMatch(stack -> stack.what().equals(override))) return override;
        return null;
    }

    private int effectiveCircuit(RecipePattern recipe) {
        var policy=catalystPolicies.get(recipe.id());
        return policy==null?recipe.circuit():org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.circuit(recipe,policy);
    }

    private List<AEItemKey> effectiveVirtualInputs(String recipeId) {
        var policy=catalystPolicies.get(recipeId);
        if(policy==null)return virtualInputKeys.getOrDefault(recipeId,List.of());
        var recipe=getRecipePattern(recipeId);
        if(recipe==null)return List.of();
        var tokens=new LinkedHashSet<AEItemKey>();
        for(var alternatives:recipe.nonConsumedInputs())for(var key:alternatives) {
            if(org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply.external(policy,key))continue;
            var token=virtualSupplies.get(key);if(token!=null){tokens.add(token);break;}
        }
        return List.copyOf(tokens);
    }

    public IPatternDetails getPreviewPattern(RecipePattern recipePattern) {
        return previewPatterns.getOrDefault(recipePattern.id(), recipePattern.pattern());
    }

    private @Nullable IPatternDetails buildPreviewPattern(RecipePattern recipePattern) {
        IPatternDetails original = recipePattern.pattern();
        var delivery=catalystPolicies.get(recipePattern.id());
        if(delivery!=null){var bound=candidateForFamily(recipePattern.id(),delivery.family());if(bound!=null)delivery=delivery.forMaterial(bound.materialId().toString());original=org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryPolicy.apply(delivery,original,getLevel());if(original==null)return null;}
        if(delivery!=null){original=org.gtlcore.universalpatterns.integration.ae2.handler.DirectCatalystSupply.apply(delivery,recipePattern,original,getLevel());if(original==null)return null;}
        AEKey primary = getPrimaryOutput(recipePattern);
        if(delivery!=null&&!delivery.primaryOutputs().isEmpty())primary=org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryPolicy.primary(delivery);
        List<AEItemKey> virtualInputs = effectiveVirtualInputs(recipePattern.id());
        if (!inputOverrides.containsKey(recipePattern.id()) && virtualInputs.isEmpty() &&
                (primary == null || original.getPrimaryOutput().what().equals(primary)))
            return original;
        if (!(original instanceof AEProcessingPattern processing)) return original;
        List<GenericStack> outputs = new ArrayList<>(Arrays.stream(processing.getSparseOutputs()).filter(Objects::nonNull).toList());
        if (primary != null) {var chosenPrimary=primary;outputs.sort(Comparator.comparing(stack -> !stack.what().equals(chosenPrimary)));}
        List<GenericStack> inputs = new ArrayList<>();
        // One token grants unlimited matching supply for its non-consumed payload, independently of recipe quantity.
        for (AEItemKey token : virtualInputs) inputs.add(new GenericStack(token, 1));
        try {
            Map<AEKey, Long> configuredInputs = new LinkedHashMap<>();
            for (InputSlot slot : recipePattern.inputSlots()) {
                configuredInputs.merge(getInputChoice(recipePattern, slot), slot.amount(), Math::addExact);
            }
            var consumed=configuredInputs.entrySet().stream().map(e->new GenericStack(e.getKey(),e.getValue())).toList();
            inputs.addAll(delivery==null?consumed:org.gtlcore.universalpatterns.integration.ae2.handler.DeliveryPolicy.inputs(delivery,consumed));
            if(inputs.isEmpty())return null;
            ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new), outputs.toArray(GenericStack[]::new));
            encoded.getOrCreateTag().putString("gtlcoreRecipe", recipePattern.id());
            encoded.getOrCreateTag().putInt("gtlcoreRecipeCircuit", recipePattern.circuit());
            var decoded=PatternDetailsHelper.decodePattern(encoded, getLevel());
            return delivery==null||decoded==null?decoded:org.gtlcore.universalpatterns.integration.ae2.handler.DirectCatalystSupply.apply(delivery,recipePattern,decoded,getLevel());
        } catch (IllegalArgumentException | ArithmeticException ignored) {
            return null;
        }
    }

    public @Nullable InputSlot getInputSlot(RecipePattern pattern, String slotId) {
        return pattern.inputSlots().stream().filter(slot -> slot.id().equals(slotId)).findFirst().orElse(null);
    }

    public AEKey getInputChoice(RecipePattern pattern, InputSlot slot) {
        AEKey selected = inputOverrides.getOrDefault(pattern.id(), Map.of()).get(slot.id());
        return selected != null && slot.alternatives().contains(selected) ? selected : slot.defaultKey();
    }

    public List<AEItemKey> getVirtualInputKeys(RecipePattern pattern) {
        return effectiveVirtualInputs(pattern.id());
    }

    public void setInputChoice(String recipeId, String slotId, @Nullable AEKey choice) {
        RecipePattern pattern = patternById.get(recipeId);
        if (isRemote() || pattern == null) return;
        InputSlot slot = getInputSlot(pattern, slotId);
        if (slot == null || (choice != null && !slot.alternatives().contains(choice))) return;
        Map<String, AEKey> choices = inputOverrides.get(recipeId);
        boolean changed;
        if (choice == null || choice.equals(slot.defaultKey())) {
            changed = choices != null && choices.remove(slotId) != null;
            if (choices != null && choices.isEmpty()) inputOverrides.remove(recipeId);
        } else {
            if (choices == null) {
                choices = new HashMap<>();
                inputOverrides.put(recipeId, choices);
            }
            changed = !choice.equals(choices.put(slotId, choice));
        }
        if (changed) configurationChanged();
    }

    public void setRecipeBlacklisted(String id, boolean excluded) {
        if (isRemote() || (excluded && !patternById.containsKey(id))) return;
        boolean changed = excluded ? blacklist.add(id) : blacklist.remove(id);
        if (changed) configurationChanged();
    }

    public void setRecipeWhitelisted(String id, boolean allowed) {
        if (isRemote() || (allowed && !patternById.containsKey(id))) return;
        boolean changed = allowed ? whitelist.add(id) : whitelist.remove(id);
        if (changed) configurationChanged();
    }

    public void setPrimaryOutput(String id, @Nullable AEKey primary) {
        RecipePattern pattern = patternById.get(id);
        if (isRemote() || pattern == null) return;
        if (primary == null) {
            if (primaryOverrides.remove(id) != null) configurationChanged();
        } else if (Arrays.stream(pattern.pattern().getOutputs()).anyMatch(stack -> stack.what().equals(primary))) {
            if (!primary.equals(primaryOverrides.put(id, primary))) configurationChanged();
        }
    }

    public boolean hasUniversalPattern(int slot) {
        return universalPatternSlot.getStackInSlot(slot).getItem() instanceof UniversalPatternItem;
    }

    private boolean hasAnyUniversalPattern() {
        for (int slot = 0; slot < UNIVERSAL_PATTERN_SLOTS; slot++) {
            if (hasUniversalPattern(slot)) return true;
        }
        return false;
    }

    public Selection universalSelection(int slot) {
        return universalSelections.get(slot);
    }

    public @Nullable Candidate universalCandidate(String id) {
        return universalCandidates.get(id);
    }

    public @Nullable Candidate universalCandidate(int slot, String id) {
        Candidate bound = candidateForFamily(id, universalSelection(slot).family());
        return bound == null ? universalCandidate(id) : bound;
    }

    public @Nullable Candidate candidateForFamily(String id, String family) {
        Candidate preferred = universalCandidates.get(id);
        if (preferred != null && preferred.family().equals(family)) return preferred;
        Candidate casting = RegisteredProcessFamilies.legacyCasting(preferred, family);
        if (casting != null) return casting;
        Candidate legacy = legacyUniversalCandidates.get(id);
        return legacy != null && legacy.family().equals(family) ? legacy : null;
    }

    public int universalSelectedCount(int slot) {
        return effectiveUniversalSelections.get(slot).size();
    }

    public boolean isInUniversalPattern(int slot, String id) {
        return universalReason(universalSelection(slot), id) <= 2;
    }

    public void bindUniversalFamily(int slot, String id) {
        if (isRemote() || !hasUniversalPattern(slot) || !universalSelection(slot).canBind()) return;
        Candidate candidate = universalCandidates.get(id);
        int status = getRecipeStatus(id);
        if (candidate == null || candidate.family().length() > 512 || (status != 0 && status != 7)) return;
        saveUniversalSelection(slot, new Selection(candidate.family(), Set.of(), Set.of(), "all", "").withSupplyFrom(universalSelection(slot)));
    }

    public void selectUniversalMaterials(int slot, List<String> recipeIds, boolean enabled) {
        if (isRemote() || !hasUniversalPattern(slot)) return;
        Selection selection = universalSelection(slot);
        if (selection.family().isEmpty()) return;
        var materials = new HashSet<>(selection.materials());
        var excluded = new HashSet<>(selection.excluded());
        for (String id : recipeIds) {
            Candidate candidate = candidateForFamily(id, selection.family());
            if (candidate == null) continue;
            if (enabled) {
                int status = getRecipeStatus(id);
                if (status == 0 || status == 7) {
                    materials.add(candidate.materialId());
                    excluded.remove(candidate.materialId());
                }
            } else {
                materials.remove(candidate.materialId());
                excluded.add(candidate.materialId());
            }
        }
        if (materials.size() <= UniversalPatternItem.MAX_MATERIALS && excluded.size() <= UniversalPatternItem.MAX_MATERIALS) {
            saveUniversalSelection(slot, new Selection(selection.family(), Set.copyOf(materials), Set.copyOf(excluded), selection.allow(), selection.deny(), selection.recipes(), selection.externalCatalysts(), selection.catalystModes(), selection.requiredFluid(),selection.fixedInputs()).withDeliveryFrom(selection));
        }
    }

    public void setUniversalRules(int slot, String allow, String deny) {
        if (isRemote() || !hasUniversalPattern(slot)) return;
        Selection selection = universalSelection(slot);
        if (selection.family().isEmpty()) return;
        UniversalMaterialRules.compile(allow, getLevel());
        UniversalMaterialRules.compile(deny, getLevel());
        saveUniversalSelection(slot, new Selection(selection.family(), selection.materials(), selection.excluded(), allow.strip(), deny.strip(), selection.recipes(), selection.externalCatalysts(), selection.catalystModes(), selection.requiredFluid(),selection.fixedInputs()).withDeliveryFrom(selection));
    }

    public ItemStack universalCardSnapshot(int slot) {
        return universalPatternSlot.getStackInSlot(slot).copy();
    }

    public boolean storeUniversalDraft(int slot, ItemStack original, Selection selection) {
        if (isRemote() || !hasUniversalPattern(slot) || !ItemStack.matches(original, universalCardSnapshot(slot))) return false;
        ItemStack stack = universalCardSnapshot(slot);
        UniversalPatternItem.writeDraft(stack, selection);
        universalPatternSlot.setStackInSlot(slot, stack);
        configurationRevision++;
        markDirty();
        return true;
    }

    public boolean discardUniversalDraft(int slot, ItemStack original) {
        if (isRemote() || !hasUniversalPattern(slot) || !ItemStack.matches(original, universalCardSnapshot(slot))) return false;
        ItemStack stack = universalCardSnapshot(slot);
        UniversalPatternItem.discardDraft(stack);
        universalPatternSlot.setStackInSlot(slot, stack);
        configurationRevision++;
        markDirty();
        return true;
    }

    public boolean applyUniversalDraft(int slot, ItemStack original, Selection selection) {
        if (isRemote() || !hasUniversalPattern(slot) || !ItemStack.matches(original, universalCardSnapshot(slot))) return false;
        UniversalMaterialRules.compile(selection.allow(), getLevel());
        UniversalMaterialRules.compile(selection.deny(), getLevel());
        ItemStack stack = universalCardSnapshot(slot);
        UniversalPatternItem.write(stack, selection);
        UniversalPatternItem.discardDraft(stack);
        universalPatternSlot.setStackInSlot(slot, stack);
        configurationChanged();
        return true;
    }

    /** Zero through two include a recipe. Other values explain its exclusion. */
    public int universalReason(Selection selection, String recipeId) {
        Candidate candidate = candidateForFamily(recipeId, selection.family());
        if (candidate == null) return 3;
        int status = getRecipeStatus(recipeId);
        if (status != 0 && status != 7) return 9 + status;
        if(!org.gtlcore.universalpatterns.integration.ae2.handler.ConsumedFluidFilter.matches(selection,getRecipePattern(recipeId)))return 8;
        var material = candidate.materialId();
        Boolean override=selection.recipeOverrides().get(recipeId);
        if(override!=null)return override?1:4;
        if (selection.excluded().contains(material)) return 4;
        if (savedRule(selection.deny(), true).test(candidate)) return 5;
        String chosen = selection.recipes().get(material);
        if (chosen != null && !chosen.equals(recipeId)) return 6;
        if (selection.materials().contains(material)) return 1;
        return savedRule(selection.allow(), false).test(candidate) ? 0 : 7;
    }

    private void saveUniversalSelection(int slot, Selection selection) {
        if (universalSelection(slot).equals(selection)) return;
        ItemStack stack = universalPatternSlot.getStackInSlot(slot).copy();
        UniversalPatternItem.write(stack, selection);
        universalPatternSlot.setStackInSlot(slot, stack);
        configurationChanged();
    }

    public void addFilterRule(Target target, String expression) {
        addRule(filterRules, target, expression);
    }

    public void addWhitelistRule(Target target, String expression) {
        addRule(whitelistRules, target, expression);
    }

    private void addRule(List<Rule> rules, Target target, String expression) {
        if (isRemote() || expression.isBlank() || expression.length() > MERecipePatternFilter.MAX_EXPRESSION_LENGTH ||
                rules.size() >= MERecipePatternFilter.MAX_RULES)
            return;
        Rule rule = new Rule(target, expression);
        if (!rules.contains(rule)) {
            rules.add(rule);
            rebuildRuleMatches();
            configurationChanged();
        }
    }

    public void removeFilterRule(Rule rule) {
        removeRule(filterRules, rule);
    }

    public void removeWhitelistRule(Rule rule) {
        removeRule(whitelistRules, rule);
    }

    private void removeRule(List<Rule> rules, Rule rule) {
        if (!isRemote() && rules.remove(rule)) {
            rebuildRuleMatches();
            configurationChanged();
        }
    }

    private void configurationChanged() {
        configurationRevision++;
        // Keep already accepted jobs in their original recipe slots; new jobs use the updated published patterns.
        refreshCraftingPatterns();
        markDirty();
    }

    private ListTag collectSlotData() {
        ListTag saved = new ListTag();
        for (var entry : Int2ReferenceMaps.fastIterable(internalSlots)) {
            CompoundTag inventory = entry.getValue().serializeNBT();
            if (inventory.isEmpty()) continue;
            CompoundTag data = new CompoundTag();
            data.put("pattern", recipePatterns.get(entry.getIntKey()).pattern().getDefinition().toTag());
            data.put("slot", inventory);
            saved.add(data);
        }
        return saved;
    }

    private void restoreSlotData(ListTag saved) {
        Map<AEItemKey, Integer> definitions = new HashMap<>();
        for (int index = 0; index < recipePatterns.size(); index++) {
            definitions.put(recipePatterns.get(index).pattern().getDefinition(), index);
        }
        for (int index = 0; index < saved.size(); index++) {
            CompoundTag data = saved.getCompound(index);
            Integer slot = definitions.get(AEItemKey.fromTag(data.getCompound("pattern")));
            if (slot != null && !internalSlots.containsKey(slot)) {
                InternalSlot inventory = getInternalSlot(slot);
                inventory.deserializeNBT(data.getCompound("slot"));
                restoreCircuit(inventory, effectiveCircuit(recipePatterns.get(slot)));
                if (inventory.isActive()) activeSlots.add(slot);
            } else {
                refundPersistedSlot(data.getCompound("slot"));
            }
        }
        AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource);
    }

    private void refundPersistedSlot(CompoundTag data) {
        Object2LongOpenHashMap<AEItemKey> items = new Object2LongOpenHashMap<>();
        Object2LongOpenHashMap<AEFluidKey> fluids = new Object2LongOpenHashMap<>();
        AEUtils.appendPersistedInventory(data.getList("inventory", Tag.TAG_COMPOUND), AEItemKey::fromTag, items);
        AEUtils.appendPersistedInventory(data.getList("fluidInventory", Tag.TAG_COMPOUND), AEFluidKey::fromTag, fluids);
        refundSlot(items, fluids);
    }

    private void notifyInputsChanged() {
        markDirty();
        recipeHandler.notifyRecipeListeners();
    }

    @Override
    protected void update() {
        super.update();
        if (!buffer.isEmpty() && AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource)) markDirty();
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputHolder) {
        refreshPatterns();
        return super.pushPattern(pattern, inputHolder);
    }

    @Override
    public boolean gtlcore$canProduceGraphPattern(IPatternDetails pattern) {
        refreshPatterns();
        return super.gtlcore$canProduceGraphPattern(pattern);
    }

    @Override
    protected @Nullable Integer getSlotIndexForPattern(IPatternDetails pattern) {
        int slot = patternToSlot.getInt(pattern);
        return slot < 0 ? null : slot;
    }

    @Override
    protected int getInternalSlotCount() {
        return recipePatterns.size();
    }

    @Override
    protected boolean hasPatternInSlot(int slot) {
        return slot >= 0 && slot < recipePatterns.size();
    }

    @Override
    protected InternalSlot getInternalSlot(int index) {
        InternalSlot current=internalSlots.computeIfAbsent(index, slotIndex -> {
            InternalSlot slot = new InternalSlot(slotIndex);
            restoreCircuit(slot, effectiveCircuit(recipePatterns.get(slotIndex)));
            recipeCache.put(slotIndex, recipePatterns.get(slotIndex).recipe());
            slot.setOnContentsChanged(() -> {
                if (slot.isActive()) activeSlots.add(slotIndex);
                else activeSlots.remove(slotIndex);
                notifyInputsChanged();
            });
            return slot;
        });
        if (!current.isActive()) restoreCircuit(current,effectiveCircuit(recipePatterns.get(index)));
        return current;
    }

    private static void restoreCircuit(InternalSlot slot, int circuit) {
        if (circuit >= 0) slot.getCacheManager().setCircuitCache(circuit);
        else slot.getCacheManager().clearCircuitCache();
    }

    public boolean matchesRecipe(int slot, GTRecipe recipe) {
        if (!hasPatternInSlot(slot)) return false;
        GTRecipe expected = recipePatterns.get(slot).recipe();
        return expected.recipeType == recipe.recipeType && Objects.equals(expected.id, recipe.id);
    }

    @Override
    protected @Nullable InternalSlot getInternalSlotOrNull(int index) {
        return internalSlots.get(index);
    }

    @Override
    protected int[] getActiveAndUnCachedSlots() {
        // Generated slots already know their recipe and do not need ingredient-tree discovery.
        return new int[0];
    }

    @Override
    public Pair<Object2LongOpenHashMap<Item>, Object2LongOpenHashMap<Fluid>> getMergedInternalSlot() {
        Object2LongOpenHashMap<Item> items = new Object2LongOpenHashMap<>();
        Object2LongOpenHashMap<Fluid> fluids = new Object2LongOpenHashMap<>();
        for (InternalSlot slot : internalSlots.values()) {
            MEPatternBufferJadeMerger.mergeItemKeys(items, slot.getItemInventory());
            MEPatternBufferJadeMerger.mergeFluidKeys(fluids, slot.getFluidInventory());
        }
        MEPatternBufferJadeMerger.mergeItemStacks(items, sharedCatalystInventory.getContents());
        MEPatternBufferJadeMerger.mergeFluidStacks(fluids, sharedCatalystTank.getContents());
        return Pair.of(items, fluids);
    }

    @Override
    public long gtlcore$graphCapacity(IPatternDetails pattern, Map<AEKey, Long> inputPerRun, long requested) {
        if (!gtlcore$canProduceGraphPattern(pattern)) return 0;
        InternalSlot slot = internalSlots.get(patternToSlot.getInt(pattern));
        if (slot == null) return requested;
        for (var input : inputPerRun.entrySet()) {
            long held;
            if (input.getKey() instanceof AEItemKey item) held = slot.getItemInventory().getLong(item);
            else if (input.getKey() instanceof AEFluidKey fluid) held = slot.getFluidInventory().getLong(fluid);
            else return 0;
            if (held < 0) return 0;
            requested = Math.min(requested, (Long.MAX_VALUE - held) / input.getValue());
        }
        return requested;
    }

    @Override
    protected boolean hasRecipeCacheInSlot(int slot) {
        return recipeCache.containsKey(slot);
    }

    @Override
    protected int[] getActiveSlots() {
        activeSlots.removeIf(index -> !internalSlots.get(index).isActive());
        return activeSlots.toIntArray();
    }

    @Override
    protected Iterable<InternalSlot> getActiveInternalSlots() {
        List<InternalSlot> slots = new ObjectArrayList<>();
        for (int index : getActiveSlots()) slots.add(internalSlots.get(index));
        return slots;
    }

    @Override
    protected void refundAll(ClickData clickData) {
        if (clickData.isRemote) return;
        for (InternalSlot slot : internalSlots.values()) {
            refundSlot(slot.getItemInventory(), slot.getFluidInventory());
            slot.clearVirtualSupply();
        }
        activeSlots.clear();
        for (int index = 0; index < pendingSlotData.size(); index++) {
            refundPersistedSlot(pendingSlotData.getCompound(index).getCompound("slot"));
        }
        pendingSlotData = new ListTag();
        AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource);
        notifyInputsChanged();
    }

    @Override
    public void saveCustomPersistedData(@NotNull CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        ListTag saved = collectSlotData();
        saved.addAll(pendingSlotData);
        tag.put(SLOT_DATA_KEY, saved);
        tag.put("recipeBlacklist", saveRecipeList(blacklist));
        tag.put("recipeWhitelist", saveRecipeList(whitelist));
        tag.put("recipeFilterRules", saveRules(filterRules));
        tag.put("recipeWhitelistRules", saveRules(whitelistRules));
        ListTag primaries = new ListTag();
        primaryOverrides.forEach((id, primary) -> {
            CompoundTag data = new CompoundTag();
            data.putString("recipe", id);
            data.put("output", primary.toTagGeneric());
            primaries.add(data);
        });
        tag.put("recipePrimaryOutputs", primaries);
        ListTag inputs = new ListTag();
        inputOverrides.forEach((recipeId, choices) -> choices.forEach((slotId, choice) -> {
            CompoundTag data = new CompoundTag();
            data.putString("recipe", recipeId);
            data.putString("input", slotId);
            data.put("material", choice.toTagGeneric());
            inputs.add(data);
        }));
        tag.put("recipeInputChoices", inputs);
    }

    private static ListTag saveRecipeList(Set<String> ids) {
        ListTag saved = new ListTag();
        ids.forEach(id -> saved.add(StringTag.valueOf(id)));
        return saved;
    }

    private static ListTag saveRules(List<Rule> filterRules) {
        ListTag rules = new ListTag();
        for (Rule rule : filterRules) {
            CompoundTag data = new CompoundTag();
            data.putString("target", rule.target().name());
            data.putString("expression", rule.expression());
            rules.add(data);
        }
        return rules;
    }

    @Override
    public void loadCustomPersistedData(@NotNull CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        pendingSlotData = tag.getList(SLOT_DATA_KEY, Tag.TAG_COMPOUND).copy();
        loadRecipeList(tag.getList("recipeBlacklist", Tag.TAG_STRING), blacklist);
        loadRecipeList(tag.getList("recipeWhitelist", Tag.TAG_STRING), whitelist);
        loadRules(tag.getList("recipeFilterRules", Tag.TAG_COMPOUND), filterRules);
        loadRules(tag.getList("recipeWhitelistRules", Tag.TAG_COMPOUND), whitelistRules);
        rebuildRuleMatches();
        primaryOverrides.clear();
        ListTag primaries = tag.getList("recipePrimaryOutputs", Tag.TAG_COMPOUND);
        for (int index = 0; index < primaries.size(); index++) {
            CompoundTag data = primaries.getCompound(index);
            AEKey primary = AEKey.fromTagGeneric(data.getCompound("output"));
            if (primary != null) primaryOverrides.put(data.getString("recipe"), primary);
        }
        inputOverrides.clear();
        ListTag inputs = tag.getList("recipeInputChoices", Tag.TAG_COMPOUND);
        for (int index = 0; index < inputs.size(); index++) {
            CompoundTag data = inputs.getCompound(index);
            AEKey choice = AEKey.fromTagGeneric(data.getCompound("material"));
            if (choice != null) inputOverrides.computeIfAbsent(data.getString("recipe"), ignored -> new HashMap<>())
                    .put(data.getString("input"), choice);
        }
        reloadGeneration = -1;
    }

    private static void loadRecipeList(ListTag saved, Set<String> ids) {
        ids.clear();
        for (int index = 0; index < saved.size(); index++) ids.add(saved.getString(index));
    }

    private static void loadRules(ListTag rules, List<Rule> filterRules) {
        filterRules.clear();
        for (int index = 0; index < rules.size() && filterRules.size() < MERecipePatternFilter.MAX_RULES; index++) {
            CompoundTag data = rules.getCompound(index);
            String expression = data.getString("expression");
            if (expression.isBlank() || expression.length() > MERecipePatternFilter.MAX_EXPRESSION_LENGTH) continue;
            try {
                Rule rule = new Rule(Target.valueOf(data.getString("target")), expression);
                if (!filterRules.contains(rule)) filterRules.add(rule);
            } catch (IllegalArgumentException ignored) {
                // Unknown targets from a newer save do not prevent the hatch from loading.
            }
        }
    }

    @Override
    public @NotNull ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        panel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                GuiTextures.PATTERN_OVERLAY, GuiTextures.PATTERN_OVERLAY,
                () -> universalPatternMode || hasAnyUniversalPattern(), (click, enabled) -> {
                    if (!click.isRemote && !hasAnyUniversalPattern()) {
                        universalPatternMode = enabled;
                        configurationChanged();
                    }
                }).setTooltipsSupplier(enabled -> List.of(Component.translatable(
                        "gtceu.machine.me_recipe_pattern_buffer." + (enabled ? "pattern_mode_on" : "pattern_mode_off")))));
        panel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                org.gtlcore.gtlcore.api.gui.GuiTextures.BUTTON_DISABLE_BYPRODUCT.getSubTexture(0, 0, 1, 0.5),
                org.gtlcore.gtlcore.api.gui.GuiTextures.BUTTON_DISABLE_BYPRODUCT.getSubTexture(0, 0.5, 1, 0.5),
                () -> !keepByProduct, (clickData, pressed) -> {
                    if (clickData.isRemote) return;
                    keepByProduct = !pressed;
                    configurationChanged();
                }).setTooltipsSupplier(pressed -> List.of(Component.translatable("tooltip.gtlcore.disable_by_product")
                        .append(Component.translatable(pressed ? "gtceu.multiblock.universal.distinct.yes" : "gtceu.multiblock.universal.distinct.no")))));
    }

    @Override
    public void attachSideTabs(TabsWidget sideTabs) {
        super.attachSideTabs(sideTabs);
        sideTabs.attachSubTab(new RecipePatternManagementPage(this, Page.SEARCH));
        sideTabs.attachSubTab(new RecipePatternManagementPage(this, Page.UNIVERSAL));
        sideTabs.attachSubTab(new RecipePatternManagementPage(this, Page.FILTERS));
    }

    @Override
    public ModularUI createUI(Player player) {
        return new ModularUI(176, 166, this, player)
                .widget(new RecipePatternMachineUIWidget(this, 176, 166));
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 248, 158);
        group.addWidget(new LabelWidget(8, 4, () -> isOnline ? "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline")
                .setTextColor(0xFF303541).setDropShadow(false));
        group.addWidget(new AETextInputButtonWidget(180, 4, 60, 10).setText(customName).setOnConfirm(this::setCustomName)
                .setButtonTooltips(Component.translatable("gui.gtceu.rename.desc")));
        group.addWidget(new LabelWidget(8, 23, () -> Component.translatable("gtceu.machine.me_recipe_pattern_buffer.mode",
                activeRecipeTypes.isEmpty() ? Component.translatable("gtceu.machine.me_recipe_pattern_buffer.unformed").getString() :
                        activeRecipeTypes.stream().map(type -> Component.translatable(type.registryName.toLanguageKey()).getString())
                                .collect(Collectors.joining(", ")))
                .getString()).setTextColor(0xFF303541).setDropShadow(false));
        group.addWidget(new LabelWidget(8, 40, "item.gtlcore.universal_pattern").setTextColor(0xFF303541).setDropShadow(false));
        for (int slot = 0; slot < UNIVERSAL_PATTERN_SLOTS; slot++) {
            group.addWidget(new SlotWidget(universalPatternSlot, slot, 162 + 20 * slot, 35)
                    .setChangeListener(() -> {
                        if (!isRemote()) {
                            if (hasAnyUniversalPattern()) universalPatternMode = true;
                            configurationChanged();
                        }
                    })
                    .setBackground(GuiTextures.SLOT, GuiTextures.PATTERN_OVERLAY));
        }
        group.addWidget(new RecipePatternBrowserWidget(8, 55, this, Page.OVERVIEW));
        group.addWidget(new LabelWidget(8, 141, () -> Component.translatable("gtceu.machine.me_recipe_pattern_buffer.buffer_counts",
                getActiveSlots().length, buffer.size()).getString()).setTextColor(0xFF616978).setDropShadow(false));
        return group;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return Collections.unmodifiableList(availablePatterns);
    }

    @Override
    public boolean isVisibleInTerminal() {
        return false;
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return InternalInventory.empty();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        return new PatternContainerGroup(AEItemKey.of(getDefinition().asStack()),
                customName.isEmpty() ? getDefinition().getItem().getDescription() : Component.literal(customName), List.of());
    }

    @Override
    protected @NotNull RecipeMEPatternTrait createMETrait() {
        return new RecipeMEPatternTrait();
    }

    @Override
    public Pair<IMERecipeHandlerTrait<Ingredient, ItemStack>, IMERecipeHandlerTrait<FluidIngredient, FluidStack>> getMERecipeHandlerTraits() {
        return Pair.of(recipeHandler.getMeItemHandler(), recipeHandler.getMeFluidHandler());
    }

    private class Ticker implements IGridTickable {

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(5, 60, false, true);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!getMainNode().isActive() || buffer.isEmpty()) return TickRateModulation.SLEEP;
            if (AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource)) {
                markDirty();
                return TickRateModulation.URGENT;
            }
            return TickRateModulation.SLOWER;
        }
    }

    protected class RecipeMEPatternTrait extends MEIOTrait implements IMEPatternTrait {

        public RecipeMEPatternTrait() {
            super(MERecipePatternBufferPartMachine.this);
        }

        @Override
        public @NotNull ObjectSet<@NotNull GTRecipe> getCachedGTRecipe() {
            ObjectSet<GTRecipe> recipes = new ObjectOpenHashSet<>();
            for (int slot : getActiveSlots()) recipes.add(recipeCache.get(slot));
            return recipes;
        }

        @Override
        public void setSlotCacheRecipe(int index, GTRecipe recipe) {
            // Each generated slot belongs to one known GT recipe; matching must not replace that binding.
            if (hasPatternInSlot(index)) recipeCache.put(index, recipePatterns.get(index).recipe());
        }

        @Override
        public @NotNull Int2ReferenceMap<ObjectSet<@NotNull GTRecipe>> getSlot2RecipesCache() {
            Int2ReferenceMap<ObjectSet<@NotNull GTRecipe>> slots = new Int2ReferenceOpenHashMap<>();
            for (var entry : Int2ReferenceMaps.fastIterable(recipeCache)) {
                slots.put(entry.getIntKey(), new ObjectArraySet<>(new GTRecipe[] { entry.getValue() }));
            }
            return slots;
        }

        @Override
        public void setOnPatternChange(IntConsumer removeMapOnSlot) {
            removeSlotFromMap = removeMapOnSlot;
        }

        @Override
        public boolean hasCacheInSlot(int slot) {
            return recipeCache.containsKey(slot);
        }
    }
}
