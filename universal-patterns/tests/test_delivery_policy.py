from pathlib import Path
import re,subprocess,tempfile
root=Path(__file__).resolve().parents[2]
s=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/DeliveryPolicy.java').read_text(encoding='utf-8');s=re.sub(r'^(package|import) .*;\s*$','',s,flags=re.M).replace('public final class DeliveryPolicy','static final class DeliveryPolicy').replace('com.mojang.brigadier.exceptions.CommandSyntaxException','CommandSyntaxException').replace('net.minecraft.resources.ResourceLocation','ResourceLocation').replace('net.minecraft.core.registries.BuiltInRegistries','BuiltInRegistries')
f='''import java.util.*;
public class DeliveryProbe {
 static class Value{String text;Value(String t){text=t;}Value copy(){return new Value(text);}}
 static class Tag extends HashMap<String,Value>{String encoded;Tag(String s){encoded=s;}public String toString(){return (containsKey("#c")?get("#c").text+"|":"")+encoded;}String getString(String k){return k.equals("id")?encoded:containsKey(k)?get(k).text:"";}void putString(String k,String v){put(k,new Value(v));}boolean contains(String k){return containsKey(k);}Set<String> getAllKeys(){return keySet();}}
 static class CommandSyntaxException extends Exception{}
 static class TagParser{static Tag parseTag(String s)throws CommandSyntaxException{if(s.equals("bad"))throw new CommandSyntaxException();var parts=s.split("[|]",2);var t=new Tag(parts.length==2?parts[1]:s);if(parts.length==2)t.putString("#c",parts[0]);return t;}}
 static Map<String,AEKey> registry=new HashMap<>();
 static class AEKey{String id;AEKey(String id){this.id=id;registry.put(id,this);}Tag toTag(){return new Tag(id);}Tag toTagGeneric(){var t=toTag();t.putString("#c",this instanceof AEFluidKey?"ae2:fluid":"ae2:item");return t;}static AEKey fromTagGeneric(Tag t){return t.contains("#c")?registry.get(t.encoded):null;}}
 record ResourceLocation(String id){static ResourceLocation tryParse(String id){return new ResourceLocation(id);}}
 static class Registry{boolean fluid;Registry(boolean f){fluid=f;}boolean containsKey(ResourceLocation id){return fluid?registry.get(id.id()) instanceof AEFluidKey:registry.get(id.id()) instanceof AEItemKey;}}
 static class BuiltInRegistries{static Registry ITEM=new Registry(false),FLUID=new Registry(true);}
 record AEKeyType(String getId){static AEKeyType items(){return new AEKeyType("ae2:item");}static AEKeyType fluids(){return new AEKeyType("ae2:fluid");}}
 static class AEItemKey extends AEKey{AEItemKey(String id){super(id);}}
 static class AEFluidKey extends AEKey{AEFluidKey(String id){super(id);}}
 static class CatalystSupply{static String identity(AEKey k){return k.id;}}
 record GenericStack(AEKey what,long amount){}
 record Selection(Set<String> ignoredInputs,Map<String,Long> extraInputs,Set<String> ignoredOutputs,Map<String,Long> extraOutputs,Map<String,String> primaryOutputs){Selection(Set<String> i,Map<String,Long> e){this(i,e,Set.of(),Map.of(),Map.of());}Selection(Set<String> i,Map<String,Long> e,Set<String> o,Map<String,Long> x){this(i,e,o,x,Map.of());}}
 static class Level{}
 static class Stack{Tag tag=new Tag("pattern");GenericStack[] inputs,outputs;Tag getTag(){return tag;}Tag getOrCreateTag(){return tag;}}
 record Definition(Stack getReadOnlyStack){}
 interface IPatternDetails{Definition getDefinition();}
 static class AEProcessingPattern implements IPatternDetails{static final int MAX_INPUT_SLOTS=9,MAX_OUTPUT_SLOTS=3;Stack stack=new Stack();AEProcessingPattern(GenericStack[] i,GenericStack[] o){stack.inputs=i;stack.outputs=o;}public Definition getDefinition(){return new Definition(stack);}GenericStack[] getSparseInputs(){return stack.inputs;}GenericStack[] getSparseOutputs(){return stack.outputs;}}
 static class PatternDetailsHelper{static Stack encodeProcessingPattern(GenericStack[] i,GenericStack[] o){var s=new Stack();s.inputs=i;s.outputs=o;return s;}static IPatternDetails decodePattern(Stack s,Level l){var p=new AEProcessingPattern(s.inputs,s.outputs);p.stack=s;return p;}}
 __SOURCE__
 static void check(boolean b){if(!b)throw new AssertionError();}
 static long amount(List<GenericStack> values,AEKey k){return values.stream().filter(s->s.what==k).mapToLong(GenericStack::amount).sum();}
 public static void main(String[] args){
 var rod=new AEItemKey("rod");var water=new AEFluidKey("water");var oil=new AEFluidKey("oil");var bolt=new AEItemKey("bolt");var nbt=new AEItemKey("rod{variant:1}");
 var original=new AEProcessingPattern(new GenericStack[]{new GenericStack(rod,2),new GenericStack(water,1000)},new GenericStack[]{new GenericStack(bolt,4)});original.stack.tag.put("gtlcoreRecipe",new Value("registered:cut"));
 var policy=new Selection(Set.of("water"),Map.of("oil",500L,"rod",3L));
 var changed=(AEProcessingPattern)DeliveryPolicy.apply(policy,original,new Level());check(changed!=null);
 var values=Arrays.asList(changed.getSparseInputs());check(amount(values,rod)==5&&amount(values,water)==0&&amount(values,oil)==500);
 check(Arrays.equals(changed.getSparseOutputs(),original.getSparseOutputs()));check(changed.stack.tag.get("gtlcoreRecipe").text.equals("registered:cut"));
 check(original.getSparseInputs()[0].amount()==2);check(DeliveryPolicy.apply(new Selection(Set.of(),Map.of()),original,new Level())==original);
 check(DeliveryPolicy.inputs(new Selection(Set.of("rod"),Map.of("rod",3L)),List.of(new GenericStack(rod,2),new GenericStack(nbt,4))).equals(List.of(new GenericStack(nbt,4))));
 check(DeliveryPolicy.apply(new Selection(Set.of("water","rod"),Map.of()),original,new Level())==null);
 check(DeliveryPolicy.apply(new Selection(Set.of(),Map.of("rod",Long.MAX_VALUE)),original,new Level())==null);
 check(DeliveryPolicy.apply(new Selection(Set.of(),Map.of("bad",1L)),original,new Level())==null);
 var large=DeliveryPolicy.inputs(new Selection(Set.of(),Map.of("rod",3000000000L)),List.of(new GenericStack(rod,2)));check(amount(large,rod)==3000000002L);
 var tooMany=new HashMap<String,Long>();for(int i=0;i<10;i++){new AEItemKey("extra"+i);tooMany.put("extra"+i,1L);}check(DeliveryPolicy.apply(new Selection(Set.of(),tooMany),original,new Level())==null);

 var outputPolicy=new Selection(Set.of(),Map.of(),Set.of(),Map.of("bolt",2L,"oil",500L));
 var changedOutputs=(AEProcessingPattern)DeliveryPolicy.apply(outputPolicy,original,new Level());
 check(amount(Arrays.asList(changedOutputs.getSparseOutputs()),bolt)==6);check(changedOutputs.getSparseOutputs().length==2);check(amount(Arrays.asList(changedOutputs.getSparseOutputs()),oil)==500);check(Arrays.equals(changedOutputs.getSparseInputs(),original.getSparseInputs()));
 check(DeliveryPolicy.apply(new Selection(Set.of(),Map.of(),Set.of("bolt"),Map.of()),original,new Level())==null);
 check(!DeliveryPolicy.valid(new Selection(Set.of(),Map.of(),Set.of("bolt"),Map.of()),original));
 var multi=new AEProcessingPattern(original.getSparseInputs(),new GenericStack[]{new GenericStack(bolt,4),new GenericStack(water,1000)});
 var omit=(AEProcessingPattern)DeliveryPolicy.apply(new Selection(Set.of(),Map.of(),Set.of("bolt"),Map.of("water",3000000000L)),multi,new Level());
 check(omit.getSparseOutputs().length==1&&omit.getSparseOutputs()[0].amount()==3000001000L);
 check(DeliveryPolicy.apply(new Selection(Set.of(),Map.of(),Set.of(),Map.of("bolt",Long.MAX_VALUE)),original,new Level())==null);
 var reordered=(AEProcessingPattern)DeliveryPolicy.apply(new Selection(Set.of(),Map.of(),Set.of(),Map.of(),Map.of("material","water")),multi,new Level());check(reordered.getSparseOutputs()[0].what()==water);
 try{check(DeliveryPolicy.decode(DeliveryPolicy.encode(water))==water);check(DeliveryPolicy.decode(DeliveryPolicy.encode(rod))==rod);check(DeliveryPolicy.decode("water")==water);}catch(Exception e){throw new AssertionError(e);}
 check(DeliveryPolicy.encode(water).startsWith("ae2:fluid|"));
 System.out.println("PASS: output-only edits preserve inputs, add explicit extra outputs, omit individual outputs, handle fluids/long amounts, reject empty-output and overflow plans.");
 System.out.println("PASS: production delivery encoding adds actual item/fluid amounts, omits ignored keys incl extras, merges duplicates, preserves NBT distinctions/output/recipe metadata and original patterns; long quantities work, empty/overflow/invalid/over-capacity plans fail closed.");
 }
}'''.replace('__SOURCE__',s)
with tempfile.TemporaryDirectory(dir=root/'build') as t:
 p=Path(t);(p/'DeliveryProbe.java').write_text(f,encoding='utf-8');jdk=Path('C:/Program Files/Java/jdk-22/bin');subprocess.run([jdk/'javac.exe','-d',p,p/'DeliveryProbe.java'],check=True);subprocess.run([jdk/'java.exe','-cp',p,'DeliveryProbe'],check=True)
