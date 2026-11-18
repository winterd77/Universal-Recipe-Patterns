package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.crafting.*;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.Level;
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
  var amounts=new LinkedHashMap<AEKey,Long>();
  for(var stack:original)if(stack!=null&&!policy.ignoredInputs().contains(CatalystSupply.identity(stack.what())))amounts.merge(stack.what(),stack.amount(),Math::addExact);
  for(var stack:extras(policy))if(!policy.ignoredInputs().contains(CatalystSupply.identity(stack.what())))amounts.merge(stack.what(),stack.amount(),Math::addExact);
  return amounts.entrySet().stream().map(e->new GenericStack(e.getKey(),e.getValue())).toList();
 }
 public static List<GenericStack> outputs(Selection policy,List<GenericStack> original){
  var amounts=new LinkedHashMap<AEKey,Long>();
  for(var stack:original)if(stack!=null&&!policy.ignoredOutputs().contains(CatalystSupply.identity(stack.what())))amounts.merge(stack.what(),stack.amount(),Math::addExact);
  for(var stack:outputExtras(policy))if(!policy.ignoredOutputs().contains(CatalystSupply.identity(stack.what())))amounts.merge(stack.what(),stack.amount(),Math::addExact);
  var values=new ArrayList<>(amounts.entrySet().stream().map(e->new GenericStack(e.getKey(),e.getValue())).toList());
  var primary=primary(policy);if(primary!=null)values.sort(Comparator.comparing(v->!v.what().equals(primary)));return List.copyOf(values);
 }
 public static AEKey primary(Selection policy) {
  if(policy.primaryOutputs().isEmpty())return null;
  try{return decode(policy.primaryOutputs().values().iterator().next());}
  catch(com.mojang.brigadier.exceptions.CommandSyntaxException invalid){throw new IllegalArgumentException("Invalid primary output",invalid);}
 }
 public static boolean valid(Selection policy,IPatternDetails original){
  if(policy.ignoredInputs().isEmpty()&&policy.extraInputs().isEmpty()&&policy.ignoredOutputs().isEmpty()&&policy.extraOutputs().isEmpty()&&policy.primaryOutputs().isEmpty())return true;
  if(!(original instanceof AEProcessingPattern p))return false;
  try{var values=inputs(policy,Arrays.asList(p.getSparseInputs()));var out=outputs(policy,Arrays.asList(p.getSparseOutputs()));return !values.isEmpty()&&values.size()<=AEProcessingPattern.MAX_INPUT_SLOTS&&!out.isEmpty()&&out.size()<=AEProcessingPattern.MAX_OUTPUT_SLOTS;}
  catch(IllegalArgumentException|ArithmeticException invalid){return false;}
 }
 public static IPatternDetails apply(Selection policy,IPatternDetails original,Level level){
  if(policy.ignoredInputs().isEmpty()&&policy.extraInputs().isEmpty()&&policy.ignoredOutputs().isEmpty()&&policy.extraOutputs().isEmpty()&&policy.primaryOutputs().isEmpty())return original;
  if(!(original instanceof AEProcessingPattern processing))return null;
  try{
   var inputs=inputs(policy,Arrays.asList(processing.getSparseInputs()));
   // Never publish a zero-input crafting plan. Missing preloaded consumables must not become free crafts.
   if(inputs.isEmpty()||inputs.size()>AEProcessingPattern.MAX_INPUT_SLOTS)return null;
   var outputs=outputs(policy,Arrays.asList(processing.getSparseOutputs()));
   if(outputs.isEmpty()||outputs.size()>AEProcessingPattern.MAX_OUTPUT_SLOTS)return null;
   var encoded=PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new),outputs.toArray(GenericStack[]::new));
   var old=original.getDefinition().getReadOnlyStack().getTag();var tag=encoded.getOrCreateTag();
   if(old!=null)for(var name:old.getAllKeys())if(!tag.contains(name))tag.put(name,old.get(name).copy());
   return PatternDetailsHelper.decodePattern(encoded,level);
  }catch(IllegalArgumentException|ArithmeticException invalid){return null;}
 }
}
