from pathlib import Path
import re,subprocess,tempfile
r=Path(__file__).resolve().parents[2];folder=r/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler'
s=(folder/'SourceForms.java').read_text(encoding='utf-8');s=re.sub(r'^(package|import) .*;\s*$','',s,flags=re.M).replace('public final class SourceForms','static final class SourceForms')
rules=(folder/'UniversalMaterialRules.java').read_text(encoding='utf-8');a=rules.index('        if(token.startsWith("source:"))');b=rules.index('        if (token.startsWith("process:")',a);condition=rules[a:b]
code='''import java.util.*;import java.util.function.*;
public class FormsFixture {
 record ResourceLocation(String id){}
 record Material(ResourceLocation getResourceLocation){}
 static class AEKey {Material material;String form,id,nbt="";AEKey(Material m,String f,String i){material=m;form=f;id=i;}ResourceLocation getId(){return new ResourceLocation(id);}}
 static class AEItemKey extends AEKey {AEItemKey(Material m,String f,String i){super(m,f,i);}}
 static class UniversalPatternFamily {static Material materialOf(AEKey key){return key.material;}static String formOf(AEKey key){return key.form;}}
 static class CatalystSupply {static String identity(AEKey key){return key.id+key.nbt;}}
 record Candidate(Material material,ResourceLocation materialId){}
 static class Level{}
 static class GTRegistries {static class RECIPE_TYPES{static Object get(ResourceLocation id){return id.id.startsWith("gtceu:")?id:null;}}}
 static class BuiltInRegistries {static class RECIPE_TYPE {static boolean containsKey(ResourceLocation id){return false;}}}
 static Map<ResourceLocation,Map<ResourceLocation,Set<String>>> index=new HashMap<>();
 static ResourceLocation processId(String id){return new ResourceLocation(id);}
 static Map<ResourceLocation,Set<String>> productionForms(Level l,ResourceLocation type){return index.getOrDefault(type,Map.of());}
 static Predicate<Candidate> condition(String token,Level level){__COND__ return null;}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  var iron=new Material(new ResourceLocation("iron"));var copper=new Material(new ResourceLocation("copper"));
  var dust=new AEItemKey(iron,"item:dust","iron_dust");var hot=new AEItemKey(iron,"item:ingotHot","hot_iron");var molten=new AEKey(iron,"fluid:molten","molten_iron");
  var forms=new HashMap<ResourceLocation,Set<String>>();SourceForms.add(forms,dust);SourceForms.add(forms,hot);SourceForms.add(forms,molten);SourceForms.add(forms,new AEItemKey(copper,"item:ingot","copper_ingot"));
  var frozen=SourceForms.freeze(forms);check(frozen.get(iron.getResourceLocation).equals(Set.of("item:dust","item:ingothot","fluid:molten")));
  index.put(new ResourceLocation("gtceu:mixer"),Map.of(iron.getResourceLocation,Set.of("item:dust"),copper.getResourceLocation,Set.of("fluid:molten")));
  var ironCandidate=new Candidate(iron,iron.getResourceLocation);var copperCandidate=new Candidate(copper,copper.getResourceLocation);
  check(condition("source:direct:item:dust:gtceu:mixer",new Level()).test(ironCandidate));
  check(!condition("source:direct:item:dust:gtceu:mixer",new Level()).test(copperCandidate));
  check(condition("source:other:item:dust:gtceu:mixer",new Level()).test(copperCandidate));
  check(!condition("source:other:item:dust:gtceu:mixer",new Level()).test(ironCandidate));
  index.put(new ResourceLocation("gtceu:forge"),frozen);
  check(condition("source:direct:item:ingothot:gtceu:forge",new Level()).test(ironCandidate));
  check(!condition("source:direct:item:ingot:gtceu:forge",new Level()).test(ironCandidate));
  check(condition("source:other:item:ingothot:gtceu:forge",new Level()).test(ironCandidate));
  check(SourceForms.matches(Set.of("fluid:gas","fluid:plasma"),"fluid:molten",true));
  check(!SourceForms.matches(Set.of("fluid:gas","fluid:plasma"),"fluid:molten",false));
  check(!SourceForms.matches(Set.of(),"item:dust",true));
  var unknown=new AEItemKey(null,null,"custom");unknown.nbt="a";var unknown2=new AEItemKey(null,null,"custom");unknown2.nbt="b";
  check(!SourceForms.form(unknown).equals(SourceForms.form(unknown2)));check(!SourceForms.matches(Set.of(SourceForms.form(unknown)),SourceForms.form(unknown2),true));
  SourceForms.add(forms,new AEItemKey(iron,"item:plate","plate"));check(!frozen.get(iron.getResourceLocation).contains("item:plate"));
  System.out.println("PASS: powders, hot/normal ingots, molten/liquid/gas/plasma distinguish forms; direct/other independently match, reference form persists across candidate materials, unknown identity and immutable indexes preserved.");
 }
__HELPER__
}'''.replace('__COND__',condition).replace('__HELPER__',s)
j=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp);(p/'FormsFixture.java').write_text(code,encoding='utf-8');subprocess.run([j/'javac.exe','-d',p,p/'FormsFixture.java'],check=True);subprocess.run([j/'java.exe','-cp',p,'FormsFixture'],check=True)
assert 'attributes.add("process:" + name)' not in rules
assert 'token.startsWith("process:") || token.startsWith("use:")' in rules
assert 'source:direct:' in rules and 'source:other:' in rules
