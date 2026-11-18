from pathlib import Path
import re,subprocess,tempfile
root=Path.cwd();env={};exec((root/'build/enhancements-v2/verify-universal-drafts.py').read_text(encoding='utf-8').split('with tempfile.TemporaryDirectory')[0],env);code=env['code'];extract=env['method']
src=root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns'
helper=(src/'integration/ae2/handler/DeliveryEdits.java').read_text(encoding='utf-8');helper=re.sub(r'^(package|import) .*;\s*$','',helper,flags=re.M).replace('public final class DeliveryEdits','static final class DeliveryEdits')
ui=(src/'client/gui/widget/RecipePatternBrowserWidget.java').read_text(encoding='utf-8');action=extract(ui,'            case REMOVE_EXTRA_INPUT, REMOVE_EXTRA_OUTPUT -> {');action=action[action.index('                if(page!='):action.rindex('}')].replace('org.gtlcore.universalpatterns.integration.ae2.handler.','')
code=code.replace('record Candidate(String family,ResourceLocation materialId) {}','record Candidate(String family,ResourceLocation materialId){Recipe recipe(){return new Recipe();}}')
code=code.replace('static class ConsumedFluidFilter {','static class ConsumedFluidFilter {static List<AEKey> inputs(Recipe r){return List.of(base);}')
extra='''
 enum Page{UNIVERSAL} Page page=Page.UNIVERSAL;
 static final int REMOVE_EXTRA_INPUT=56,REMOVE_EXTRA_OUTPUT=57;
 record AEKey(String id){String toTag(){return id;}}
 record GenericStack(AEKey what,long amount){}
 static AEKey base=new AEKey("a".repeat(64)),extraKey=new AEKey("b".repeat(64));
 static class Recipe{Recipe pattern(){return this;}GenericStack[] getOutputs(){return new GenericStack[]{new GenericStack(base,4)};}}
 static class TagParser{static String parseTag(String s){return s;}}
 static Map<String,AEKey> aliases=new HashMap<>();
 static class DeliveryPolicy{static String encode(AEKey k){return "typed:"+k.id();}static AEKey decode(String s){return aliases.get(s);}}
 static class CatalystSupply{static String identity(AEKey k){return k.id();}}
 __HELPER__
 boolean storeItemEdit(Selection next,AEKey key,boolean output){storeDraft(next);return true;}
 void remove(int id,long revision,int slot,String recipeId,AEKey key){__ACTION__}
'''.replace('__HELPER__',helper).replace('__ACTION__',action)
code=code.replace(' public static void main(',extra+' public static void main(');old=extract(code,' public static void main(')
main=r''' public static void main(String[] args){
 String material="gt:iron",prefix=material+"\n",other="gt:copper";
 aliases.put("typed:"+extraKey.id(),extraKey);aliases.put("reordered",extraKey);aliases.put("typed:"+base.id(),base);
 var legacy=new Selection("f",Set.of(),Set.of(),"all","").withExtraInput(extraKey.id(),3).withExtraInput("reordered",2).withIgnoredInput(extraKey.id(),true);
 var cleaned=DeliveryEdits.setExtra(legacy,material,extraKey,0,false);
 check(cleaned.forMaterial(material).extraInputs().isEmpty());check(cleaned.forMaterial(other).extraInputs().size()==2);
 var changed=DeliveryEdits.setExtra(legacy,material,extraKey,8,false);check(changed.forMaterial(material).extraInputs().size()==1);check(changed.forMaterial(material).extraInputs().get("typed:"+extraKey.id())==8);
 var ui=new DraftFixture();ui.machine.candidates.put("iron",new Candidate("f",new ResourceLocation(material)));ui.selectedId="iron";
 var selection=legacy.withExtraOutput(prefix+"reordered",4).withExtraOutput(prefix+"typed:"+extraKey.id(),5).withIgnoredOutput(prefix+extraKey.id(),true).withPrimaryOutput(material,"reordered");
 UniversalPatternItem.write(ui.machine.cards.get(0),selection);ui.draft();
 ui.remove(56,ui.machine.getConfigurationRevision(),0,"iron",extraKey);
 check(ui.draft().forMaterial(material).extraInputs().isEmpty());check(!ui.draft().forMaterial(material).ignoredInputs().contains(extraKey.id()));
 check(ui.draft().forMaterial(other).extraInputs().size()==2);
 ui.remove(57,ui.machine.getConfigurationRevision(),0,"iron",extraKey);
 check(ui.draft().forMaterial(material).extraOutputs().isEmpty());check(ui.draft().forMaterial(material).primaryOutputs().isEmpty());check(ui.draft().forMaterial(material).ignoredOutputs().isEmpty());
 var saved=ui.draft();ui.remove(56,-1,0,"iron",base);check(ui.draft().equals(saved));ui.remove(56,ui.machine.getConfigurationRevision(),1,"iron",base);check(ui.draft().equals(saved));ui.remove(56,ui.machine.getConfigurationRevision(),0,"copper",base);check(ui.draft().equals(saved));
 ui.editDraft(PatternEdit.APPLY,List.of());check(ui.machine.universalSelection(0).equals(saved));check(UniversalPatternItem.readDraft(ui.machine.cards.get(0)).equals(saved));
 var nativeExtra=selection.withExtraOutput(prefix+"typed:"+base.id(),2).withIgnoredOutput(prefix+base.id(),true);
 var nativeClean=DeliveryEdits.setExtra(nativeExtra,material,base,0,true);check(nativeClean.forMaterial(material).ignoredOutputs().contains(base.id()));
 System.out.println("PASS: production extra deletion matches legacy/reordered NBT keys, removes all aliases only for current material, clears orphan ignore/primary state, preserves original recipe settings, rejects stale/wrong-card/changed-recipe actions, and survives apply/reopen.");
 }'''
code=code.replace(old,main)
with tempfile.TemporaryDirectory(dir=root/'build') as t:
 p=Path(t);(p/'DraftFixture.java').write_text(code,encoding='utf-8');j=Path('C:/Program Files/Java/jdk-22/bin');subprocess.run([j/'javac.exe','-encoding','UTF-8','-d',p,p/'DraftFixture.java'],check=True);subprocess.run([j/'java.exe','-cp',p,'DraftFixture'],check=True)
assert 'openExtraOutput' not in ui
assert 'page==Page.UNIVERSAL?selected.pattern():machine.getPreviewPattern(selected)' in ui
assert 'universal_outputs_label' in ui and 'universal_output_edit_hint' in ui
assert '(id==SET_OUTPUT_EXTRA&&amount>0)' in ui
print('PASS: no output-add UI; positive output-add actions rejected; universal preview uses registered outputs; unique output labels avoid old translation collisions.')

remove_ui=extract(ui,'    private void removeExtra(')
assert 'closeSupplyChooser' in remove_ui
assert 'button(190,207,66,16' in ui and 'this::openPendingSync' in ui
assert 'PendingPatternSync.enqueue' in ui
print('PASS: settings close after edits; a dedicated main-page button reads durable pending changes.')
