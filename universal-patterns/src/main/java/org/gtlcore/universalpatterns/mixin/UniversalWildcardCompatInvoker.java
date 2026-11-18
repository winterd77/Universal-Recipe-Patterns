package org.gtlcore.universalpatterns.mixin;
import appeng.api.crafting.IPatternDetails;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;
@Mixin(targets="org.gtlcore.gtlcore.integration.wildcard.WildcardPatternCompatImpl",remap=false)
public interface UniversalWildcardCompatInvoker {
    @Invoker("expandPatterns")
    static List<IPatternDetails> universalPatterns$expandOriginal(ItemStack card, Level level) { throw new AssertionError(); }
}
