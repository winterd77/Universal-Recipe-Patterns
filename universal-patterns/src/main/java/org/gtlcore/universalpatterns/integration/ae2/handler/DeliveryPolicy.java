package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.crafting.*;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem.Selection;
import java.util.*;
/** Edits actual AE delivery inputs, after registered-recipe selection. Input and output policies are independent. */
public final class DeliveryPolicy {
 private DeliveryPolicy() {}
 public static String encode(AEKey key){return key.toTagGeneric().toString();}
 public static AEKey decode(String encoded) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
  var tag=TagParser.parseTag(encoded);
  if(!tag.contains("#c")) {
   var id=net.minecraft.resources.ResourceLocation.tryParse(tag.getString("id"));
   boolean item=id!=null&&net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id);
   boolean fluid=id!=null&&net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(id);
   if(item==fluid)throw new IllegalArgumentException("Ambiguous legacy delivery key; select it again");
   tag.putString("#c",(item?AEKeyType.items():AEKeyType.fluids()).getId().toString());
  }
  return AEKey.fromTagGeneric(tag);
 }
 public static List<GenericStack> extras(Selection policy){
  var result=new ArrayList<GenericStack>();
  for(var entry:policy.extraInputs().entrySet()){
   try{var key=decode(entry.getKey());
    if(!(key instanceof AEItemKey || key instanceof AEFluidKey))throw new IllegalArgumentException("Invalid delivery key");
    result.add(new GenericStack(key,entry.getValue()));
   }catch(com.mojang.brigadier.exceptions.CommandSyntaxException error){throw new IllegalArgumentException("Invalid delivery NBT",error);}
  }
  return List.copyOf(result);
 }
 public static List<GenericStack> outputExtras(Selection policy){
  var result=new ArrayList<GenericStack>();
  for(var entry:policy.extraOutputs().entrySet()){
   try{var key=decode(entry.getKey());
    if(!(key instanceof AEItemKey || key instanceof AEFluidKey))throw new IllegalArgumentException("Invalid delivery key");
    result.add(new GenericStack(key,entry.getValue()));
   }catch(com.mojang.brigadier.exceptions.CommandSyntaxException error){throw new IllegalArgumentException("Invalid delivery NBT",error);}
  }
  return List.copyOf(result);
 }
 public static List<GenericStack> inputs(Selection policy,List<GenericStack> original){
  return batchAmounts(original,extras(policy),policy.ignoredInputs(),policy.batchMultiplier());
 }
 public static List<GenericStack> outputs(Selection policy,List<GenericStack> original){
  var first = original.stream().filter(Objects::nonNull).findFirst();
  return outputs(policy, original, first.map(GenericStack::what).orElse(null));
 }
 public static List<GenericStack> outputs(Selection policy,List<GenericStack> original,AEKey recipePrimary){
  var extra = outputExtras(policy);
  return plannedOutputs(policy,original,extra,chosenPrimary(policy,original,extra,recipePrimary));
 }
 public static List<GenericStack> outputs(Selection policy,RecipePattern recipe,AEKey recipePrimary,Level level){
  var extra = outputExtras(policy);
  var chosen = chosenPrimary(policy,Arrays.asList(recipe.pattern().getOutputs()),extra,recipePrimary);
  var products = RecipeYield.outputs(recipe,policy.outputMultiplier(),level,key ->
          (policy.keepByproducts() || key.equals(chosen)) &&
                  (policy.ignoredOutputs().isEmpty() || !policy.ignoredOutputs().contains(CatalystSupply.identity(key))));
  return plannedOutputs(policy,products,extra,chosen);
 }
 private static AEKey chosenPrimary(Selection policy,List<GenericStack> original,List<GenericStack> extra,AEKey recipePrimary){
  var chosen = primary(policy);
  if (chosen == null || java.util.stream.Stream.concat(original.stream(),extra.stream())
          .filter(Objects::nonNull).noneMatch(s -> s.what().equals(chosen))) {
   return recipePrimary;
  }
  return chosen;
 }
 private static List<GenericStack> plannedOutputs(Selection policy,List<GenericStack> original,List<GenericStack> extra,AEKey primary){
  if (!policy.keepByproducts()) {
   original = original.stream().filter(s -> s != null && s.what().equals(primary)).toList();
   extra = extra.stream().filter(s -> s.what().equals(primary)).toList();
  }
  var values = new ArrayList<>(batchAmounts(original,extra,policy.ignoredOutputs(),policy.batchMultiplier()));
  if (primary != null) values.sort(Comparator.comparing(v -> !v.what().equals(primary)));
  return List.copyOf(values);
 }
 public static List<GenericStack> batchAmounts(List<GenericStack> original, List<GenericStack> extra,
                                              Set<String> ignored, int batches) {
  if (batches < 1) throw new IllegalArgumentException("Batch size must be positive");
  var amounts = new LinkedHashMap<AEKey,Long>();
  for (var group : List.of(original, extra)) {
   for (var stack : group) {
    if (stack == null || !ignored.isEmpty() && ignored.contains(CatalystSupply.identity(stack.what()))) continue;
    amounts.merge(stack.what(), stack.amount(), Math::addExact);
   }
  }
  return amounts.entrySet().stream()
          .map(e -> new GenericStack(e.getKey(), Math.multiplyExact(e.getValue(), (long)batches))).toList();
 }
 public static AEKey primary(Selection policy) {
  if(policy.primaryOutputs().isEmpty())return null;
  try{return decode(policy.primaryOutputs().values().iterator().next());}
  catch(com.mojang.brigadier.exceptions.CommandSyntaxException invalid){throw new IllegalArgumentException("Invalid primary output",invalid);}
 }
 private static boolean unchanged(Selection policy) {
  return policy.outputMultiplier()==1 && policy.batchMultiplier()==1 && policy.ignoredInputs().isEmpty() && policy.extraInputs().isEmpty()
          && policy.ignoredOutputs().isEmpty() && policy.extraOutputs().isEmpty() && policy.primaryOutputs().isEmpty();
 }
 public static boolean valid(Selection policy,IPatternDetails original){
  if(!(original instanceof AEProcessingPattern processing))return false;
  try {
   var in=inputs(policy,Arrays.asList(processing.getSparseInputs()));
   var out=outputs(policy,Arrays.asList(processing.getSparseOutputs()));
   return !in.isEmpty() && in.size()<=AEProcessingPattern.MAX_INPUT_SLOTS && !out.isEmpty() && out.size()<=AEProcessingPattern.MAX_OUTPUT_SLOTS;
  }catch(IllegalArgumentException|ArithmeticException invalid){return false;}
 }
 public static IPatternDetails apply(Selection policy,IPatternDetails original,Level level){
  if (policy.outputMultiplier()!=1 || !policy.itemChoices().isEmpty()) {
   var tag=original.getDefinition().getReadOnlyStack().getTag();
   var recipe=tag==null?null:UniversalPatternExpansion.recipe(level,tag.getString("gtlcoreRecipe"));
   if (recipe==null) return null;
   return apply(policy,original,recipe,recipe.primaryOutput(),level);
  }
  return apply(policy,original,null,original.getPrimaryOutput().what(),level);
 }
 public static IPatternDetails apply(Selection policy,RecipePattern recipe,Level level){
  return apply(policy,recipe,recipe.primaryOutput(),level);
 }
 public static IPatternDetails apply(Selection policy,RecipePattern recipe,AEKey primary,Level level){
  return apply(policy,recipe.pattern(),recipe,primary,level);
 }
 private static IPatternDetails apply(Selection policy,IPatternDetails original,RecipePattern recipe,AEKey primary,Level level){
  boolean substitutesItems = recipe != null && !policy.itemChoices().isEmpty() && recipe.inputSlots().stream().anyMatch(
          slot -> !ItemSubstitutions.resolve(policy,slot,slot.defaultKey()).equals(slot.defaultKey()));
  if(!substitutesItems && unchanged(policy) && original.getPrimaryOutput().what().equals(primary)
          && (policy.keepByproducts() || original.getOutputs().length == 1)) return original;
  if(!(original instanceof AEProcessingPattern processing))return null;
  try{
   var inputs = !substitutesItems ? inputs(policy,Arrays.asList(processing.getSparseInputs()))
           : ItemSubstitutions.inputs(policy, recipe, slot -> slot.defaultKey());
   // Never publish a zero-input crafting plan. Missing preloaded consumables must not become free crafts.
   if(inputs.isEmpty()||inputs.size()>AEProcessingPattern.MAX_INPUT_SLOTS)return null;
   var outputs=recipe == null || policy.outputMultiplier()==1 ? outputs(policy,Arrays.asList(processing.getSparseOutputs()),primary)
           : outputs(policy,recipe,primary,level);
   if(outputs.isEmpty()||outputs.size()>AEProcessingPattern.MAX_OUTPUT_SLOTS)return null;
   var encoded=PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new),outputs.toArray(GenericStack[]::new));
   var old=original.getDefinition().getReadOnlyStack().getTag();var tag=encoded.getOrCreateTag();
   if(old!=null)for(var name:old.getAllKeys())if(!tag.contains(name))tag.put(name,old.get(name).copy());
   return PatternDetailsHelper.decodePattern(encoded,level);
  }catch(IllegalArgumentException|ArithmeticException invalid){return null;}
 }
}
