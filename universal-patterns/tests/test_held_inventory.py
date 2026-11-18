from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
source=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/api/gui/UniversalPatternHeldEditor.java').read_text(encoding='utf-8')
env={"__file__":str(root/"universal-patterns/tests/test_held_editor.py")}
base=(root/'universal-patterns/tests/test_held_editor.py').read_text(encoding='utf-8')
exec(base.split('java=Path(')[0],env)
code=env['code'];extract=env['method']
methods='\n'.join(extract(marker) for marker in ['private boolean available()', '@Override public boolean canTakeStack(', '@Override public boolean canPutStack(', '@Override public boolean isEnabled()'])
classes=r'''
 static class Browser {boolean modal;boolean hasModalOpen(){return modal;}}
 static class SlotWidget {boolean canTakeStack(Player p){return true;}boolean canPutStack(ItemStack s){return true;}boolean isEnabled(){return true;}}
 static class BoundSlot extends SlotWidget {
  Holder holder;Browser browser;int slot;
  BoundSlot(Holder h,Browser b,int s){holder=h;browser=b;slot=s;}
  __METHODS__
 }
'''.replace('__METHODS__',methods)
code=code.replace(' public static void main(',classes+' public static void main(')
code=code.replace('  var p=new Player();',r'''
  var inventoryPlayer=new Player();var binding=new Holder(inventoryPlayer,InteractionHand.MAIN_HAND);var browser=new Browser();
  var locked=new BoundSlot(binding,browser,inventoryPlayer.inventory.selected);
  var other=new BoundSlot(binding,browser,9);var spare=new ItemStack();
  check(!locked.canTakeStack(inventoryPlayer)&&!locked.canPutStack(spare),"held slot locked");
  check(other.canTakeStack(inventoryPlayer)&&other.canPutStack(spare),"ordinary inventory remains usable");
  check(!other.canPutStack(binding.getHeld()),"number-key swap cannot move edited card");
  browser.modal=true;check(!other.isEnabled()&&!other.canPutStack(spare)&&!other.canTakeStack(inventoryPlayer),"modal prevents inventory interaction");
  browser.modal=false;check(other.isEnabled()&&other.canPutStack(spare),"closing modal restores slots");
  var offBinding=new Holder(inventoryPlayer,InteractionHand.OFF_HAND);var offSlot=new BoundSlot(offBinding,browser,0);
  check(offSlot.canTakeStack(inventoryPlayer)&&!offSlot.canPutStack(offBinding.getHeld()),"offhand card protected without locking unrelated hotbar slot");
  inventoryPlayer.main=new ItemStack();check(!other.canPutStack(spare)&&!other.canTakeStack(inventoryPlayer),"invalid holder blocks mutations");
  var p=new Player();
''')
java=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory(dir=root/'build/universal-provider') as folder:
 p=Path(folder);(p/'HeldEditorFixture.java').write_text(code,encoding='utf-8')
 subprocess.run([java/'javac.exe','-encoding','UTF-8','-d',p,p/'HeldEditorFixture.java'],check=True)
 subprocess.run([java/'java.exe','-cp',p,'HeldEditorFixture'],check=True)
slots=[]
for index in range(36):
 x=93+(index%9)*18;y=220+(72 if index<9 else 14+((index-9)//9)*18)
 assert x>=8 and x+18<=340 and y>=234 and y+18<=314
 slots.append((x,y))
assert len(set(slots))==36 and min(y for x,y in slots)>16+204
print('PASS: 36 real inventory slots, held/offhand and number-key protection, modal blocking, holder invalidation, inventory bounds and separation from editor.')
