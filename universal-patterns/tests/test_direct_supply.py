from pathlib import Path
import re,subprocess,tempfile
r=Path(__file__).resolve().parents[2]
s=(r/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/DirectCatalystSupply.java').read_text(encoding='utf-8')
s=re.sub(r'^(package|import) .*;\s*$','',s,flags=re.M).replace('public final class DirectCatalystSupply','static final class DirectCatalystSupply')
code='''import java.util.*;
public class DirectFixture {
 record AEKey(String id){}
 static class AEItemKey {static AEKey of(Stack s){return s.key;}}
 static class AEFluidKey {static AEKey of(String fluid,Object tag){return new AEKey(fluid+tag);}}
 record Stack(AEKey key){String getFluid(){return key.id;} Object getTag(){return "";}}
 record GenericStack(AEKey what,long amount){}
 static class Ingredient {Stack[] stacks; Ingredient(Stack...s){stacks=s;} Stack[] getItems(){return stacks;}Stack[] getStacks(){return stacks;}long getAmount(){return 1000;}}
 static class SizedIngredient extends Ingredient {SizedIngredient(Stack...s){super(s);}long getAmount(){return 4;}}
 static class LongIngredient extends Ingredient {long getActualAmount(){return 3000000000L;}}
 static class Cap {Ingredient of(Object o){return (Ingredient)o;}}
 static class ItemRecipeCapability {static Cap CAP=new Cap();} static class FluidRecipeCapability {static Cap CAP=new Cap();}
 static class Content {int chance;Ingredient content;Content(int c,Ingredient i){chance=c;content=i;}}
 record Recipe(List<Content> items,List<Content> fluids,List<Content> tick){List<Content> getInputContents(Cap cap){return cap==ItemRecipeCapability.CAP?items:fluids;}List<Content> getTickInputContents(Cap cap){return cap==ItemRecipeCapability.CAP?tick:List.of();}}
 record RecipePattern(Recipe recipe){}
 record Selection(Set<AEKey> direct){}
 static class CatalystSupply {static int mode(Selection s,AEKey k){return s.direct.contains(k)?3:0;}static int circuit(RecipePattern r,Selection s){return -1;}}
 static class Level{}
 static class Tag {Map<String,Object> values=new HashMap<>();String getString(String s){return (String)values.getOrDefault(s,"");}void putString(String k,String v){values.put(k,v);}void putInt(String k,int v){values.put(k,v);}}
 static class Encoded {Tag tag=new Tag();GenericStack[] in,out;Encoded(GenericStack[] i,GenericStack[] o){in=i;out=o;}Encoded getReadOnlyStack(){return this;}Tag getTag(){return tag;}Tag getOrCreateTag(){return tag;}}
 interface IPatternDetails {Encoded getDefinition();}
 static class AEProcessingPattern implements IPatternDetails {Encoded e;AEProcessingPattern(Encoded e){this.e=e;}public Encoded getDefinition(){return e;}GenericStack[] getSparseInputs(){return e.in;}GenericStack[] getSparseOutputs(){return e.out;}}
 static class PatternDetailsHelper {static Encoded encodeProcessingPattern(GenericStack[] i,GenericStack[] o){if(i.length>81)throw new IllegalArgumentException();return new Encoded(i,o);}static IPatternDetails decodePattern(Encoded e,Level l){return new AEProcessingPattern(e);}}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 var circuit=new AEKey("circuit:3");var other=new AEKey("circuit:4");var mold=new AEKey("mold");var water=new AEKey("water");var iron=new AEKey("iron");
 var recipe=new RecipePattern(new Recipe(List.of(new Content(0,new Ingredient(new Stack(circuit),new Stack(other))),new Content(0,new SizedIngredient(new Stack(mold)))),List.of(new Content(0,new Ingredient(new Stack(water)))),List.of(new Content(0,new Ingredient(new Stack(circuit))))));
 var original=new AEProcessingPattern(new Encoded(new GenericStack[]{new GenericStack(iron,8)},new GenericStack[]{new GenericStack(iron,1)}));original.e.tag.putString("gtlcoreRecipe","test");
 check(DirectCatalystSupply.apply(new Selection(Set.of()),recipe,original,new Level())==original);
 var result=(AEProcessingPattern)DirectCatalystSupply.apply(new Selection(Set.of(circuit,mold,water)),recipe,original,new Level());
 var amounts=new HashMap<AEKey,Long>();for(var g:result.getSparseInputs())amounts.put(g.what(),g.amount());
 check(amounts.equals(Map.of(iron,8L,circuit,1L,mold,4L,water,1000L)));check(!amounts.containsKey(other));check(result.e.tag.getString("gtlcoreRecipe").equals("test"));check(result.e.tag.values.get("gtlcoreRecipeCircuit").equals(-1));check(original.getSparseInputs().length==1);
 System.out.println("PASS: direct items/fluids, catalyst quantities, circuit NBT identities, alternative selection, reusable tick deduplication, no virtual mode delivery, metadata and original pattern preserved.");
 }
'''+s+'\n}'
java=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory() as t:
 p=Path(t);(p/'DirectFixture.java').write_text(code,encoding='utf-8');subprocess.run([java/'javac.exe','-d',p,p/'DirectFixture.java'],check=True);subprocess.run([java/'java.exe','-cp',p,'DirectFixture'],check=True)
