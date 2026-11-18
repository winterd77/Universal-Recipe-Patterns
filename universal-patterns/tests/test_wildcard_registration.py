from pathlib import Path
import zipfile,json,hashlib,os,re
root=Path(__file__).resolve().parents[2]
core=root/'dist/归档/gtlcore-1.2.3.3-fix4-chain-ae.jar'
baseline=root/'dist/归档/gtlcore-1.2.3.3-fix4-wireless-me-clusters.jar'
addon=root/'dist/gtl-universal-patterns-1.0.27.jar'
with zipfile.ZipFile(core) as c,zipfile.ZipFile(baseline) as b,zipfile.ZipFile(addon) as a:
 legacy_names={k for n in b.namelist() if '/lang/' in n and n.endswith('.json') for k in json.loads(b.read(n)) if 'wildcard' in k.lower()}
 assert legacy_names
 protected=[n for n in b.namelist() if 'wildcard' in n.lower() and not n.endswith('/')]
 assert protected
 for name in protected:
  assert name in c.namelist() and c.read(name)==b.read(name),name
  assert name not in a.namelist(),'addon overwrites '+name
 for name in a.namelist():
  if '/lang/' in name and name.endswith('.json'):
   assert not (legacy_names & json.loads(a.read(name)).keys()),'legacy name override'
  if name.startswith('data/'):
   assert 'wildcard' not in name.lower(),'legacy data override'
 cfg=json.loads(a.read('gtluniversalpatterns.mixins.json'))
 assert not any(n.startswith('UniversalBuffer') for n in cfg['mixins'])
 runtime=a.read('org/gtlcore/universalpatterns/mixin/UniversalWildcardRuntimeMixin.class')
 assert b'registerMachines' not in runtime and b'langValue' not in runtime
 assert b'attachSideTabs' in runtime and b'WildcardPatternEditor' in runtime
 assert b'UniversalDynamicSlots' in runtime
config=Path(os.environ['APPDATA'])/'PrismLauncher/instances/GregTech-Leisure-1.4.5.4/minecraft/config/ftbquests/quests/chapters'
references={}
for path in config.glob('*.snbt'):
 text=path.read_text(encoding='utf-8')
 ids=sorted(set(re.findall(r'"([a-z0-9_]+:[a-z0-9_/]*wildcard[a-z0-9_/]*)"',text)))
 if ids:references[path.name]={'ids':ids,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
report={'protected_core_entries':len(protected),'quest_references':references,'core_sha256':hashlib.sha256(core.read_bytes()).hexdigest()}
(root/'build/universal-provider/registration-audit.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(f'PASS: {len(protected)} original wildcard classes/resources byte-identical to baseline; no wildcard IDs/names/tags/recipe overrides in addon; native side-tab integration present; quest references recorded without edits.')
