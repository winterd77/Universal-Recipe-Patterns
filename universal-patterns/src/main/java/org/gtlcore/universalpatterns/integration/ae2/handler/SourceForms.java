package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.stacks.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
/** Material forms, not interchangeable phases. Unknown forms retain exact key identity. */
public final class SourceForms {
 public static String form(AEKey key){
  String known=UniversalPatternFamily.formOf(key);
  return known!=null?known.toLowerCase(Locale.ROOT):(key instanceof AEItemKey?"itemkey:":"fluidkey:")+CatalystSupply.identity(key);
 }
 public static void add(Map<ResourceLocation,Set<String>> forms,AEKey key){
  if(key==null)return;
  var material=UniversalPatternFamily.materialOf(key);
  forms.computeIfAbsent(material==null?key.getId():material.getResourceLocation(),ignored->new HashSet<>()).add(form(key));
 }
 public static boolean matches(Set<String> forms,String reference,boolean other){
  if(!other)return forms.contains(reference);
  if(!(reference.startsWith("item:")||reference.startsWith("fluid:")))return false;
  return forms.stream().anyMatch(form->!form.equals(reference)&&(form.startsWith("item:")||form.startsWith("fluid:")));
 }
 public static Map<ResourceLocation,Set<String>> freeze(Map<ResourceLocation,Set<String>> forms){
  var result=new HashMap<ResourceLocation,Set<String>>();forms.forEach((key,value)->result.put(key,Set.copyOf(value)));return Map.copyOf(result);
 }
}
