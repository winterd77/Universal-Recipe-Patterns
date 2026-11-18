from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
source=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/api/gui/UniversalPatternHeldEditor.java').read_text(encoding='utf-8')
def method(marker):
 start=source.index(marker);end=source.index('{',start)+1;depth=1
 while depth:
  depth+=(source[end]=='{')-(source[end]=='}');end+=1
 return source[start:end]
holder=method('public static final class Holder')
read=method('@Override public ItemStack getStackInSlot(')
write=method('@Override public void setStackInSlot(')
code=r'''
import java.util.*;
public class HeldEditorFixture {
 enum InteractionHand { MAIN_HAND, OFF_HAND }
 static class Tag extends HashMap<String,String>{Tag copy(){var t=new Tag();t.putAll(this);return t;}}
 static class ItemStack {
  static final ItemStack EMPTY=new ItemStack();Tag tag=new Tag();boolean empty;
  boolean isEmpty(){return empty;}Tag getTag(){return tag;}void setTag(Tag value){tag=value;}
 }
 static class Inventory {int selected,dirty;void setChanged(){dirty++;}}
 static class Player {
  Inventory inventory=new Inventory();ItemStack main=new ItemStack(),off=new ItemStack();boolean remote;
  Inventory getInventory(){return inventory;}ItemStack getItemInHand(InteractionHand hand){return hand==InteractionHand.MAIN_HAND?main:off;}
 }
 static class HeldItemHolder {
  Player player;InteractionHand hand;ItemStack held;
  HeldItemHolder(Player p,InteractionHand h){player=p;hand=h;held=p.getItemInHand(h);}
  Player getPlayer(){return player;}InteractionHand getHand(){return hand;}ItemStack getHeld(){return held;}
  boolean isInvalid(){return false;}boolean isRemote(){return player.remote;}void markAsDirty(){}
 }
 __HOLDER__
 static class BaseCards {public ItemStack getStackInSlot(int s){return null;}public void setStackInSlot(int s,ItemStack v){}}
 static class Cards extends BaseCards {Holder holder;Cards(Holder h){holder=h;}__READ__ __WRITE__}
 static void check(boolean yes,String text){if(!yes)throw new AssertionError(text);}
 public static void main(String[] args){
  var p=new Player();var held=p.main;var holder=new Holder(p,InteractionHand.MAIN_HAND);var cards=new Cards(holder);
  var draft=new ItemStack();draft.tag.put("UniversalDraft","draft-with-external-catalysts");cards.setStackInSlot(0,draft);
  check(p.main==held&&!holder.isInvalid(),"writing must retain held identity");check(p.main.tag.equals(draft.tag)&&p.main.tag!=draft.tag,"copy NBT, no alias");
  check(p.inventory.dirty==1,"mark inventory dirty");
  var applied=new ItemStack();applied.tag.put("RecipeFamily","molten-to-ingot");cards.setStackInSlot(0,applied);
  check(!p.main.tag.containsKey("UniversalDraft")&&!holder.isInvalid(),"apply remains valid");
  p.inventory.selected=1;check(holder.isInvalid(),"hotbar change invalidates");cards.setStackInSlot(0,draft);check(p.main.tag.equals(applied.tag),"stale write rejected");
  p.inventory.selected=0;p.main=new ItemStack();p.main.tag=held.tag.copy();check(holder.isInvalid(),"equal-looking replacement invalidates");
  cards.setStackInSlot(0,draft);check(!p.main.tag.containsKey("UniversalDraft"),"replacement card untouched");
  var off=new Holder(p,InteractionHand.OFF_HAND);p.inventory.selected=8;check(!off.isInvalid(),"offhand independent of hotbar");
  var offCards=new Cards(off);offCards.setStackInSlot(0,draft);check(p.off.tag.containsKey("UniversalDraft"),"offhand writes correct card");
  p.remote=true;offCards.setStackInSlot(0,applied);check(p.off.tag.containsKey("UniversalDraft"),"client cannot commit");
  p.remote=false;offCards.setStackInSlot(1,applied);check(p.off.tag.containsKey("UniversalDraft"),"invalid slot rejected");
  p.off.empty=true;check(off.isInvalid(),"removed stack invalidates");
  System.out.println("PASS: production held-editor binding covers main/offhand, in-place NBT save, copied tags, inventory dirtiness, hotbar changes, identical replacement cards, invalid slots and client-write rejection.");
 }
}
'''.replace('__HOLDER__',holder).replace('__READ__',read).replace('__WRITE__',write)
assert 'new ModularUI(348,314,holder,player)' in source
assert 'new RecipePatternBrowserWidget(8,16,editor' in source
assert 8+332<=348 and 16+268<=314
assert 16+204<=220 and 220+94<=314
java=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory(dir=root/'build/universal-provider') as folder:
 path=Path(folder);(path/'HeldEditorFixture.java').write_text(code,encoding='utf-8')
 subprocess.run([java/'javac.exe','-encoding','UTF-8','-d',path,path/'HeldEditorFixture.java'],check=True)
 subprocess.run([java/'java.exe','-cp',path,'HeldEditorFixture'],check=True)
