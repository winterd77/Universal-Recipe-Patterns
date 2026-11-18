from pathlib import Path
import re,subprocess,tempfile
root=Path(__file__).resolve().parents[2]
s=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/InputConstraintOptions.java').read_text()
s=re.sub(r'^(package|import) .*;\s*$','',s,flags=re.M).replace('public final class InputConstraintOptions','static final class InputConstraintOptions')
f='''import java.util.*;
public class RoleProbe {
 static class AEKey {String id;AEKey(String id){this.id=id;}}
 static class AEFluidKey extends AEKey{AEFluidKey(String id){super(id);}}
 static class AEItemKey extends AEKey{String form;AEItemKey(String id,String f){super(id);form=f;}AEItemKey getItem(){return this;}}
 record Prefix(String name){} static class Entry{Prefix tagPrefix;Entry(String f){tagPrefix=f==null?null:new Prefix(f);}}
 static class ChemicalHelper {static Entry getUnificationEntry(AEItemKey i){return new Entry(i.form);}}
 static class CatalystSupply {static String identity(AEKey k){return k.id;}}
 record RecipePattern(List<AEKey> keys,List<Long> amounts){}
 static class ConsumedFluidFilter {static List<AEKey> inputs(RecipePattern r){return r==null?List.of():r.keys.stream().distinct().toList();}}
 __SOURCE__
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 var al=new AEItemKey("al","rod");var cu=new AEItemKey("cu","rod");var water=new AEFluidKey("water");var oil=new AEFluidKey("oil");var oxygen=new AEFluidKey("oxygen");
 var a=new RecipePattern(List.of(al,water),List.of(1L,4L));
 var b=new RecipePattern(List.of(oil,cu),List.of(1000L,9L));
 var extra=new AEItemKey("extra",null);var c=new RecipePattern(List.of(extra,water,cu),List.of(8L,2000L,18L));
 var multi=new RecipePattern(List.of(water,oxygen,al),List.of(3L,4L,6L));
 var family=List.of(a,b,c,multi);
 check(new HashSet<>(InputConstraintOptions.candidates(a,family,water)).equals(Set.of(water,oil)));
 check(new HashSet<>(InputConstraintOptions.candidates(a,family,al)).equals(Set.of(al,cu)));
 check(InputConstraintOptions.candidates(multi,family,water).equals(List.of(water)));
 var iron=new AEItemKey("iron","dust");var carbon=new AEItemKey("carbon","dust");var copper=new AEItemKey("copper","dust");
 var mix=new RecipePattern(List.of(iron,carbon,water),List.of(3L,1L,10L));var mix2=new RecipePattern(List.of(water,copper,iron),List.of(20L,2L,9L));
 check(InputConstraintOptions.candidates(mix,List.of(mix,mix2),iron).equals(List.of(iron)));
 check(InputConstraintOptions.candidates(c,family,extra).equals(List.of(extra)));
 check(InputConstraintOptions.candidates(null,family,water).equals(List.of(water)));
 // Reopen on an oil variant while the card already requires water: one group, not two requirements.
 var aliases=InputConstraintOptions.candidates(b,family,water);check(aliases.contains(oil)&&aliases.contains(water));
 var groups=new HashMap<AEKey,List<AEKey>>();groups.put(oil,InputConstraintOptions.candidates(b,family,oil));groups.put(water,aliases);groups.put(cu,InputConstraintOptions.candidates(b,family,cu));
 var overview=InputConstraintOptions.overview(b.keys,List.of(water,oil,al,cu),groups,Set.of("water"));
 check(overview.size()==2&&overview.contains(water)&&overview.contains(cu)&&!overview.contains(oil));
 var chosen=InputConstraintOptions.replace(Set.of("water","al"),aliases,oil);check(chosen.equals(Set.of("oil","al")));
 // Repair a legacy contradictory group atomically; independent requirements survive.
 check(InputConstraintOptions.replace(Set.of("water","oil","al"),aliases,water).equals(Set.of("water","al")));
 check(InputConstraintOptions.replace(Set.of("water","oil","al"),aliases,null).equals(Set.of("al")));
 check(InputConstraintOptions.replace(Set.of("water","oxygen","al"),InputConstraintOptions.candidates(multi,family,water),null).equals(Set.of("oxygen","al")));
 check(InputConstraintOptions.overview(multi.keys,List.of(water,oxygen,al),Map.of(water,List.of(water),oxygen,List.of(oxygen),al,List.of(al)),Set.of("water","oxygen")).size()==3);
 var fake=new AEFluidKey("unregistered");check(InputConstraintOptions.candidates(b,family,fake).isEmpty());
 // Shared context resolves one replaceable fluid even when another fluid is mandatory.
 var multiOil=new RecipePattern(List.of(oxygen,oil,al),List.of(7L,20L,9L));
 var fluids=List.of(multi,multiOil);check(new HashSet<>(InputConstraintOptions.candidates(multi,fluids,water)).equals(Set.of(water,oil)));
 check(InputConstraintOptions.candidates(multi,fluids,oxygen).equals(List.of(oxygen)));
 check(InputConstraintOptions.replace(Set.of("water","oxygen"),InputConstraintOptions.candidates(multi,fluids,water),oil).equals(Set.of("oil","oxygen")));
 // Multiple powders: only the changed component forms a candidate group.
 check(new HashSet<>(InputConstraintOptions.candidates(mix,List.of(mix,mix2),carbon)).equals(Set.of(carbon,copper)));
 // Non-GT items can be alternatives when the remaining real inputs agree.
 var seed=new AEItemKey("seed",null);var otherSeed=new AEItemKey("otherSeed",null);
 var plant=new RecipePattern(List.of(seed,water),List.of(1L,5L));var plant2=new RecipePattern(List.of(water,otherSeed),List.of(8L,2L));
 check(new HashSet<>(InputConstraintOptions.candidates(plant,List.of(plant,plant2),seed)).equals(Set.of(seed,otherSeed)));
 // Co-occurrence wins over alternative evidence; never erase a real independent input.
 var coexist=new RecipePattern(List.of(seed,otherSeed,water),List.of(1L,1L,5L));
 check(InputConstraintOptions.candidates(plant,List.of(plant,plant2,coexist),seed).equals(List.of(seed)));
 // Duplicate representations of one key do not create positional roles.
 var split=new RecipePattern(List.of(al,water,al),List.of(1L,4L,2L));check(InputConstraintOptions.candidates(split,family,water).contains(oil));
 System.out.println("PASS: production candidate matching ignores input order, quantity and unrelated inputs; ambiguous multiple fluids/powders and unknown items do not acquire guessed substitutions.");
 }
}'''.replace('__SOURCE__',s)
with tempfile.TemporaryDirectory(dir=root/'build') as tmp:
 p=Path(tmp);(p/'RoleProbe.java').write_text(f,encoding='utf-8');jdk=Path('C:/Program Files/Java/jdk-22/bin');subprocess.run([jdk/'javac.exe','-d',p,p/'RoleProbe.java'],check=True);subprocess.run([jdk/'java.exe','-cp',p,'RoleProbe'],check=True)
