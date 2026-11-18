from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2];env={};exec((root/'build/enhancements-v2/verify-universal-drafts.py').read_text(encoding='utf-8').split('with tempfile.TemporaryDirectory')[0],env)
code=env['code'];extract=env['method'];ui=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/client/gui/widget/RecipePatternBrowserWidget.java').read_text(encoding='utf-8')
action=extract(ui,'            case SYNC_OUTPUT -> {');action=action[action.index('                if(page!='):action.rindex('}')].replace('org.gtlcore.universalpatterns.integration.ae2.handler.','')
code=code.replace('record Candidate(String family,ResourceLocation materialId) {}','record Candidate(String family,ResourceLocation materialId) {AEKey output(){return outputs.get(materialId.toString());}}')
code=code.replace('static class Machine {','static class Machine {List<Recipe> cachedRecipes(){return candidates.keySet().stream().map(Recipe::new).toList();}')
extra='''static class DeliveryEdits{static Selection setExtra(Selection s,String material,AEKey key,long amount,boolean output){String scope=material+"\\n";return output?s.withExtraOutput(scope+DeliveryPolicy.encode(key),amount):s.scopeInputs(material).withExtraInput(scope+key.toTag().toString(),0).withExtraInput(scope+DeliveryPolicy.encode(key),amount);}}
 enum Page{UNIVERSAL} Page page=Page.UNIVERSAL;
 record AEKey(String id){String toTag(){return id;}}
 record Recipe(String id){}
 static Map<String,AEKey> outputs=new HashMap<>();
 static class CatalystSupply{static String identity(AEKey key){return key.id();}}
 static class DeliveryPolicy{static String encode(AEKey key){return key.id();}}
 void sync(long revision,int slot,String recipeId,AEKey key,boolean fixed){__ACTION__}
'''.replace('__ACTION__',action)
code=code.replace(' public static void main(',extra+' public static void main(')
old=extract(code,' public static void main(')
code=code.replace(old,''' public static void main(String[] args){
 var ui=new DraftFixture();var iron=new ResourceLocation("gt:iron");var copper=new ResourceLocation("gt:copper");var steel=new ResourceLocation("gt:steel");
 var a=new AEKey("a".repeat(64));var b=new AEKey("b".repeat(64));var c=new AEKey("c".repeat(64));outputs.put(iron.toString(),a);outputs.put(copper.toString(),b);outputs.put(steel.toString(),c);
 ui.machine.candidates.put("iron",new Candidate("f",iron));ui.machine.candidates.put("copper",new Candidate("f",copper));ui.machine.candidates.put("steel",new Candidate("f",steel));
 var selection=new Selection("f",Set.of(),Set.of(steel),"all","").withExtraOutput("gt:iron\\n"+a.id(),2).withIgnoredOutput("gt:iron\\n"+a.id(),true).withPrimaryOutput("gt:iron",a.id());
 UniversalPatternItem.write(ui.machine.cards.get(0),selection);ui.selectedId="iron";ui.draft();
 ui.sync(ui.machine.getConfigurationRevision(),0,"iron",a,false);
 var next=ui.draft();check(next.forOutputMaterial("gt:copper").extraOutputs().isEmpty());check(!next.forOutputMaterial("gt:copper").extraOutputs().containsKey(a.id()));check(next.forOutputMaterial("gt:copper").ignoredOutputs().contains(b.id()));check(next.forOutputMaterial("gt:copper").primaryOutputs().get("gt:copper").equals(b.id()));check(next.forOutputMaterial("gt:steel").extraOutputs().isEmpty());
 ui.sync(-1,0,"iron",a,true);check(ui.draft().equals(next));ui.sync(ui.machine.getConfigurationRevision(),1,"iron",a,true);check(ui.draft().equals(next));
 ui.sync(ui.machine.getConfigurationRevision(),0,"iron",a,true);check(ui.draft().forOutputMaterial("gt:copper").extraOutputs().isEmpty());
 check(ui.machine.universalSelection(0).equals(selection));
 System.out.println("PASS: actual sync action maps material products, fixed sync retains exact key, scopes quantity/ignore/primary, skips excluded materials and rejects stale/wrong-slot requests without publishing drafts.");
 }''')
with tempfile.TemporaryDirectory(dir=root/'build') as t:
 p=Path(t);(p/'DraftFixture.java').write_text(code,encoding='utf-8');jdk=Path('C:/Program Files/Java/jdk-22/bin');subprocess.run([jdk/'javac.exe','-encoding','UTF-8','-d',p,p/'DraftFixture.java'],check=True);subprocess.run([jdk/'java.exe','-cp',p,'DraftFixture'],check=True)
