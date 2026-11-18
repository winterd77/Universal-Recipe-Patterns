from pathlib import Path
import subprocess,tempfile,re
root=Path(__file__).resolve().parents[2]
env={};exec((root/'build/enhancements-v2/verify-universal-drafts.py').read_text(encoding='utf-8').split('with tempfile.TemporaryDirectory')[0],env)
code=env['code'];extract=env['method']
s=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/client/gui/widget/RecipePatternBrowserWidget.java').read_text(encoding='utf-8')
action=extract(s,'            case SET_REQUIRED_FLUID -> {');action=action[action.index('                if(page!='):action.rindex('}')].replace('org.gtlcore.universalpatterns.integration.ae2.handler.CatalystSupply','CatalystSupply')
code=code.replace('enum SearchVisibility { ALL, ON_FAMILY }','enum SearchVisibility { ALL, ON_FAMILY, ON_PATTERN }')
extra='''
 enum Page{UNIVERSAL,SEARCH} Page page=Page.UNIVERSAL;
 static class AEKey {String identity;AEKey(String s){identity=s;}}
 static class AEFluidKey extends AEKey {AEFluidKey(String s){super(s);}}
 record GenericStack(AEKey what){}
 static class CatalystSupply {static String identity(AEKey key){return key.identity;}}
 List<GenericStack> options=List.of();List<GenericStack> familyFluidOptions(){return options;}
 void setFluid(long revision,int slot,AEKey key){setFluid(revision,slot,key,true);}
 void setFluid(long revision,int slot,AEKey key,boolean fixed){__ACTION__}
'''.replace('__ACTION__',action)
code=code.replace(' public static void main(',extra+' public static void main(')
old=extract(code,' public static void main(')
code=code.replace(old,''' public static void main(String[] args){
 var ui=new DraftFixture();var base=new Selection("gt:cut|rod|bolt",Set.of(),Set.of(),"all","");UniversalPatternItem.write(ui.machine.cards.get(0),base);
 var water=new AEFluidKey("a".repeat(64));var oil=new AEFluidKey("b".repeat(64));ui.options=List.of(new GenericStack(water),new GenericStack(oil));
 ui.setFluid(ui.machine.getConfigurationRevision(),0,water);check(ui.draft().requiredInputs().contains(water.identity));check(ui.machine.universalSelection(0).requiredInputs().isEmpty());check(ui.searchVisibility==SearchVisibility.ON_PATTERN);
 var good=ui.draft();ui.setFluid(-1,0,oil);check(ui.draft().equals(good));ui.setFluid(ui.machine.getConfigurationRevision(),1,oil);check(ui.draft().equals(good));
 ui.setFluid(ui.machine.getConfigurationRevision(),0,new AEFluidKey("c".repeat(64)));check(ui.draft().equals(good));
 ui.setFluid(ui.machine.getConfigurationRevision(),0,new AEKey(water.identity));check(ui.draft().equals(good));
 var item=new AEKey("d".repeat(64));ui.options=List.of(new GenericStack(water),new GenericStack(oil),new GenericStack(item));
 ui.setFluid(ui.machine.getConfigurationRevision(),0,item);check(ui.draft().requiredInputs().equals(Set.of(water.identity,item.identity)));
 ui.setFluid(ui.machine.getConfigurationRevision(),0,water,false);check(ui.draft().requiredInputs().equals(Set.of(item.identity)));
 good=ui.draft();
 var reopened=new DraftFixture();reopened.machine=ui.machine;check(reopened.draft().equals(good));reopened.editDraft(PatternEdit.APPLY,List.of());check(ui.machine.universalSelection(0).equals(good));
 reopened.options=ui.options;reopened.setFluid(reopened.machine.getConfigurationRevision(),0,null);check(reopened.draft().requiredInputs().isEmpty());check(ui.machine.universalSelection(0).equals(good));
 reopened.editDraft(PatternEdit.REVERT,List.of());check(reopened.draft().equals(good));
 System.out.println("PASS: production ingredient action combines fixed item/fluid, toggles individual wildcard, rejects stale/wrong-card/foreign requests; draft/apply/reopen/reset/revert preserve all constraints.");
 }
''')
jdk=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory(dir=root/'build/universal-provider') as tmp:
 p=Path(tmp);(p/'DraftFixture.java').write_text(code,encoding='utf-8');subprocess.run([jdk/'javac.exe','-encoding','UTF-8','-d',p,p/'DraftFixture.java'],check=True);subprocess.run([jdk/'java.exe','-cp',p,'DraftFixture'],check=True)
body=extract(s,'    private void openFluidChooser(')
rects=[tuple(map(int,m.groups())) for m in re.finditer(r'(?:button|line)\((\d+),(\d+),(\d+),(\d+)',body)]
rects=[r for r in rects if r!=(66,72,200,36)] # empty-state text replaces rows
rects += [(66,70+i*27,200,23) for i in range(4)]+[(66,49,200,16)]
for x,y,w,h in rects:assert 66<=x and x+w<=266 and 16<=y and y+h<=248
for i,(x,y,w,h) in enumerate(rects):
 for a,b,c,d in rects[i+1:]:assert max(x,a)>=min(x+w,a+c) or max(y,b)>=min(y+h,b+d),((x,y,w,h),(a,b,c,d))
assert 'widget.setSelfPositionY(46)' in s and 'widget.setSelfPositionY(130)' in s
assert 46+3*28<=130 and 130+12<=28+118-4
machine=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/common/machine/multiblock/part/ae/MERecipePatternBufferPartMachine.java').read_text(encoding='utf-8')
assert 'ConsumedFluidFilter.matches' in extract(machine,'    private Set<ResourceLocation> evaluateSelection(')
assert 'ConsumedFluidFilter.matches' in extract(machine,'    public int universalReason(')
assert 'supplyChooser.charTyped(codePoint,modifiers)' in s and 'supplyChooser.keyPressed(keyCode,scanCode,modifiers)' in s
print('PASS: four item/fluid rows, search and controls fit panel without overlap; search keyboard routed; list arrows inset; buffer publication and reasons use shared input constraint filter.')
