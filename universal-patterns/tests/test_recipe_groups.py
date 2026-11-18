from pathlib import Path
import re,subprocess,tempfile
root=Path(__file__).resolve().parents[2]
s=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/RecipeVariantGroups.java').read_text(encoding='utf-8')
s=re.sub(r'^(package|import) .*;\s*$','',s,flags=re.M).replace('public final class RecipeVariantGroups','static final class RecipeVariantGroups')
f='''import java.util.*;
public class GroupProbe {
 static class AEKey {String id;AEKey(String id){this.id=id;}}
 static class AEFluidKey extends AEKey{AEFluidKey(String id){super(id);}}
 static class CatalystSupply{static String identity(AEKey k){return k.id;}}
 record GenericStack(AEKey what,long amount){} record Pattern(GenericStack[] getOutputs){}
 record Slot(AEKey defaultKey,long amount,List<AEKey> alternatives){}
 static class Content{int chance=1,maxChance=1;}
 static class Type{String registryName="cut";}
 static class Recipe{Type recipeType=new Type();List<Object> conditions=new ArrayList<>(),ingredientActions=new ArrayList<>();Map<String,List<Content>> outputs=new HashMap<>();Map<String,String> tickInputs=Map.of("power","32"),tickOutputs=Map.of();String data="{}";boolean isFuel;int ocTier,parallels;}
 record RecipePattern(Recipe recipe,Pattern pattern,List<Slot> inputSlots,int circuit,List<List<AEKey>> nonConsumedInputs,boolean chancedInput,String id){}
 __SOURCE__
 static Slot slot(AEKey key,long amount){return new Slot(key,amount,List.of(key));}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 var rod=new AEKey("al:rod");var bolt=new AEKey("al:bolt");var water=new AEFluidKey("water");var oil=new AEFluidKey("oil");
 var result=new Pattern(new GenericStack[]{new GenericStack(bolt,4)});
 var a=new RecipePattern(new Recipe(),result,List.of(slot(rod,1),slot(water,100)),0,List.of(),false,"a");
 var b=new RecipePattern(new Recipe(),result,List.of(slot(oil,5),slot(rod,1)),0,List.of(),false,"b");
 check(RecipeVariantGroups.key(a).equals(RecipeVariantGroups.key(b)));
 var changedAmount=new RecipePattern(new Recipe(),result,List.of(slot(rod,2),slot(water,100)),0,List.of(),false,"c");check(RecipeVariantGroups.groups(List.of(a,changedAmount)).size()==1);
 var two=new RecipePattern(new Recipe(),result,List.of(slot(rod,1),slot(water,100),slot(oil,5)),0,List.of(),false,"two");check(RecipeVariantGroups.groups(List.of(a,two)).size()==2);
 var steam=new AEFluidKey("steam");
 var twoAlt=new RecipePattern(new Recipe(),result,List.of(slot(steam,7),slot(rod,1),slot(oil,5)),0,List.of(),false,"twoAlt");
 check(RecipeVariantGroups.groups(List.of(two,twoAlt)).size()==1);
 var otherRod=new AEKey("otherRod");
 var itemAlt=new RecipePattern(new Recipe(),result,List.of(slot(water,100),slot(otherRod,2)),0,List.of(),false,"itemAlt");
 check(RecipeVariantGroups.groups(List.of(a,itemAlt)).size()==1);
 var unknown=new AEKey("extra");var unrelated=new RecipePattern(new Recipe(),result,List.of(slot(unknown,3),slot(steam,4)),0,List.of(),false,"unrelated");
 check(RecipeVariantGroups.groups(List.of(a,unrelated)).size()==2);
 var conditional=new Recipe();conditional.conditions.add("temperature");var c=new RecipePattern(conditional,result,a.inputSlots,0,List.of(),false,"conditional");check(RecipeVariantGroups.key(c).equals("single:conditional"));
 var diff=new RecipePattern(new Recipe(),new Pattern(new GenericStack[]{new GenericStack(bolt,8)}),a.inputSlots,0,List.of(),false,"diff");check(!RecipeVariantGroups.key(a).equals(RecipeVariantGroups.key(diff)));
 System.out.println("PASS: generic item and multi-fluid alternatives group by unchanged inputs; order and variant amounts do not matter; extra inputs, unrelated paths, outputs and conditions stay separate.");
 }
}'''.replace('__SOURCE__',s)
with tempfile.TemporaryDirectory(dir=root/'build') as t:
 p=Path(t);(p/'GroupProbe.java').write_text(f,encoding='utf-8');jdk=Path('C:/Program Files/Java/jdk-22/bin');subprocess.run([jdk/'javac.exe','-d',p,p/'GroupProbe.java'],check=True);subprocess.run([jdk/'java.exe','-cp',p,'GroupProbe'],check=True)
