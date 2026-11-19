package org.gtlcore.universalpatterns.integration.ae2.handler;
import appeng.api.stacks.*;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import org.gtlcore.universalpatterns.common.item.UniversalPatternItem.Selection;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper.RecipePattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
/** Exact item/fluid identity, including secondary NBT; old global mode remains the migration default. */
public final class CatalystSupply {
    private CatalystSupply() {}
    public static String identity(AEKey key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.toTag().toString().getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static boolean external(Selection selection, AEKey key) { return mode(selection, key) != 0; }
    public static int mode(Selection selection, AEKey key) {
        var override = selection.catalystModes().get(identity(key));
        if (override != null) return override;
        if (selection.defaultSupplyMode() >= 0) return selection.defaultSupplyMode();
        if (selection.externalCatalysts()) return 1;
        return key instanceof AEItemKey item && IntCircuitBehaviour.isIntegratedCircuit(item.getReadOnlyStack()) ? 3 : 0;
    }
    public static Map<AEKey,AEItemKey> filter(Selection selection,Map<AEKey,AEItemKey> supplies) {
        var result=new HashMap<AEKey,AEItemKey>();
        supplies.forEach((key,token)->{if(!external(selection,key))result.put(key,token);});
        return Map.copyOf(result);
    }
    public static int circuit(RecipePattern recipe,Selection selection) {
        if(recipe.circuit()<0)return recipe.circuit();
        for(var alternatives:recipe.nonConsumedInputs())for(var key:alternatives)
            if(key instanceof AEItemKey item && IntCircuitBehaviour.isIntegratedCircuit(item.getReadOnlyStack()) &&
                    IntCircuitBehaviour.getCircuitConfiguration(item.getReadOnlyStack())==recipe.circuit() && external(selection,key))return -1;
        return recipe.circuit();
    }
}
