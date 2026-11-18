from pathlib import Path
import os,subprocess,zipfile,json,sys
root=Path(__file__).resolve().parents[2]
jar=Path(sys.argv[1]) if len(sys.argv)>1 else root/'dist/universal-recipe-patterns-1.0.31.jar'
prism=Path(os.environ['APPDATA'])/'PrismLauncher';libs=prism/'libraries'
forge=libs/'net/minecraftforge/forge/1.20.1-47.4.16/forge-1.20.1-47.4.16-universal.jar'
with zipfile.ZipFile(jar) as z:
 assert json.loads(z.read('pack.mcmeta'))['pack']['pack_format']==15
 recipe=json.loads(z.read('data/gtlcore/recipes/universal_pattern.json'))
 assert recipe['type']=='minecraft:crafting_shapeless'
 assert recipe['ingredients']==[{'item':'ae2:blank_pattern'}]
 assert recipe['result']['item']=='gtlcore:universal_pattern'
 assert 'conditions' not in recipe
 assert json.loads(z.read('assets/gtlcore/lang/zh_cn.json'))['item.gtlcore.universal_pattern']=='通用样板'
 model=json.loads(z.read('assets/gtlcore/models/item/universal_pattern.json'))
 assert model['textures']['layer0']=='gtlcore:item/universal_pattern'
 assert z.read('assets/gtlcore/textures/item/universal_pattern.png').startswith(b'\x89PNG')
 assert z.testzip() is None
 print('PASS: root pack metadata, shapeless conversion, stable item ID, Chinese name, model and texture present.')
work=root/'build/universal-provider';java=Path('C:/Program Files/Java/jdk-22/bin')
cp=';'.join(str(libs/f'org/ow2/asm/{n}/9.8/{n}-9.8.jar') for n in ['asm','asm-tree'])
subprocess.run([java/'javac.exe','-cp',cp,'-d',work,root/'universal-patterns/tests/ProviderContractAudit.java',root/'universal-patterns/tests/CreativeTabAudit.java'],check=True)
subprocess.run([java/'java.exe','-cp',str(work)+';'+cp,'CreativeTabAudit',jar,forge],check=True)
