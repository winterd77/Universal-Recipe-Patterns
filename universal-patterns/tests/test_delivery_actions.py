from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2];env={};exec((root/'build/enhancements-v2/verify-universal-drafts.py').read_text(encoding='utf-8').split('with tempfile.TemporaryDirectory')[0],env)
code=env['code'];extract=env['method'];ui=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/client/gui/widget/RecipePatternBrowserWidget.java').read_text(encoding='utf-8')
action=extract(ui,'            case SET_DELIVERY_IGNORE, SET_DELIVERY_EXTRA -> {');action=action[action.index('                if(page!='):action.rindex('}')]
action=action.replace('org.gtlcore.universalpatterns.integration.ae2.handler.','')
extra='''static class DeliveryEdits{static Selection setExtra(Selection s,String material,AEKey key,long amount,boolean output){String scope=material+"\\n";return output?s.withExtraOutput(scope+DeliveryPolicy.encode(key),amount):s.scopeInputs(material).withExtraInput(scope+key.toTag().toString(),0).withExtraInput(scope+DeliveryPolicy.encode(key),amount);}}
 enum Page{UNIVERSAL} Page page=Page.UNIVERSAL;
 static final int SET_DELIVERY_IGNORE=48,SET_DELIVERY_EXTRA=49;
 static class AEKey{String id;AEKey(String s){id=s;}String toTag(){return id;}}
 static class AEItemKey extends AEKey{AEItemKey(String s){super(s);}}
 static class AEFluidKey extends AEKey{AEFluidKey(String s){super(s);}}
 record GenericStack(AEKey what,long amount){}
 static class CatalystSupply{static String identity(AEKey k){return k.id;}}
 static class DeliveryPolicy{static String encode(AEKey k){return k.id;}static List<GenericStack> extras(Selection s){return List.of();}}
 static AEKey known=new AEFluidKey("a".repeat(64));
 List<GenericStack> familyFluidOptions(){return List.of(new GenericStack(known,1000));}
 boolean storeItemEdit(Selection next,AEKey key,boolean output){storeDraft(next);return true;}
 void updateDelivery(int id,long revision,int slot,AEKey key,boolean ignore,long amount){__ACTION__}
'''.replace('__ACTION__',action)
code=code.replace('static class ConsumedFluidFilter {','static class ConsumedFluidFilter {static List<AEKey> inputs(Object recipe){return List.of(known);}')
code=code.replace(' public static void main(',extra+' public static void main(')
old=extract(code,' public static void main(')
code=code.replace(old,''' public static void main(String[] args){
 var ui=new DraftFixture();UniversalPatternItem.write(ui.machine.cards.get(0),new Selection("f",Set.of(),Set.of(),"all",""));ui.machine.candidates.put("iron",new Candidate("f",new ResourceLocation("gt:iron")));ui.selectedId="iron";ui.draft();
 ui.updateDelivery(48,ui.machine.getConfigurationRevision(),0,ui.known,true,0);check(ui.draft().forMaterial("gt:iron").ignoredInputs().contains(ui.known.id));check(ui.machine.universalSelection(0).ignoredInputs().isEmpty());
 var extra=new AEItemKey("b".repeat(64));ui.updateDelivery(49,ui.machine.getConfigurationRevision(),0,extra,false,3000000000L);check(ui.draft().forMaterial("gt:iron").extraInputs().get(extra.id)==3000000000L);
 var saved=ui.draft();ui.updateDelivery(49,-1,0,extra,false,1);check(ui.draft().equals(saved));ui.updateDelivery(49,ui.machine.getConfigurationRevision(),1,extra,false,1);check(ui.draft().equals(saved));ui.updateDelivery(49,ui.machine.getConfigurationRevision(),0,extra,false,-1);check(ui.draft().equals(saved));
 ui.updateDelivery(48,ui.machine.getConfigurationRevision(),0,extra,true,0);check(ui.draft().equals(saved));
 ui.updateDelivery(49,ui.machine.getConfigurationRevision(),0,ui.known,false,500);check(!ui.draft().forMaterial("gt:iron").ignoredInputs().contains(ui.known.id));
 ui.updateDelivery(49,ui.machine.getConfigurationRevision(),0,extra,false,0);check(!ui.draft().forMaterial("gt:iron").extraInputs().containsKey(extra.id));
 ui.editDraft(PatternEdit.APPLY,List.of());check(ui.machine.universalSelection(0).equals(ui.draft()));
 System.out.println("PASS: production delivery actions preserve draft/apply, accept custom items and long amounts, restore ignored input on adding, remove extras, reject stale/wrong-card/negative/unknown-ignore requests.");
 }''')
with tempfile.TemporaryDirectory(dir=root/'build') as t:
 p=Path(t);(p/'DraftFixture.java').write_text(code,encoding='utf-8');jdk=Path('C:/Program Files/Java/jdk-22/bin');subprocess.run([jdk/'javac.exe','-encoding','UTF-8','-d',p,p/'DraftFixture.java'],check=True);subprocess.run([jdk/'java.exe','-cp',p,'DraftFixture'],check=True)
