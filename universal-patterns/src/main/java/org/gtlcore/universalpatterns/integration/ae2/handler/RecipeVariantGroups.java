package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.stacks.*;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import java.util.*;
/** Presentation-only grouping. Every member retains its original registered recipe. */
public final class RecipeVariantGroups {
 private RecipeVariantGroups() {}
 public static String key(RecipePattern p) {
  var recipe=p.recipe();
  if(p.chancedInput() || !recipe.conditions.isEmpty() || !recipe.ingredientActions.isEmpty() ||
     recipe.outputs.values().stream().flatMap(Collection::stream).anyMatch(c->c.chance!=c.maxChance) ||
     p.inputSlots().stream().anyMatch(s->s.alternatives().size()!=1))return "single:"+p.id();
  var items=p.inputSlots().stream().filter(s->!(s.defaultKey() instanceof AEFluidKey)).map(s->CatalystSupply.identity(s.defaultKey())+"@"+s.amount()).sorted().toList();
  var outputs=Arrays.stream(p.pattern().getOutputs()).map(s->CatalystSupply.identity(s.what())+"@"+s.amount()).sorted().toList();
  var reusable=p.nonConsumedInputs().stream().map(list->list.stream().map(CatalystSupply::identity).sorted().toList().toString()).sorted().toList();
  return List.of(recipe.recipeType.registryName.toString(),outputs,reusable,p.circuit(),String.valueOf(recipe.data),recipe.tickInputs.toString(),recipe.tickOutputs.toString(),recipe.isFuel,recipe.ocTier,recipe.parallels).toString();
 }
 public static List<List<RecipePattern>> groups(List<RecipePattern> recipes) {
  int[] parent=new int[recipes.size()];for(int i=0;i<parent.length;i++)parent[i]=i;
  var seen=new HashMap<String,Integer>();
  for(int i=0;i<recipes.size();i++){
   var recipe=recipes.get(i);String base=key(recipe);if(base.startsWith("single:"))continue;
   var slots=recipe.inputSlots();
   for(var removed:slots){
    var rest=new ArrayList<String>();boolean skipped=false;
    for(var slot:slots){if(!skipped&&slot==removed){skipped=true;continue;}rest.add(CatalystSupply.identity(slot.defaultKey())+"@"+slot.amount());}
    Collections.sort(rest);
    String signature=base+"|"+(removed.defaultKey() instanceof AEFluidKey?"fluid":"item")+rest;
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
