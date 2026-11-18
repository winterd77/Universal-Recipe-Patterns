package org.gtlcore.universalpatterns.mixin;
import appeng.api.crafting.IPatternDetails;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternReload;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import appeng.api.stacks.AEItemKey;
import org.gtlcore.universalpatterns.integration.ae2.handler.WildcardPatternEditor;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalRecipeBinding;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternExpansion;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalVirtualSupply;
import org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply;
import appeng.api.stacks.AEKey;
import java.util.Map;
import net.minecraft.world.level.Level;
import org.gtlcore.universalpatterns.api.gui.RecipePatternManagementPage;
import org.gtlcore.universalpatterns.api.gui.RecipePatternMachineUIWidget;
import org.gtlcore.universalpatterns.client.gui.widget.RecipePatternBrowserWidget.Page;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.world.entity.player.Player;
/** Both wildcard-buffer variants already have isolated per-recipe inventories and refund logic. */
@Mixin(targets={"org.gtlcore.gtlcore.integration.wildcard.MEWildcardPatternBufferPartMachine",
        "org.gtlcore.gtlcore.integration.wildcard.MEStockingWildcardPatternBufferPartMachine"}, remap=false)
public abstract class UniversalWildcardRuntimeMixin extends MEPatternBufferPartMachineBase implements UniversalPatternReload.Target, UniversalRecipeBinding {
    protected UniversalWildcardRuntimeMixin(com.gregtechceu.gtceu.api.machine.IMachineBlockEntity holder, com.gregtechceu.gtceu.api.capability.recipe.IO io) { super(holder, io); }
    @Shadow(remap=false) @Final private ItemStackTransfer wildcardPatternSlot;
    @Shadow(remap=false) @Final private List<InternalSlot> internalSlots;
    @Shadow(remap=false) @Final private List<IPatternDetails> expandedPatterns;
    @Shadow(remap=false) protected abstract void onWildcardPatternChange();
    @Shadow(remap=false) private void refundPersistedSlotData(List<CompoundTag> slots) { throw new AssertionError(); }
    @Unique private ListTag universalPatterns$pending = new ListTag();
    @Unique private WildcardPatternEditor universalPatterns$editor;
    @Unique private Map<AEKey, AEItemKey> universalPatterns$supplies = Map.of();
    @Unique private long universalPatterns$lastSupplyCheck = Long.MIN_VALUE;
    @Unique private AEItemKey universalPatterns$identity(IPatternDetails pattern) {
        var tag=pattern.getDefinition().getReadOnlyStack().getTag();
        var recipe=tag==null?null:UniversalPatternExpansion.recipe(getLevel(),tag.getString("gtlcoreRecipe"));
        // Virtual tokens are a supply route, not recipe identity; availability may change on reconnect.
        return recipe==null?pattern.getDefinition():recipe.pattern().getDefinition();
    }
    @Redirect(method="refreshPatterns", at=@At(value="INVOKE", target="Lorg/gtlcore/gtlcore/integration/wildcard/WildcardPatternCompatImpl;expandPatterns(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Ljava/util/List;"))
    private List<IPatternDetails> universalPatterns$expandWithSupply(ItemStack card, Level level) {
        if (!(card.getItem() instanceof UniversalPatternItem)) return UniversalWildcardCompatInvoker.universalPatterns$expandOriginal(card,level);
        var patterns=UniversalPatternExpansion.expand(card,level);
        universalPatterns$supplies = CatalystSupply.filter(UniversalPatternItem.read(card),UniversalVirtualSupply.relevant(patterns,level,UniversalVirtualSupply.available(getMainNode().getGrid(),actionSource)));
        return UniversalVirtualSupply.decorate(patterns,level,universalPatterns$supplies);
    }
    @Inject(method="update", at=@At("RETURN"))
    private void universalPatterns$watchSupply(CallbackInfo ci) {
        if(isRemote() || getLevel()==null)return;
        long now=getLevel().getGameTime();
        if(universalPatterns$lastSupplyCheck!=Long.MIN_VALUE && now-universalPatterns$lastSupplyCheck<20)return;
        universalPatterns$lastSupplyCheck=now;
        var card=wildcardPatternSlot.getStackInSlot(0);
        if(!(card.getItem() instanceof UniversalPatternItem))return;
        var supplies=CatalystSupply.filter(UniversalPatternItem.read(card),UniversalVirtualSupply.relevant(expandedPatterns,getLevel(),UniversalVirtualSupply.available(getMainNode().getGrid(),actionSource)));
        if(!supplies.equals(universalPatterns$supplies))onWildcardPatternChange();
    }
    @Override public boolean universalPatterns$matchesRecipe(int slot, com.gregtechceu.gtceu.api.recipe.GTRecipe recipe) {
        if (!(wildcardPatternSlot.getStackInSlot(0).getItem() instanceof UniversalPatternItem)) return true;
        if (slot < 0 || slot >= expandedPatterns.size()) return false;
        var tag = expandedPatterns.get(slot).getDefinition().getReadOnlyStack().getTag();
        return tag != null && tag.getString("gtlcoreRecipe").equals(recipe.recipeType.registryName + "/" + recipe.id);
    }
    @Override public void attachSideTabs(TabsWidget tabs) {
        super.attachSideTabs(tabs);
        if (universalPatterns$editor == null)
            universalPatterns$editor = new WildcardPatternEditor(this, wildcardPatternSlot, this::onWildcardPatternChange);
        tabs.attachSubTab(new RecipePatternManagementPage(universalPatterns$editor, Page.UNIVERSAL));
    }
    @Override public ModularUI createUI(Player player) {
        return new ModularUI(176, 166, this, player).widget(new RecipePatternMachineUIWidget(this, 176, 166));
    }
    @Inject(method="saveCustomPersistedData", at=@At("RETURN"))
    private void universalPatterns$save(CompoundTag tag, boolean forDrop, CallbackInfo ci) {
        if (!(wildcardPatternSlot.getStackInSlot(0).getItem() instanceof UniversalPatternItem)) return;
        var entries = new ListTag();
        for (int i=0; i<internalSlots.size(); i++) {
            if (!internalSlots.get(i).isActive()) continue;
            var entry = new CompoundTag();
            entry.put("Pattern", universalPatterns$identity(expandedPatterns.get(i)).toStack().save(new CompoundTag()));
            entry.put("Slot", internalSlots.get(i).serializeNBT());
            entries.add(entry);
        }
        tag.put("UniversalDynamicSlots", internalSlots.isEmpty() && !universalPatterns$pending.isEmpty() ? universalPatterns$pending.copy() : entries);
        // Legacy wildcard output-only signatures collide when two real recipes make the same output.
        tag.remove("wildcardDynamicSlots");
    }
    @Inject(method="loadCustomPersistedData", at=@At("RETURN"))
    private void universalPatterns$load(CompoundTag tag, CallbackInfo ci) {
        universalPatterns$pending = tag.getList("UniversalDynamicSlots", Tag.TAG_COMPOUND).copy();
    }
    @Override public void universalPatterns$reload() {
        if (getLevel() != null && !isRemote() && getLevel().hasChunkAt(getPos()) &&
                com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(getLevel(), getPos()) == (Object)this &&
                wildcardPatternSlot.getStackInSlot(0).getItem() instanceof UniversalPatternItem) onWildcardPatternChange();
    }
    @Inject(method="refreshPatterns", at=@At("RETURN"))
    private void universalPatterns$circuits(boolean restore, CallbackInfo ci) {
        if (!(wildcardPatternSlot.getStackInSlot(0).getItem() instanceof UniversalPatternItem)) return;
        UniversalPatternReload.track(this);
        if (restore && !universalPatterns$pending.isEmpty()) {
            var byDefinition = new HashMap<AEItemKey, Integer>();
            for (int i=0; i<expandedPatterns.size(); i++) byDefinition.put(universalPatterns$identity(expandedPatterns.get(i)), i);
            var refunds = new ArrayList<CompoundTag>();
            for (int i=0; i<universalPatterns$pending.size(); i++) {
                var entry = universalPatterns$pending.getCompound(i);
                Integer slot = byDefinition.remove(AEItemKey.of(ItemStack.of(entry.getCompound("Pattern"))));
                if (slot == null) refunds.add(entry.getCompound("Slot"));
                else internalSlots.get(slot).deserializeNBT(entry.getCompound("Slot"));
            }
            if (!refunds.isEmpty()) refundPersistedSlotData(refunds);
        }
        universalPatterns$pending = new ListTag();
        for (int i=0; i<expandedPatterns.size(); i++) {
            var tag = expandedPatterns.get(i).getDefinition().getReadOnlyStack().getTag();
            if (tag != null && tag.contains("gtlcoreRecipeCircuit")) {
                var recipe=UniversalPatternExpansion.recipe(getLevel(),tag.getString("gtlcoreRecipe"));
                int circuit = recipe==null ? tag.getInt("gtlcoreRecipeCircuit") : CatalystSupply.circuit(recipe,UniversalPatternItem.read(wildcardPatternSlot.getStackInSlot(0)));
                if (circuit >= 0) internalSlots.get(i).getCacheManager().setCircuitCache(circuit);
                else internalSlots.get(i).getCacheManager().clearCircuitCache();
            }
        }
    }
}
