package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.stacks.AEKey;
import net.minecraft.nbt.TagParser;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem.Selection;
/** Match logical keys instead of the textual ordering of serialized NBT. */
public final class DeliveryEdits {
 private DeliveryEdits(){}
 public static boolean sameKey(String encoded,AEKey key){
  try{if(key.equals(DeliveryPolicy.decode(encoded)))return true;}catch(Exception invalid){}
  try{var tag=TagParser.parseTag(encoded);return !tag.contains("#c")&&tag.equals(key.toTag());}catch(Exception invalid){return false;}
 }
 public static Selection setExtra(Selection original,String material,AEKey key,long amount,boolean output){
  if(amount<0)throw new IllegalArgumentException("Negative extra amount");
  var result=output?original:original.scopeInputs(material);String prefix=material+"\n";
  var entries=output?result.extraOutputs():result.extraInputs();
  for(var encoded:entries.keySet())if(encoded.startsWith(prefix)&&sameKey(encoded.substring(prefix.length()),key))
   result=output?result.withExtraOutput(encoded,0):result.withExtraInput(encoded,0);
  if(amount>0)result=output?result.withExtraOutput(prefix+DeliveryPolicy.encode(key),amount):result.withExtraInput(prefix+DeliveryPolicy.encode(key),amount);
  return result;
 }
}
