from pathlib import Path
import subprocess,tempfile,re
root=Path(__file__).resolve().parents[2]
env={};exec((root/'build/enhancements-v2/verify-universal-drafts.py').read_text(encoding='utf-8').split('with tempfile.TemporaryDirectory')[0],env)
code=env['code'].replace('  Object getRecipePattern(String id){return id;}','');extract=env['method']
s=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/CatalystSupply.java').read_text(encoding='utf-8')
s=re.sub(r'^(package|import) .*;\s*$', '',s,flags=re.M).replace('public final class CatalystSupply','static final class CatalystSupply')
code=code.replace('import java.util.*;','import java.util.*;import java.nio.charset.StandardCharsets;import java.security.*;')
code=code.replace('  CompoundTag tag=new CompoundTag();','  int configuration=-1;CompoundTag tag=new CompoundTag();')
extra=r"""
 static class AEKey {String tag;AEKey(String t){tag=t;}String toTag(){return tag;}}
 static class AEItemKey extends AEKey {ItemStack stack=new ItemStack();AEItemKey(String t,int circuit){super(t);stack.configuration=circuit;}ItemStack getReadOnlyStack(){return stack;}}
 static class IntCircuitBehaviour {static boolean isIntegratedCircuit(ItemStack s){return s.configuration>=0;}static int getCircuitConfiguration(ItemStack s){return s.configuration;}}
 record RecipePattern(int circuit,List<List<AEKey>> nonConsumedInputs){}
"""
widget=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/client/gui/widget/RecipePatternBrowserWidget.java').read_text(encoding='utf-8')
handler=extract(widget,'            case SET_CATALYST_MODE -> {')
handler=handler[handler.index('                if(page!='):handler.rindex('}')]
handler=handler.replace('org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply','CatalystSupply')
code=code.replace('public class DraftFixture {','public class DraftFixture { enum Page{UNIVERSAL} Page page=Page.UNIVERSAL;')
code=code.replace('static class Machine {','static class Machine {RecipePattern active;RecipePattern getRecipePattern(String id){return id.equals("steel_a")?active:null;}')
extra+=' void setMode(long revision,int slot,String recipeId,AEKey key,int mode){'+handler+'}\n'
code=code.replace(' public static void main(',extra+s+' public static void main(')
old=extract(code,' public static void main(')
main=r""" public static void main(String[] args){
 var circuit=new AEItemKey("item:gt:programmed_circuit{Configuration:3}",3);
 var circuit4=new AEItemKey("item:gt:programmed_circuit{Configuration:4}",4);
 var mold=new AEItemKey("item:gt:gear_mold",-1);
 var waterA=new AEKey("fluid:minecraft:water{grade:1}");var waterB=new AEKey("fluid:minecraft:water{grade:2}");
 var tokenC=new AEItemKey("virtual:circuit",-1);var tokenM=new AEItemKey("virtual:mold",-1);var tokenW=new AEItemKey("virtual:water",-1);
 var old=new Selection("gt:mixer|blend|dust",Set.of(),Set.of(),"all","",Map.of(),true);
 check(CatalystSupply.external(old,circuit)&&CatalystSupply.external(old,mold));
 var mixed=old.withCatalystMode(CatalystSupply.identity(circuit),false).withCatalystMode(CatalystSupply.identity(mold),true);
 check(!CatalystSupply.external(mixed,circuit)&&CatalystSupply.external(mixed,mold)&&CatalystSupply.external(mixed,circuit4));
 var supplied=CatalystSupply.filter(mixed,Map.of(circuit,tokenC,mold,tokenM));check(supplied.size()==1&&supplied.get(circuit)==tokenC);
 var recipe=new RecipePattern(3,List.of(List.of(circuit),List.of(mold)));
 check(CatalystSupply.circuit(recipe,mixed)==3);check(CatalystSupply.circuit(recipe,old)==-1);
 var fluids=new Selection("fluid_family",Set.of(),Set.of(),"all","",Map.of(),false).withCatalystMode(CatalystSupply.identity(waterA),true);
 check(CatalystSupply.external(fluids,waterA)&&!CatalystSupply.external(fluids,waterB));
 check(CatalystSupply.filter(fluids,Map.of(waterA,tokenW,waterB,tokenW)).keySet().equals(Set.of(waterB)));
 var card=new ItemStack();UniversalPatternItem.write(card,old);UniversalPatternItem.writeDraft(card,mixed);
 check(UniversalPatternItem.read(card).equals(old));check(UniversalPatternItem.readDraft(card.copy()).equals(mixed));
 UniversalPatternItem.discardDraft(card);check(UniversalPatternItem.readDraft(card).equals(old));
 UniversalPatternItem.write(card,mixed);check(UniversalPatternItem.read(card.copy()).equals(mixed));
 var replacement=new Selection("another_family",Set.of(),Set.of(),"all","",Map.of()).withSupplyFrom(mixed);check(replacement.catalystModes().equals(mixed.catalystModes()));
 var network=new DraftFixture();network.machine.cards.put(0,card.copy());network.machine.active=recipe;
 network.setMode(network.machine.getConfigurationRevision(),0,"steel_a",circuit,1);check(CatalystSupply.external(network.draft(),circuit));check(network.machine.universalSelection(0).equals(mixed));
 var good=network.draft();network.setMode(network.machine.getConfigurationRevision(),0,"steel_a",waterA,0);check(network.draft().equals(good));
 network.setMode(network.machine.getConfigurationRevision(),1,"steel_a",mold,0);check(network.draft().equals(good));
 network.setMode(network.machine.getConfigurationRevision(),0,"foreign_recipe",mold,0);check(network.draft().equals(good));

 var constrained=mixed.withRequiredFluid("a".repeat(64));UniversalPatternItem.write(card,constrained);check(UniversalPatternItem.read(card.copy()).equals(constrained));
 UniversalPatternItem.writeDraft(card,constrained.withRequiredFluid("b".repeat(64)));check(UniversalPatternItem.readDraft(card).requiredFluid().equals("b".repeat(64)));UniversalPatternItem.discardDraft(card);check(UniversalPatternItem.readDraft(card).equals(constrained));
 var multi=constrained.withFixedInput("b".repeat(64),true);check(multi.requiredInputs().equals(Set.of("a".repeat(64),"b".repeat(64))));
 UniversalPatternItem.write(card,multi);check(UniversalPatternItem.read(card.copy()).equals(multi));
 check(multi.withCatalystMode(CatalystSupply.identity(mold),2).requiredInputs().equals(multi.requiredInputs()));
 check(multi.withFixedInput("a".repeat(64),false).requiredInputs().equals(Set.of("b".repeat(64))));
 check(multi.withoutFixedInputs().requiredInputs().isEmpty());
 var direct=mixed.withCatalystMode(CatalystSupply.identity(circuit),3);
 check(CatalystSupply.mode(direct,circuit)==3&&CatalystSupply.circuit(recipe,direct)==-1);
 check(CatalystSupply.filter(direct,Map.of(circuit,tokenC,mold,tokenM)).isEmpty());
 UniversalPatternItem.write(card,direct);check(UniversalPatternItem.read(card.copy()).equals(direct));
 UniversalPatternItem.writeDraft(card,direct);check(UniversalPatternItem.readDraft(card.copy()).equals(direct));
 network.setMode(network.machine.getConfigurationRevision(),0,"steel_a",circuit,3);check(CatalystSupply.mode(network.draft(),circuit)==3);
 good=network.draft();
 var ignored=mixed.withCatalystMode(CatalystSupply.identity(circuit),2);
 check(CatalystSupply.mode(ignored,circuit)==2&&CatalystSupply.external(ignored,circuit));
 check(CatalystSupply.circuit(recipe,ignored)==-1&&CatalystSupply.filter(ignored,Map.of(circuit,tokenC,mold,tokenM)).isEmpty());
 UniversalPatternItem.write(card,ignored);check(UniversalPatternItem.read(card.copy()).equals(ignored));
 UniversalPatternItem.writeDraft(card,mixed);check(UniversalPatternItem.readDraft(card.copy()).equals(mixed));
 UniversalPatternItem.discardDraft(card);check(CatalystSupply.mode(UniversalPatternItem.readDraft(card),circuit)==2);
 var legacyCard=new ItemStack();UniversalPatternItem.write(legacyCard,old);
 var legacyModes=new CompoundTag();legacyModes.putBoolean(CatalystSupply.identity(circuit),false);legacyModes.putBoolean(CatalystSupply.identity(mold),true);
 legacyModes.putByte(CatalystSupply.identity(waterA),(byte)9);legacyCard.tag.put("CatalystModes",legacyModes);
 check(CatalystSupply.mode(UniversalPatternItem.read(legacyCard),circuit)==0&&CatalystSupply.mode(UniversalPatternItem.read(legacyCard),mold)==1);
 check(!UniversalPatternItem.read(legacyCard).catalystModes().containsKey(CatalystSupply.identity(waterA)));
 network.setMode(network.machine.getConfigurationRevision(),0,"steel_a",circuit,9);check(network.draft().equals(good));
 network.setMode(-1,0,"steel_a",circuit,2);check(network.draft().equals(good));
 UniversalPatternItem.write(card,mixed);
 var ui=new DraftFixture();ui.machine.cards.put(0,card.copy());ui.editDraft(PatternEdit.EXTERNAL_CATALYSTS,List.of());check(ui.draft().equals(mixed));
 ui.editDraft(PatternEdit.UNBIND,List.of());check(ui.draft().catalystModes().equals(mixed.catalystModes()));
 ui.editDraft(PatternEdit.REVERT,List.of());check(ui.draft().equals(mixed));
 System.out.println("PASS: four per-item modes, legacy 0/1 byte migration, ignore persistence and token/circuit exclusion, invalid/stale action rejection, circuit cache disable, old global defaults, independent item/fluid NBT variants, filtered token publication, draft/apply/reopen/revert and family changes retain overrides.");
 }
"""
code=code.replace(old,main)
java=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory(dir=root/'build/universal-provider') as folder:
 p=Path(folder);(p/'DraftFixture.java').write_text(code,encoding='utf-8');subprocess.run([java/'javac.exe','-encoding','UTF-8','-d',p,p/'DraftFixture.java'],check=True);subprocess.run([java/'java.exe','-cp',p,'DraftFixture'],check=True)