ui=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/client/gui/widget/RecipePatternBrowserWidget.java').read_text()
assert 'InputConstraintOptions.overview(selectedInputs.stream()' in ui and 'constraintAnchor==null' in ui
assert '(key!=null && !candidates.contains(key))' in ui and '!recipeId.equals(selectedId)' in ui
print('PASS: overview uses selected inputs plus saved fixed requirements; replacement validates card revision, recipe identity and recomputed candidates.')

import zipfile,json
with zipfile.ZipFile(root/'dist/gtl-universal-patterns-1.0.27.jar') as z:
 labels=json.loads(z.read('assets/gtceu/lang/zh_cn.json'))
 assert labels['gtceu.machine.me_recipe_pattern_buffer.ingredients.fluid_title']=='当前配方 · 原料项'
 assert labels['gtceu.machine.me_recipe_pattern_buffer.ingredients.conflict']=='冲突'
 assert '\u8133' not in ui and '\u00d7' not in ui
 assert b'\xe8\x84\xb3' not in z.read('org/gtlcore/universalpatterns/client/gui/widget/RecipePatternBrowserWidget.class')
print('PASS: saved water/current oil collapse into one group; replacement and reset remove all group conflicts; actual two-fluid recipes stay independent; shipped labels and close glyph verified.')
