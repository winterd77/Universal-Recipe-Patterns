package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.stacks.*;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternFilter;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternFilter.*;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem.Selection;
import org.gtlcore.universalpatterns.common.machine.multiblock.part.ae.MERecipePatternBufferPartMachine.RecipeStatistics;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternFamily.Candidate;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.Predicate;

/** Plain editor model: owns no machine, node, inventory, registration or recipe execution. */
public final class WildcardPatternEditor implements PatternEditorHost {
    public interface Context {
        Level getLevel();
        boolean isRemote();
        default boolean isValid() { return true; }
        Set<GTRecipeType> recipeTypes();
        void markDirty();
    }
    private final Context owner;
    private final ItemStackTransfer cards;
    private final Runnable publish;
    private long configurationRevision;
    private long indexedGeneration = -1;
    private long observedGeneration = -1;
    private Set<GTRecipeType> indexedTypes = Set.of();
    private ItemStack snapshot = ItemStack.EMPTY;
    private final List<RecipePattern> recipePatterns = new ArrayList<>();
    private final Map<String,RecipePattern> patternById = new LinkedHashMap<>();
    private final Map<String,Candidate> universalCandidates = new HashMap<>();
    private final Map<String,Candidate> legacyUniversalCandidates = new HashMap<>();
    private final Map<String,Descriptor> searchMetadata = new HashMap<>();
    private final Set<String> published = new HashSet<>();
    private Selection publishedSelection;
    private long publishedGeneration = -1;
    public WildcardPatternEditor(MEPatternBufferPartMachineBase owner, ItemStackTransfer cards, Runnable publish) {
        this(new Context() {
            public Level getLevel() { return owner.getLevel(); }
            public boolean isRemote() { return owner.isRemote(); }
            public Set<GTRecipeType> recipeTypes() { return MERecipePatternHelper.getActiveRecipeTypes(owner.getControllers()); }
            public void markDirty() { owner.markDirty(); }
        },cards,publish);
    }
    public WildcardPatternEditor(Context owner, ItemStackTransfer cards, Runnable publish) {
        this.owner=owner;this.cards=cards;this.publish=publish;
    }
    @Override public Level getLevel() { return owner.getLevel(); }
    @Override public int universalPatternSlots() { return 1; }
    @Override public boolean cardEditorOnly() { return true; }
    @Override public long getConfigurationRevision() {
        long generation=MERecipePatternHelper.getReloadGeneration();
        if (generation!=observedGeneration) { observedGeneration=generation;configurationRevision++; }
        var current=cards.getStackInSlot(0);
        if (!ItemStack.matches(snapshot,current)) { snapshot=current.copy();configurationRevision++; }
        return configurationRevision;
    }
    private void refresh() { refresh(null); }

