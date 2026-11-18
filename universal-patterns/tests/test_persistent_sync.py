from pathlib import Path
# Reuse real Selection/NBT methods and actual deletion helper; add queue publication action.
import re,subprocess,tempfile
root=Path.cwd();env={'__file__':str(root/'universal-patterns/tests/test_extra_deletion.py')};raw=(root/'universal-patterns/tests/test_extra_deletion.py').read_text(encoding='utf-8-sig');exec(raw.split('\nwith tempfile.TemporaryDirectory')[0],env)
code=env['code'];extract=env['extract'];ui=env['ui'];src=env['src']
code=code.replace('record AEKey(String id){String toTag(){return id;}}','static class AEKey{String id;AEKey(String v){id=v;}String id(){return id;}String toTag(){return id;}}static class AEItemKey extends AEKey{AEItemKey(String v){super(v);}}static class AEFluidKey extends AEKey{AEFluidKey(String v){super(v);}}')
code=code.replace('base=new AEKey(', 'base=new AEItemKey(').replace('extraKey=new AEKey(', 'extraKey=new AEItemKey(')
code=code.replace('Recipe recipe(){return new Recipe();}', 'Recipe recipe(){return new Recipe(materialId.toString());}AEKey output(){return base;}')
code=code.replace('static class Recipe{Recipe pattern()', 'static class Recipe{String id;Recipe(String id){this.id=id;}String id(){return id;}Recipe pattern()')
code=code.replace('static class Machine {','static class Machine {List<Recipe> cachedRecipes(){return candidates.keySet().stream().map(Recipe::new).toList();}')
code=code.replace('static class DeliveryPolicy{','''static class DeliveryPolicy{
 static List<GenericStack> extras(Selection s){return s.extraInputs().entrySet().stream().map(e->new GenericStack(decode(e.getKey()),e.getValue())).toList();}
 static List<GenericStack> outputExtras(Selection s){return s.extraOutputs().entrySet().stream().map(e->new GenericStack(decode(e.getKey()),e.getValue())).toList();}
''')
pending=(src/'integration/ae2/handler/PendingPatternSync.java').read_text(encoding='utf-8');pending=re.sub(r'^(package|import) .*;\s*$','',pending,flags=re.M).replace('public final class PendingPatternSync','static final class PendingPatternSync')
action=extract(ui,'            case SYNC_PENDING -> {');action=action[action.index('                if(page!='):action.rindex('}')].replace('org.gtlcore.universalpatterns.integration.ae2.handler.','')
code=code.replace(extract(code,' boolean storeItemEdit('),'')
extra=pending+'''\nstatic class InputConstraintOptions{static class Index{Index(List<Recipe> recipes){}}}
 static class InputSyncMatcher{static AEKey match(Candidate source,Candidate target,AEKey key,InputConstraintOptions.Index index){return key==base?base:null;}}
 boolean storeItemEdit(Selection next,AEKey key,boolean output){storeDraft(PendingPatternSync.enqueue(next,machine.candidateForFamily(selectedId,next.family()),key,output));return true;}
 void syncPending(long revision,int slot,String idKey,boolean fixed){__ACTION__}
'''.replace('__ACTION__',action)
code=code.replace(' public static void main(',extra+' public static void main(');old=extract(code,' public static void main(')
main=r''' public static void main(String[] args){
 aliases.put("typed:"+extraKey.id(),extraKey);aliases.put("typed:"+base.id(),base);
 var iron=new ResourceLocation("gt:iron");var copper=new ResourceLocation("gt:copper");var steel=new ResourceLocation("gt:steel");
 var ui=new DraftFixture();ui.machine.candidates.put("gt:iron",new Candidate("f",iron));ui.machine.candidates.put("gt:copper",new Candidate("f",copper));ui.machine.candidates.put("gt:steel",new Candidate("f",steel));ui.selectedId="gt:iron";
 var before=new Selection("f",Set.of(),Set.of(steel),"all","");
 for(var material:List.of("gt:iron","gt:copper","gt:steel"))before=DeliveryEdits.setExtra(before,material,extraKey,4,false);
 UniversalPatternItem.write(ui.machine.cards.get(0),before);ui.draft();
 ui.storeItemEdit(DeliveryEdits.setExtra(ui.draft(),"gt:iron",extraKey,8,false),extraKey,false);
 String id=ui.draft().pendingSync().keySet().iterator().next();check(PendingPatternSync.parse(id,ui.draft().pendingSync().get(id)).amount()==8);
 ui.remove(56,ui.machine.getConfigurationRevision(),0,"gt:iron",extraKey);check(ui.draft().pendingSync().size()==1);
 var change=PendingPatternSync.parse(id,ui.draft().pendingSync().get(id));check(change.amount()==0&&change.key()==extraKey);
 check(ui.draft().forMaterial("gt:iron").extraInputs().isEmpty());
 var reopened=new DraftFixture();reopened.machine=ui.machine;reopened.selectedId="gt:copper";check(reopened.draft().pendingSync().equals(ui.draft().pendingSync()));
 reopened.editDraft(PatternEdit.APPLY,List.of());check(reopened.machine.universalSelection(0).pendingSync().containsKey(id));
 var again=new DraftFixture();again.machine=reopened.machine;again.selectedId="gt:copper";again.draft();
 var saved=again.draft();again.syncPending(-1,0,id,true);check(again.draft().equals(saved));again.syncPending(again.machine.getConfigurationRevision(),1,id,true);check(again.draft().equals(saved));
 again.syncPending(again.machine.getConfigurationRevision(),0,id,false);check(again.draft().pendingSync().containsKey(id));
 var missingSource=again.machine.candidates.remove("gt:iron");again.syncPending(again.machine.getConfigurationRevision(),0,id,true);check(again.draft().pendingSync().containsKey(id));again.machine.candidates.put("gt:iron",missingSource);
 again.syncPending(again.machine.getConfigurationRevision(),0,id,true);
 check(again.draft().pendingSync().isEmpty());check(again.draft().forMaterial("gt:copper").extraInputs().isEmpty());check(again.draft().forMaterial("gt:steel").extraInputs().get("typed:"+extraKey.id())==4);
 again.editDraft(PatternEdit.APPLY,List.of());check(UniversalPatternItem.readDraft(again.machine.cards.get(0)).pendingSync().isEmpty());
 var blank=new DraftFixture();check(blank.draft().pendingSync().isEmpty());
 var queued=PendingPatternSync.enqueue(before,ui.machine.candidates.get("gt:iron"),extraKey,false);var isolated=new ItemStack();UniversalPatternItem.writeDraft(isolated,queued);check(UniversalPatternItem.readDraft(isolated).pendingSync().equals(queued.pendingSync()));
 var primaryA=before.withPrimaryOutput("gt:iron",DeliveryPolicy.encode(base));primaryA=PendingPatternSync.enqueue(primaryA,ui.machine.candidates.get("gt:iron"),base,true);
 var primaryB=primaryA.withPrimaryOutput("gt:iron",DeliveryPolicy.encode(extraKey));primaryB=PendingPatternSync.enqueue(primaryB,ui.machine.candidates.get("gt:iron"),extraKey,true);
 check(PendingPatternSync.entries(primaryB).stream().filter(c->c.output()&&c.primary()).count()==1);
 check(PendingPatternSync.entries(primaryB).stream().filter(c->c.key()==base).noneMatch(c->c.primary()));
 var casting=new DraftFixture();casting.selectedId="gt:iron";casting.machine.candidates.put("gt:iron",new Candidate("casting-shared",iron));
 var oldCasting=primaryB.withFamily("casting-legacy").withRecipeOverride("gt:iron",true);
 UniversalPatternItem.write(casting.machine.cards.get(0),oldCasting);
 casting.editDraft(PatternEdit.EXPAND_CASTING,List.of("gt:iron"));
 check(casting.draft().family().equals("casting-shared"));
 check(casting.draft().withFamily("casting-legacy").equals(oldCasting));
 check(PendingPatternSync.entries(casting.draft()).size()==PendingPatternSync.entries(oldCasting).size());
 check(casting.machine.universalSelection(0).equals(oldCasting));
 casting.editDraft(PatternEdit.APPLY,List.of());
 check(casting.machine.universalSelection(0).equals(casting.draft()));
 System.out.println("PASS: expanding a legacy casting card moves its queued edits, retains its rules and recipe overrides, and only changes publication on apply.");
 System.out.println("PASS: real queued edits survive item deletion, draft close/reopen and apply/reopen; main-page sync does not depend on selected material or deleted slot, clears completed entries, preserves excluded materials and rejects stale/wrong-card requests.");
 }'''
