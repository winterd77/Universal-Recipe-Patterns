from pathlib import Path
import subprocess,tempfile,re
r=Path(__file__).resolve().parents[2]
def method(src,marker):
 a=src.index(marker);b=src.index('{',a)+1;d=1
 while d:d+=(src[b]=='{')-(src[b]=='}');b+=1
 return src[a:b]
s=(r/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/UniversalMaterialRules.java').read_text(encoding='utf-8')
methods='\n'.join(method(s,m) for m in ['    private static List<String> availableProcesses()', '    private static synchronized Set<ResourceLocation> materialProcess(', '    private static ProcessCache processCache('])
code='''import java.util.*;
public class SourceFixture {
 record ResourceLocation(String id) implements Comparable<ResourceLocation>{public String toString(){return id;}public int compareTo(ResourceLocation other){return id.compareTo(other.id);}}
 record Type(ResourceLocation registryName){}
 record Recipe(Set<ResourceLocation> inputs,Set<ResourceLocation> outputs){}
 static class RecipeManager {Map<Type,List<Recipe>> recipes=new HashMap<>();}
 record Level(RecipeManager manager){RecipeManager getRecipeManager(){return manager;}}
 static class GTRegistries {static class RECIPE_TYPES {static Map<ResourceLocation,Type> types=new HashMap<>();static Collection<Type> values(){return types.values();}static Type get(ResourceLocation id){return types.get(id);}}}
 static class MERecipePatternHelper {static int scans;static long generation;static long getReloadGeneration(){return generation;}static List<Recipe> getRecipes(RecipeManager m,Set<Type> types){scans++;return types.stream().flatMap(t->m.recipes.getOrDefault(t,List.of()).stream()).toList();}}
 static List<String> SAMPLE_PROCESSES=List.of("blast_furnace");
 static ResourceLocation processId(String name){return new ResourceLocation(name.equals("blast_furnace")?"gtceu:blast_furnace":name);}
 record Process(ResourceLocation type,boolean inputs){}
 record ProcessCache(long generation,Map<Process,Set<ResourceLocation>> materials,Map<Boolean,Set<Object>> polarizerKeys,Map<ResourceLocation,Map<ResourceLocation,Set<String>>> forms){}
 static Map<RecipeManager,ProcessCache> PROCESS_CACHE=new WeakHashMap<>();
 static void visitKeys(Recipe r,boolean inputs,java.util.function.Consumer<ResourceLocation> c){(inputs?r.inputs:r.outputs).forEach(c);}
 static void addMaterial(ResourceLocation key,Set<ResourceLocation> dest){dest.add(key);}
 static Set<ResourceLocation> index(Level l,Process p){return Set.of();}
 static class SourceForms {static void add(Map<ResourceLocation,Set<String>> m,ResourceLocation k){m.computeIfAbsent(k,x->new HashSet<>()).add("item:dust");}static Map<ResourceLocation,Set<String>> freeze(Map<ResourceLocation,Set<String>> m){return Map.copyOf(m);}}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 var manager=new RecipeManager();var level=new Level(manager);var advanced=new ResourceLocation("gtceu:advanced");var feed=new ResourceLocation("gtceu:feed");
 for(String id:List.of("gtceu:blast_furnace","gtceu:dimensionally_transcendent_plasma_forge","gtceu:plasma_condenser","gtceu:large_chemical_reactor","future:custom_synthesis")){
  var key=new ResourceLocation(id);var type=new Type(key);GTRegistries.RECIPE_TYPES.types.put(key,type);manager.recipes.put(type,List.of(new Recipe(Set.of(feed),Set.of(advanced))));
 }
 var names=availableProcesses();check(names.size()==5&&names.contains("future:custom_synthesis")&&names.contains("blast_furnace")&&!names.contains("gtceu:blast_furnace"));
 for(String name:names){var id=processId(name);check(materialProcess(level,new Process(id,false)).contains(advanced));check(materialProcess(level,new Process(id,true)).contains(feed));check(!materialProcess(level,new Process(id,true)).contains(advanced));}
 check(MERecipePatternHelper.scans==5);
 var id=processId("future:custom_synthesis");manager.recipes.put(GTRegistries.RECIPE_TYPES.get(id),List.of());
 check(materialProcess(level,new Process(id,false)).contains(advanced));MERecipePatternHelper.generation++;
 check(materialProcess(level,new Process(id,false)).isEmpty());check(MERecipePatternHelper.scans==6);
 check(materialProcess(new Level(new RecipeManager()),new Process(id,false)).isEmpty());
 System.out.println("PASS: dynamic registered source discovery, aliases deduplicated, separate input/output membership, one scan per type for both directions, reload and world isolation.");
 }
'''+methods+'\n}'
java=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp);(p/'SourceFixture.java').write_text(code,encoding='utf-8');subprocess.run([java/'javac.exe','-d',p,p/'SourceFixture.java'],check=True);subprocess.run([java/'java.exe','-cp',p,'SourceFixture'],check=True)
assert 'MERecipePatternHelper.getRecipes' in s and 'content.chance > 0' in s and 'recipe.getTickOutputContents(fluid)' in s
