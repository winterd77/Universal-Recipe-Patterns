from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[2]
def body(p,marker):
 s=p.read_text(encoding='utf-8-sig');a=s.index('{',s.index(marker))+1;b=a;d=1
 while d:d+=(s[b]=='{')-(s[b]=='}');b+=1
 return s[a:b-1]
b=r/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/mixin'
slot=body(b/'UniversalTerminalSlotMixin.java','private void universalPatterns$reject(')
encode=body(b/'UniversalTerminalEncodeMixin.java','private void universalPatterns$protect(')
core=body(r/'enhancements/core-recipes/src/UniversalPatternRecipeOverrideMixin.java','private void gtlcore$replaceUniversalRecipe(')
code='''import java.util.*;
public class SafetyFixture {
 static class UniversalPatternItem{} static class PatternEncodingTermMenu{} static class ProviderMenu{}
 record ItemStack(Object item){Object getItem(){return item;}}
 static class CallbackInfo {boolean cancelled;void cancel(){cancelled=true;}}
 static class CallbackInfoReturnable<T>{T result;void setReturnValue(T v){result=v;}}
 static class RestrictedInputSlot {Object menu;ItemStack item;RestrictedInputSlot(Object m,ItemStack i){menu=m;item=i;}Object getMenu(){return menu;}ItemStack getItem(){return item;}
 void reject(ItemStack stack,CallbackInfoReturnable<Boolean> cir){__SLOT__}}
 static class Guard {RestrictedInputSlot encodedPatternSlot;void protect(CallbackInfo ci){__ENCODE__}}
 record ResourceLocation(String value){static ResourceLocation tryParse(String s){return new ResourceLocation(s);}}
 static class JsonElement{}
 static class ModList {static Set<String> loaded=Set.of();static ModList get(){return new ModList();}boolean isLoaded(String name){return loaded.contains(name);}}
 static void remove(Map<ResourceLocation,JsonElement> recipes){__CORE__}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 var card=new ItemStack(new UniversalPatternItem());var ordinary=new ItemStack(new Object());
 var terminal=new RestrictedInputSlot(new PatternEncodingTermMenu(),card);var result=new CallbackInfoReturnable<Boolean>();terminal.reject(card,result);check(Boolean.FALSE.equals(result.result));
 result=new CallbackInfoReturnable<>();terminal.reject(ordinary,result);check(result.result==null);
 var provider=new RestrictedInputSlot(new ProviderMenu(),card);result=new CallbackInfoReturnable<>();provider.reject(card,result);check(result.result==null);
 var guard=new Guard();guard.encodedPatternSlot=terminal;var ci=new CallbackInfo();guard.protect(ci);check(ci.cancelled&&terminal.item==card);
 terminal.item=ordinary;ci=new CallbackInfo();guard.protect(ci);check(!ci.cancelled);
 var base=ResourceLocation.tryParse("gtlcore:universal_pattern");var replacement=ResourceLocation.tryParse("gtlcore:universal_pattern_from_wildcard");var reset=ResourceLocation.tryParse("gtlcore:universal_pattern_reset");
 for(boolean addon:List.of(false,true))for(boolean wildcard:List.of(false,true))for(boolean present:List.of(false,true)){
  var mods=new HashSet<String>();if(addon)mods.add("gtluniversalpatterns");if(wildcard)mods.add("wildcard_pattern");ModList.loaded=mods;
  var recipes=new HashMap<ResourceLocation,JsonElement>();recipes.put(base,new JsonElement());recipes.put(reset,new JsonElement());if(present)recipes.put(replacement,new JsonElement());
  remove(recipes);check(recipes.containsKey(base)!= (addon&&wildcard&&present));check(recipes.containsKey(reset));if(present)check(recipes.containsKey(replacement));remove(recipes);
 }
 System.out.println("PASS: terminal rejects universal cards but preserves normal cards/provider insertion; already inserted card protected from overwrite; Core replacement presence/mod conditions/reset preservation and repeated reload removal.");
 }
}'''.replace('__SLOT__',slot).replace('__ENCODE__',encode).replace('__CORE__',core)
j=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp);(p/'SafetyFixture.java').write_text(code,encoding='utf-8');subprocess.run([j/'javac.exe','-d',p,p/'SafetyFixture.java'],check=True);subprocess.run([j/'java.exe','-cp',p,'SafetyFixture'],check=True)
