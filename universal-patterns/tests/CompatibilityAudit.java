import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;
public class CompatibilityAudit {
 static ClassNode node(String jar,String name)throws Exception{
  try(var z=new ZipFile(jar)){var n=new ClassNode();new ClassReader(z.getInputStream(z.getEntry(name+".class"))).accept(n,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return n;}
 }
 static String normalized(String s){return s.replace("org/gtlcore/universalpatterns/","org/gtlcore/gtlcore/");}
 static String code(MethodNode m){var p=new Textifier();m.accept(new TraceMethodVisitor(p));var w=new StringWriter();p.print(new PrintWriter(w));return normalized(w.toString());}
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 static void compare(ClassNode old,ClassNode now,Set<String> methods){
  for(String name:methods){var a=old.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();var b=now.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();check(normalized(a.desc).equals(normalized(b.desc))&&code(a).equals(code(b)),"Changed persistence method: "+name);}
 }
 static Set<String> persisted(ClassNode n){var result=new TreeSet<String>();for(var f:n.fields){if(f.visibleAnnotations!=null)for(var a:f.visibleAnnotations)if(a.desc.endsWith("/Persisted;"))result.add(f.name+":"+f.desc+":"+a.values);}return result;}
 public static void main(String[] args)throws Exception{
  String old=args[0],core=args[1],addon=args[2],a="org/gtlcore/gtlcore/",b="org/gtlcore/universalpatterns/";
  var oi=node(old,a+"common/item/UniversalPatternItem");var ni=node(addon,b+"common/item/UniversalPatternItem");
  compare(oi,ni,Set.of("<clinit>","read","readRecipes","readMaterials","readRule","writeMaterials","write","readDraft","writeDraft","discardDraft","register"));
  compare(node(old,a+"common/item/UniversalPatternItem$Selection"),node(addon,b+"common/item/UniversalPatternItem$Selection"),Set.of("empty","canBind"));
  var om=node(old,a+"common/machine/multiblock/part/ae/MERecipePatternBufferPartMachine");var nm=node(addon,b+"common/machine/multiblock/part/ae/MERecipePatternBufferPartMachine");
  check(persisted(om).equals(persisted(nm)),"Managed persisted fields changed");
  compare(om,nm,Set.of("saveCustomPersistedData","loadCustomPersistedData","saveRecipeList","loadRecipeList","saveRules","loadRules","collectSlotData"));
  check(ni.visibleAnnotations.toString()!=null,"annotations");
  boolean mod=false;for(var x:ni.visibleAnnotations)if(x.desc.contains("EventBusSubscriber"))mod=x.values.contains("gtluniversalpatterns");check(mod,"Item subscriber is not owned by addon");
  var target=node(core,a+"common/data/GTLMachines$GTAEMachines");var init=target.methods.stream().filter(m->m.name.equals("<clinit>")).findFirst().orElseThrow();
  int all=0,named=0;boolean selected=false;
  for(var ins:init.instructions){if(ins instanceof LdcInsnNode l&&"me_recipe_pattern_buffer".equals(l.cst))selected=true;
   if(ins instanceof MethodInsnNode m&&m.owner.equals("com/gregtechceu/gtceu/api/registry/registrate/GTRegistrate")&&m.name.equals("machine")&&m.desc.equals("(Ljava/lang/String;Ljava/util/function/Function;)Lcom/gregtechceu/gtceu/api/registry/registrate/MachineBuilder;")){all++;if(selected){named++;selected=false;}}
  }
  check(all>0&&named==1,"Expected exactly one named recipe buffer registration");
  var mixin=node(addon,b+"mixin/RecipeBufferFactoryMixin");var hook=mixin.methods.stream().filter(m->m.name.equals("universalPatterns$factory")).findFirst().orElseThrow();
  var modify=hook.visibleAnnotations.stream().filter(x->x.desc.endsWith("/ModifyArg;")).findFirst().orElseThrow();
  check(modify.values.contains("index") && modify.values.contains(1),"Factory argument index must remain one");
  check(hook.desc.equals("(Ljava/lang/String;Ljava/util/function/Function;)Ljava/util/function/Function;"),"Full invocation args must match the ModifyArg handler");
  check(!code(hook).contains("invoke/arg/Args"),"Factory still depends on synthetic Args");
  check(code(hook).contains("me_recipe_pattern_buffer"),"Missing registration-name guard");
  System.out.println("PASS: unchanged item NBT entry points and machine save/load bytecode identical (additive selection field tested by draft fixture) after package relocation; managed persistence fields unchanged; addon registration owner correct; exactly one named factory among "+all+" registration calls.");
 }
}
