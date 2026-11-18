from pathlib import Path
import json,zipfile
root=Path.cwd()
def enabled(c,mods):
 if c['type']=='forge:mod_loaded':return c['modid'] in mods
 if c['type']=='forge:not':return not enabled(c['value'],mods)
 raise AssertionError(c)
with zipfile.ZipFile(root/'dist/gtl-universal-patterns-1.0.27.jar') as z:
 base=json.loads(z.read('data/gtlcore/recipes/universal_pattern.json'));reset=json.loads(z.read('data/gtlcore/recipes/universal_pattern_reset.json'))
with zipfile.ZipFile(root/'dist/gtlcore-1.2.3.3-fix4-chain-ae-ui-recipes-v3.jar') as z:pack=json.loads(z.read('data/gtlcore/recipes/universal_pattern_from_wildcard.json'))
assert base['type']==pack['type']=='minecraft:crafting_shapeless'
assert base['ingredients']==[{'item':'ae2:blank_pattern'}]
assert pack['ingredients']==[{'item':'wildcard_pattern:wildcard_pattern'}]
assert base['result']==pack['result']=={'item':'gtlcore:universal_pattern'}
mods={'gtlcore','gtluniversalpatterns','wildcard_pattern'}
assert 'conditions' not in base
assert all(enabled(c,mods) for c in pack['conditions'])
assert not all(enabled(c,{'gtlcore'}) for c in pack['conditions'])
assert not list((root/'universal-patterns/src').rglob('CorePatternRecipeCondition*'))
assert reset['result']['item']=='gtlcore:universal_pattern' and 'nbt' not in reset['result']
print('PASS: packaged recipe ownership, shapeless ingredients, GTLCore suppression, missing-addon guard and clean reset recipe.')
