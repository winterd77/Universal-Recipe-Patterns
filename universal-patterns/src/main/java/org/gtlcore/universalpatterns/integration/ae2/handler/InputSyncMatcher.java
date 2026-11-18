package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.stacks.AEKey;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternFamily.Candidate;
/** Match real ingredient semantics, not slot index, quantity or an invented replacement. */
public final class InputSyncMatcher {
 private InputSyncMatcher(){}
 public static AEKey match(Candidate source,Candidate target,AEKey key,InputConstraintOptions.Index index){
  if(!ConsumedFluidFilter.inputs(source.recipe()).contains(key))return null;
  var material=UniversalPatternFamily.materialOf(key);var form=UniversalPatternFamily.formOf(key);
  if(material!=null&&material==source.material()&&target.material()!=null&&form!=null){
   var matches=ConsumedFluidFilter.inputs(target.recipe()).stream().filter(k->UniversalPatternFamily.materialOf(k)==target.material()&&form.equals(UniversalPatternFamily.formOf(k))).toList();
   return matches.size()==1?matches.getFirst():null;
  }
  var candidates=index.candidates(source.recipe(),key);
  var matches=ConsumedFluidFilter.inputs(target.recipe()).stream().filter(candidates::contains).toList();
  return matches.size()==1?matches.getFirst():null;
 }
}
