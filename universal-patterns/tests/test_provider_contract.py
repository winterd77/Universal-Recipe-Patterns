from pathlib import Path
import os, subprocess
root=Path(__file__).resolve().parents[2]
java=Path('C:/Program Files/Java/jdk-22/bin')
prism=Path(os.environ['APPDATA'])/'PrismLauncher'
mods=prism/'instances/GregTech-Leisure-1.4.5.4/minecraft/mods'
cp=';'.join(str(prism/f'libraries/org/ow2/asm/{n}/9.8/{n}-9.8.jar') for n in ['asm','asm-tree'])
work=root/'build/universal-provider'
subprocess.run([java/'javac.exe','-cp',cp,'-d',work,root/'universal-patterns/tests/ProviderContractAudit.java'],check=True)
core=root/'dist/gtlcore-1.2.3.3-fix4-chain-ae.jar'
if not core.exists(): core=root/'dist/归档/gtlcore-1.2.3.3-fix4-chain-ae.jar'
subprocess.run([java/'java.exe','-cp',str(work)+';'+cp,'ProviderContractAudit',root/'dist/gtl-universal-patterns-1.0.27.jar',core,mods/'appliedenergistics2-forge-15.4.10.jar',mods/'ExtendedAE-1.20-1.4.19-forge.jar'],check=True)
