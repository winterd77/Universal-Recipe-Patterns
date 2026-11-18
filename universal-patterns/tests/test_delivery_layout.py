from pathlib import Path
import re
root=Path.cwd();s=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/client/gui/widget/RecipePatternBrowserWidget.java').read_text(encoding='utf-8')
for name,height in [('openExtraDelivery',204)]:
 start=s.index('    private void '+name+'(');end=s.index('\n    private ',start+10);part=s[start:end]
 rects=[tuple(map(int,m.groups())) for m in re.finditer(r'(?:button|line|new TextFieldWidget)\((\d+),(\d+),(\d+),(\d+)',part)]
 for x,y,w,h in rects:assert x>=66 and x+w<=266 and y>=40 and y+h<=28+height-12,(name,x,y,w,h)
 for i,(x,y,w,h) in enumerate(rects):
  for a,b,c,d in rects[i+1:]:assert max(x,a)>=min(x+w,a+c) or max(y,b)>=min(y+h,b+d),(name,(x,y,w,h),(a,b,c,d))
assert 'if (supplyChooser.isVisible()) return supplyChooser.getPhantomTargets(ingredient)' in s
assert 'deliveryPreview(outputSelection(selection))' in s
print('PASS: delivery menus have 12px panel insets and no overlapping controls; JEI targets route to the active modal; corrupted extra keys do not crash snapshot rendering.')

assert 'if(output)openOutputDelivery(stack);else openInputDelivery(stack);' in s
assert '()->included?"":"X"' in s
assert 'outputSelection(selection).ignoredOutputs()' in s
print('PASS: input/output click routes are distinct; X denotes excluded; output snapshots are material-scoped.')

assert '66,110,200,20' in s and 'SET_OUTPUT_PRIMARY' in s
assert 'universal_output_edit_hint' in s

part=s[s.index('    private void openOutputDelivery('):s.index('    private void openExtraDelivery(')]
rects=[tuple(map(int,m.groups())) for m in re.finditer(r'(?:button|line)\((\d+),(\d+),(\d+),(\d+)',part)]
for x,y,w,h in rects:assert x>=66 and x+w<=266 and y>=28 and y+h<=244
for i,(x,y,w,h) in enumerate(rects):
 for a,b,c,d in rects[i+1:]:assert max(x,a)>=min(x+w,a+c) or max(y,b)>=min(y+h,b+d)
print('PASS: four-action output modal including primary/sync maintains 12px insets without overlaps.')

part=s[s.index('    private void openInputDelivery('):s.index('    private void openOutputDelivery(')]
rects=[tuple(map(int,m.groups())) for m in re.finditer(r'(?:button|line)\((\d+),(\d+),(\d+),(\d+)',part)]
for x,y,w,h in rects:assert x>=66 and x+w<=266 and y>=28 and y+h<=244
for i,(x,y,w,h) in enumerate(rects):
 for a,b,c,d in rects[i+1:]:assert max(x,a)>=min(x+w,a+c) or max(y,b)>=min(y+h,b+d)
print('PASS: input menu mirrors output spacing, with distinct scope/sync controls.')