    private void refresh(@Nullable String family) {
        if (getLevel()==null || getLevel().isClientSide) return;
        var applied = UniversalPatternItem.read(cards.getStackInSlot(0));
        long currentGeneration = MERecipePatternHelper.getReloadGeneration();
        if (!applied.equals(publishedSelection) || currentGeneration != publishedGeneration) {
            publishedSelection=applied;publishedGeneration=currentGeneration;published.clear();
            for (var pattern : UniversalPatternExpansion.expand(cards.getStackInSlot(0),getLevel())) {
                var tag=pattern.getDefinition().getReadOnlyStack().getTag();
                if(tag!=null)published.add(tag.getString("gtlcoreRecipe"));
            }
        }
        // Browsing must allow changing family, including another machine recipe type.
        var types=new HashSet<GTRecipeType>();
        if (family != null && !family.isBlank()) {
            var id=ResourceLocation.tryParse(family.split("\\|",2)[0]);
            if(id!=null) { var type=GTRegistries.RECIPE_TYPES.get(id); if(type!=null)types.add(type); }
        }
        if(types.isEmpty()) types.addAll(GTRegistries.RECIPE_TYPES.values());
        long generation=MERecipePatternHelper.getReloadGeneration();
        if(indexedGeneration==generation && indexedTypes.equals(types)) return;
        indexedGeneration=generation;indexedTypes=Set.copyOf(types);configurationRevision++;
        recipePatterns.clear();patternById.clear();universalCandidates.clear();legacyUniversalCandidates.clear();searchMetadata.clear();
        for(var recipe:MERecipePatternHelper.getRecipes(getLevel().getRecipeManager(),types)) {
            var pattern=MERecipePatternHelper.createPattern(recipe,getLevel());
            if(pattern==null)continue;
            recipePatterns.add(pattern);patternById.put(pattern.id(),pattern);
            searchMetadata.put(pattern.id(),MERecipePatternFilter.describe(recipe));
            var families=RecipeRoute.describe(pattern,pattern.primaryOutput(),true,getLevel());
            if(families.preferred()!=null)universalCandidates.put(pattern.id(),families.preferred());
            if(families.legacy()!=null)legacyUniversalCandidates.put(pattern.id(),families.legacy());
        }
    }
    @Override public boolean hasUniversalPattern(int slot) { return slot==0 && cards.getStackInSlot(0).getItem() instanceof UniversalPatternItem; }
    public Selection universalSelection(int slot) { return UniversalPatternItem.read(universalCardSnapshot(slot)); }
    @Override public ItemStack universalCardSnapshot(int slot) { return slot==0?cards.getStackInSlot(0).copy():ItemStack.EMPTY; }
    private boolean matches(int slot,ItemStack expected) { return !owner.isRemote() && owner.isValid() && hasUniversalPattern(slot) && ItemStack.matches(expected,universalCardSnapshot(slot)); }
    private void save(ItemStack stack,boolean applied) {
        cards.setStackInSlot(0,stack);configurationRevision++;snapshot=stack.copy();owner.markDirty();
        if(applied)publish.run();
    }
    @Override public boolean storeUniversalDraft(int slot,ItemStack expected,Selection selection) {
        if(!matches(slot,expected))return false;
        var stack=universalCardSnapshot(slot);UniversalPatternItem.writeDraft(stack,selection);save(stack,false);return true;
    }
    @Override public boolean discardUniversalDraft(int slot,ItemStack expected) {
        if(!matches(slot,expected))return false;
        var stack=universalCardSnapshot(slot);UniversalPatternItem.discardDraft(stack);save(stack,false);return true;
    }
    @Override public boolean applyUniversalDraft(int slot,ItemStack expected,Selection selection) {
        if(!matches(slot,expected))return false;
        UniversalMaterialRules.compile(selection.allow(),getLevel());UniversalMaterialRules.compile(selection.deny(),getLevel());
        var stack=universalCardSnapshot(slot);UniversalPatternItem.write(stack,selection);UniversalPatternItem.discardDraft(stack);save(stack,true);return true;
    }
    @Override public int getRecipeStatus(String id) {
        var pattern=patternById.get(id);
        if(pattern==null || pattern.primaryOutput()==null)return 3;
        if(pattern.chancedInput())return 6;
        return published.contains(id) ? 0 : 7;
    }
    @Override public RecipeStatistics getRecipeStatistics() {
        return new RecipeStatistics(published.size(),0,0,0,
            (int)recipePatterns.stream().filter(p->p.primaryOutput()==null).count(),
            (int)recipePatterns.stream().filter(RecipePattern::chancedInput).count(),0);
    }
    @Override public List<RecipePattern> familyRecipes(String family) { refresh(family); return cachedRecipes(); }
    @Override public boolean hasRecipeCatalog() { return indexedGeneration >= 0; }
    @Override public List<RecipePattern> cachedRecipes() { return List.copyOf(recipePatterns); }

    public List<RecipePattern> searchRecipes(String query, Set<String> localizedMaterials) {
        return searchRecipes(query, localizedMaterials, -1);
    }

    public List<RecipePattern> findUniversalRecipes(AEKey sample, String expression, int slot) {
        refresh();
        Predicate<Candidate> rule = expression.isBlank() ? candidate -> true : UniversalMaterialRules.compile(expression, getLevel());
        return recipePatterns.stream().filter(pattern -> {
            Candidate candidate = universalCandidate(slot, pattern.id());
            return candidate != null && candidate.hasRecipeSample(sample) && rule.test(candidate);
        }).toList();
    }

    public List<String> universalSampleAttributes(AEKey sample, boolean forRecipes) {
        refresh();
        return UniversalMaterialRules.sampleAttributes(sample, getLevel(), universalCandidates.values(), forRecipes);
    }

    public List<RecipePattern> searchRecipes(String query, Set<String> localizedMaterials, int universalSlot) {
        refresh();
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

    public @Nullable Candidate universalCandidate(String id) {
        return universalCandidates.get(id);
    }

    public @Nullable Candidate universalCandidate(int slot, String id) {
        Candidate bound = candidateForFamily(id, universalSelection(slot).family());
        return bound == null ? universalCandidate(id) : bound;
    }

    public @Nullable Candidate candidateForFamily(String id, String family) {
        return RecipeRoute.resolve(universalCandidates.get(id),legacyUniversalCandidates.get(id),family);
    }
}
