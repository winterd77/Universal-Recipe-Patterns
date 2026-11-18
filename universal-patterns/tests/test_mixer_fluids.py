from pathlib import Path
import os,subprocess,tempfile
root=Path(__file__).resolve().parents[2]
def method(source,marker):
 a=source.index(marker);b=source.index('{',a)+1;depth=1
 while depth:depth+=(source[b]=='{')-(source[b]=='}');b+=1
 return source[a:b]
source=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/RegisteredProcessFamilies.java').read_text(encoding='utf-8')
profile=method(source,'    static @Nullable String powderBlendProfile(')+method(source,'    private static boolean isPowder(')
profile=profile.replace('@Nullable ','').replace('RegisteredProcessFamilies::','MixerFluidFixture::')
helper_path = os.environ.get('GTL_PATTERN_HELPER_SOURCE')
if not helper_path:
    raise SystemExit('Set GTL_PATTERN_HELPER_SOURCE to MERecipePatternHelper.java from the matching GTLCore source.')
helper = Path(helper_path).read_text(encoding='utf-8')
start=helper.index('    private static boolean appendFluids(List<Content> contents, Map<AEKey, Long> stacks, long multiplier, boolean output,')
fluid=method(helper[start:],'    private static boolean appendFluids(').replace('@Nullable ','')+method(helper,'    private static void add(')
code=r"""import java.util.*;
public class MixerFluidFixture {
 static class AEKey {String id,form;AEKey(String i,String f){id=i;form=f;}String getId(){return id;}public boolean equals(Object o){return o instanceof AEKey k&&id.equals(k.id)&&form.equals(k.form);}public int hashCode(){return Objects.hash(id,form);}}
 static class AEItemKey extends AEKey {AEItemKey(String id,String form){super(id,form);}}
 static class AEFluidKey extends AEKey {AEFluidKey(String id){super(id,"fluid:liquid");}static AEFluidKey of(String fluid,String tag){return new AEFluidKey(fluid+tag);}}
 static class UniversalPatternFamily {static String formOf(AEKey k){return k.form;}}
 record InputSlot(String id,long amount,List<AEKey> alternatives,AEKey defaultKey){}
 record RecipePattern(List<InputSlot> inputSlots){}
 record FluidStack(String getFluid,String getTag){boolean isEmpty(){return false;}}
 record Ingredient(FluidStack[] getStacks,long getAmount){}
 static class Content {int chance,maxChance=100;Object content;Content(int c,Ingredient i){chance=c;content=i;}}
 static class FluidRecipeCapability {static final FluidRecipeCapability CAP=new FluidRecipeCapability();Ingredient of(Object o){return (Ingredient)o;}}
 __PROFILE__
 __FLUID__
 static InputSlot slot(AEKey k,long n){return new InputSlot(k.id,n,List.of(k),k);}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 var a=new AEItemKey("copper","item:dust");var b=new AEItemKey("tin","item:dust");var c=new AEItemKey("carbon","item:dustTiny");
 var bronze=new AEItemKey("bronze","item:dust");var steel=new AEItemKey("steel","item:dust");var water=new AEFluidKey("water");var acid=new AEFluidKey("acid");
 var two=new RecipePattern(List.of(slot(a,3),slot(b,1)));var three=new RecipePattern(List.of(slot(a,9),slot(b,2),slot(c,7)));
 check(powderBlendProfile(two,bronze).equals(powderBlendProfile(three,steel)));check(two.inputSlots().get(0).amount()==3&&three.inputSlots().size()==3);
 var wet=new RecipePattern(List.of(slot(a,2),slot(b,1),slot(water,1000)));var wet2=new RecipePattern(List.of(slot(c,8),slot(a,9),slot(b,7),slot(water,4000)));
 check(powderBlendProfile(wet,bronze).equals(powderBlendProfile(wet2,steel)));check(!powderBlendProfile(wet,bronze).equals(powderBlendProfile(two,bronze)));
 check(!powderBlendProfile(wet,bronze).equals(powderBlendProfile(new RecipePattern(List.of(slot(a,2),slot(acid,1000))),bronze)));
 check(powderBlendProfile(two,new AEItemKey("iron","item:ingot"))==null);
 var stacks=new HashMap<AEKey,Long>();var slots=new ArrayList<InputSlot>();
 var consumed=new Content(100,new Ingredient(new FluidStack[]{new FluidStack("water","{grade:1}")},1500));
 var reusable=new Content(0,new Ingredient(new FluidStack[]{new FluidStack("acid","{}")},500));
 check(appendFluids(List.of(consumed,reusable),stacks,20,false,"tick_fluid",slots));check(stacks.get(AEFluidKey.of("water","{grade:1}"))==30000L&&slots.get(0).amount()==30000L&&slots.size()==1);
 check(appendFluids(List.of(new Content(100,new Ingredient(new FluidStack[]{new FluidStack("water","{grade:2}")},250))),stacks,1,false,"fluid",slots));check(stacks.size()==2&&slots.size()==2);
 var outputs=new HashMap<AEKey,Long>();check(appendFluids(List.of(consumed),outputs,1,true,"out",null));check(outputs.get(AEFluidKey.of("water","{grade:1}"))==1500L);
 boolean overflow=false;try{appendFluids(List.of(new Content(100,new Ingredient(new FluidStack[]{new FluidStack("water","{}")},Long.MAX_VALUE))),new HashMap<>(),2,false,"fluid",new ArrayList<>());}catch(ArithmeticException e){overflow=true;}check(overflow);
 System.out.println("PASS: production powder family accepts different ingredient counts/identities/amounts; dry/wet and different reagents remain separate. Production fluid encoding preserves amounts, tick scaling, NBT and outputs, excludes reusable fluids, and rejects overflow.");
 }
}
""".replace('__PROFILE__',profile).replace('__FLUID__',fluid)
java=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory(dir=root/'build/universal-provider') as folder:
 p=Path(folder);(p/'MixerFluidFixture.java').write_text(code,encoding='utf-8');subprocess.run([java/'javac.exe','-d',p,p/'MixerFluidFixture.java'],check=True);subprocess.run([java/'java.exe','-cp',p,'MixerFluidFixture'],check=True)
