import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public class PatternSafetyAudit {
 static ClassNode node(String jar,String path)throws Exception{try(var z=new ZipFile(jar)){var n=new ClassNode();new ClassReader(z.getInputStream(z.getEntry(path+".class"))).accept(n,0);return n;}}
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 static void injections(ClassNode mix,ClassNode target){
  for(var method:mix.methods)if(method.visibleAnnotations!=null)for(var a:method.visibleAnnotations)if(a.desc.endsWith("/Inject;")){
   var selectors=new ArrayList<String>();for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals("method"))for(var v:(List<?>)a.values.get(i+1))selectors.add(v.toString());
   check(selectors.stream().anyMatch(s->target.methods.stream().anyMatch(m->s.equals(m.name)||s.equals(m.name+m.desc))),"missing injection "+selectors);
  }
 }
 public static void main(String[] args)throws Exception{
  var terminal=node(args[1],"appeng/menu/me/items/PatternEncodingTermMenu");var slot=node(args[1],"appeng/menu/slot/RestrictedInputSlot");
  var prefix="org/gtlcore/universalpatterns/mixin/";
  injections(node(args[0],prefix+"UniversalTerminalSlotMixin"),slot);
  injections(node(args[0],prefix+"UniversalTerminalEncodeMixin"),terminal);
  check(terminal.fields.stream().anyMatch(f->f.name.equals("encodedPatternSlot")&&f.desc.equals("Lappeng/menu/slot/RestrictedInputSlot;")),"encoded slot shadow");
  check(terminal.methods.stream().anyMatch(m->m.name.equals("encode"))&&terminal.methods.stream().anyMatch(m->m.name.equals("clearPattern")),"both overwrite paths");
  injections(node(args[2],"org/gtlcore/gtlcore/mixin/recipe/UniversalPatternRecipeOverrideMixin"),node(args[3],"net/minecraft/world/item/crafting/RecipeManager"));
  try(var z=new ZipFile(args[2])){
   var manifest=new java.util.jar.Manifest(z.getInputStream(z.getEntry("META-INF/MANIFEST.MF")));
   check(manifest.getMainAttributes().getValue("MixinConfigs").contains("gtlcore-universal-recipes.mixins.json"),"Core mixin not activated");
  }
  System.out.println("PASS: installed AE slot and encode/clear targets, shadow descriptor, Minecraft recipe apply selector, Core mixin manifest activation.");
 }
}
