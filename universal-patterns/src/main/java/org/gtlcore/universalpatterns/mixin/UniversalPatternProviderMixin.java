package org.gtlcore.universalpatterns.mixin;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternExpansion;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternReload;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
/** Shared by AE2 and both ExtendedAE 36-slot provider forms. */
// wildcard_pattern overwrites updatePatterns at priority 1000. INVOKE injections into
// that merged body must have strictly higher priority (Mixin InjectionPoint.checkPriority).
@Mixin(value=PatternProviderLogic.class, priority=1100, remap=false)
public abstract class UniversalPatternProviderMixin implements UniversalPatternReload.Target {
    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow @Final private AppEngInternalInventory patternInventory;
    @Shadow @Final private List<IPatternDetails> patterns;
    @Shadow @Final private Set<AEKey> patternInputs;
    @Shadow public abstract void updatePatterns();
    @Override public void universalPatterns$reload() {
        var block = host.getBlockEntity();
        if (!block.isRemoved() && block.getLevel() != null && !block.getLevel().isClientSide) updatePatterns();
    }
    @Inject(method="updatePatterns", at=@At(value="INVOKE", target="Lappeng/api/networking/crafting/ICraftingProvider;requestUpdate(Lappeng/api/networking/IManagedGridNode;)V"))
    private void universalPatterns$expand(CallbackInfo ci) {
        UniversalPatternReload.track(this);
        var known = new HashSet<>(patterns);
        for (var card : patternInventory) {
            for (var pattern : UniversalPatternExpansion.expand(card, host.getBlockEntity().getLevel())) {
                if (!known.add(pattern)) continue;
                patterns.add(pattern);
                for (var input : pattern.getInputs()) for (var candidate : input.getPossibleInputs())
                    patternInputs.add(candidate.what().dropSecondary());
            }
        }
    }
}
