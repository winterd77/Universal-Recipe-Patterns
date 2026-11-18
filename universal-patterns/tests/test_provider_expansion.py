from pathlib import Path
import subprocess, tempfile, re
root=Path(__file__).resolve().parents[2]
source=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/UniversalPatternExpansion.java').read_text(encoding='utf-8')
source=re.sub(r'^(package|import) .*;\s*$', '', source, flags=re.M).replace('public final class UniversalPatternExpansion','static final class UniversalPatternExpansion')
fixture=r'''
import java.util.*;
import java.util.function.Predicate;
public class ExpansionFixture {
 record ResourceLocation(String value) {static ResourceLocation tryParse(String s){return s.contains(":")?new ResourceLocation(s):null;}}
 record Selection(String family, Set<ResourceLocation> materials, Set<ResourceLocation> excluded, String allow, String deny, Map<ResourceLocation,String> recipes, boolean externalCatalysts,String requiredFluid,Map<String,Boolean> recipeOverrides) {
 Selection(String family, Set<ResourceLocation> materials, Set<ResourceLocation> excluded, String allow, String deny, Map<ResourceLocation,String> recipes, boolean externalCatalysts,String requiredFluid){this(family,materials,excluded,allow,deny,recipes,externalCatalysts,requiredFluid,Map.of());}
 Selection withOverrides(Map<String,Boolean> overrides){return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,requiredFluid,overrides);}
 Selection forMaterial(String material){return this;}Set<String> requiredInputs(){return requiredFluid.isEmpty()?Set.of():Set.of(requiredFluid.split(","));}Selection(String f,Set<ResourceLocation> m,Set<ResourceLocation> e,String a,String d,Map<ResourceLocation,String> r,boolean x){this(f,m,e,a,d,r,x,"");}Selection withRequiredFluid(String key){return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,key);}}
 static class DirectCatalystSupply {static int calls;static IPatternDetails apply(Selection s,MERecipePatternHelper.RecipePattern r,IPatternDetails p,Level l){calls++;return p;}}
 static class DeliveryPolicy {static IPatternDetails apply(Selection s,IPatternDetails p,Level l){return p;}}
 static class UniversalPatternItem {static Selection read(ItemStack s){return s.selection;}}
 static class ItemStack {Object item=new UniversalPatternItem();Selection selection;String draft="";ItemStack(Selection s){selection=s;}Object getItem(){return item;}}
 record IPatternDetails(String id,Map<String,Long> io) {IPatternDetails(String id){this(id,new HashMap<>());}}
 static class Level {Object getRecipeManager(){return this;}}
 static class GTRegistries {static class RECIPE_TYPES {static Object get(ResourceLocation id){return id.value.equals("gt:test")?"gt:test":null;}}}
 static class MERecipePatternHelper {
  static long generation;static int scans;static List<RecipePattern> registered=new ArrayList<>();
  record RecipePattern(String id,IPatternDetails pattern,int circuit,Object primaryOutput,boolean chancedInput,String family,ResourceLocation material) {
   List<InputSlot> inputSlots(){return pattern.io().entrySet().stream().map(e->new InputSlot(e.getKey().startsWith("fluid:")?new AEFluidKey(e.getKey()):new AEKey(e.getKey()),e.getValue())).toList();}
  }
  static long getReloadGeneration(){return generation;}
  static List<RecipePattern> getRecipes(Object manager,Set<Object> types){scans++;return registered;}
  static RecipePattern createPattern(RecipePattern r,Level l){return r;}
 }
 record Candidate(String family, ResourceLocation materialId) {}
 static class RegisteredProcessFamilies {
  static Candidate describe(MERecipePatternHelper.RecipePattern r,Object output,boolean b){return new Candidate(r.family(),r.material());}
  static Candidate legacyCasting(Candidate preferred,String family){return null;}
 }
 static class UniversalPatternFamily {
  static Candidate describe(MERecipePatternHelper.RecipePattern r,Object o){return new Candidate(r.family(),r.material());}
  static Candidate describePetrochemical(MERecipePatternHelper.RecipePattern r,Object o,Level l){return null;}
 }
 static class UniversalMaterialRules {
  static Predicate<Candidate> compile(String rule,Level l){if(rule.equals("bad"))throw new IllegalArgumentException();return c->rule.equals("all")||rule.equals(c.materialId().value);}
 }
 static class AEKey {String id;AEKey(String id){this.id=id;}}
 static class AEFluidKey extends AEKey {AEFluidKey(String id){super(id);}}
 record InputSlot(AEKey defaultKey,long amount){}
 static class CatalystSupply {static String identity(AEKey key){return key.id;}}
 __FILTER__
 __SOURCE__
 static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
 static Selection s(Set<ResourceLocation> selected,Set<ResourceLocation> excluded,String allow,String deny,Map<ResourceLocation,String> variants,boolean external){return new Selection("gt:test|molten|ingot",selected,excluded,allow,deny,variants,external);}
 static MERecipePatternHelper.RecipePattern recipe(String id,String family,ResourceLocation material,boolean chanced,Object output){return new MERecipePatternHelper.RecipePattern(id,new IPatternDetails(id),3,output,chanced,family,material);}
 public static void main(String[] args){
  var a=new ResourceLocation("gt:a");var b=new ResourceLocation("gt:b");var level=new Level();
  String family="gt:test|molten|ingot";
  MERecipePatternHelper.registered.addAll(List.of(recipe("a1",family,a,false,"out"),recipe("a2",family,a,false,"out"),recipe("b",family,b,false,"out"),recipe("other","gt:test|hot|ingot",a,false,"out"),recipe("chance",family,a,true,"out"),recipe("noPrimary",family,a,false,null)));
  var card=new ItemStack(s(Set.of(a),Set.of(),"","",Map.of(),true));
  MERecipePatternHelper.registered.get(0).pattern().io().put("copper_dust",3L);
  MERecipePatternHelper.registered.get(0).pattern().io().put("water",1000L);
  MERecipePatternHelper.registered.get(1).pattern().io().put("tin_dust",7L);
  MERecipePatternHelper.registered.get(1).pattern().io().put("water",4000L);
  var first=UniversalPatternExpansion.expand(card,level);
  check(first.get(0)==MERecipePatternHelper.registered.get(0).pattern() && first.get(1)==MERecipePatternHelper.registered.get(1).pattern(),"variable IO must use each registered recipe object unchanged");
  check(first.get(0).io().get("water")==1000L&&first.get(1).io().get("water")==4000L,"individual fluid quantities retained");
  check(first.size()==2,"multiple real variants must survive");check(MERecipePatternHelper.scans==1,"first type scan");
  var originalSelection=card.selection;
  card.selection=originalSelection.withOverrides(Map.of("a1",false));
  check(UniversalPatternExpansion.expand(card,level).equals(List.of(MERecipePatternHelper.registered.get(1).pattern())),"disable a1 leaves same-material a2 enabled");
  card.selection=originalSelection.withOverrides(Map.of("a1",true,"a2",false));
  check(UniversalPatternExpansion.expand(card,level).equals(List.of(MERecipePatternHelper.registered.get(0).pattern())),"independent inverse toggles");
  card.selection=originalSelection;
  card.draft="unapplied-all-materials";
  check(UniversalPatternExpansion.expand(card,level)==first,"draft must not affect publication");
  card.selection=s(Set.of(a),Set.of(),"","",Map.of(a,"a2"),true);
  check(UniversalPatternExpansion.expand(card,level).equals(List.of(MERecipePatternHelper.registered.stream().filter(r->r.id().equals("a2")).findFirst().orElseThrow().pattern())),"chosen recipe identity");
  card.selection=s(Set.of(a),Set.of(a),"all","",Map.of(),true);
  check(UniversalPatternExpansion.expand(card,level).equals(List.of(new IPatternDetails("b"))),"exclusion wins over manual and rules");
  card.selection=s(Set.of(a),Set.of(),"all","gt:a",Map.of(),true);
  check(UniversalPatternExpansion.expand(card,level).equals(List.of(new IPatternDetails("b"))),"deny wins");
  card.selection=s(Set.of(a),Set.of(),"bad","",Map.of(),true);
  check(UniversalPatternExpansion.expand(card,level).isEmpty(),"invalid expression must fail closed");
  card.selection=s(Set.of(a),Set.of(),"","",Map.of(a,"unregistered"),true);
  check(UniversalPatternExpansion.expand(card,level).isEmpty(),"cannot synthesize unregistered recipe");
  check(MERecipePatternHelper.scans==1,"edits must reuse type index");
  card.selection=s(Set.of(a),Set.of(),"","",Map.of(),true);
  MERecipePatternHelper.registered.removeIf(r->r.id().equals("a1"));MERecipePatternHelper.generation++;
  check(UniversalPatternExpansion.expand(card,level).equals(List.of(MERecipePatternHelper.registered.stream().filter(r->r.id().equals("a2")).findFirst().orElseThrow().pattern())),"reload removes deleted recipe");
  check(MERecipePatternHelper.scans==2,"one rebuild after reload");
  card.selection=new Selection("invalid",Set.of(a),Set.of(),"","",Map.of(),true);
  check(UniversalPatternExpansion.expand(card,level).isEmpty(),"malformed family");
  card.item=new Object();check(UniversalPatternExpansion.expand(card,level).isEmpty(),"ordinary cards unchanged");
  MERecipePatternHelper.registered.clear();UniversalPatternExpansion.invalidate();
  var aw=recipe("aw",family,a,false,"bolt");aw.pattern().io().put("fluid:water",4L);
  aw.pattern().io().put("item:catalyst",2L);
  var ao=recipe("ao",family,a,false,"bolt");ao.pattern().io().put("fluid:oil",1L);
  var bw=recipe("bw",family,b,false,"bolt");bw.pattern().io().put("fluid:water",32L);
  var bn=recipe("bn",family,b,false,"bolt");bn.pattern().io().put("fluid:water{grade:2}",32L);
  MERecipePatternHelper.registered.addAll(List.of(aw,ao,bw,bn,recipe("dry",family,b,false,"bolt")));
  var all=s(Set.of(),Set.of(),"all","",Map.of(),false);
  var constrained=new ItemStack(all.withRequiredFluid("fluid:water"));
  var water=UniversalPatternExpansion.expand(constrained,level);
  check(water.equals(List.of(aw.pattern(),bw.pattern())),"whole-card water excludes oil, dry and different NBT across materials");
  check(water.get(0).io().get("fluid:water")==4L&&water.get(1).io().get("fluid:water")==32L,"registered per-recipe quantities untouched");
  constrained.selection=all.withRequiredFluid("fluid:water,item:catalyst");check(UniversalPatternExpansion.expand(constrained,level).equals(List.of(aw.pattern())),"AND constraint combines fixed item and fixed fluid without slot-number assumptions");
  constrained.selection=all.withRequiredFluid("item:catalyst");check(UniversalPatternExpansion.expand(constrained,level).equals(List.of(aw.pattern())),"item-only constraint supported");
  constrained.selection=all.withRequiredFluid("fluid:oil");check(UniversalPatternExpansion.expand(constrained,level).equals(List.of(ao.pattern())),"fluid choice changes publication and cache key");
  constrained.selection=all.withRequiredFluid("fluid:missing");check(UniversalPatternExpansion.expand(constrained,level).isEmpty(),"missing selected fluid fails closed");
  constrained.selection=all;check(UniversalPatternExpansion.expand(constrained,level).size()==5,"reset constraint restores original registered variants");
  constrained.selection=s(Set.of(a),Set.of(),"","",Map.of(a,"ao"),false).withRequiredFluid("fluid:water");check(UniversalPatternExpansion.expand(constrained,level).isEmpty(),"manual oil recipe cannot bypass fluid constraint");
  check(DirectCatalystSupply.calls>0,"all provider expansion passes through direct supply policy");
  System.out.println("PASS: whole-card fluid restriction on production publication, NBT, actual amounts, cache isolation and clearing. ");
  System.out.println("PASS: production expansion selects registered variants, excludes chanced/foreign families, enforces deny/exclusions, ignores drafts, fails closed, caches by type, and invalidates on reload.");
 }
}
'''.replace('__SOURCE__',source)
filter_source=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/ConsumedFluidFilter.java').read_text(encoding='utf-8')
filter_source=re.sub(r'^(package|import) .*;\s*$', '',filter_source,flags=re.M).replace('public final class ConsumedFluidFilter','static final class ConsumedFluidFilter').replace('RecipePattern recipe','MERecipePatternHelper.RecipePattern recipe')
fixture=fixture.replace('__FILTER__',filter_source)
java=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory(dir=root/'build/universal-provider') as folder:
 path=Path(folder);(path/'ExpansionFixture.java').write_text(fixture,encoding='utf-8')
 subprocess.run([java/'javac.exe','-encoding','UTF-8','-d',path,path/'ExpansionFixture.java'],check=True)
 subprocess.run([java/'java.exe','-cp',path,'ExpansionFixture'],check=True)
