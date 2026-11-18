package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.stacks.*;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem.Selection;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternFamily.Candidate;
import java.util.*;
/** A durable change record: deletion remains synchronizable after its item disappears. */
public final class PendingPatternSync {
 private PendingPatternSync(){}
 public record Change(String id,String family,String material,String recipeId,AEKey key,boolean output,long amount,boolean ignored,boolean primary){}
 public static Selection enqueue(Selection selection,Candidate source,AEKey key,boolean output){
  String material=source.materialId().toString();var effective=selection.forMaterial(material);
  long amount=(output?DeliveryPolicy.outputExtras(effective):DeliveryPolicy.extras(effective)).stream().filter(v->v.what().equals(key)).mapToLong(GenericStack::amount).reduce(0L,Math::addExact);
  boolean ignored=(output?effective.ignoredOutputs():effective.ignoredInputs()).contains(CatalystSupply.identity(key));
  boolean primary=output&&effective.primaryOutputs().values().stream().anyMatch(v->DeliveryEdits.sameKey(v,key));
  String id=source.family()+"\n"+material+"\n"+(output?"O":"I")+"\n"+CatalystSupply.identity(key);
  String value=source.recipe().id()+"\n"+DeliveryPolicy.encode(key)+"\n"+amount+"\n"+ignored+"\n"+primary;
  var result=selection.withPendingSync(id,value);
  // One material has one primary output: refresh existing queued flags when that choice changes.
  for(var entry:result.pendingSync().entrySet()){
   var old=parse(entry.getKey(),entry.getValue());
   if(old!=null&&old.family().equals(source.family())&&old.material().equals(material)&&old.output()){
    boolean currentPrimary=effective.primaryOutputs().values().stream().anyMatch(v->DeliveryEdits.sameKey(v,old.key()));
    if(currentPrimary!=old.primary()){
     String updated=old.recipeId()+"\n"+DeliveryPolicy.encode(old.key())+"\n"+old.amount()+"\n"+old.ignored()+"\n"+currentPrimary;
     result=result.withPendingSync(old.id(),updated);
    }
   }
  }
  return result;
 }
 public static Change parse(String id,String value){
  try{var identity=id.split("\n",4);var data=value.split("\n",5);if(identity.length!=4||data.length!=5)return null;
   var key=DeliveryPolicy.decode(data[1]);long amount=Long.parseLong(data[2]);if(!(key instanceof AEItemKey||key instanceof AEFluidKey)||amount<0)return null;
   if(!Set.of("I","O").contains(identity[2])||!CatalystSupply.identity(key).equals(identity[3]))return null;
   return new Change(id,identity[0],identity[1],data[0],key,identity[2].equals("O"),amount,Boolean.parseBoolean(data[3]),Boolean.parseBoolean(data[4]));
  }catch(Exception invalid){return null;}
 }
 public static List<Change> entries(Selection selection){
  return selection.pendingSync().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->parse(e.getKey(),e.getValue())).filter(Objects::nonNull).filter(c->c.family().equals(selection.family())).toList();
 }
 public static Selection apply(Selection selection,Change change,String material,AEKey target){
  String identity=material+"\n"+CatalystSupply.identity(target);
  var next=DeliveryEdits.setExtra(selection,material,target,change.output()?0:change.amount(),change.output());
  next=change.output()?next.withIgnoredOutput(identity,change.ignored()):next.withIgnoredInput(identity,change.ignored());
  if(change.output()){
   if(change.primary())next=next.withPrimaryOutput(material,DeliveryPolicy.encode(target));
   else if(next.primaryOutputs().containsKey(material)&&DeliveryEdits.sameKey(next.primaryOutputs().get(material),target))next=next.withPrimaryOutput(material,"");
  }
  return next;
 }
}
