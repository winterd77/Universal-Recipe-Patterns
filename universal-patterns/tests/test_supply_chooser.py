from pathlib import Path
import re,json
root=Path(__file__).resolve().parents[2]
s=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/client/gui/widget/RecipePatternBrowserWidget.java').read_text(encoding='utf-8')
def method(marker):
 a=s.index(marker);b=s.index('{',a)+1;d=1
 while d:d+=(s[b]=='{')-(s[b]=='}');b+=1
 return s[a:b]
body=method('    private void openSupplyChooser(')
rects=[tuple(map(int,m.groups())) for m in re.finditer(r'(?:button|line)\((\d+),(\d+),(\d+),(\d+)',body)]
assert 'button(66,85+mode*27,200,21' in body and 'mode<4' in body
rects += [(66,59,18,18),*( (66,85+mode*27,200,21) for mode in range(4))]
for x,y,w,h in rects:assert x>=54+12 and y>=28+9 and x+w<=278-12 and y+h<=254-9,(x,y,w,h)
for i,(x,y,w,h) in enumerate(rects):
 for a,b,c,d in rects[i+1:]:assert max(x,a)>=min(x+w,a+c) or max(y,b)>=min(y+h,b+d),((x,y,w,h),(a,b,c,d))
assert 'new ButtonWidget(0,0,332,shieldHeight' in body and 'new ButtonWidget(54,28,224,226' in body
assert 'compact ? 294 : 268' in body and 16+294<=314
assert 'supplyChooser.isVisible()' in method('    public boolean hasModalOpen(')
assert 'supplyChooser.isVisible()) return true' in method('    public boolean mouseWheelMove(')
assert 'closeSupplyChooser(); return true;' in method('    public boolean keyPressed(')
assert 'supplyChooser.isVisible()' in method('    public boolean charTyped(')
assert 'final long revision=patternRevision' in body and 'writeVarLong(revision)' in body and 'writeVarInt(slot)' in body
slots=method('    private void refreshCatalystSlots(')
assert 'openSupplyChooser(stack,mode)' in slots and 'send(' not in slots and 'line(i*38+22,0,30,18' in slots
assert 'setType(TextTexture.TextType.HIDE)' in s
labels=json.loads((root/'universal-patterns/locales.json').read_text(encoding='utf-8'))
for lang in ('zh_cn','en_us'):
 for n in range(4):assert labels[lang]['gtceu.machine.me_recipe_pattern_buffer.ingredients.supply_mode.'+str(n)]
print('PASS: four equal buttons, panel padding, non-overlap, icon/name alignment, clipped text, current marker, click shield, Escape/keyboard/wheel handling, captured card revision and localized labels.')
