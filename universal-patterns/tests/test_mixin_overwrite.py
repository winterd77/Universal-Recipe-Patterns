from pathlib import Path
import os,subprocess,sys
root=Path(__file__).resolve().parents[2]
java=Path('C:/Program Files/Java/jdk-22/bin')
prism=Path(os.environ['APPDATA'])/'PrismLauncher'
mods=prism/'instances/GregTech-Leisure-1.4.5.4/minecraft/mods'
libs=prism/'libraries'
work=root/'build/universal-provider'
dependencies=[libs/'org/spongepowered/mixin/0.8.5/mixin-0.8.5.jar']
dependencies += [libs/f'org/ow2/asm/{n}/9.8/{n}-9.8.jar' for n in ['asm','asm-tree','asm-util','asm-analysis','asm-commons']]
dependencies += list((libs/'com/google/guava/guava').rglob('*.jar'))
cp=';'.join(map(str,[work,*dependencies]))
subprocess.run([java/'javac.exe','-proc:none','-cp',cp,'-d',work,root/'universal-patterns/tests/ProviderContractAudit.java',root/'universal-patterns/tests/MixinOverwriteAudit.java'],check=True)
artifact=Path(sys.argv[1]) if len(sys.argv)>1 else root/'dist/gtl-universal-patterns-1.0.27.jar'
subprocess.run([java/'java.exe','-cp',cp,'MixinOverwriteAudit',artifact,mods/'wildcard_pattern-0.1.2-gtl.jar'],check=True)
