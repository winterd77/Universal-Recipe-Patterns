import java.util.*;
import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
/** Runs the installed Mixin library's priority gate without starting Minecraft or Forge. */
public class MixinOverwriteAudit {
 static class Probe extends InjectionPoint {
  public boolean find(String desc,InsnList instructions,Collection<AbstractInsnNode> nodes){return false;}
 }
 static int priority(ClassNode node) {
  var annotation=node.invisibleAnnotations.stream().filter(a->a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
  for(int i=0;i<annotation.values.size();i+=2)if(annotation.values.get(i).equals("priority"))return (Integer)annotation.values.get(i+1);
  return 1000;
 }
 public static void main(String[] args)throws Exception {
  var ours=ProviderContractAudit.node(args[0],"org/gtlcore/universalpatterns/mixin/UniversalPatternProviderMixin");
  var wildcard=ProviderContractAudit.node(args[1],"org/leodreamer/wildcard_pattern/mixin/PatternProviderLogicMixin");
  var overwritten=ProviderContractAudit.method(wildcard,"updatePatterns");
  ProviderContractAudit.check(overwritten.visibleAnnotations.stream().anyMatch(a->a.desc.endsWith("/Overwrite;")),"Expected installed wildcard overwrite");
  ProviderContractAudit.check(ProviderContractAudit.invokes(overwritten,"appeng/api/networking/crafting/ICraftingProvider","requestUpdate"),"Merged method lost publication injection point");
  var gate=new Probe();
  ProviderContractAudit.check(!gate.checkPriority(priority(wildcard),1000),"Original startup failure no longer reproduced");
  ProviderContractAudit.check(gate.checkPriority(priority(wildcard),priority(ours)),"Addon still fails installed Mixin overwrite priority gate");
  var recognition=ProviderContractAudit.node(args[1],"org/leodreamer/wildcard_pattern/mixin/PatternDetailsHelperMixin");
  ProviderContractAudit.check(recognition.methods.stream().noneMatch(m->m.visibleAnnotations!=null&&m.visibleAnnotations.stream().anyMatch(a->a.desc.endsWith("/Overwrite;"))),"Recognition gained another overwrite");
  var targets=Set.of("appeng/helpers/patternprovider/PatternProviderLogic","appeng/api/crafting/PatternDetailsHelper",
    "org/gtlcore/gtlcore/integration/wildcard/WildcardPatternCompatImpl",
    "org/gtlcore/gtlcore/integration/wildcard/MEWildcardPatternBufferPartMachine",
    "org/gtlcore/gtlcore/integration/wildcard/MEStockingWildcardPatternBufferPartMachine",
    "org/gtlcore/gtlcore/common/machine/multiblock/part/ae/MEPatternBufferRecipeHandlerTraitBase$MEItemInputHandlerBase",
    "org/gtlcore/gtlcore/common/machine/multiblock/part/ae/MEPatternBufferRecipeHandlerTraitBase$MEFluidHandlerBase");
  int scanned=0,overlaps=0;
  var integratedMethods=Set.of("updatePatterns","isEncodedPattern","expandPatterns","isWildcardPattern","refreshPatterns",
      "saveCustomPersistedData","loadCustomPersistedData","update","meHandleRecipeInner","createUI","attachSideTabs");
  try(var jars=Files.list(Path.of(args[1]).getParent())) {
   for(var jar:jars.filter(p->p.toString().endsWith(".jar")).toList())try(var zip=new ZipFile(jar.toFile())) {
    for(var entry:Collections.list(zip.entries())) {
     if(!entry.getName().endsWith(".class") || !entry.getName().toLowerCase().contains("mixin"))continue;
     var node=new ClassNode();new ClassReader(zip.getInputStream(entry)).accept(node,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG);scanned++;
     if(node.invisibleAnnotations==null)continue;
     var annotations=node.invisibleAnnotations.stream().filter(a->a.desc.endsWith("/Mixin;")).toList();
     if(annotations.isEmpty())continue;
     var a=annotations.getFirst();var matched=new HashSet<String>();
     for(int i=0;i<a.values.size();i+=2){var key=a.values.get(i);if(!key.equals("value")&&!key.equals("targets"))continue;
      for(var t:(List<?>)a.values.get(i+1)){String name=t instanceof Type type?type.getInternalName():t.toString().replace('.','/');if(targets.contains(name))matched.add(name);}}
     if(matched.isEmpty())continue;
     for(var method:node.methods)if(method.visibleAnnotations!=null&&method.visibleAnnotations.stream().anyMatch(x->x.desc.endsWith("/Overwrite;"))){
      overlaps++;System.out.println("OVERWRITE: "+jar.getFileName()+" "+node.name+"."+method.name+" priority="+priority(node));
      boolean reviewedPush=node.name.equals("stone/mae2/mixins/PatternProviderLogicMixin")&&method.name.equals("pushPattern");
      if(reviewedPush){
       var body=ProviderContractAudit.method(ProviderContractAudit.node(jar.toString(),node.name),"pushPattern");
       ProviderContractAudit.check(ProviderContractAudit.invokes(body,"java/util/List","contains"),"MAE2 lost published-pattern membership check");
      }
      ProviderContractAudit.check(!integratedMethods.contains(method.name) || node.name.equals(wildcard.name)&&method.name.equals("updatePatterns"),"Additional overwrite needs review: "+node.name+"."+method.name);
     }
    }
   }
  }
  ProviderContractAudit.check(overlaps>=2,"Expected diagnosed wildcard overwrite and MAE2 overwrites");
  System.out.println("PASS: scanned "+scanned+" installed mixin-related classes; no additional overwrite of addon-integrated methods; MAE2 push keeps published-pattern membership check.");
  System.out.println("PASS: installed Mixin 0.8.5 reproduces old 1000/1000 rejection and accepts addon "+priority(ours)+" over wildcard "+priority(wildcard)+"; overwritten publication point and additive recognition verified.");
 }
}
