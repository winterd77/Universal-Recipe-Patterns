from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2];env={};exec((root/'build/enhancements-v2/verify-universal-drafts.py').read_text(encoding='utf-8').split('with tempfile.TemporaryDirectory')[0],env)
code=env['code'];extract=env['method'];ui=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/client/gui/widget/RecipePatternBrowserWidget.java').read_text(encoding='utf-8')
action=extract(ui,'            case SET_MATERIAL_SELECTED -> {');action=action[action.index('                if(page!='):action.rindex('}')]
extra='''enum Page{UNIVERSAL} Page page=Page.UNIVERSAL;
 int reason(String id){return machine.universalReason(draft(),id);}
 void checkMaterial(long revision,int slot,String recipeId,boolean selected){__ACTION__}
'''.replace('__ACTION__',action)
code=code.replace(' public static void main(',extra+' public static void main(')
old=extract(code,' public static void main(')
code=code.replace(old,''' public static void main(String[] args){
 var ui=new DraftFixture();var iron=new ResourceLocation("gt:iron");var copper=new ResourceLocation("gt:copper");
 ui.machine.candidates.put("iron",new Candidate("f",iron));ui.machine.candidates.put("copper",new Candidate("f",copper));
 UniversalPatternItem.write(ui.machine.cards.get(0),new Selection("f",Set.of(),Set.of(),"all",""));
 ui.selectedId="iron";ui.draft();ui.checkMaterial(ui.machine.getConfigurationRevision(),0,"copper",false);
 check(ui.selectedId.equals("iron"));check(Boolean.FALSE.equals(ui.draft().recipeOverrides().get("copper")));check(ui.searchVisibility==SearchVisibility.ON_FAMILY);check(ui.machine.universalSelection(0).recipeOverrides().isEmpty());
 ui.checkMaterial(ui.machine.getConfigurationRevision(),0,"copper",true);check(Boolean.TRUE.equals(ui.draft().recipeOverrides().get("copper")));
 var saved=ui.draft();ui.checkMaterial(-1,0,"iron",false);check(ui.draft().equals(saved));ui.checkMaterial(ui.machine.getConfigurationRevision(),1,"iron",false);check(ui.draft().equals(saved));
 ui.editDraft(PatternEdit.APPLY,List.of());check(ui.machine.universalSelection(0).equals(saved));
 System.out.println("PASS: production row action selects/deselects a non-previewed material, preserves preview and unchecked rows, keeps draft/apply separation and rejects stale/wrong-card requests.");
 }''')
with tempfile.TemporaryDirectory(dir=root/'build') as t:
 p=Path(t);(p/'DraftFixture.java').write_text(code,encoding='utf-8');jdk=Path('C:/Program Files/Java/jdk-22/bin');subprocess.run([jdk/'javac.exe','-encoding','UTF-8','-d',p,p/'DraftFixture.java'],check=True);subprocess.run([jdk/'java.exe','-cp',p,'DraftFixture'],check=True)
