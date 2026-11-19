package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.stacks.*;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.InputSlot;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import java.util.*;
/** Presentation-only grouping. Every member retains its original registered recipe. */
public final class RecipeVariantGroups {
 private RecipeVariantGroups() {}
 public static String key(RecipePattern p) {
  var recipe=p.recipe();
  if(p.chancedInput() || !recipe.conditions.isEmpty() || !recipe.ingredientActions.isEmpty() ||
     recipe.outputs.values().stream().flatMap(Collection::stream).anyMatch(c->c.chance!=c.maxChance))return "single:"+p.id();
  var outputs=Arrays.stream(p.pattern().getOutputs()).map(s->identity(s.what())+"@"+s.amount()).toList();
  var reusable=p.nonConsumedInputs().stream().map(list->list.stream().map(RecipeVariantGroups::identity).sorted().toList().toString()).sorted().toList();
  return List.of(recipe.recipeType.registryName.toString(),outputs,reusable,p.circuit(),String.valueOf(recipe.data),recipe.tickInputs.toString(),recipe.tickOutputs.toString(),recipe.isFuel,recipe.ocTier,recipe.parallels).toString();
 }
 private static String identity(AEKey key) {
  return key.getType().getId()+"/"+CatalystSupply.identity(key);
 }
 private static String slotSignature(InputSlot slot) {
  return slot.alternatives().stream().map(RecipeVariantGroups::identity).distinct().sorted().toList()+"@"+slot.amount();
 }
 public static List<List<RecipePattern>> groups(List<RecipePattern> recipes) {
  if(recipes.size()==1)return List.of(recipes);
  int[] parent=new int[recipes.size()];for(int i=0;i<parent.length;i++)parent[i]=i;
  var seen=new HashMap<String,Integer>();
  for(int i=0;i<recipes.size();i++){
   var recipe=recipes.get(i);String base=key(recipe);if(base.startsWith("single:"))continue;
   var slots=recipe.inputSlots();
   var signatures=slots.stream().map(RecipeVariantGroups::slotSignature).toList();
   for(int slotIndex=0;slotIndex<slots.size();slotIndex++){
    var removed=slots.get(slotIndex);
    var rest=new ArrayList<>(signatures);
    rest.remove(slotIndex);
    Collections.sort(rest);
    String kind = removed.id().split(":",2)[0];
    String signature=base+"|"+kind+"@"+removed.amount()+"|"+rest;
    Integer previous=seen.putIfAbsent(signature,i);
    if(previous!=null){int a=root(parent,i),b=root(parent,previous);parent[a]=b;}
   }
  }
  var result=new LinkedHashMap<Integer,List<RecipePattern>>();
  for(int i=0;i<recipes.size();i++)result.computeIfAbsent(root(parent,i),k->new ArrayList<>()).add(recipes.get(i));
  return List.copyOf(result.values());
 }
 private static int root(int[] parent,int value){while(parent[value]!=value){parent[value]=parent[parent[value]];value=parent[value];}return value;}

}
