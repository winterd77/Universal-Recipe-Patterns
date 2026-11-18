from pathlib import Path
import subprocess,tempfile,json,zipfile
root=Path(__file__).resolve().parents[2]
def method(s,marker):
 a=s.index(marker);b=s.index('{',a);i=b+1;depth=1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
host=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/WildcardPatternEditor.java').read_text(encoding='utf-8')
reason=method(host,'    public int universalReason(')
reason='\n'.join(line for line in reason.splitlines() if 'DeliveryPolicy.valid' not in line)
f='''import java.util.*;import java.util.function.*;
public class HeldReasonProbe {
 record Candidate(String materialId){}
 record Selection(String family,Set<String> excluded,String deny,Map<String,String> recipes,Set<String> materials,String allow,Set<String> fixed){}
 static class ConsumedFluidFilter{static boolean matches(Selection s,String recipe){return s.fixed.isEmpty()||s.fixed.contains(recipe);}}
 Candidate candidateForFamily(String id,String family){return new Candidate("aluminum");}
 int getRecipeStatus(String id){return 7;}String getRecipePattern(String id){return id;}
 Predicate<Candidate> savedRule(String rule,boolean deny){return c->rule.equals("all");}
 __REASON__
 public static void main(String[] args){
 var host=new HeldReasonProbe();var fixed=new Selection("cut",Set.of(),"",Map.of(),Set.of(),"all",Set.of("water"));
 if(host.universalReason(fixed,"water")!=0||host.universalReason(fixed,"oil")!=8)throw new AssertionError();
 var manual=new Selection("cut",Set.of(),"",Map.of("aluminum","oil"),Set.of("aluminum"),"all",Set.of("water"));
 if(host.universalReason(manual,"oil")!=8)throw new AssertionError();
 System.out.println("PASS: actual held/wildcard editor rejects oil preview when water is fixed, including manually included oil variants.");
 }
}'''.replace('__REASON__',reason)
with tempfile.TemporaryDirectory(dir=root/'build') as t:
 p=Path(t);(p/'HeldReasonProbe.java').write_text(f,encoding='utf-8');jdk=Path('C:/Program Files/Java/jdk-22/bin');subprocess.run([jdk/'javac.exe','-d',p,p/'HeldReasonProbe.java'],check=True);subprocess.run([jdk/'java.exe','-cp',p,'HeldReasonProbe'],check=True)
ui=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/client/gui/widget/RecipePatternBrowserWidget.java').read_text(encoding='utf-8')
assert 'boolean fixed=key!=null;' in method(ui,'    private void chooseRequiredFluid(')
assert 'SearchVisibility.ON_PATTERN' in method(ui,'    private void showFamily(')
assert 'button(204, 207' not in ui
assert 'PatternEdit.UNBIND' in method(ui,'    private void addInlineIngredients(')
assert 'PatternEdit.ADD:selectedMaterialState' not in method(ui,'    private void addInlineIngredients(')
with zipfile.ZipFile(root/'dist/gtl-universal-patterns-1.0.27.jar') as z:
 reset=json.loads(z.read('data/gtlcore/recipes/universal_pattern_reset.json'))
 assert reset=={'type':'minecraft:crafting_shapeless','ingredients':[{'item':'gtlcore:universal_pattern'}],'result':{'item':'gtlcore:universal_pattern','count':1}}
 labels=json.loads(z.read('assets/gtceu/lang/zh_cn.json'))
 assert labels['gtceu.machine.me_recipe_pattern_buffer.draft_apply']=='保存并应用'
 assert labels['gtceu.machine.me_recipe_pattern_buffer.outputs_short']=='产物'
 assert b'ConsumedFluidFilter' in z.read('org/gtlcore/universalpatterns/integration/ae2/handler/WildcardPatternEditor.class')
print('PASS: shipped host filter, explicit/idempotent selection, constrained reopen, type cancellation inside detail panel, inclusion menu, one primary save action and clean-output reset recipe.')
