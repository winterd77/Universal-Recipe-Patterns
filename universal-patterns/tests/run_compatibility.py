from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[2]
os.chdir(root)
java=Path("C:/Program Files/Java/jdk-22/bin")
libs=Path(os.environ["APPDATA"])/"PrismLauncher/libraries"
cp=";".join(str(libs/f"org/ow2/asm/{n}/9.8/{n}-9.8.jar") for n in ["asm","asm-tree","asm-util","asm-analysis"])
work=root/"build/universal-patterns"
subprocess.run([java/"javac.exe","-cp",cp,"-d",work,root/"universal-patterns/tests/CompatibilityAudit.java"],check=True)
subprocess.run([java/"java.exe","-cp",str(work)+";"+cp,"CompatibilityAudit",root/"dist/归档/legacy/gtlcore-1.2.3.3-fix4-chain-ae-universal-patterns.jar",root/"dist/归档/gtlcore-1.2.3.3-fix4-chain-ae.jar",root/"dist/gtl-universal-patterns-1.0.27.jar"],check=True)
def method(source,marker):
 start=source.index(marker);begin=source.index("{",start);end=begin+1;depth=1
 while depth:
  depth+=(source[end]=="{")-(source[end]=="}");end+=1
 return source[start:end]
src=root/"universal-patterns/src/main/java/org/gtlcore/universalpatterns"
factory=method((src/"mixin/RecipeBufferFactoryMixin.java").read_text(),"    private static Function<IMachineBlockEntity, MetaMachine> universalPatterns$factory(")
verify=method((src/"mixin/RecipeBufferFactoryMixin.java").read_text(),"    private static void universalPatterns$verifyFactory(")
register=method((src/"common/item/UniversalPatternItem.java").read_text(),"    public static void register(")
code="""
import java.util.*;import java.util.function.*;
public class RegistrationFixture {
 interface IMachineBlockEntity {}
 static class MetaMachine {}
 enum IO { BOTH }
 static class MERecipePatternBufferPartMachine extends MetaMachine {
  final IMachineBlockEntity holder; final IO io;
  MERecipePatternBufferPartMachine(IMachineBlockEntity h,IO i){holder=h;io=i;}
 }
 static int universalPatterns$factoryMatches;
 static class CallbackInfo {}
 __VERIFY__
 __FACTORY__
 static class ForgeRegistries {static class Keys {static final String ITEMS="items";}}
 static class RegisterEvent {
  final String key;int calls;String id;
  RegisterEvent(String key){this.key=key;}String getRegistryKey(){return key;}
  void register(String key,String id,Supplier<UniversalPatternItem> make){
   if(!key.equals(this.key)||make.get()==null)throw new AssertionError();this.id=id;calls++;
  }
 }
 static class UniversalPatternItem {static final String ID="gtlcore:universal_pattern";__REGISTER__}
 public static void main(String[] unused){
  Function<IMachineBlockEntity,MetaMachine> original=h->new MetaMachine();
  IMachineBlockEntity holder=new IMachineBlockEntity(){};
  for(String name:List.of("me_pattern_buffer","me_extend_pattern_buffer","me_recipe_pattern_buffer_proxy","another_machine")){
   if(universalPatterns$factory(name,original)!=original)throw new AssertionError(name);
  }
  try { universalPatterns$verifyFactory(new CallbackInfo()); throw new AssertionError("missing accepted"); } catch(IllegalStateException expected) {}
  Function<IMachineBlockEntity,MetaMachine> updated=universalPatterns$factory("me_recipe_pattern_buffer",original);
  universalPatterns$verifyFactory(new CallbackInfo());
  var machine=(MERecipePatternBufferPartMachine)updated.apply(holder);
  if(machine.holder!=holder||machine.io!=IO.BOTH)throw new AssertionError();
  if(updated.apply(holder)==machine)throw new AssertionError("factory reused instance");
  try { universalPatterns$factory("me_recipe_pattern_buffer",original); throw new AssertionError("duplicate accepted"); } catch(IllegalStateException expected) {}
  var items=new RegisterEvent("items");UniversalPatternItem.register(items);
  if(items.calls!=1||!items.id.equals("gtlcore:universal_pattern"))throw new AssertionError();
  var blocks=new RegisterEvent("blocks");UniversalPatternItem.register(blocks);if(blocks.calls!=0)throw new AssertionError();
  System.out.println("PASS: production factory hook replaces only the exact recipe-buffer name with a fresh IO.BOTH machine; item registration fires only for item registry.");
 }
}
""".replace("__VERIFY__",verify).replace("__FACTORY__",factory).replace("__REGISTER__",register)
with tempfile.TemporaryDirectory(dir=work) as folder:
 path=Path(folder);(path/"RegistrationFixture.java").write_text(code,encoding="utf-8")
 subprocess.run([java/"javac.exe","-d",path,path/"RegistrationFixture.java"],check=True)
 subprocess.run([java/"java.exe","-cp",path,"RegistrationFixture"],check=True)
