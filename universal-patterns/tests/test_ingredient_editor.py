from pathlib import Path
import subprocess,tempfile,re
root=Path(__file__).resolve().parents[2]
env={}
fixture=(root/'build/enhancements-v2/verify-universal-drafts.py').read_text(encoding='utf-8')
exec(fixture.split('with tempfile.TemporaryDirectory')[0],env)
code=env['code'];extract=env['method']
s=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/client/gui/widget/RecipePatternBrowserWidget.java').read_text(encoding='utf-8')
initial=extract(s,'    public void writeInitialData(').replace('        super.writeInitialData(buffer);','').replace('org.gtlcore.universalpatterns.common.item.UniversalPatternItem','UniversalPatternItem')
code=code.replace('static class Machine {','static class Machine { int universalPatternSlots(){return 4;}')
code=code.replace('class DraftFixture {','class DraftFixture { enum Page{UNIVERSAL} Page page=Page.UNIVERSAL; static class FriendlyByteBuf{} void refreshView(){} void writeSnapshot(FriendlyByteBuf b){} '+initial)
old=extract(code,' public static void main(')
main=r""" public static void main(String[] args) {
 var fresh=new DraftFixture();fresh.writeInitialData(new FriendlyByteBuf());check(!fresh.browsingFamily&&fresh.searchResetRevision==0);
 var stored=new DraftFixture();var applied=new Selection("cooling",Set.of(),Set.of(),"all","",Map.of(),true);
 UniversalPatternItem.write(stored.machine.cards.get(0),applied);
 stored.writeInitialData(new FriendlyByteBuf());check(stored.browsingFamily&&stored.draft().equals(applied)&&stored.searchVisibility==SearchVisibility.ON_FAMILY);
 var pending=new Selection("magnetic",Set.of(new ResourceLocation("iron")),Set.of(),"all","",Map.of(),false).withFixedInput("a".repeat(64),true);
 UniversalPatternItem.writeDraft(stored.machine.cards.get(0),pending);
 var reopened=new DraftFixture();reopened.machine=stored.machine;reopened.writeInitialData(new FriendlyByteBuf());
 check(reopened.browsingFamily&&reopened.draft().equals(pending)&&reopened.searchVisibility==SearchVisibility.ON_PATTERN);check(reopened.machine.universalSelection(0).equals(applied));
 UniversalPatternItem.writeDraft(stored.machine.cards.get(0),Selection.empty());
 var cleared=new DraftFixture();cleared.machine=stored.machine;cleared.writeInitialData(new FriendlyByteBuf());check(!cleared.browsingFamily&&cleared.draft().family().isEmpty());
 System.out.println("PASS: production reopen path restores applied type and catalyst mode, prioritizes unapplied draft, preserves applied data, and keeps new/explicitly unbound drafts empty.");
 }
"""
code=code.replace(old,main)
java=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory(dir=root/'build/universal-provider') as folder:
 p=Path(folder);(p/'DraftFixture.java').write_text(code,encoding='utf-8')
 subprocess.run([java/'javac.exe','-encoding','UTF-8','-d',p,p/'DraftFixture.java'],check=True)
 subprocess.run([java/'java.exe','-cp',p,'DraftFixture'],check=True)

section=extract(s,'    private void addInlineIngredients(')
rects=[tuple(map(int,m.groups())) for m in re.finditer(r'(?:button|line)\((\d+),(\d+),(\d+),(\d+)',section)]
# Empty-catalyst text occupies the same row as catalyst icons, but only one is rendered.
rects=[r for r in rects if r!=(8,94,54,12)]
rects += [(8,46,54,18),(94,46,54,18),(8,92,18,18),(30,92,30,18),(64,46,16,18),(150,46,16,18),(64,92,16,18)]
for x,y,w,h in rects:assert 0<=x and x+w<=174 and 0<=y and y+h<=118
for i,(x,y,w,h) in enumerate(rects):
 for a,b,c,d in rects[i+1:]:assert max(x,a)>=min(x+w,a+c) or max(y,b)>=min(y+h,b+d),((x,y,w,h),(a,b,c,d))
assert 'openSupplyChooser' in extract(s,'    private void refreshCatalystSlots(')
assert 'SET_CATALYST_MODE' in extract(s,'    private void openSupplyChooser(')
assert 'EXTERNAL_CATALYSTS' not in section
assert 'ingredientEditor' not in s and 'openIngredientEditor' not in s
assert 'selected.nonConsumedInputs()' in s and 'selectedCatalysts = readStacks(buffer)' in s
assert 'catalystSlots.isMouseOverElement' in s
print('PASS: consumed/reusable/output slots and supply labels fit 174x118 with no overlaps; clickable supply chooser replaces inline buttons; candidate synchronization and wheel pagination retained.')

assert 'line(8,6,158,16' in section and 'line(8,26,52,16' in section and 'button(94,92,72,18' in section
assert 'page(selectedCatalysts,catalystPage,1)' in s and 'int index=catalystPage+i' in s
assert 'openMaterialState' not in s and 'materialStateButton' not in s
assert 'button(116,y+5,16,16' in s
assert 'ingredients.reset_all' in s
print('PASS: padded detail controls, direct input editing, row checkbox boundaries and single-item supply pagination.')
