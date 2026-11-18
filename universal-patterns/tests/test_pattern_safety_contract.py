from pathlib import Path
import os,subprocess
r=Path.cwd();j=Path('C:/Program Files/Java/jdk-22/bin');prism=Path(os.environ['APPDATA'])/'PrismLauncher';mods=prism/'instances/GregTech-Leisure-1.4.5.4/minecraft/mods';w=r/'build/universal-provider'
cp=';'.join(str(prism/f'libraries/org/ow2/asm/{n}/9.8/{n}-9.8.jar') for n in ['asm','asm-tree'])
subprocess.run([j/'javac.exe','-proc:none','-cp',cp,'-d',w,r/'universal-patterns/tests/PatternSafetyAudit.java'],check=True)
subprocess.run([j/'java.exe','-cp',str(w)+';'+cp,'PatternSafetyAudit',r/'dist/gtl-universal-patterns-1.0.27.jar',next(mods.glob('appliedenergistics2-forge-*.jar')),r/'dist/gtlcore-1.2.3.3-fix4-chain-ae-ui-recipes-v3.jar',r/'build/gtlcore-wireless/client-mojmap.jar'],check=True)
