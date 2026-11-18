import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Inspects installed target bytecode without loading Minecraft or Forge. */
public class ProviderContractAudit {
 static ClassNode node(String jar,String path)throws Exception {
  try(var z=new ZipFile(jar)){var n=new ClassNode();new ClassReader(z.getInputStream(z.getEntry(path+".class"))).accept(n,0);return n;}
 }
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 static MethodNode method(ClassNode n,String name){return n.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
 static boolean invokes(MethodNode m,String owner,String name){for(var i:m.instructions)if(i instanceof MethodInsnNode x && x.owner.equals(owner)&&x.name.equals(name))return true;return false;}
 public static void main(String[] args)throws Exception {
  String addon=args[0],core=args[1],ae=args[2],extended=args[3];
  String logic="appeng/helpers/patternprovider/PatternProviderLogic";
  var provider=node(ae,logic);
  check(invokes(method(provider,"updatePatterns"),"appeng/api/networking/crafting/ICraftingProvider","requestUpdate"),"publication hook missing");
  check(node(ae,"appeng/menu/slot/RestrictedInputSlot").methods.stream().anyMatch(m->invokes(m,"appeng/api/crafting/PatternDetailsHelper","isEncodedPattern")),"slot recognition path changed");
  for(String type:List.of("common/tileentities/TileExPatternProvider","common/parts/PartExPatternProvider")){
   var m=method(node(extended,"com/glodblock/github/extendedae/"+type),"createLogic");
   check(invokes(m,logic,"<init>"),"ExtendedAE bypasses shared provider: "+type);
   boolean slots=false;for(var i:m.instructions)if(i instanceof IntInsnNode x && x.operand==36)slots=true;
   check(slots,"extended inventory changed");
  }
  String prefix="org/gtlcore/universalpatterns/mixin/";
  for(String name:List.of("UniversalPatternRecognitionMixin","UniversalPatternProviderMixin","UniversalWildcardBufferMixin","UniversalWildcardRuntimeMixin","UniversalRecipeBindingMixin","UniversalWildcardCompatInvoker")){
   var mixin=node(addon,prefix+name);
   var annotation=mixin.invisibleAnnotations.stream().filter(a->a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
   List<String> targets=new ArrayList<>();
   for(int i=0;i<annotation.values.size();i+=2){String key=(String)annotation.values.get(i);var value=annotation.values.get(i+1);
    if(key.equals("value"))for(var t:(List<?>)value)targets.add(((Type)t).getInternalName());
    if(key.equals("targets"))for(var t:(List<?>)value)targets.add(t.toString().replace('.','/'));
   }
   for(String target:targets){
    var actual=node(target.startsWith("appeng/")?ae:core,target);
    for(var f:mixin.fields)if(f.visibleAnnotations!=null && f.visibleAnnotations.stream().anyMatch(a->a.desc.endsWith("/Shadow;")))
     check(actual.fields.stream().anyMatch(a->a.name.equals(f.name)&&a.desc.equals(f.desc)),"shadow field mismatch: "+target+"."+f.name);
    for(var m:mixin.methods){
     if(m.visibleAnnotations==null)continue;
     for(var a:m.visibleAnnotations){
      if(a.desc.endsWith("/Shadow;"))check(actual.methods.stream().anyMatch(t->t.name.equals(m.name)&&t.desc.equals(m.desc)),"shadow method mismatch: "+m.name);
      if(a.desc.endsWith("/Inject;"))for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals("method"))
       for(var selector:(List<?>)a.values.get(i+1)) check(actual.methods.stream().anyMatch(t->t.name.equals(selector)),"inject target missing: "+target+"."+selector);
     }
    }
   }
  }
  try(var z=new ZipFile(addon)){
   for(String forbidden:List.of("UniversalBufferMixin","UniversalBufferPushMixin","UniversalBufferFilterMixin"))check(z.getEntry(prefix+forbidden+".class")==null,"ordinary buffer integration retained");
  }
  System.out.println("PASS: AE slot recognition, shared publication hook, both ExtendedAE 36-slot providers, wildcard targets/shadows, and removal of ordinary-buffer hooks.");
 }
}
