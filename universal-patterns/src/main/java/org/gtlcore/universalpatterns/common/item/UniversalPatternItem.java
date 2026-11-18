package org.gtlcore.universalpatterns.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternFamily;
import org.gtlcore.universalpatterns.integration.ae2.handler.MaterialExpression;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import net.minecraft.nbt.CompoundTag;

@Mod.EventBusSubscriber(modid = "gtluniversalpatterns", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UniversalPatternItem extends Item implements com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory.IHeldItemUIHolder {
    public static final int MAX_MATERIALS = 2048;
    private static final String FAMILY = "RecipeFamily";
    private static final String MATERIALS = "Materials";
    private static final String EXCLUDED = "ExcludedMaterials";
    private static final String ALLOW = "AllowRule";
    private static final String DENY = "DenyRule";
    private static final ResourceLocation ID = GTLCore.id("universal_pattern");

    private UniversalPatternItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory.INSTANCE.openUI(serverPlayer,hand);
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }

    @Override
    public com.lowdragmc.lowdraglib.gui.modular.ModularUI createUI(net.minecraft.world.entity.player.Player player,
            com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory.HeldItemHolder holder) {
        return org.gtlcore.universalpatterns.api.gui.UniversalPatternHeldEditor.create(player,holder);
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) {
            event.register(ForgeRegistries.Keys.ITEMS, ID, UniversalPatternItem::new);
        }
    }

    @SubscribeEvent
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(GTLCore.id(GTLCore.MOD_ID))) {
            Item item = ForgeRegistries.ITEMS.getValue(ID);
            // Use Forge's own stable Supplier overload, not an inherited Mojmap-only Output method.
            if (item != null) event.accept((java.util.function.Supplier<Item>) item::asItem);
        }
    }

    public record Selection(String family, Set<ResourceLocation> materials, Set<ResourceLocation> excluded,
                            String allow, String deny, Map<ResourceLocation, String> recipes, boolean externalCatalysts, Map<String,Integer> catalystModes, String requiredFluid, Set<String> fixedInputs, Set<String> ignoredInputs, Map<String,Long> extraInputs, Set<String> ignoredOutputs, Map<String,Long> extraOutputs, Map<String,String> primaryOutputs, Map<String,String> pendingSync, Map<String,Boolean> recipeOverrides) {
        public Selection(String family, Set<ResourceLocation> materials, Set<ResourceLocation> excluded, String allow, String deny, Map<ResourceLocation,String> recipes, boolean externalCatalysts, Map<String,Integer> catalystModes, String requiredFluid, Set<String> fixedInputs) {
            this(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,Set.of(),Map.of());
        }
        public Selection(String family, Set<ResourceLocation> materials, Set<ResourceLocation> excluded, String allow, String deny, Map<ResourceLocation,String> recipes, boolean externalCatalysts, Map<String,Integer> catalystModes, String requiredFluid, Set<String> fixedInputs, Set<String> ignoredInputs, Map<String,Long> extraInputs) {
            this(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,ignoredInputs,extraInputs,Set.of(),Map.of());
        }
        public Selection(String family, Set<ResourceLocation> materials, Set<ResourceLocation> excluded, String allow, String deny, Map<ResourceLocation,String> recipes, boolean externalCatalysts, Map<String,Integer> catalystModes, String requiredFluid, Set<String> fixedInputs, Set<String> ignoredInputs, Map<String,Long> extraInputs, Set<String> ignoredOutputs, Map<String,Long> extraOutputs) {
            this(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,ignoredInputs,extraInputs,ignoredOutputs,extraOutputs,Map.of());
        }
        public Selection(String family,Set<ResourceLocation> materials,Set<ResourceLocation> excluded,String allow,String deny,Map<ResourceLocation,String> recipes,boolean externalCatalysts,Map<String,Integer> catalystModes,String requiredFluid,Set<String> fixedInputs,Set<String> ignoredInputs,Map<String,Long> extraInputs,Set<String> ignoredOutputs,Map<String,Long> extraOutputs,Map<String,String> primaryOutputs){
            this(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,ignoredInputs,extraInputs,ignoredOutputs,extraOutputs,primaryOutputs,Map.of());
        }
        public Selection(String family,Set<ResourceLocation> materials,Set<ResourceLocation> excluded,String allow,String deny,Map<ResourceLocation,String> recipes,boolean externalCatalysts,Map<String,Integer> catalystModes,String requiredFluid,Set<String> fixedInputs,Set<String> ignoredInputs,Map<String,Long> extraInputs,Set<String> ignoredOutputs,Map<String,Long> extraOutputs,Map<String,String> primaryOutputs,Map<String,String> pendingSync){
            this(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,ignoredInputs,extraInputs,ignoredOutputs,extraOutputs,primaryOutputs,pendingSync,Map.of());
        }
        public Selection withRecipeOverride(String id,Boolean enabled){
            var values=new HashMap<>(recipeOverrides);if(enabled==null)values.remove(id);else values.put(id,enabled);
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,ignoredInputs,extraInputs,ignoredOutputs,extraOutputs,primaryOutputs,pendingSync,values);
        }
        public Selection withPendingSync(String id,String value){
            var pending=new LinkedHashMap<>(pendingSync);if(value==null)pending.remove(id);else pending.put(id,value);
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,ignoredInputs,extraInputs,ignoredOutputs,extraOutputs,primaryOutputs,pending,recipeOverrides);
        }
        public Selection withPrimaryOutput(String material,String encoded) {
            var values=new HashMap<>(primaryOutputs);if(encoded.isEmpty())values.remove(material);else values.put(material,encoded);
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,ignoredInputs,extraInputs,ignoredOutputs,extraOutputs,values,pendingSync,recipeOverrides);
        }
        public Selection forMaterial(String material) {
            String prefix=material+"\n";var ignored=new HashSet<String>();var extra=new HashMap<String,Long>();
            for(var key:ignoredOutputs)if(key.startsWith(prefix))ignored.add(key.substring(prefix.length()));
            for(var entry:extraOutputs.entrySet())if(entry.getKey().startsWith(prefix))extra.put(entry.getKey().substring(prefix.length()),entry.getValue());
            boolean scoped=ignoredInputs.contains(prefix+"@");var inputIgnored=new HashSet<String>();var inputExtra=new HashMap<String,Long>();
            for(var key:ignoredInputs)if(key.startsWith(prefix)&&!key.equals(prefix+"@"))inputIgnored.add(key.substring(prefix.length()));else if(!scoped&&!key.contains("\n"))inputIgnored.add(key);
            for(var entry:extraInputs.entrySet())if(!scoped&&!entry.getKey().contains("\n"))inputExtra.put(entry.getKey(),entry.getValue());
            for(var entry:extraInputs.entrySet())if(entry.getKey().startsWith(prefix))inputExtra.put(entry.getKey().substring(prefix.length()),entry.getValue());
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,inputIgnored,inputExtra,ignored,extra,primaryOutputs.containsKey(material)?Map.of(material,primaryOutputs.get(material)):Map.of(),Map.of());
        }
        public Selection forOutputMaterial(String material) { return forMaterial(material); }
        /** Snapshot inherited legacy defaults once, so clearing a local edit cannot re-enable a global entry. */
        public Selection scopeInputs(String material) {
            String prefix=material+"\n";if(ignoredInputs.contains(prefix+"@"))return this;
            var effective=forMaterial(material);var ignored=new HashSet<>(ignoredInputs);var extra=new HashMap<>(extraInputs);
            ignored.add(prefix+"@");for(var key:effective.ignoredInputs)ignored.add(prefix+key);
            effective.extraInputs.forEach((key,value)->extra.put(prefix+key,value));
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,ignored,extra,ignoredOutputs,extraOutputs,primaryOutputs,pendingSync,recipeOverrides);
        }
        public Selection withIgnoredOutput(String identity,boolean ignored) {
            var values=new HashSet<>(ignoredOutputs);if(ignored)values.add(identity);else values.remove(identity);
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,ignoredInputs,extraInputs,values,extraOutputs,primaryOutputs,pendingSync,recipeOverrides);
        }
        public Selection withExtraOutput(String encoded,long amount) {
            var values=new HashMap<>(extraOutputs);if(amount==0)values.remove(encoded);else values.put(encoded,amount);
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,ignoredInputs,extraInputs,ignoredOutputs,values,primaryOutputs,pendingSync,recipeOverrides);
        }
        public Selection withDeliveryFrom(Selection old) {
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,old.ignoredInputs,old.extraInputs,old.ignoredOutputs,old.extraOutputs,old.primaryOutputs,old.pendingSync,recipeOverrides.isEmpty()?old.recipeOverrides:recipeOverrides);
        }

        public Selection withFamily(String nextFamily) {
            var migrated = new HashMap<>(pendingSync);
            String prefix = family + "\n";
            for (var entry : pendingSync.entrySet()) {
                if (!entry.getKey().startsWith(prefix)) continue;
                migrated.remove(entry.getKey());
                migrated.put(nextFamily + entry.getKey().substring(family.length()), entry.getValue());
            }
            return new Selection(nextFamily, materials, excluded, allow, deny, recipes, externalCatalysts, catalystModes,
                    requiredFluid, fixedInputs, ignoredInputs, extraInputs, ignoredOutputs, extraOutputs, primaryOutputs,
                    migrated, recipeOverrides);
        }
        public Selection withIgnoredInput(String identity,boolean ignored) {
            var values=new HashSet<>(ignoredInputs);if(ignored)values.add(identity);else values.remove(identity);
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,values,extraInputs,ignoredOutputs,extraOutputs,primaryOutputs,pendingSync,recipeOverrides);
        }
        public Selection withExtraInput(String encoded,long amount) {
            var values=new HashMap<>(extraInputs);if(amount==0)values.remove(encoded);else values.put(encoded,amount);
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,fixedInputs,ignoredInputs,values,ignoredOutputs,extraOutputs,primaryOutputs,pendingSync,recipeOverrides);
        }
        public Selection(String family, Set<ResourceLocation> materials, Set<ResourceLocation> excluded, String allow, String deny, Map<ResourceLocation,String> recipes, boolean externalCatalysts, Map<String,Integer> catalystModes, String requiredFluid) {
            this(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,requiredFluid,Set.of());
        }
        public Selection(String family, Set<ResourceLocation> materials, Set<ResourceLocation> excluded, String allow, String deny, Map<ResourceLocation,String> recipes, boolean externalCatalysts, Map<String,Integer> catalystModes) {
            this(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,"");
        }
        public Set<String> requiredInputs() {
            var keys=new HashSet<>(fixedInputs);if(!requiredFluid.isEmpty())keys.add(requiredFluid);return Set.copyOf(keys);
        }
        public Selection withFixedInput(String identity,boolean fixed) {
            var keys=new HashSet<>(requiredInputs());if(fixed)keys.add(identity);else keys.remove(identity);
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,"",keys,ignoredInputs,extraInputs,ignoredOutputs,extraOutputs,primaryOutputs,pendingSync,recipeOverrides);
        }
        public Selection withoutFixedInputs() {
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,"",Set.of(),ignoredInputs,extraInputs,ignoredOutputs,extraOutputs,primaryOutputs,pendingSync,recipeOverrides);
        }
        public Selection withRequiredFluid(String identity) {
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,catalystModes,identity,fixedInputs,ignoredInputs,extraInputs,ignoredOutputs,extraOutputs,primaryOutputs,pendingSync,recipeOverrides);
        }
        public Selection(String family, Set<ResourceLocation> materials, Set<ResourceLocation> excluded, String allow, String deny, Map<ResourceLocation, String> recipes) {
            this(family, materials, excluded, allow, deny, recipes, false);
        }
        public Selection(String family, Set<ResourceLocation> materials, Set<ResourceLocation> excluded, String allow, String deny, Map<ResourceLocation,String> recipes, boolean externalCatalysts) {
            this(family,materials,excluded,allow,deny,recipes,externalCatalysts,Map.of());
        }
        // 0 virtual, 1 supplied by target, 2 ignored by the pattern. Legacy booleans are NBT bytes 0/1.
        public int supplyMode(String identity) { return catalystModes.getOrDefault(identity,externalCatalysts ? 1 : 0); }
        public boolean externalFor(String identity) { return supplyMode(identity) != 0; }
        public Selection withCatalystMode(String identity, boolean external) {
            return withCatalystMode(identity, external ? 1 : 0);
        }
        public Selection withCatalystMode(String identity, int mode) {
            if(mode<0 || mode>3)throw new IllegalArgumentException("Invalid catalyst supply mode");
            var modes=new HashMap<>(catalystModes);modes.put(identity,mode);
            if(modes.size()>MAX_MATERIALS)throw new IllegalArgumentException("Too many catalyst overrides");
            return new Selection(family,materials,excluded,allow,deny,recipes,externalCatalysts,modes,requiredFluid,fixedInputs,ignoredInputs,extraInputs,ignoredOutputs,extraOutputs,primaryOutputs,pendingSync,recipeOverrides);
        }
        public Selection withSupplyFrom(Selection old) {
            return new Selection(family,materials,excluded,allow,deny,recipes,old.externalCatalysts,old.catalystModes,old.requiredFluid,old.fixedInputs,old.ignoredInputs,old.extraInputs,old.ignoredOutputs,old.extraOutputs,old.primaryOutputs,old.pendingSync,recipeOverrides);
        }
        public Selection withExternalCatalysts(boolean value) {
            return new Selection(family, materials, excluded, allow, deny, recipes, value, catalystModes,requiredFluid,fixedInputs,ignoredInputs,extraInputs,ignoredOutputs,extraOutputs,primaryOutputs,pendingSync,recipeOverrides);
        }
        public Selection(String family, Set<ResourceLocation> materials, Set<ResourceLocation> excluded, String allow, String deny) {
            this(family, materials, excluded, allow, deny, Map.of());
        }
        public Selection {
            recipeOverrides=Map.copyOf(recipeOverrides);
            if(recipeOverrides.size()>4096||recipeOverrides.keySet().stream().anyMatch(k->k.isBlank()||k.length()>1024))throw new IllegalArgumentException("Invalid recipe overrides");
            pendingSync=Map.copyOf(pendingSync);
            if(pendingSync.size()>256||pendingSync.entrySet().stream().anyMatch(e->e.getKey().length()>2048||e.getValue().length()>12288))throw new IllegalArgumentException("Too many pending sync edits");
            materials = Set.copyOf(materials);
            excluded = Set.copyOf(excluded);
            recipes = Map.copyOf(recipes);
            catalystModes = Map.copyOf(catalystModes);
            fixedInputs=Set.copyOf(fixedInputs);
            ignoredInputs=Set.copyOf(ignoredInputs);extraInputs=Map.copyOf(extraInputs);
            ignoredOutputs=Set.copyOf(ignoredOutputs);extraOutputs=Map.copyOf(extraOutputs);primaryOutputs=Map.copyOf(primaryOutputs);
            if(primaryOutputs.size()>4096||primaryOutputs.entrySet().stream().anyMatch(e->e.getKey().length()>256||e.getValue().isBlank()||e.getValue().length()>8192))throw new IllegalArgumentException("Invalid primary output");
            if(ignoredInputs.size()>4096 || ignoredInputs.stream().anyMatch(k->!k.matches("(?:[^\\n]{1,256}\\n(?:@|[0-9a-f]{64})|[0-9a-f]{64})")))throw new IllegalArgumentException("Invalid ignored input");
            if(ignoredOutputs.size()>4096 || ignoredOutputs.stream().anyMatch(k->!k.matches("(?:[^\\n]{1,256}\\n)?[0-9a-f]{64}")))throw new IllegalArgumentException("Invalid ignored output");
            if(extraInputs.size()>4096 || extraInputs.entrySet().stream().anyMatch(e->e.getKey().isBlank()||e.getKey().length()>8192||e.getValue()<=0))throw new IllegalArgumentException("Invalid extra input");
            if(extraOutputs.size()>4096 || extraOutputs.entrySet().stream().anyMatch(e->e.getKey().isBlank()||e.getKey().length()>8192||e.getValue()<=0))throw new IllegalArgumentException("Invalid extra output");
            if(fixedInputs.size()>64 || fixedInputs.stream().anyMatch(key->!key.matches("[0-9a-f]{64}")))throw new IllegalArgumentException("Invalid fixed ingredients");
            if(!requiredFluid.isEmpty() && !requiredFluid.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid fluid constraint");
        }
        public static Selection empty() { return new Selection("", Set.of(), Set.of(), "", ""); }

        public boolean canBind() {
            return materials.isEmpty() && allow.isBlank() && deny.isBlank();
        }
    }

    public static Selection read(ItemStack stack) {
        if (!(stack.getItem() instanceof UniversalPatternItem) || !stack.hasTag()) return Selection.empty();
        return readSelection(stack.getTag());
    }

    private static Selection readSelection(CompoundTag tag) {
        String family = tag.getString(FAMILY);
        if (family.length() > 512) return Selection.empty();
        return new Selection(family, readMaterials(tag.getList(MATERIALS, Tag.TAG_STRING)),
                readMaterials(tag.getList(EXCLUDED, Tag.TAG_STRING)), readRule(tag.getString(ALLOW)), readRule(tag.getString(DENY)), readRecipes(tag.getCompound("RecipeChoices")), tag.getBoolean("ExternalCatalysts"), readCatalystModes(tag.getCompound("CatalystModes")), readFluidConstraint(tag), readFixedInputs(tag), readIgnoredInputs(tag), readExtraInputs(tag), readIgnoredOutputs(tag), readExtraOutputs(tag), readPrimaryOutputs(tag), readPendingSync(tag),readRecipeOverrides(tag));
    }

    private static Map<String,Boolean> readRecipeOverrides(CompoundTag tag){
        var result=new HashMap<String,Boolean>();var values=tag.getCompound("RecipeOverrides");
        for(String key:values.getAllKeys())if(!key.isBlank()&&key.length()<=1024&&values.contains(key,Tag.TAG_BYTE)&&result.size()<4096)result.put(key,values.getBoolean(key));
        return Map.copyOf(result);
    }
    private static Map<String,String> readPendingSync(CompoundTag tag){
        var source=tag.getCompound("PendingSync");var values=new LinkedHashMap<String,String>();
        for(var key:source.getAllKeys())if(values.size()<256&&key.length()<=2048){String value=source.getString(key);if(value.length()<=12288)values.put(key,value);}
        return Map.copyOf(values);
    }
    private static Map<String,String> readPrimaryOutputs(CompoundTag tag) {
        var entries=tag.getCompound("PrimaryOutputs");var result=new HashMap<String,String>();
        for(var key:entries.getAllKeys())if(result.size()<4096&&key.length()<=256){String value=entries.getString(key);if(!value.isBlank()&&value.length()<=8192)result.put(key,value);}
        return Map.copyOf(result);
    }
    private static Set<String> readIgnoredInputs(CompoundTag tag) {
        var list=tag.getList("IgnoredInputs",Tag.TAG_STRING);var result=new HashSet<String>();
        for(int i=0;i<Math.min(list.size(),4096);i++){var value=list.getString(i);if(value.matches("(?:[^\\n]{1,256}\\n(?:@|[0-9a-f]{64})|[0-9a-f]{64})"))result.add(value);}
        return Set.copyOf(result);
    }
    private static Set<String> readIgnoredOutputs(CompoundTag tag) {
        var list=tag.getList("IgnoredOutputs",Tag.TAG_STRING);var result=new HashSet<String>();
        for(int i=0;i<Math.min(list.size(),4096);i++){var value=list.getString(i);if(value.matches("(?:[^\\n]{1,256}\\n)?[0-9a-f]{64}"))result.add(value);}
        return Set.copyOf(result);
    }
    private static Map<String,Long> readExtraInputs(CompoundTag tag) {
        var entries=tag.getCompound("ExtraInputs");var result=new HashMap<String,Long>();
        for(var key:entries.getAllKeys())if(result.size()<4096&&!key.isBlank()&&key.length()<=8192){long amount=entries.getLong(key);if(amount>0)result.put(key,amount);}
        return Map.copyOf(result);
    }
    private static Map<String,Long> readExtraOutputs(CompoundTag tag) {
        var entries=tag.getCompound("ExtraOutputs");var result=new HashMap<String,Long>();
        for(var key:entries.getAllKeys())if(result.size()<4096&&!key.isBlank()&&key.length()<=8192){long amount=entries.getLong(key);if(amount>0)result.put(key,amount);}
        return Map.copyOf(result);
    }
    private static Set<String> readFixedInputs(CompoundTag tag) {
        var list=tag.getList("FixedInputs",Tag.TAG_STRING);var result=new HashSet<String>();
        if(list.size()>64)return Set.of("0".repeat(64));
        for(int i=0;i<list.size();i++) {String key=list.getString(i);result.add(key.matches("[0-9a-f]{64}")?key:"0".repeat(64));}
        return Set.copyOf(result);
    }

    private static String readFluidConstraint(CompoundTag tag) {
        String value=tag.getString("RequiredFluid");
        // Corrupt constraints must not silently publish all fluid variants.
        return value.isEmpty() || value.matches("[0-9a-f]{64}") ? value : "0".repeat(64);
    }

    private static Map<String,Integer> readCatalystModes(CompoundTag tag) {
        var modes=new HashMap<String,Integer>();
        for(String key:tag.getAllKeys())if(key.matches("[0-9a-f]{64}") && modes.size()<MAX_MATERIALS && tag.contains(key,Tag.TAG_BYTE)) {
            int mode=tag.getByte(key);
            if(mode>=0 && mode<=3)modes.put(key,mode);
        }
        return Map.copyOf(modes);
    }

    private static Map<ResourceLocation, String> readRecipes(CompoundTag tag) {
        var result = new HashMap<ResourceLocation, String>();
        for (String key : tag.getAllKeys()) {
            ResourceLocation material = ResourceLocation.tryParse(key);
            String recipe = tag.getString(key);
            if (material != null && recipe.length() <= 1024 && result.size() < MAX_MATERIALS) result.put(material, recipe);
        }
        return Map.copyOf(result);
    }

    private static Set<ResourceLocation> readMaterials(ListTag saved) {
        var materials = new LinkedHashSet<ResourceLocation>();
        for (int i = 0; i < Math.min(saved.size(), MAX_MATERIALS); i++) {
            String raw = saved.getString(i);
            ResourceLocation id = raw.isEmpty() || raw.length() > 256 ? null : ResourceLocation.tryParse(raw);
            if (id != null) materials.add(id);
        }
        return Set.copyOf(materials);
    }

    private static String readRule(String rule) {
        return rule.length() <= MaterialExpression.MAX_LENGTH ? rule : "";
    }

    private static ListTag writeMaterials(Set<ResourceLocation> materials) {
        var saved = new ListTag();
        materials.stream().sorted().forEach(id -> saved.add(StringTag.valueOf(id.toString())));
        return saved;
    }

    public static void write(ItemStack stack, Selection selection) {
        writeSelection(stack.getOrCreateTag(), selection);
    }

    private static void writeSelection(CompoundTag tag, Selection selection) {
        tag.putString(FAMILY, selection.family());
        var pending=new CompoundTag();selection.pendingSync().forEach(pending::putString);tag.put("PendingSync",pending);
        var primary=new CompoundTag();selection.primaryOutputs().forEach(primary::putString);tag.put("PrimaryOutputs",primary);
        var ignored=new ListTag();selection.ignoredInputs().stream().sorted().forEach(k->ignored.add(StringTag.valueOf(k)));tag.put("IgnoredInputs",ignored);
        var ignoredOut=new ListTag();selection.ignoredOutputs().stream().sorted().forEach(k->ignoredOut.add(StringTag.valueOf(k)));tag.put("IgnoredOutputs",ignoredOut);
        var extras=new CompoundTag();selection.extraInputs().forEach(extras::putLong);tag.put("ExtraInputs",extras);
        var extrasOut=new CompoundTag();selection.extraOutputs().forEach(extrasOut::putLong);tag.put("ExtraOutputs",extrasOut);
        tag.putString("RequiredFluid",selection.requiredFluid());
        var fixed=new ListTag();selection.fixedInputs().stream().sorted().forEach(key->fixed.add(StringTag.valueOf(key)));tag.put("FixedInputs",fixed);
        tag.putBoolean("ExternalCatalysts", selection.externalCatalysts());
        var overrides=new CompoundTag();selection.recipeOverrides().forEach(overrides::putBoolean);tag.put("RecipeOverrides",overrides);
        var modes=new CompoundTag();selection.catalystModes().forEach((key,mode)->modes.putByte(key,mode.byteValue()));tag.put("CatalystModes",modes);
        tag.put(MATERIALS, writeMaterials(selection.materials()));
        tag.put(EXCLUDED, writeMaterials(selection.excluded()));
        tag.putString(ALLOW, selection.allow());
        tag.putString(DENY, selection.deny());
        var choices = new CompoundTag();
        selection.recipes().forEach((material, recipe) -> choices.putString(material.toString(), recipe));
        tag.put("RecipeChoices", choices);
    }

    public static Selection readDraft(ItemStack stack) {
        if (!(stack.getItem() instanceof UniversalPatternItem)) return Selection.empty();
        var tag = stack.getTag();
        return tag != null && tag.contains("UniversalDraft", Tag.TAG_COMPOUND) ?
                readSelection(tag.getCompound("UniversalDraft")) : read(stack);
    }

    public static void writeDraft(ItemStack stack, Selection draft) {
        var tag = new CompoundTag();
        writeSelection(tag, draft);
        stack.getOrCreateTag().put("UniversalDraft", tag);
    }

    public static void discardDraft(ItemStack stack) {
        stack.getOrCreateTag().remove("UniversalDraft");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Selection selection = read(stack);
        tooltip.add(UniversalPatternFamily.label(selection.family()).copy().withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.gtlcore.universal_pattern.count", selection.materials().size()).withStyle(ChatFormatting.GRAY));
        if (!selection.allow().isEmpty() || !selection.deny().isEmpty() || !selection.excluded().isEmpty()) {
            tooltip.add(Component.translatable("item.gtlcore.universal_pattern.has_rules").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.gtlcore.universal_pattern.help").withStyle(ChatFormatting.GRAY));
    }
}
