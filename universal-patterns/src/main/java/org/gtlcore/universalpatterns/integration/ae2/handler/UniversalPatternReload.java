package org.gtlcore.universalpatterns.integration.ae2.handler;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
/** Weak registration does not keep unloaded providers or worlds alive. */
@Mod.EventBusSubscriber(modid="gtluniversalpatterns")
public final class UniversalPatternReload {
    public interface Target { void universalPatterns$reload(); }
    private static final Set<Target> TARGETS = Collections.newSetFromMap(new WeakHashMap<>());
    public static void track(Target target) { TARGETS.add(target); }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void reload(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) return;
        UniversalPatternExpansion.invalidate();
        for (Target target : List.copyOf(TARGETS)) target.universalPatterns$reload();
    }
}
