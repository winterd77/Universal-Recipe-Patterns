package org.gtlcore.universalpatterns.integration.ae2.handler;

import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlag;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GCyMRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.gtlcore.universalpatterns.integration.ae2.handler.UniversalPatternFamily.Candidate;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class UniversalMaterialRules {
    private static final Pattern ID_GLOB = Pattern.compile("[a-z0-9_.?*-]+(?::[a-z0-9_./?*-]+)?|[a-z0-9_./?*-]+");
    private static final List<String> SAMPLE_PROCESSES = List.of("mixer", "alloy_blast", "blast_furnace", "washing", "smelting",
            "macerator", "sifter", "thermal_centrifuge", "electromagnetic_separator", "electrolyzer", "centrifuge", "arc_furnace",
            "vacuum_freezer", "bender", "cutter", "extruder", "lathe", "wiremill", "compressor", "forming_press", "fluid_solidifier",
            "chemical_reactor", "chemical_bath", "distillation_tower", "distillery", "cracker", "fermenter", "fluid_heater", "canner",
            "polarizer", "petrochemical_plant", "fuel_refining", "fission_reactor", "isa_mill", "flotating_beneficiation", "assembler");
    private static final Map<RecipeManager, ProcessCache> PROCESS_CACHE = new WeakHashMap<>();
    private static final Pattern TEMPERATURE = Pattern.compile("temp(>=|<=|=|>|<)([0-9]{1,8})");
    private static final Pattern RECIPE_LIMIT = Pattern.compile("(eut|ticks)(>=|<=|=|>|<)([0-9]{1,18})");
    private static final Map<String, PropertyKey<?>> PROPERTIES = Map.ofEntries(
            Map.entry("blast", PropertyKey.BLAST), Map.entry("alloy_blast", PropertyKey.ALLOY_BLAST),
            Map.entry("dust", PropertyKey.DUST), Map.entry("fluid", PropertyKey.FLUID), Map.entry("gem", PropertyKey.GEM),
            Map.entry("ingot", PropertyKey.INGOT), Map.entry("polymer", PropertyKey.POLYMER), Map.entry("ore", PropertyKey.ORE),
            Map.entry("tool", PropertyKey.TOOL), Map.entry("rotor", PropertyKey.ROTOR), Map.entry("wire", PropertyKey.WIRE),
            Map.entry("wood", PropertyKey.WOOD), Map.entry("hazard", PropertyKey.HAZARD),
            Map.entry("fluid_pipe", PropertyKey.FLUID_PIPE), Map.entry("item_pipe", PropertyKey.ITEM_PIPE));

    private UniversalMaterialRules() {}

    public static boolean isExpression(String query) {
        String value = query.strip().toLowerCase(Locale.ROOT);
        return value.startsWith("#") || value.startsWith("in:#") || value.startsWith("out:#") ||
                value.startsWith("tag:") || value.startsWith("flag:") || value.startsWith("prop:") ||
                value.startsWith("source:") || value.startsWith("process:") || value.startsWith("use:") || value.startsWith("material:") ||
                value.startsWith("product:") || value.startsWith("input:") || value.startsWith("line:") ||
                value.startsWith("catalyst:") || value.startsWith("circuit:") || value.startsWith("result:#") ||
                value.startsWith("sample:") || value.startsWith("recipe:") || value.startsWith("recipe_io:") ||
                TEMPERATURE.matcher(value).matches() || RECIPE_LIMIT.matcher(value).matches() || value.equals("all") ||
                value.startsWith("!") || value.startsWith("(") || value.contains("&") || value.contains("|") || isGlob(value);
    }

    public static Predicate<Candidate> compile(String expression, Level level) {
        return MaterialExpression.parse(expression, token -> condition(token, level));
    }

    private static Predicate<Candidate> condition(String token, Level level) {
        if (token.equals("all")) return candidate -> true;
        if (token.equals("product:metal")) {
            return candidate -> PetrochemicalIndex.isMetal(UniversalPatternFamily.materialOf(candidate.output()));
        }
        if (token.equals("product:petrochemical")) {
            var index = PetrochemicalIndex.get(level);
            return candidate -> index.isProduct(candidate.output());
        }
        if (token.equals("line:petrochemical")) {
            var index = PetrochemicalIndex.get(level);
            return candidate -> index.isRoute(candidate.recipe().recipe());
        }
        if (token.equals("line:magnetization")) return candidate -> UniversalPatternFamily.isMagnetization(candidate.recipe());
        if (token.equals("line:demagnetization")) return UniversalPatternFamily::isDemagnetization;
        if (token.startsWith("line:")) {
            String name = token.substring(5);
            return RegisteredProcessFamilies.supportsLine(name) ? candidate -> RegisteredProcessFamilies.matchesLine(candidate, name) : null;
        }
        if (token.equals("product:purified_water")) return candidate -> RegisteredProcessFamilies.isPurifiedWater(candidate.output());
        if (token.equals("product:plasma_cell")) return candidate -> RegisteredProcessFamilies.isFilledPlasmaCell(candidate.output());
        if (token.startsWith("input:") || token.startsWith("product:")) {
            String phase = token.substring(token.indexOf(':') + 1);
            if (phase.equals("plasma") || phase.equals("molten") || phase.equals("liquid") || phase.equals("gas")) {
                return token.startsWith("input:") ? candidate -> anyInput(candidate, key -> RegisteredProcessFamilies.isPhase(key, phase)) :
                        candidate -> RegisteredProcessFamilies.isPhase(candidate.output(), phase);
            }
        }
        if (token.equals("input:magnetizable") || token.equals("product:magnetizable")) {
            var inputs = indexedPolarizerKeys(level, true);
            return token.startsWith("input:") ? candidate -> anyInput(candidate, inputs::contains) :
                    candidate -> inputs.contains(candidate.output());
        }
        if (token.equals("input:magnetized") || token.equals("product:magnetized")) {
            var outputs = indexedPolarizerKeys(level, false);
            Predicate<AEKey> magnetized = key -> outputs.contains(key) || isMagneticItem(key);
            return token.startsWith("input:") ? candidate -> anyInput(candidate, magnetized) :
                    candidate -> magnetized.test(candidate.output());
        }
        if (token.startsWith("in:#")) {
            var tag = tagPredicate(token.substring(4));
            return candidate -> anyInput(candidate, tag);
        }
        if (token.startsWith("result:#")) {
            var tag = tagPredicate(token.substring(8));
            return candidate -> Arrays.stream(candidate.recipe().pattern().getOutputs()).anyMatch(stack -> tag.test(stack.what()));
        }
        if (token.startsWith("#") || token.startsWith("tag:") || token.startsWith("out:#")) {
            int split = token.indexOf('#');
            var tag = tagPredicate(token.substring(split < 0 ? 4 : split + 1));
            return candidate -> tag.test(candidate.output()) ||
                    (!token.startsWith("out:") && candidate.inputs().stream().anyMatch(tag));
        }
        if (token.startsWith("sample:") || token.startsWith("recipe_io:")) {
            String expression = token.substring(token.indexOf(':') + 1);
            var matches = expression.startsWith("#") ? tagPredicate(expression.substring(1)) : keyPredicate(expression);
            return token.startsWith("sample:") ? candidate -> candidate.matchesSample(matches) :
                    candidate -> candidate.matchesRecipeSample(matches);
        }
        if (token.startsWith("recipe:")) {
            var recipeType = idPredicate(token.substring(7));
            return candidate -> recipeType.test(candidate.recipe().recipe().recipeType.registryName);
        }
        if (token.startsWith("catalyst:")) {
            String value = token.substring(9);
            boolean tag = value.startsWith("#");
            Predicate<AEKey> matches = tag ? tagPredicate(value.substring(1)) : keyPredicate(value);
            return candidate -> candidate.recipe().nonConsumedInputs().stream().flatMap(List::stream)
                    .anyMatch(matches);
        }
        if (token.startsWith("circuit:")) {
            String value = token.substring(8);
            int circuit = value.equals("none") ? -1 : Integer.parseInt(value);
            return candidate -> candidate.recipe().circuit() == circuit;
        }
        if (token.startsWith("material:")) {
            var material = idPredicate(token.substring(9));
            return candidate -> material.test(candidate.materialId());
        }
        if (token.startsWith("flag:")) {
            MaterialFlag flag = MaterialFlag.getByName(token.substring(5));
            return flag == null ? null : candidate -> candidate.hasMaterial(material -> material.hasFlag(flag));
        }
        if (token.startsWith("prop:")) {
            String name = token.substring(5);
            return switch (name) {
                case "element" -> candidate -> candidate.hasMaterial(Material::isElement);
                case "compound" -> candidate -> candidate.hasMaterial(material -> material.getMaterialComponents().size() > 1);
                case "radioactive" -> candidate -> candidate.hasMaterial(Material::isRadioactive);
                default -> {
                    var property = PROPERTIES.get(name);
                    yield property == null ? null : candidate -> candidate.hasMaterial(material -> material.hasProperty(property));
                }
            };
        }
        if(token.startsWith("source:")) {
            String[] parts=token.split(":",5);
            if(parts.length!=5||!(parts[1].equals("direct")||parts[1].equals("other")))return null;
            String form=parts[2]+":"+parts[3];
            if(!Set.of("item","fluid","itemkey","fluidkey").contains(parts[2]))return null;
            var type=processId(parts[4]);
            if(GTRegistries.RECIPE_TYPES.get(type)==null&&!BuiltInRegistries.RECIPE_TYPE.containsKey(type))return null;
            var forms=productionForms(level,type);
            boolean other=parts[1].equals("other");
            return candidate -> SourceForms.matches(forms.getOrDefault(candidate.material()==null?candidate.materialId():candidate.material().getResourceLocation(),Set.of()),form,other);
        }
        if (token.startsWith("process:") || token.startsWith("use:")) {
            boolean inputs = token.startsWith("use:");
            var type = processId(token.substring(inputs ? 4 : 8));
            if (GTRegistries.RECIPE_TYPES.get(type) == null && !BuiltInRegistries.RECIPE_TYPE.containsKey(type)) return null;
            var materials = materialProcess(level, new Process(type, inputs));
            return candidate -> materials.contains(candidate.materialId()) ||
                    candidate.hasMaterial(material -> materials.contains(material.getResourceLocation()));
        }
        var temperature = TEMPERATURE.matcher(token);
        if (temperature.matches()) {
            int degrees = Integer.parseInt(temperature.group(2));
            String operation = temperature.group(1);
            return candidate -> candidate.hasMaterial(material -> compare(material.getBlastTemperature(), operation, degrees));
        }
        var limit = RECIPE_LIMIT.matcher(token);
        if (limit.matches()) {
            boolean energy = limit.group(1).equals("eut");
            String operation = limit.group(2);
            long amount = Long.parseLong(limit.group(3));
            return candidate -> compare(energy ? recipeEnergy(candidate.recipe().recipe()) :
                    candidate.recipe().recipe().duration, operation, amount);
        }
        if (isGlob(token) && ID_GLOB.matcher(token).matches()) {
            var pattern = keyPredicate(token);
            return candidate -> candidate.matchesSample(pattern);
        }
        return null;
    }

    public static List<String> sampleAttributes(AEKey key, Level level, Collection<Candidate> candidates, boolean forRecipes) {
        var attributes = new LinkedHashSet<String>();
        String role = forRecipes ? "recipe_io:" : "sample:";
        attributes.add(role + key.getId());
        var eligible = candidates.stream().filter(candidate -> forRecipes ? candidate.hasRecipeSample(key) : candidate.matchesSample(key::equals)).toList();
        if (forRecipes) {
            eligible.stream().map(candidate -> "recipe:" + candidate.recipe().recipe().recipeType.registryName)
                    .sorted().forEach(attributes::add);
        }
        if (key instanceof AEItemKey) {
            BuiltInRegistries.ITEM.getTagNames().filter(key::isTagged).map(tag -> role + "#" + tag.location()).sorted().forEach(attributes::add);
        } else if (key instanceof AEFluidKey) {
            BuiltInRegistries.FLUID.getTagNames().filter(key::isTagged).map(tag -> role + "#" + tag.location()).sorted().forEach(attributes::add);
        }
        Material material = UniversalPatternFamily.materialOf(key);
        if (material != null) {
            PROPERTIES.keySet().stream().sorted().filter(name -> material.hasProperty(PROPERTIES.get(name)))
                    .map(name -> "prop:" + name).forEach(attributes::add);
            if (material.isElement()) attributes.add("prop:element");
            if (material.getMaterialComponents().size() > 1) attributes.add("prop:compound");
            if (material.isRadioactive()) attributes.add("prop:radioactive");
            if (ModList.get().isLoaded("wildcard_pattern")) MaterialFlagsFromWildcard.add(material, attributes);
        }
        var materialId = material == null ? key.getId() : material.getResourceLocation();
        for (String name : availableProcesses()) {
            var type = processId(name);
            if (GTRegistries.RECIPE_TYPES.get(type) == null && !BuiltInRegistries.RECIPE_TYPE.containsKey(type)) continue;
            if (materialProcess(level, new Process(type, true)).contains(materialId)) attributes.add("use:" + name);
            var forms=productionForms(level,type).getOrDefault(materialId,Set.of());
            String form=SourceForms.form(key);
            if(SourceForms.matches(forms,form,false))attributes.add("source:direct:"+form+":"+name);
            if(material!=null&&SourceForms.matches(forms,form,true))attributes.add("source:other:"+form+":"+name);
        }
        if (!eligible.isEmpty()) {
            var routes = new TreeSet<>(RegisteredProcessFamilies.lineNames());
            Collections.addAll(routes, "petrochemical", "magnetization", "demagnetization");
            for (String route : routes) {
                String term = "line:" + route;
                if (eligible.stream().anyMatch(condition(term, level))) attributes.add(term);
            }
            for (String term : List.of("product:metal", "product:petrochemical", "product:plasma_cell", "product:purified_water",
                    "input:plasma", "product:plasma", "input:molten", "product:molten", "input:magnetizable", "product:magnetized")) {
                if (eligible.stream().anyMatch(condition(term, level))) attributes.add(term);
            }
        }
        return List.copyOf(attributes);
    }

    private static final class MaterialFlagsFromWildcard {
        private static void add(Material material, Set<String> attributes) {
            if (material instanceof org.leodreamer.wildcard_pattern.api.IMaterial flags) {
                flags.wildcard$getFlags().stream().map(flag -> "flag:" + flag).sorted().forEach(attributes::add);
            }
        }
    }

    private static boolean isGlob(String value) {
        return value.indexOf('*') >= 0 || value.indexOf('?') >= 0;
    }

    private static Predicate<AEKey> keyPredicate(String expression) {
        var ids = idPredicate(expression);
        return key -> ids.test(key.getId());
    }

    private static Predicate<ResourceLocation> idPredicate(String expression) {
        if (!isGlob(expression)) return id(expression)::equals;
        if (!ID_GLOB.matcher(expression).matches()) throw new IllegalArgumentException(expression);
        boolean fullId = expression.indexOf(':') >= 0;
        return key -> matchesGlob(expression, fullId ? key.toString() : key.getPath());
    }

    private static boolean matchesGlob(String pattern, String value) {
        int rule = 0, text = 0, star = -1, retry = 0;
        while (text < value.length()) {
            char part = rule < pattern.length() ? pattern.charAt(rule) : 0;
            if (part == '?' || part == value.charAt(text)) {
                rule++;
                text++;
            } else if (part == '*') {
                star = rule++;
                retry = text;
            } else if (star >= 0) {
                rule = star + 1;
                text = ++retry;
            } else return false;
        }
        while (rule < pattern.length() && pattern.charAt(rule) == '*') rule++;
        return rule == pattern.length();
    }

    private static Predicate<AEKey> tagPredicate(String expression) {
        if (!isGlob(expression)) {
            var tag = id(expression);
            return key -> hasTag(key, tag);
        }
        var match = idPredicate(expression);
        var itemTags = BuiltInRegistries.ITEM.getTagNames().filter(tag -> match.test(tag.location())).toList();
        var fluidTags = BuiltInRegistries.FLUID.getTagNames().filter(tag -> match.test(tag.location())).toList();
        return key -> key instanceof AEItemKey && itemTags.stream().anyMatch(key::isTagged) ||
                key instanceof AEFluidKey && fluidTags.stream().anyMatch(key::isTagged);
    }

    private static boolean anyInput(Candidate candidate, Predicate<AEKey> condition) {
        return candidate.recipe().inputSlots().stream().flatMap(slot -> slot.alternatives().stream()).anyMatch(condition);
    }

    private static boolean isMagneticItem(AEKey key) {
        if (!(key instanceof AEItemKey)) return false;
        Material material = UniversalPatternFamily.materialOf(key);
        return material != null && material.hasFlag(MaterialFlags.IS_MAGNETIC);
    }

    private static boolean compare(long actual, String operation, long desired) {
        return switch (operation) {
            case ">=" -> actual >= desired;
            case "<=" -> actual <= desired;
            case ">" -> actual > desired;
            case "<" -> actual < desired;
            default -> actual == desired;
        };
    }

    private static long recipeEnergy(GTRecipe recipe) {
        var eu = EURecipeCapability.CAP;
        return recipe.getTickInputContents(eu).stream().mapToLong(content -> eu.of(content.content)).sum();
    }

    private static ResourceLocation id(String text) {
        var id = text.isEmpty() ? null : ResourceLocation.tryParse(text);
        if (id == null) throw new IllegalArgumentException(text);
        return id;
    }

    // Preserve persisted short aliases; discover every additional registered GT process.
    private static List<String> availableProcesses() {
        var names = new LinkedHashMap<ResourceLocation,String>();
        for (String name : SAMPLE_PROCESSES) names.putIfAbsent(processId(name),name);
        GTRegistries.RECIPE_TYPES.values().stream().map(type -> type.registryName).sorted()
                .forEach(type -> names.putIfAbsent(type,type.toString()));
        return List.copyOf(names.values());
    }

    private static ResourceLocation processId(String name) {
        return switch (name) {
            case "mixer", "mixing" -> GTRecipeTypes.MIXER_RECIPES.registryName;
            case "washing", "ore_washer" -> GTRecipeTypes.ORE_WASHER_RECIPES.registryName;
            case "blast_furnace", "electric_blast_furnace" -> GTRecipeTypes.BLAST_RECIPES.registryName;
            case "alloy_blast", "alloy_blast_smelter" -> GCyMRecipeTypes.ALLOY_BLAST_RECIPES.registryName;
            case "petrochemical_plant" -> org.gtlcore.gtlcore.common.data.GTLRecipeTypes.PETROCHEMICAL_PLANT_RECIPES.registryName;
            case "fuel_refining" -> org.gtlcore.gtlcore.common.data.GTLRecipeTypes.FUEL_REFINING_RECIPES.registryName;
            case "smelting", "blasting", "smoking" -> id("minecraft:" + name);
            default -> id(name.contains(":") ? name : "gtceu:" + name);
        };
    }

    private static boolean hasTag(AEKey key, ResourceLocation tag) {
        if (key instanceof AEItemKey) return key.isTagged(TagKey.create(Registries.ITEM, tag));
        return key instanceof AEFluidKey && key.isTagged(TagKey.create(Registries.FLUID, tag));
    }

    private record Process(ResourceLocation type, boolean inputs) {}
    private record ProcessCache(long generation, Map<Process, Set<ResourceLocation>> materials,
                                Map<Boolean, Set<AEKey>> polarizerKeys, Map<ResourceLocation,Map<ResourceLocation,Set<String>>> forms) {}

    private static synchronized Set<ResourceLocation> materialProcess(Level level, Process process) {
        var cache=processCache(level).materials();
        if(!cache.containsKey(process)) {
            var type=GTRegistries.RECIPE_TYPES.get(process.type());
            if(type!=null) {
                var inputs=new HashSet<ResourceLocation>();var outputs=new HashSet<ResourceLocation>();
                var forms=new HashMap<ResourceLocation,Set<String>>();
                for(var recipe:MERecipePatternHelper.getRecipes(level.getRecipeManager(),Set.of(type))) {
                    visitKeys(recipe,true,key->addMaterial(key,inputs));
                    visitKeys(recipe,false,key->{addMaterial(key,outputs);SourceForms.add(forms,key);});
                }
                cache.put(new Process(process.type(),true),Set.copyOf(inputs));
                cache.put(new Process(process.type(),false),Set.copyOf(outputs));
                processCache(level).forms().put(process.type(),SourceForms.freeze(forms));
            } else cache.put(process,index(level,process));
        }
        return cache.get(process);
    }

    private static synchronized Map<ResourceLocation,Set<String>> productionForms(Level level,ResourceLocation type) {
        materialProcess(level,new Process(type,false));
        return processCache(level).forms().computeIfAbsent(type,ignored->{
            var forms=new HashMap<ResourceLocation,Set<String>>();
            var vanilla=BuiltInRegistries.RECIPE_TYPE.get(type);
            for(var recipe:level.getRecipeManager().getRecipes())if(recipe.getType()==vanilla)
                SourceForms.add(forms,AEItemKey.of(recipe.getResultItem(level.registryAccess())));
            return SourceForms.freeze(forms);
        });
    }

    private static synchronized Set<AEKey> indexedPolarizerKeys(Level level, boolean inputs) {
        return processCache(level).polarizerKeys().computeIfAbsent(inputs, readInputs -> {
            var keys = new HashSet<AEKey>();
            for (GTRecipe recipe : level.getRecipeManager().getAllRecipesFor(GTRecipeTypes.POLARIZER_RECIPES)) {
                visitKeys(recipe, readInputs, keys::add);
            }
            return Set.copyOf(keys);
        });
    }

    private static ProcessCache processCache(Level level) {
        RecipeManager manager = level.getRecipeManager();
        long generation = MERecipePatternHelper.getReloadGeneration();
        var cache = PROCESS_CACHE.get(manager);
        if (cache == null || cache.generation() != generation) {
            cache = new ProcessCache(generation, new HashMap<>(), new HashMap<>(), new HashMap<>());
            PROCESS_CACHE.put(manager, cache);
        }
        return cache;
    }

    private static Set<ResourceLocation> index(Level level, Process process) {
        var materials = new HashSet<ResourceLocation>();
        var manager = level.getRecipeManager();
        var type = GTRegistries.RECIPE_TYPES.get(process.type());
        if (type != null) {
            for (GTRecipe recipe : MERecipePatternHelper.getRecipes(manager,Set.of(type))) {
                visitKeys(recipe, process.inputs(), key -> addMaterial(key, materials));
            }
        } else {
            var vanilla = BuiltInRegistries.RECIPE_TYPE.get(process.type());
            for (var recipe : manager.getRecipes()) {
                if (recipe.getType() != vanilla) continue;
                if (process.inputs()) recipe.getIngredients().forEach(ingredient -> visitItems(ingredient, key -> addMaterial(key, materials)));
                else addMaterial(AEItemKey.of(recipe.getResultItem(level.registryAccess())), materials);
            }
        }
        return Set.copyOf(materials);
    }

    private static void visitKeys(GTRecipe recipe, boolean inputs, Consumer<AEKey> consumer) {
        var item = ItemRecipeCapability.CAP;
        var fluid = FluidRecipeCapability.CAP;
        for (var list : inputs ? List.of(recipe.getInputContents(item), recipe.getTickInputContents(item))
                : List.of(recipe.getOutputContents(item), recipe.getTickOutputContents(item))) {
            for (var content : list) if (content.chance > 0) visitItems(item.of(content.content), consumer);
        }
        for (var list : inputs ? List.of(recipe.getInputContents(fluid), recipe.getTickInputContents(fluid))
                : List.of(recipe.getOutputContents(fluid), recipe.getTickOutputContents(fluid))) {
            for (var content : list) {
                if (content.chance <= 0) continue;
                for (var stack : fluid.of(content.content).getStacks()) {
                    if (!stack.isEmpty()) consumer.accept(AEFluidKey.of(stack.getFluid(), stack.getTag()));
                }
            }
        }
    }

    private static void visitItems(Ingredient ingredient, Consumer<AEKey> consumer) {
        while (true) {
            if (ingredient instanceof SizedIngredient sized) ingredient = sized.getInner();
            else if (ingredient instanceof IntProviderIngredient provider) ingredient = provider.getInner();
            else break;
        }
        for (ItemStack stack : ingredient.getItems()) {
            AEItemKey key = AEItemKey.of(stack);
            if (key != null) consumer.accept(key);
        }
    }

    private static void addMaterial(AEKey key, Set<ResourceLocation> materials) {
        if (key == null) return;
        Material material = UniversalPatternFamily.materialOf(key);
        materials.add(material == null ? key.getId() : material.getResourceLocation());
    }
}
