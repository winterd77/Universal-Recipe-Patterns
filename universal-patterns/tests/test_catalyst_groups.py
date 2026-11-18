from pathlib import Path
import re,tempfile,subprocess
r=Path(__file__).resolve().parents[2];s=(r/'universal-patterns/src/main/java/org/gtlcore/universalpatterns/integration/ae2/handler/CatalystGroupSupply.java').read_text(encoding='utf-8-sig');s=re.sub(r'^(package|import) .*;\s*$','',s,flags=re.M).replace('public final class CatalystGroupSupply','static final class CatalystGroupSupply')
code='''import java.util.*;
public class GroupFixture {
 static class AEKey {String id,tag,form;Object material;AEKey(String id,String tag){this.id=id;this.tag=tag;}String getId(){return id;}}
 static class AEItemKey extends AEKey{AEItemKey(String id,String tag){super(id,tag);}}
 record Recipe(List<List<AEKey>> nonConsumedInputs){}
 record Candidate(String family,Object material,Recipe recipe){}
 static class UniversalPatternFamily {static Object materialOf(AEKey k){return k.material;}static String formOf(AEKey k){return k.form;}}
 static class CatalystSupply {static String identity(AEKey key){return key.id+":"+key.tag;}}
 record Selection(Map<String,Integer> modes){Selection withCatalystMode(String key,int mode){var m=new HashMap<>(modes);m.put(key,mode);return new Selection(Map.copyOf(m));}}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 var c1=new AEItemKey("circuit","1");var c2=new AEItemKey("circuit","2");var mold=new AEItemKey("mold","1");var foreign=new AEItemKey("circuit","3");
 var a=new Candidate("mixer",null,new Recipe(List.of(List.of(c1))));var b=new Candidate("mixer",null,new Recipe(List.of(List.of(c2),List.of(mold))));var other=new Candidate("other",null,new Recipe(List.of(List.of(foreign))));
 var base=new Selection(Map.of(CatalystSupply.identity(mold),1));
 for(int mode=0;mode<4;mode++){var result=CatalystGroupSupply.apply(base,a,List.of(a,b,other),c1,mode);check(result.modes.get(CatalystSupply.identity(c1))==mode&&result.modes.get(CatalystSupply.identity(c2))==mode);check(result.modes.get(CatalystSupply.identity(mold))==1&&!result.modes.containsKey(CatalystSupply.identity(foreign)));}
 var ma=new Object();var mb=new Object();var ia=new AEItemKey("a_tool","");ia.material=ma;ia.form="tool";var ib=new AEItemKey("b_tool","");ib.material=mb;ib.form="tool";
 var ca=new Candidate("forming",ma,new Recipe(List.of(List.of(ia))));var cb=new Candidate("forming",mb,new Recipe(List.of(List.of(ib))));
 check(CatalystGroupSupply.matches(ca,cb,ia,ib));ib.form="rod";check(!CatalystGroupSupply.matches(ca,cb,ia,ib));
 check(!CatalystGroupSupply.matches(a,b,c1,new AEKey("circuit","1")));
 check(CatalystGroupSupply.apply(base,a,List.of(b),mold,3)==base);
 System.out.println("PASS: four supply modes across circuit NBT configurations; unrelated molds, other families, item/fluid collisions and different forms excluded; only real reusable inputs accepted.");
 }
'''+s+'}'
j=Path('C:/Program Files/Java/jdk-22/bin')
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp);(p/'GroupFixture.java').write_text(code,encoding='utf-8');subprocess.run([j/'javac.exe','-d',p,p/'GroupFixture.java'],check=True);subprocess.run([j/'java.exe','-cp',p,'GroupFixture'],check=True)
