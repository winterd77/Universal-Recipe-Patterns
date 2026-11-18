from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
sources = root / "universal-patterns/src/main/java/org/gtlcore/universalpatterns"
families = (sources / "integration/ae2/handler/RegisteredProcessFamilies.java").read_text(encoding="utf-8")


def section(source, start, stop):
    offset = source.index(start)
    return source[offset:source.index(stop, offset)]


methods = "".join([
    section(families, "    private static final String CASTING_STAGE", "    private RegisteredProcessFamilies()"),
    section(families, "    private static @Nullable Candidate describeCasting(", "    static @Nullable String powderBlendProfile("),
    section(families, "    private static Candidate group(", "    private static @Nullable AEKey purifiedOutput("),
    section(families, "    private static String signature(RecipePattern recipe)", "    private static @Nullable String declaredTag("),
    section(families, "    private static String inputForm(", "    private static String type("),
]).replace("@Nullable ", "").replace("MERecipePatternHelper.InputSlot", "InputSlot")

fixture = r"""
import java.util.*;

public class CastingFamilyFixture {
    record ResourceLocation(String namespace, String path) {
        String getNamespace() { return namespace; }
        String getPath() { return path; }
        public String toString() { return namespace + ":" + path; }
    }
    record Material(String id) {}
    record Stack(boolean hasTag) {}
    interface AEKey {
        ResourceLocation getId();
        Material material();
        String form();
        default boolean isTagged(Object tag) { return false; }
    }
    record AEItemKey(ResourceLocation getId, Material material, String form, Stack getReadOnlyStack) implements AEKey {}
    record AEFluidKey(ResourceLocation getId, Material material, String form) implements AEKey {}
    record InputSlot(AEKey defaultKey, List<AEKey> alternatives, long amount) {}
    record GenericStack(AEKey what) {}
    record Pattern(GenericStack[] getOutputs) {}
    record Type(ResourceLocation registryName) {}
    static class GTRecipe {
        final Type recipeType = new Type(new ResourceLocation("gtceu", "fluid_solidifier"));
    }
    record RecipePattern(GTRecipe recipe, Pattern pattern, List<InputSlot> inputSlots,
                         List<List<AEKey>> nonConsumedInputs, int circuit) {}
    record Candidate(String family, Material material, List<AEKey> inputs, AEKey output,
                     RecipePattern recipe, AEKey selector) {}
    static class UniversalPatternFamily {
        static Material materialOf(AEKey key) { return key.material(); }
        static String formOf(AEKey key) { return key.form(); }
    }
    static String declaredTag(RecipePattern recipe, InputSlot slot) { return null; }
    static boolean isPhase(AEKey key, String phase) { return ("fluid:" + phase).equals(key.form()); }
    static boolean filledCell(AEKey key) { return false; }
    static class ItemTags { static final Object LOGS = new Object(); }

    __METHODS__

    static ResourceLocation id(String path) { return new ResourceLocation("gtceu", path); }
    static AEItemKey mold(String shape) { return new AEItemKey(id(shape + "_casting_mold"), null, null, new Stack(false)); }

    static RecipePattern recipe(String materialName, String phase, String shape, AEKey tool, long amount) {
        var material = new Material(materialName);
        var fluid = new AEFluidKey(id(materialName), material, "fluid:" + phase);
        var output = new AEItemKey(id(materialName + "_" + shape), material, "item:" + shape, new Stack(false));
        return new RecipePattern(new GTRecipe(), new Pattern(new GenericStack[] {new GenericStack(output)}),
                List.of(new InputSlot(fluid, List.of(fluid), amount)), List.of(List.of(tool)), -1);
    }

    static Candidate casting(RecipePattern recipe) {
        return describeCasting(recipe, recipe.pattern().getOutputs()[0].what());
    }

    static void check(boolean expected, String message) {
        if (!expected) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var rubber = recipe("rubber", "liquid", "plate", mold("plate"), 144);
        var silicone = recipe("silicone_rubber", "liquid", "plate", mold("plate"), 144);
        var styrene = recipe("styrene_butadiene_rubber", "liquid", "plate", mold("plate"), 144);
        var polyethylene = recipe("polyethylene", "liquid", "plate", mold("plate"), 144);
        var copper = recipe("copper", "molten", "plate", mold("plate"), 288);
        var anchor = casting(rubber);
        check(anchor != null, "rubber plate casting recognized");
        for (var recipe : List.of(rubber, silicone, styrene, polyethylene, copper)) {
            var candidate = casting(recipe);
            check(candidate.family().equals(anchor.family()), "same mold and form should join");
            check(candidate.recipe() == recipe && candidate.inputs().getFirst().equals(recipe.inputSlots().getFirst().defaultKey())
                    && candidate.selector().equals(recipe.pattern().getOutputs()[0].what()), "candidate retains actual input, output and recipe");
        }
        check(casting(copper).recipe().inputSlots().getFirst().amount() == 288, "actual quantity retained");
        check(!casting(recipe("silicone_rubber", "liquid", "plate", mold("custom"), 144)).family().equals(anchor.family()),
                "different mold stays separate");
        check(!casting(recipe("rubber", "liquid", "ingot", mold("plate"), 144)).family().equals(anchor.family()),
                "different output form stays separate");
        check(casting(recipe("rubber", "gas", "plate", mold("plate"), 144)) == null, "gas is not a casting feed");
        check(casting(new RecipePattern(rubber.recipe(), polyethylene.pattern(), rubber.inputSlots(),
                rubber.nonConsumedInputs(), -1)) == null, "different feed and output materials stay separate");
        check(casting(new RecipePattern(rubber.recipe(), rubber.pattern(),
                List.of(rubber.inputSlots().getFirst(), copper.inputSlots().getFirst()), rubber.nonConsumedInputs(), -1)) == null,
                "requiring another consumed material stays separate");
        check(casting(new RecipePattern(rubber.recipe(), rubber.pattern(), rubber.inputSlots(), List.of(), -1)) == null,
                "no mold stays separate");

        String oldFamily = "gtceu:fluid_solidifier|stage:casting,fluidid:gtceu:rubber|item:plate";
        var legacy = legacyCasting(anchor, oldFamily);
        check(legacy != null && legacy.family().equals(oldFamily) && legacy.selector().equals(anchor.selector()),
                "old saved card still matches its original recipe and material identity");
        check(legacyCasting(casting(silicone), oldFamily) == null, "old saved card does not gain another fluid");
        check(legacyCasting(anchor, anchor.family()) == null, "a shared family does not offer migration again");
        System.out.println("PASS: real casting classifier, same-mold expansion, actual IO, and exact legacy family compatibility.");
    }
}
""".replace("__METHODS__", methods)

jdk = Path("C:/Program Files/Java/jdk-22/bin")
with tempfile.TemporaryDirectory() as directory:
    source = Path(directory) / "CastingFamilyFixture.java"
    source.write_text(fixture, encoding="utf-8")
    subprocess.run([jdk / "java.exe", source], check=True)
