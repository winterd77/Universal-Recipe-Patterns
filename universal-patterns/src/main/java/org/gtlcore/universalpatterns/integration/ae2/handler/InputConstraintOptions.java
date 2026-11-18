package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.stacks.*;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import java.util.*;
/** Candidate correspondence is semantic and unambiguous, never a slot or amount comparison. */
public final class InputConstraintOptions {
 private InputConstraintOptions() {}
 static String role(AEKey key) {
  if(key instanceof AEFluidKey)return "fluid";
  if(key instanceof AEItemKey item){var entry=ChemicalHelper.getUnificationEntry(item.getItem());if(entry!=null&&entry.tagPrefix!=null)return "item:"+entry.tagPrefix.name();}
  return "exact:"+CatalystSupply.identity(key);
 }
 public static List<AEKey> candidates(RecipePattern current,List<RecipePattern> family,AEKey anchor) {
  return new Index(family).candidates(current,anchor);
 }
 /** Build once per snapshot, rather than rescanning the catalog for every input. */
 public static final class Index {
  private final Map<String,Set<AEKey>> contexts=new HashMap<>(),roles=new HashMap<>();
  private final Map<AEKey,Set<AEKey>> together=new HashMap<>();
  private final Map<AEKey,List<Set<AEKey>>> byKey=new HashMap<>();
  public Index(List<RecipePattern> family) {
   for(var recipe:family){
    var keys=ConsumedFluidFilter.inputs(recipe);
    for(var key:keys){
     together.computeIfAbsent(key,k->new HashSet<>()).addAll(keys);
     String type=key instanceof AEFluidKey?"fluid":"item";
     String context=type+keys.stream().filter(k->!k.equals(key)).map(CatalystSupply::identity).sorted().toList();
     contexts.computeIfAbsent(context,k->new LinkedHashSet<>()).add(key);
     String role=role(key);
     if(!role.startsWith("exact:") && keys.stream().filter(k->role(k).equals(role)).count()==1)roles.computeIfAbsent(role,k->new LinkedHashSet<>()).add(key);
    }
   }
   for(var group:contexts.values())for(var key:group)byKey.computeIfAbsent(key,k->new ArrayList<>()).add(group);
  }
  public List<AEKey> candidates(RecipePattern current,AEKey anchor) {
   if(current==null)return List.of(anchor);
   if(!together.containsKey(anchor))return List.of();
   var inputs=ConsumedFluidFilter.inputs(current);var proposed=new HashSet<AEKey>();proposed.add(anchor);
   for(var group:byKey.getOrDefault(anchor,List.of()))if(group.stream().filter(inputs::contains).count()==1)proposed.addAll(group);
   String role=role(anchor);
   if(inputs.stream().filter(k->role(k).equals(role)).count()==1)proposed.addAll(roles.getOrDefault(role,Set.of()));
   // Never treat inputs which coexist in any real recipe as interchangeable.
   // A deterministic pairwise check also prevents unsafe transitive A-B-C merges.
   var result=new ArrayList<AEKey>();result.add(anchor);
   for(var key:proposed.stream().sorted(Comparator.comparing(CatalystSupply::identity)).toList()){
    if(key.equals(anchor))continue;
    if(result.stream().noneMatch(k->together.getOrDefault(k,Set.of()).contains(key)))result.add(key);
   }
   if(result.stream().filter(inputs::contains).count()>1)return List.of(anchor);
   return List.copyOf(result);
  }
 }
 public static List<AEKey> overview(List<AEKey> current,List<AEKey> known,Map<AEKey,List<AEKey>> candidates,Set<String> fixed) {
  var result=new LinkedHashSet<AEKey>();var covered=new HashSet<AEKey>();
  for(var key:current){
   if(covered.contains(key))continue;
   var group=candidates.getOrDefault(key,List.of(key));
   var choices=group.stream().filter(k->fixed.contains(CatalystSupply.identity(k))).toList();
   result.add(choices.isEmpty()?key:choices.get(0));covered.addAll(group);
  }
  for(var key:known)if(fixed.contains(CatalystSupply.identity(key))&&!covered.contains(key)){result.add(key);covered.addAll(candidates.getOrDefault(key,List.of(key)));}
  return List.copyOf(result);
 }
 public static Set<String> replace(Set<String> fixed,List<AEKey> group,AEKey selected) {
  if(selected!=null&&!group.contains(selected))throw new IllegalArgumentException("Foreign candidate");
  var result=new HashSet<>(fixed);for(var key:group)result.remove(CatalystSupply.identity(key));
  if(selected!=null)result.add(CatalystSupply.identity(selected));return Set.copyOf(result);
 }
}
