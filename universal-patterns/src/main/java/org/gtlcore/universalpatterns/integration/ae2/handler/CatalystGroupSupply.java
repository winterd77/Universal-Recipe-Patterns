package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.stacks.*;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem.Selection;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternFamily.Candidate;
import java.util.*;
/** Same registered item/fluid across configurations, or corresponding material forms. Never slot-index matching. */
public final class CatalystGroupSupply {
 public static boolean matches(Candidate source,Candidate target,AEKey selected,AEKey candidate){
  if((selected instanceof AEItemKey)!=(candidate instanceof AEItemKey))return false;
  if(selected.getId().equals(candidate.getId()))return true;
  var material=UniversalPatternFamily.materialOf(selected);var form=UniversalPatternFamily.formOf(selected);
  return material!=null&&material==source.material()&&target.material()!=null&&form!=null&&
      target.material()==UniversalPatternFamily.materialOf(candidate)&&form.equals(UniversalPatternFamily.formOf(candidate));
 }
 public static Selection apply(Selection policy,Candidate source,Collection<Candidate> targets,AEKey key,int mode){
  if(source==null||source.recipe().nonConsumedInputs().stream().flatMap(List::stream).noneMatch(key::equals))return policy;
  var result=policy.withCatalystMode(CatalystSupply.identity(key),mode);
  for(var target:targets)if(target!=null&&target.family().equals(source.family()))
   for(var alternatives:target.recipe().nonConsumedInputs())for(var candidate:alternatives)
    if(matches(source,target,key,candidate))result=result.withCatalystMode(CatalystSupply.identity(candidate),mode);
  return result;
 }
}
