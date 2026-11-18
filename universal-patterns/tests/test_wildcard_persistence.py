from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
source=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/mixin/UniversalWildcardRuntimeMixin.java').read_text(encoding='utf-8')
def method(marker):
 start=source.index(marker);end=source.index('{',start)+1;depth=1
 while depth:
  depth+=(source[end]=='{')-(source[end]=='}');end+=1
 return source[start:end]
methods='\n'.join(method(marker) for marker in ['private AEItemKey universalPatterns$identity(', 'private void universalPatterns$save(', 'private void universalPatterns$load(', 'private void universalPatterns$circuits('])
code=r'''
import java.util.*;
public class WildcardPersistenceFixture {
 static class Tag {static final int TAG_COMPOUND=10;}
 static class CompoundTag extends HashMap<String,Object> {
  CompoundTag getCompound(String k){return (CompoundTag)getOrDefault(k,new CompoundTag());}
  ListTag getList(String k,int type){return (ListTag)getOrDefault(k,new ListTag());}
  int getInt(String k){return (Integer)getOrDefault(k,-1);}
  String getString(String k){return (String)getOrDefault(k,"");}
  boolean contains(String k){return containsKey(k);}
 }
 static class ListTag extends ArrayList<CompoundTag> {
  CompoundTag getCompound(int i){return get(i);}
  ListTag copy(){var r=new ListTag();r.addAll(this);return r;}
 }
 static class UniversalPatternItem {static Object read(ItemStack s){return null;}}
 static class CatalystSupply {static int circuit(Recipe r,Object policy){return r.pattern().key().circuit();}}
 static class ItemStack {
  String id;String recipe;int circuit;boolean card;
  ItemStack(String id,String recipe,int circuit){this.id=id;this.recipe=recipe;this.circuit=circuit;}
  Object getItem(){return card?new UniversalPatternItem():new Object();}
  CompoundTag getTag(){var t=new CompoundTag();t.put("gtlcoreRecipe",recipe);t.put("gtlcoreRecipeCircuit",circuit);return t;}
  CompoundTag save(CompoundTag t){t.put("id",id);t.put("recipe",recipe);t.put("circuit",circuit);return t;}
  static ItemStack of(CompoundTag t){return new ItemStack(t.getString("id"),t.getString("recipe"),t.getInt("circuit"));}
 }
 record AEItemKey(String id,String recipe,int circuit) {
  static AEItemKey of(ItemStack stack){return new AEItemKey(stack.id,stack.recipe,stack.circuit);}
  ItemStack toStack(){return new ItemStack(id,recipe,circuit);}
  ItemStack getReadOnlyStack(){return toStack();}
 }
 record IPatternDetails(AEItemKey key) {AEItemKey getDefinition(){return key;}}
 record Recipe(IPatternDetails pattern) {}
 static class UniversalPatternExpansion {
  static Map<String,Recipe> recipes=new HashMap<>();
  static Recipe recipe(Object level,String id){return recipes.get(id);}
 }
 static class UniversalPatternReload {static void track(Object o){}}
 static class CallbackInfo {}
 static class InternalSlot {
  CompoundTag data=new CompoundTag();int circuit=-1;
  InternalSlot(String material){if(!material.isEmpty())data.put("material",material);}
  boolean isActive(){return !data.isEmpty();}
  CompoundTag serializeNBT(){return data;}
  void deserializeNBT(CompoundTag value){data=value;}
  InternalSlot getCacheManager(){return this;}
  void setCircuitCache(int value){circuit=value;}
  void clearCircuitCache(){circuit=-1;}
 }
 static class Cards {ItemStack card=new ItemStack("card","",-1);Cards(){card.card=true;}ItemStack getStackInSlot(int i){return card;}}
 Cards wildcardPatternSlot=new Cards();
 List<InternalSlot> internalSlots=new ArrayList<>();
 List<IPatternDetails> expandedPatterns=new ArrayList<>();
 ListTag universalPatterns$pending=new ListTag();
 List<CompoundTag> refunds=new ArrayList<>();
 Object getLevel(){return null;}
 void refundPersistedSlotData(List<CompoundTag> slots){refunds.addAll(slots);}
 __METHODS__
 static IPatternDetails pattern(String definition,String recipe,int circuit){return new IPatternDetails(new AEItemKey(definition,recipe,circuit));}
 static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
 public static void main(String[] args){
  var a=pattern("same-output/input-a","a",1);var b=pattern("same-output/input-b","b",2);
  UniversalPatternExpansion.recipes.put("a",new Recipe(a));UniversalPatternExpansion.recipes.put("b",new Recipe(b));
  var old=new WildcardPersistenceFixture();old.expandedPatterns.addAll(List.of(pattern("a+virtual-token-v1","a",1),b));
  old.internalSlots.addAll(List.of(new InternalSlot("input-a"),new InternalSlot("input-b")));
  var tag=new CompoundTag();tag.put("wildcardDynamicSlots",new ListTag());old.universalPatterns$save(tag,false,new CallbackInfo());
  check(!tag.contains("wildcardDynamicSlots"),"output-only legacy entries must not duplicate universal slots");
  check(tag.getList("UniversalDynamicSlots",10).size()==2,"same-output variants retained separately");
  var reload=new WildcardPersistenceFixture();reload.universalPatterns$load(tag,new CallbackInfo());
  var earlySave=new CompoundTag();reload.universalPatterns$save(earlySave,false,new CallbackInfo());
  check(earlySave.getList("UniversalDynamicSlots",10).size()==2,"save before delayed refresh must preserve pending state");
  reload.expandedPatterns.addAll(List.of(b,pattern("a+virtual-token-v2","a",1)));
  reload.internalSlots.addAll(List.of(new InternalSlot(""),new InternalSlot("")));
  reload.universalPatterns$circuits(true,new CallbackInfo());
  check(reload.internalSlots.get(0).data.getString("material").equals("input-b"),"reordered variant b");
  check(reload.internalSlots.get(1).data.getString("material").equals("input-a"),"virtual route change must not lose a");
  check(reload.internalSlots.get(0).circuit==2 && reload.internalSlots.get(1).circuit==1,"circuits isolated");
  check(reload.refunds.isEmpty() && reload.universalPatterns$pending.isEmpty(),"restored once");
  var removed=new WildcardPersistenceFixture();removed.universalPatterns$load(tag,new CallbackInfo());removed.expandedPatterns.add(b);removed.internalSlots.add(new InternalSlot(""));
  removed.universalPatterns$circuits(true,new CallbackInfo());
  check(removed.refunds.size()==1 && removed.refunds.get(0).getString("material").equals("input-a"),"missing recipe refunded");
  var legacy=new WildcardPersistenceFixture();legacy.wildcardPatternSlot.card.card=false;var untouched=new CompoundTag();untouched.put("wildcardDynamicSlots",new ListTag());
  legacy.universalPatterns$save(untouched,false,new CallbackInfo());check(untouched.contains("wildcardDynamicSlots")&&!untouched.contains("UniversalDynamicSlots"),"legacy save unchanged");
  System.out.println("PASS: production wildcard persistence keeps same-output variants separate, restores reordered slots/circuits and changed virtual routes, preserves delayed-load saves, refunds orphaned inputs, and leaves legacy cards unchanged.");
 }
}
'''.replace('__METHODS__',methods)
java=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory(dir=root/'build/universal-provider') as folder:
 path=Path(folder);(path/'WildcardPersistenceFixture.java').write_text(code,encoding='utf-8')
 subprocess.run([java/'javac.exe','-encoding','UTF-8','-d',path,path/'WildcardPersistenceFixture.java'],check=True)
 subprocess.run([java/'java.exe','-cp',path,'WildcardPersistenceFixture'],check=True)
