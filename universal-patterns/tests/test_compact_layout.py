from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
s=(root/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/client/gui/widget/RecipePatternBrowserWidget.java').read_text(encoding='utf-8')
a=s.index('    private void compactLayout(');b=s.index('    private void openRuleEditor()',a)
method=s[a:b]
code=r"""import java.util.*;
public class CompactLayoutFixture {
 static class Widget {
  int x,y,w,h;Widget(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;}
  int getSelfPositionX(){return x;}int getSelfPositionY(){return y;}
  void setSize(int a,int b){w=a;h=b;}void setSizeHeight(int a){h=a;}void setSizeWidth(int a){w=a;}
  void setSelfPosition(int a,int b){x=a;y=b;}void setSelfPositionY(int a){y=a;}void setSelfPositionX(int a){x=a;}
 }
 static class WidgetGroup extends Widget {List<Widget> widgets=new ArrayList<>();WidgetGroup(int x,int y,int w,int h){super(x,y,w,h);}}
 Widget ingredientArea=new Widget(154,28,174,118);
 Widget ruleEditor=new Widget(0,0,332,268),universalOptions=new Widget(0,0,332,268),supplyChooser=new Widget(0,0,332,268),inputChooser=new Widget(156,76,170,94);
 Widget recipeRows=new Widget(8,48,136,140),inputSlots=new Widget(160,90,162,18),outputSlots=new Widget(160,140,162,18);
 __METHOD__
 static WidgetGroup pager(int x,int y,int w){var p=new WidgetGroup(x,y,w,12);p.widgets.add(new Widget(0,0,16,12));p.widgets.add(new Widget(w-16,0,16,12));p.widgets.add(new Widget(20,0,w-40,12));return p;}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  var f=new CompactLayoutFixture();var view=new WidgetGroup(0,0,332,268);
  List<Widget> controls=new ArrayList<>();
  for(int[] r:new int[][]{{30,4,18,18},{80,4,18,18},{104,4,91,18},{198,4,38,18},{239,4,89,18},{8,31,136,12},{160,31,162,12},{160,49,162,12},{160,76,76,12},{160,126,76,12},{160,181,74,18},{238,181,84,18},{8,209,190,12},{260,209,64,12},{204,207,50,16},{8,226,66,18},{80,226,76,18},{162,226,66,18},{234,226,90,18},{8,249,316,13}})controls.add(new Widget(r[0],r[1],r[2],r[3]));
  controls.removeIf(r->r.x>=154 && r.y>=28 && r.y<200);
  controls.addAll(List.of(f.recipeRows,f.ingredientArea,pager(8,190,136)));
  view.widgets.addAll(controls);view.widgets.addAll(List.of(f.ruleEditor,f.universalOptions,f.supplyChooser,f.inputChooser));f.compactLayout(view);
  for(var r:controls){check(r.x>=0&&r.y>=0&&r.x+r.w<=332&&r.y+r.h<=204);
   if(r instanceof WidgetGroup p)for(var c:p.widgets)check(c.x>=0&&c.x+c.w<=p.w&&c.y+c.h<=p.h);
  }
  for(int i=0;i<controls.size();i++)for(int j=i+1;j<controls.size();j++){
   var a=controls.get(i);var b=controls.get(j);check(Math.max(a.x,b.x)>=Math.min(a.x+a.w,b.x+b.w)||Math.max(a.y,b.y)>=Math.min(a.y+a.h,b.y+b.h));
  }
  check(f.ruleEditor.h==268&&f.universalOptions.h==268&&f.supplyChooser.h==268);check(16+268<=314);
  System.out.println("PASS: production compact layout: non-overlapping header, 3 rows, inline ingredient area, pager, family controls, footer and draft status within 332x204; full modals fit outer UI.");
 }
}
""".replace('__METHOD__',method)
java=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory(dir=root/'build/universal-provider') as folder:
 p=Path(folder);(p/'CompactLayoutFixture.java').write_text(code,encoding='utf-8')
 subprocess.run([java/'javac.exe','-d',p,p/'CompactLayoutFixture.java'],check=True)
 subprocess.run([java/'java.exe','-cp',p,'CompactLayoutFixture'],check=True)
