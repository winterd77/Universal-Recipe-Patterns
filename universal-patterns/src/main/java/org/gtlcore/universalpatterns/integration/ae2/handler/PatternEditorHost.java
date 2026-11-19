package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.*;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternFilter.*;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem.Selection;
import org.gtlcore.universalpatterns.common.machine.multiblock.part.ae.MERecipePatternBufferPartMachine.RecipeStatistics;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternFamily.Candidate;
import java.util.*;
/** Editor model shared by the legacy dedicated hatch and the existing wildcard hatch series. */
public interface PatternEditorHost {
    Level getLevel();
    long getConfigurationRevision();
    default int universalPatternSlots() { return 4; }
    default boolean cardEditorOnly() { return false; }
    boolean hasUniversalPattern(int slot);
    ItemStack universalCardSnapshot(int slot);
    boolean storeUniversalDraft(int slot, ItemStack original, Selection selection);
    boolean discardUniversalDraft(int slot, ItemStack original);
    boolean applyUniversalDraft(int slot, ItemStack original, Selection selection);
    Candidate universalCandidate(String id);
    Candidate candidateForFamily(String id, String family);
    default int universalReason(Selection selection, String id) {
        Candidate candidate = candidateForFamily(id, selection.family());
        if (candidate == null) return 3;
        int status = getRecipeStatus(id);
        if (status != 0 && status != 7) return 9 + status;
        if (!ConsumedFluidFilter.matches(selection, getRecipePattern(id))) return 8;
        return selection.recipeReason(id, candidate.materialId(),
                () -> ruleMatches(selection.allow(), candidate, false),
                () -> ruleMatches(selection.deny(), candidate, true));
    }

    private boolean ruleMatches(String rule, Candidate candidate, boolean fallback) {
        try { return UniversalMaterialRules.compile(rule, getLevel()).test(candidate); }
        catch (IllegalArgumentException invalid) { return fallback; }
    }
    default List<RecipePattern> familyRecipes(String family) { return searchRecipes("", Set.of()); }
    default boolean hasRecipeCatalog() { return true; }
    default List<RecipePattern> cachedRecipes() { return searchRecipes("", Set.of()); }
    List<RecipePattern> searchRecipes(String query, Set<String> localized);
    List<RecipePattern> searchRecipes(String query, Set<String> localized, int slot);
    List<RecipePattern> findUniversalRecipes(AEKey sample, String expression, int slot);
    List<String> universalSampleAttributes(AEKey sample, boolean forRecipes);
    RecipePattern getRecipePattern(String id);
    int getRecipeStatus(String id);
    RecipeStatistics getRecipeStatistics();
    default AEKey getPrimaryOutput(RecipePattern pattern) { return pattern.primaryOutput(); }
    default IPatternDetails getPreviewPattern(RecipePattern pattern) { return pattern.pattern(); }
    default InputSlot getInputSlot(RecipePattern pattern, String id) { return pattern.inputSlots().stream().filter(s->s.id().equals(id)).findFirst().orElse(null); }
    default AEKey getInputChoice(RecipePattern pattern, InputSlot slot) { return slot.defaultKey(); }
    default List<AEItemKey> getVirtualInputKeys(RecipePattern pattern) { return List.of(); }
    default List<String> getBlacklistedRecipes() { return List.of(); }
    default List<String> getWhitelistedRecipes() { return List.of(); }
    default List<Rule> getFilterRules() { return List.of(); }
    default List<Rule> getWhitelistRules() { return List.of(); }
    default boolean isRecipeWhitelisted(String id) { return false; }
    default boolean isWhitelistActive() { return false; }
    // Machine-wide mutations are not exposed by a card-only editor (checked before packet dispatch).
    default void setInputChoice(String recipe,String slot,AEKey key) { throw new UnsupportedOperationException(); }
    default void setPrimaryOutput(String id,AEKey key) { throw new UnsupportedOperationException(); }
    default void setRecipeBlacklisted(String id,boolean value) { throw new UnsupportedOperationException(); }
    default void setRecipeWhitelisted(String id,boolean value) { throw new UnsupportedOperationException(); }
    default void addFilterRule(Target target,String expression) { throw new UnsupportedOperationException(); }
    default void addWhitelistRule(Target target,String expression) { throw new UnsupportedOperationException(); }
    default void removeFilterRule(Rule rule) { throw new UnsupportedOperationException(); }
    default void removeWhitelistRule(Rule rule) { throw new UnsupportedOperationException(); }
}
