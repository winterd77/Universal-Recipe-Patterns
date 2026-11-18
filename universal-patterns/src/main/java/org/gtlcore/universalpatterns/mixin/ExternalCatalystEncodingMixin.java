package org.gtlcore.universalpatterns.mixin;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jei.transfer.EncodePatternTransferHandler;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import appeng.menu.me.items.PatternEncodingTermMenu;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.gtlcore.universalpatterns.integration.ae2.handler.ExternalCatalystInputs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import java.util.List;

@Mixin(value = EncodePatternTransferHandler.class, priority = 900)
public class ExternalCatalystEncodingMixin {
    @Unique private Object universalpatterns$recipe;
    @Inject(method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;", at = @At("HEAD"), remap = false)
    private void rememberRecipe(PatternEncodingTermMenu menu, Object recipeBase, IRecipeSlotsView slots, Player player, boolean max, boolean transfer, CallbackInfoReturnable<IRecipeTransferError> cir) {
        universalpatterns$recipe = recipeBase;
    }

    @Inject(method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;", at = @At("RETURN"), remap = false)
    private void releaseRecipe(PatternEncodingTermMenu menu, Object recipeBase, IRecipeSlotsView slots, Player player, boolean max, boolean transfer, CallbackInfoReturnable<IRecipeTransferError> cir) {
        universalpatterns$recipe = null;
    }
    @ModifyArg(method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
        at = @At(value = "INVOKE", target = "Lappeng/integration/modules/jeirei/EncodingHelper;encodeProcessingRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/util/List;Ljava/util/List;)V"), index = 1, remap = false)
    private List<List<GenericStack>> externalCatalysts(List<List<GenericStack>> inputs) {
        if (!Screen.hasAltDown()) return inputs;
        var result = ExternalCatalystInputs.remove(inputs, universalpatterns$recipe);
        if (result.isEmpty()) {
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(Component.translatable("gtlcore.pattern.external_empty"), false);
            return inputs;
        }
        return result;
    }
}
