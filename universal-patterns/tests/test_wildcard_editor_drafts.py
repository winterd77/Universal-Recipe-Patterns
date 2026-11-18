from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
# Reuse the NBT/API substitutes, but execute the new editor's real save/apply/revert methods.
environment={}
base=(root/'build/enhancements-v2/verify-universal-drafts.py').read_text(encoding='utf-8')
exec(base.split('with tempfile.TemporaryDirectory')[0],environment)
code=environment['code'];extract=environment['method']
adapter=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/WildcardPatternEditor.java').read_text(encoding='utf-8')
for name in ['storeUniversalDraft','discardUniversalDraft','applyUniversalDraft']:
 old=extract(code,'  '+'  public boolean '+name+'(') if '    public boolean '+name+'(' in code else extract(code,'public boolean '+name+'(')
 new=extract(adapter,'    @Override public boolean '+name+'(').replace('@Override ','')
 code=code.replace(old,new)
helpers=extract(adapter,'    private boolean matches(')+'\n'+extract(adapter,'    private void save(')
helpers=helpers.replace('cards.setStackInSlot(0,stack)','universalPatternSlot.setStackInSlot(0,stack)')
code=code.replace('static class Machine {','static class Machine {\nboolean isValid(){return present;}Machine owner=this;ItemStack snapshot=new ItemStack();Runnable publish=this::configurationChanged;\n'+helpers)
code=code.replace('return present&&cards.containsKey(slot);','return present&&slot==0&&cards.containsKey(slot);')
oldmain=extract(code,' public static void main(')
main=r''' public static void main(String[] args) {
  var machine=new Machine();var iron=new ResourceLocation("gt:iron");
  var applied=new Selection("gt:cooling|hot|ingot",Set.of(iron),Set.of(),"","",Map.of(iron,"gt:recipe_a"),false);
  UniversalPatternItem.write(machine.cards.get(0),applied);
  var draft=new Selection(applied.family(),Set.of(),Set.of(iron),"all","",Map.of(iron,"gt:recipe_b"),true).withFixedInput("a".repeat(64),true).withIgnoredInput("b".repeat(64),true).withExtraInput("{test:item}",3000000000L).withIgnoredOutput("gt:iron\n"+"c".repeat(64),true).withExtraOutput("gt:iron\n{test:output}",5L).withPrimaryOutput("gt:iron","{test:output}");
  var before=machine.universalCardSnapshot(0);
  check(machine.storeUniversalDraft(0,before,draft));check(machine.writes==0);
  check(UniversalPatternItem.read(machine.cards.get(0)).equals(applied));
  check(UniversalPatternItem.readDraft(machine.cards.get(0)).equals(draft));
  check(!machine.storeUniversalDraft(0,before,applied));
  check(!machine.storeUniversalDraft(1,machine.universalCardSnapshot(1),draft));
  var reopened=new Machine();reopened.cards.put(0,machine.cards.get(0).copy());
  check(UniversalPatternItem.readDraft(reopened.cards.get(0)).equals(draft));
  check(reopened.discardUniversalDraft(0,reopened.universalCardSnapshot(0)));
  check(UniversalPatternItem.readDraft(reopened.cards.get(0)).equals(applied));check(reopened.writes==0);
  check(machine.applyUniversalDraft(0,machine.universalCardSnapshot(0),draft));
  check(machine.writes==1 && UniversalPatternItem.read(machine.cards.get(0)).equals(draft));
  var finalReopen=new Machine();finalReopen.cards.put(0,machine.cards.get(0).copy());
  var restored=UniversalPatternItem.readDraft(finalReopen.cards.get(0));
  check(restored.requiredInputs().equals(Set.of("a".repeat(64))));
  check(restored.ignoredInputs().equals(Set.of("b".repeat(64)))&&restored.extraInputs().get("{test:item}")==3000000000L);
  check(restored.withFixedInput("c".repeat(64),true).withCatalystMode("d".repeat(64),2).extraInputs().equals(restored.extraInputs()));
  check(restored.withDeliveryFrom(Selection.empty()).ignoredInputs().isEmpty());
  check(restored.forOutputMaterial("gt:iron").extraOutputs().get("{test:output}")==5L);
  check(restored.forOutputMaterial("gt:iron").primaryOutputs().get("gt:iron").equals("{test:output}"));
  check(restored.forOutputMaterial("gt:copper").extraOutputs().isEmpty());
  check(restored.forOutputMaterial("gt:iron").ignoredOutputs().contains("c".repeat(64)));
  check(restored.withExtraInput("{another}",1).withFixedInput("e".repeat(64),true).forOutputMaterial("gt:iron").extraOutputs().get("{test:output}")==5L);
  var local=restored.scopeInputs("gt:iron").withExtraInput("gt:iron\n{test:item}",0).withIgnoredInput("gt:iron\n"+"b".repeat(64),false);
  check(local.forMaterial("gt:iron").extraInputs().isEmpty());check(local.forMaterial("gt:iron").ignoredInputs().isEmpty());
  check(local.forMaterial("gt:copper").extraInputs().get("{test:item}")==3000000000L);check(local.forMaterial("gt:copper").ignoredInputs().contains("b".repeat(64)));
  UniversalPatternItem.writeDraft(machine.cards.get(0),local);check(UniversalPatternItem.readDraft(machine.cards.get(0)).equals(local));UniversalPatternItem.discardDraft(machine.cards.get(0));
  check(!machine.cards.get(0).tag.contains("UniversalDraft",10));
  var snapshot=machine.universalCardSnapshot(0);machine.present=false;
  check(!machine.applyUniversalDraft(0,snapshot,applied));check(machine.writes==1);
  System.out.println("PASS: wildcard editor keeps applied settings while drafting, preserves moved/reopened drafts, rejects stale/card-replaced/invalid-slot edits, publishes only on apply, and preserves external catalysts plus variant choices.");
 }'''
code=code.replace(oldmain,main)
java=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory(dir=root/'build/universal-provider') as folder:
 path=Path(folder);(path/'DraftFixture.java').write_text(code,encoding='utf-8')
 subprocess.run([java/'javac.exe','-encoding','UTF-8','-d',path,path/'DraftFixture.java'],check=True)
 subprocess.run([java/'java.exe','-cp',path,'DraftFixture'],check=True)
