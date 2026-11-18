from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[2];env={};exec((r/'build/enhancements-v2/verify-universal-drafts.py').read_text(encoding='utf-8').split('with tempfile.TemporaryDirectory')[0],env)
code=env['code'];old=env['method'](code,' public static void main(')
main=''' public static void main(String[] args){
 var ui=new DraftFixture();var steel=new ResourceLocation("steel");
 ui.machine.candidates.put("steel_a",new Candidate("cooling",steel));ui.machine.candidates.put("steel_b",new Candidate("cooling",steel));
 ui.editDraft(PatternEdit.BIND,List.of("steel_a"));
 ui.editDraft(PatternEdit.REMOVE,List.of("steel_a"));
 check(ui.machine.universalReason(ui.draft(),"steel_a")==4);check(ui.machine.universalReason(ui.draft(),"steel_b")<=2);
 ui.editDraft(PatternEdit.REMOVE,List.of("steel_b"));ui.editDraft(PatternEdit.ADD,List.of("steel_a"));
 check(ui.machine.universalReason(ui.draft(),"steel_a")==1);check(ui.machine.universalReason(ui.draft(),"steel_b")==4);
 var state=ui.draft();var card=ui.machine.cards.get(0).copy();check(UniversalPatternItem.readDraft(card).equals(state));
 var reopened=new DraftFixture();reopened.machine=ui.machine;check(reopened.draft().equals(state));
 reopened.editDraft(PatternEdit.APPLY,List.of());check(reopened.machine.universalSelection(0).recipeOverrides().equals(Map.of("steel_a",true,"steel_b",false)));
 var saved=UniversalPatternItem.read(reopened.machine.cards.get(0).copy());check(saved.equals(state));
 check(saved.withExtraInput("steel\\nitem",2).recipeOverrides().equals(saved.recipeOverrides()));
 check(saved.withDeliveryFrom(Selection.empty()).recipeOverrides().equals(saved.recipeOverrides()));
 reopened.editDraft(PatternEdit.FOLLOW,List.of("steel_b"));check(!reopened.draft().recipeOverrides().containsKey("steel_b"));check(reopened.draft().recipeOverrides().get("steel_a"));
 reopened.editDraft(PatternEdit.REVERT,List.of());check(reopened.draft().equals(saved));
 System.out.println("PASS: actual row edit and reason methods independently toggle same-material recipes; NBT draft/apply/reopen, other edits, follow and revert preserve independent states.");
 }'''
code=code.replace(old,main);j=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp);(p/'DraftFixture.java').write_text(code,encoding='utf-8');subprocess.run([j/'javac.exe','-d',p,p/'DraftFixture.java'],check=True);subprocess.run([j/'java.exe','-cp',p,'DraftFixture'],check=True)
