from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
base=root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns'
match=(base/'integration/ae2/handler/RecipeSampleMatch.java').read_text()
method=match[match.index('    public static boolean matches'):match.rindex('}')]
code='''import java.util.*;
public class SampleMatchFixture {
 record AEKey(String id) {}
 record Stack(AEKey what) {}
 record Slot(List<AEKey> alternatives) {}
 record Pattern(Stack[] getOutputs) {}
 record RecipePattern(List<Slot> inputSlots, Pattern pattern) {}
 METHOD
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args){
  var ore=new AEKey("ore");var alt=new AEKey("alternate_ore");var ingot=new AEKey("ingot");var slag=new AEKey("slag");
  var p=new RecipePattern(List.of(new Slot(List.of(ore,alt))),new Pattern(new Stack[]{new Stack(ingot),new Stack(slag)}));
  check(matches(p,ore,null));check(matches(p,alt,null));check(matches(p,null,ingot));check(matches(p,null,slag));
  check(matches(p,ore,ingot));check(matches(p,alt,slag));check(!matches(p,ingot,null));check(!matches(p,null,ore));
  check(!matches(p,ingot,ore));check(!matches(p,ore,ore));check(!matches(p,slag,ingot));check(matches(p,null,null));
  System.out.println("PASS: input-only, output-only, AND, input alternatives, secondary outputs, direction mismatch and cleared filters.");
 }
}'''.replace('METHOD',method)
java=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory(dir=root/'build/universal-provider') as folder:
 p=Path(folder);(p/'SampleMatchFixture.java').write_text(code)
 subprocess.run([java/'javac.exe','-d',p,p/'SampleMatchFixture.java'],check=True)
 subprocess.run([java/'java.exe','-cp',p,'SampleMatchFixture'],check=True)
editor=(base/'integration/ae2/handler/WildcardPatternEditor.java').read_text()
widget=(base/'client/gui/widget/RecipePatternBrowserWidget.java').read_text()
def body(source,name):
 start=source.index(name);a=source.index('{',start);end=a+1;depth=1
 while depth:
  depth+=(source[end]=='{')-(source[end]=='}');end+=1
 return source[a:end]
for name in ['getConfigurationRevision()', 'getRecipeStatistics()', 'cachedRecipes()']:
 b=body(editor,name);assert 'refresh()' not in b and 'expand(' not in b and 'getRecipes(' not in b
assert 'emptyQuery ? List.<RecipePattern>of()' in body(widget,'private void refreshView()')
assert 'searchRecipes(' not in body(widget,'private void writeSnapshot(')
assert 'GTRegistries.RECIPE_TYPES.values()' in body(editor,'private void refresh(@Nullable String family)')
assert 'readDraft' not in body(editor,'private void refresh(@Nullable String family)')
assert 'serverRecipeSample = null' not in body(widget,'private void submitSearch()')
assert 'recipeSample.display(null)' not in body(widget,'private void submitSearch()')
print('PASS: no index/expansion from open revision/statistics/snapshot; blank criteria produce no results; indexing can browse all types; text search retains samples.')
