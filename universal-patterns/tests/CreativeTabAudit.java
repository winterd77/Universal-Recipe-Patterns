import org.objectweb.asm.tree.*;
public class CreativeTabAudit {
 public static void main(String[] args)throws Exception {
  var item=ProviderContractAudit.node(args[0],"org/gtlcore/universalpatterns/common/item/UniversalPatternItem");
  var forge=ProviderContractAudit.node(args[1],"net/minecraftforge/event/BuildCreativeModeTabContentsEvent");
  var method=ProviderContractAudit.method(item,"addToCreativeTab");
  int accepts=0;
  for(var ins:method.instructions)if(ins instanceof MethodInsnNode call && call.owner.equals(forge.name)) {
   ProviderContractAudit.check(forge.methods.stream().anyMatch(m->m.name.equals(call.name)&&m.desc.equals(call.desc)),"Missing runtime Forge method: "+call.name+call.desc);
   if(call.name.equals("accept")){
    accepts++;
    ProviderContractAudit.check(call.desc.equals("(Ljava/util/function/Supplier;)V"),"Must use stable Forge Supplier overload");
   }
  }
  ProviderContractAudit.check(accepts==1,"Creative tab accept hook missing");
  System.out.println("PASS: every creative-tab event call resolves against installed Forge 47.4.16; uses Supplier overload, no inherited unmapped ItemLike accept.");
 }
}
