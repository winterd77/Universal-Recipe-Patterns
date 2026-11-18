from pathlib import Path
import subprocess,tempfile,re
root=Path.cwd();env={"__file__":str(root/"universal-patterns/tests/test_input_roles.py")};exec((root/'universal-patterns/tests/test_input_roles.py').read_text(encoding='utf-8').split('with tempfile.TemporaryDirectory')[0],env)
f=env['f'];s=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/InputSyncMatcher.java').read_text(encoding='utf-8');s=re.sub(r'^(package|import) .*;\s*$','',s,flags=re.M).replace('public final class InputSyncMatcher','static final class InputSyncMatcher')
extra='''
 record Candidate(RecipePattern recipe,Object material){}
 static Map<AEKey,Object> materials=new HashMap<>();
 static class UniversalPatternFamily{static Object materialOf(AEKey k){return materials.get(k);}static String formOf(AEKey k){return k instanceof AEItemKey i?i.form:k instanceof AEFluidKey?"fluid":null;}}
'''+s
f=f.replace(' static void check(boolean b)',extra+'\n static void check(boolean b)')
pos=f.index(' var family=List.of(a,b,c,multi);')+len(' var family=List.of(a,b,c,multi);')
f=f[:pos]+'''
 Object aluminum=new Object(),copperMaterial=new Object();materials.put(al,aluminum);materials.put(cu,copperMaterial);
 var matcherIndex=new InputConstraintOptions.Index(family);
 var source=new Candidate(a,aluminum);var target=new Candidate(b,copperMaterial);
 check(InputSyncMatcher.match(source,target,al,matcherIndex)==cu);
 check(InputSyncMatcher.match(source,new Candidate(c,copperMaterial),al,matcherIndex)==cu);
 check(InputSyncMatcher.match(source,target,water,matcherIndex)==oil);
 check(InputSyncMatcher.match(source,target,new AEItemKey("custom","rod"),matcherIndex)==null);
 var another=new AEItemKey("cu_variant","rod");materials.put(another,copperMaterial);
 var ambiguous=new Candidate(new RecipePattern(List.of(cu,another),List.of(2L,9L)),copperMaterial);
 check(InputSyncMatcher.match(source,ambiguous,al,matcherIndex)==null);
 System.out.println("PASS: actual input synchronization matcher handles reordered/different-count recipes, material forms and fluids; ambiguous roles and manually added keys do not get guessed wildcard targets.");
'''+f[pos:]
with tempfile.TemporaryDirectory(dir=root/'build') as t:
 p=Path(t);(p/'RoleProbe.java').write_text(f,encoding='utf-8');j=Path('C:/Program Files/Java/jdk-22/bin');subprocess.run([j/'javac.exe','-encoding','UTF-8','-d',p,p/'RoleProbe.java'],check=True);subprocess.run([j/'java.exe','-cp',p,'RoleProbe'],check=True)
