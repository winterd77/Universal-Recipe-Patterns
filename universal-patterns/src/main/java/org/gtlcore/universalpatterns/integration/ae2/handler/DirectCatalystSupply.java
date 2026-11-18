package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.crafting.*;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem.Selection;
import net.minecraft.world.level.Level;
import java.util.*;
/** Real delivery of reusable ingredients, separate from virtual tokens and circuit caches. */
public final class DirectCatalystSupply {
 public static IPatternDetails apply(Selection policy,RecipePattern recipe,IPatternDetails original,Level level){
  if(!(original instanceof AEProcessingPattern processing))return original;
  var direct=new LinkedHashMap<AEKey,Long>();
  var raw=recipe.recipe();
  for(var contents:List.of(raw.getInputContents(ItemRecipeCapability.CAP),raw.getTickInputContents(ItemRecipeCapability.CAP)))for(var content:contents){
   if(content.chance!=0)continue;
   var ingredient=ItemRecipeCapability.CAP.of(content.content);
   long amount=ingredient instanceof LongIngredient value?value.getActualAmount():ingredient instanceof SizedIngredient value?value.getAmount():1;
   for(var stack:ingredient.getItems()){
    var key=AEItemKey.of(stack);
    if(key!=null&&CatalystSupply.mode(policy,key)==3){direct.merge(key,amount,Math::max);break;}
   }
  }
  for(var contents:List.of(raw.getInputContents(FluidRecipeCapability.CAP),raw.getTickInputContents(FluidRecipeCapability.CAP)))for(var content:contents){
   if(content.chance!=0)continue;
   var ingredient=FluidRecipeCapability.CAP.of(content.content);
   for(var stack:ingredient.getStacks()){
    var key=AEFluidKey.of(stack.getFluid(),stack.getTag());
    if(key!=null&&CatalystSupply.mode(policy,key)==3){direct.merge(key,(long)ingredient.getAmount(),Math::max);break;}
   }
  }
  if(direct.isEmpty())return original;
  try{
   var inputs=new LinkedHashMap<AEKey,Long>();
   for(var stack:processing.getSparseInputs())if(stack!=null)inputs.merge(stack.what(),stack.amount(),Math::addExact);
   direct.forEach((key,amount)->{if(amount<=0)throw new IllegalArgumentException("Invalid catalyst amount");inputs.merge(key,amount,Math::addExact);});
   var encoded=PatternDetailsHelper.encodeProcessingPattern(inputs.entrySet().stream().map(e->new GenericStack(e.getKey(),e.getValue())).toArray(GenericStack[]::new),processing.getSparseOutputs());
   var tag=original.getDefinition().getReadOnlyStack().getTag();
   if(tag!=null){encoded.getOrCreateTag().putString("gtlcoreRecipe",tag.getString("gtlcoreRecipe"));encoded.getOrCreateTag().putInt("gtlcoreRecipeCircuit",CatalystSupply.circuit(recipe,policy));}
   return PatternDetailsHelper.decodePattern(encoded,level);
  }catch(IllegalArgumentException|ArithmeticException invalid){return null;}
 }
}
