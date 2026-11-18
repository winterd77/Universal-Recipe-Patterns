from pathlib import Path
import os, subprocess, zipfile, hashlib, json, uuid

ROOT = Path(__file__).resolve().parent.parent
module = ROOT / 'universal-patterns'
work = ROOT / 'build/universal-patterns'
work.mkdir(parents=True, exist_ok=True)
old = ROOT / 'build/gtlcore-wireless'
prism = Path(os.environ['APPDATA']) / 'PrismLauncher'
mods = prism / 'instances/GregTech-Leisure-1.4.5.4/minecraft/mods'
libs = prism / 'libraries'
base = Path(os.environ.get('GTLCORE_JAR', ROOT / 'build/gtlcore-1.2.3.3-fix4.jar'))
if not base.is_file():
    raise SystemExit('Set GTLCORE_JAR to GTLCore 1.2.3.3 or 1.2.3.3-fix4, or place the fix4 JAR in build/.')
mod_jars = [jar for jar in mods.glob('*.jar') if not jar.name.lower().startswith('gtlcore-')]
jdk = Path('C:/Program Files/Java/jdk-22/bin')
classes = work / ('classes-' + uuid.uuid4().hex[:8])
classes.mkdir(exist_ok=True)
annotations = Path(os.environ.get('GTL_ANNOTATIONS_JAR', ROOT / 'build/annotations.jar'))
if not annotations.is_file():
    raise SystemExit('Set GTL_ANNOTATIONS_JAR to the JetBrains annotations JAR.')
cp = [old/'forge-mojmap.jar', old/'client-mojmap.jar', base, annotations, *mod_jars]
cp += [p for p in libs.rglob('*.jar') if 'natives-' not in p.name and 'client-' not in p.name and 'sources' not in p.name]
# Embedded dependencies are needed by the original client listener's signatures.
nested = work / f'{classes.name}-nested'
nested.mkdir(exist_ok=True)
for jar in [base, *mod_jars]:
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if name.startswith('META-INF/') and name.endswith('.jar'):
                target = nested / Path(name).name
                if not target.exists(): target.write_bytes(z.read(name))
cp += list(nested.glob('*.jar'))
args = ['--release', '21', '-encoding', 'UTF-8', '-proc:none', '-cp', ';'.join(map(str, cp)), '-d', str(classes)]
args += [str(p) for p in (module/'src/main/java').rglob('*.java')]
argfile = work/'javac.args'
argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args), encoding='utf-8')
subprocess.run([jdk/'javac.exe', '-J-Duser.language=en', '@'+str(argfile)], check=True)
compiled = work/'compiled.jar'
with zipfile.ZipFile(compiled,'w') as z:
    for p in classes.rglob('*.class'):
        # Only this nested packet was changed; retain all other packet implementations.
        if p.name.startswith('WirelessAePackets') and p.name != 'WirelessAePackets$ConnectTargetPacket.class': continue
        z.write(p,p.relative_to(classes).as_posix())
mapper = libs/'net/minecraftforge/ForgeAutoRenamingTool/0.1.22/ForgeAutoRenamingTool-0.1.22-all.jar'
mappercp = [libs/f'org/ow2/asm/{n}/9.8/{n}-9.8.jar' for n in ('asm','asm-tree','asm-commons','asm-util','asm-analysis')] + [mapper]
mapped = work/'mapped.jar'
with (work/'remap.log').open('w') as log:
    subprocess.run([jdk/'java.exe','-Xmx2g','-cp',';'.join(map(str,mappercp)), 'net.minecraftforge.fart.Main', '--threads','4','--input',compiled,'--output',mapped,'--map',old/'srg-mojmap.tsrg','--reverse','--lib',old/'forge-mojmap.jar','--lib',old/'client-mojmap.jar','--lib',base,'--lib',next(mods.glob('appliedenergistics2-forge-*.jar'))],stdout=log,stderr=log,check=True)
with zipfile.ZipFile(mapped) as z:
    changes = {n:z.read(n) for n in z.namelist() if n.endswith('.class')}
for p in (module/'src/main/resources').rglob('*'):
    if p.is_file(): changes[p.relative_to(module/'src/main/resources').as_posix()] = p.read_bytes()
out = Path(os.environ.get('GTL_UNIVERSAL_PATTERNS_OUTPUT', ROOT/'dist/universal-recipe-patterns-1.0.32.jar'))
out.parent.mkdir(parents=True, exist_ok=True)
staged = work/f'{classes.name}-release.jar'
locales = json.loads((module/'locales.json').read_text(encoding='utf-8'))
for locale,labels in locales.items():
    for namespace in ('gtlcore','gtceu'):
        changes[f'assets/{namespace}/lang/{locale}.json'] = json.dumps({key:value for key,value in labels.items() if ('gtceu' if key.startswith('gtceu.') else 'gtlcore')==namespace},ensure_ascii=False,indent=2).encode('utf-8')
changes['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\r\nMixinConfigs: gtluniversalpatterns.mixins.json\r\n\r\n'
with zipfile.ZipFile(staged,'w',zipfile.ZIP_DEFLATED) as dst:
    for n,b in changes.items():dst.writestr(n,b)
with zipfile.ZipFile(staged) as z:
    assert z.testzip() is None
staged.replace(out)
print(out,hashlib.sha256(out.read_bytes()).hexdigest(),'entries:',len(changes))
(work/'classpath.txt').write_text(';'.join(map(str,[classes,*cp])),encoding='utf-8')
