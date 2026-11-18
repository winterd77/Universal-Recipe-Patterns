package org.gtlcore.universalpatterns.mixin;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import java.util.*;
@Mixin(targets = "appeng.integration.modules.jei.transfer.EncodePatternTransferHandler$ErrorRenderer", remap = false)
public class ExternalCatalystHintMixin {
    @ModifyArg(method = "showError", at = @At(value = "INVOKE", target = "Lappeng/integration/modules/jei/JEIPlugin;drawHoveringText(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;II)V"), index = 1, remap = false)
    private List<Component> externalHint(List<Component> lines) {
        var result = new ArrayList<>(lines);
        result.add(Component.translatable("gtlcore.pattern.external_hint"));
        return result;
    }
}