code=code.replace(old,main)
with tempfile.TemporaryDirectory(dir=root/'build') as t:
 p=Path(t);(p/'DraftFixture.java').write_text(code,encoding='utf-8');j=Path('C:/Program Files/Java/jdk-22/bin');subprocess.run([j/'javac.exe','-encoding','UTF-8','-d',p,p/'DraftFixture.java'],check=True);subprocess.run([j/'java.exe','-cp',p,'DraftFixture'],check=True)

assert 'button(190,207,66,16' in ui and 'line(8, 209, 174, 12' in ui
assert '()->openInputSync(key)' not in ui and '()->openOutputSync(key)' not in ui
rects=[(8,149,174,12),(190,147,66,16),(260,149,64,12),(8,167,66,18),(80,167,76,18),(162,167,66,18),(234,167,90,18)]
for i,(x,y,w,h) in enumerate(rects):
 assert x>=8 and x+w<=324 and y+h<=204
 for a,b,c,d in rects[i+1:]:assert max(x,a)>=min(x+w,a+c) or max(y,b)>=min(y+h,b+d)
print('PASS: independent main-page sync button fits status row; item settings have no sync buttons.')

modal=[(32,20,232,18),(282,20,18,18),(32,46,268,12),(32,208,24,18),(62,208,208,18),(276,208,24,18),(32,238,268,12)]
for y in [66,132]:modal.extend([(32,y,18,18),(56,y,244,14),(56,y+16,244,12),(32,y+34,130,20),(170,y+34,130,20)])
for i,(x,y,w,h) in enumerate(modal):
 assert x>=32 and x+w<=300 and y>=20 and y+h<=250
 for a,b,c,d in modal[i+1:]:assert max(x,a)>=min(x+w,a+c) or max(y,b)>=min(y+h,b+d)
print('PASS: persisted-edit list fits its panel with two rows, pagination, feedback and no overlap.')
