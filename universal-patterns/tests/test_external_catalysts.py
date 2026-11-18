from pathlib import Path
import subprocess,re
root=Path(__file__).resolve().parents[2];work=root/'build/universal-patterns/catalyst-tests';work.mkdir(exist_ok=True)
s=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/ExternalCatalystInputs.java').read_text()
s=re.sub(r'^package .*;\n|^import .*;\n','',s,flags=re.M)
s=s.replace('public final class ExternalCatalystInputs','public final class CatalystFixture').replace('private ExternalCatalystInputs()','private CatalystFixture()')
fixture='''
 interface AEKey {}
 record Stack(String name,AEKey payload){boolean isEmpty(){return name.isEmpty();}}
 record AEItemKey(String name,AEKey wrapped) implements AEKey {static AEItemKey of(Stack s){return new AEItemKey(s.name,s.payload);}Stack getReadOnlyStack(){return new Stack(name,wrapped);}}
 record AEFluidKey(String fluid,String tag) implements AEKey {static AEFluidKey of(String f,String t){return new AEFluidKey(f,t);}}
 record GenericStack(AEKey what,long amount){}
 record Fluid(String fluid,String tag){boolean isEmpty(){return fluid.isEmpty();}String getFluid(){return fluid;}String getTag(){return tag;}}
 record Items(Stack... values){Stack[] getItems(){return values;}}
 record Fluids(Fluid... values){Fluid[] getStacks(){return values;}}
 static class Content {int chance;Object content;Content(int chance,Object value){this.chance=chance;content=value;}}
 static class ItemRecipeCapability {static final ItemRecipeCapability CAP=new ItemRecipeCapability();Items of(Object o){return (Items)o;}}
 static class FluidRecipeCapability {static final FluidRecipeCapability CAP=new FluidRecipeCapability();Fluids of(Object o){return (Fluids)o;}}
 static class GTRecipe {List<Content> items=new ArrayList<>(),tickItems=new ArrayList<>(),fluids=new ArrayList<>(),tickFluids=new ArrayList<>();List<Content> getInputContents(Object cap){return cap==ItemRecipeCapability.CAP?items:fluids;}List<Content> getTickInputContents(Object cap){return cap==ItemRecipeCapability.CAP?tickItems:tickFluids;}}
 static class GTRecipeWrapper {GTRecipe recipe;GTRecipeWrapper(GTRecipe r){recipe=r;}}
 static class VirtualIngredientBehavior {static Stack canonicalStack(Stack s){return s.payload==null?new Stack("",null):s;}static AEKey payloadItemKey(Stack s){return s.payload instanceof AEItemKey?s.payload:null;}static AEKey payloadFluidKey(Stack s){return s.payload instanceof AEFluidKey?s.payload:null;}}
 static AEItemKey item(String s){return new AEItemKey(s,null);}static List<GenericStack> slot(AEKey s){return List.of(new GenericStack(s,1));}
 static Content itemContent(String s,int chance){return new Content(chance,new Items(new Stack(s,null)));}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]args){
  var r=new GTRecipe();r.items.add(itemContent("ore",10000));r.items.add(itemContent("mold",0));r.tickItems.add(itemContent("tool",0));r.items.add(itemContent("chance",5000));
  var inputs=List.of(slot(item("ore")),slot(item("mold")),slot(item("tool")),slot(item("chance")));
  var result=remove(inputs,new GTRecipeWrapper(r));check(result.equals(List.of(inputs.get(0),inputs.get(3))));check(inputs.size()==4);
  var token=new AEItemKey("virtual",item("mold"));check(remove(List.of(slot(item("ore")),slot(token)),r).size()==1);
  r.items.add(itemContent("mold",10000));check(remove(List.of(slot(item("ore")),slot(item("mold")),slot(token)),r).size()==3);
  r.fluids.add(new Content(0,new Fluids(new Fluid("water","pure"))));
  check(remove(List.of(slot(item("ore")),slot(new AEFluidKey("water","pure")),slot(new AEFluidKey("water","dirty"))),r).size()==2);
  check(remove(List.of(List.of(new GenericStack(item("tool"),1),new GenericStack(item("ore"),1))),r).size()==1);
  check(remove(inputs,new Object())==inputs);
  check(remove(List.of(slot(item("tool"))),r).isEmpty());
  System.out.println("PASS: consumables and probabilistic inputs retained; reusable ordinary/tick inputs and tokens removed; duplicate-key safety, mixed alternatives, exact fluid tags, non-GT recipes, zero-input guard signal.");
 }
'''
s='import java.util.*;\n'+s[:s.rfind('}')]+fixture+'}\n';(work/'CatalystFixture.java').write_text(s)
j=Path('C:/Program Files/Java/jdk-22/bin');subprocess.run([j/'javac.exe','-d',work,work/'CatalystFixture.java'],check=True);subprocess.run([j/'java.exe','-cp',work,'CatalystFixture'],check=True)
