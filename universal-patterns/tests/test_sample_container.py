from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[2]
widget_package = "org.gtlcore.universalpatterns.client.gui.widget"
source_path = root / "universal-patterns/src/main/java" / widget_package.replace(".", "/")
source = (source_path / "RecipePatternSampleWidget.java").read_text(encoding="utf-8")
start = source.index("    public boolean mouseClicked(")
end = source.index("\n    @Override", start)
method = source[start:end]

fixture = """
public class MarkerProbe {
    record AEKey(String kind, String id, String nbt) {}

    static class ItemStack {
        String id, fluid, nbt;
        int count;
        ItemStack(String id, String fluid, String nbt, int count) {
            this.id = id;
            this.fluid = fluid;
            this.nbt = nbt;
            this.count = count;
        }
        boolean isEmpty() { return count == 0; }
        ItemStack copy() { return new ItemStack(id, fluid, nbt, count); }
    }

    record FluidStack(String getFluid, String getTag) {
        static final FluidStack EMPTY = new FluidStack("", "");
        boolean isEmpty() { return getFluid.isEmpty(); }
    }

    static class FluidUtil {
        static java.util.Optional<FluidStack> getFluidContained(ItemStack stack) {
            stack.count = 0;
            return stack.fluid.isEmpty() ? java.util.Optional.empty() :
                    java.util.Optional.of(new FluidStack(stack.fluid, stack.nbt));
        }
    }

    static class AEItemKey {
        static AEKey of(ItemStack stack) { return new AEKey("item", stack.id, stack.nbt); }
    }
    static class AEFluidKey {
        static AEKey of(String fluid, String tag) { return new AEKey("fluid", fluid, tag); }
    }
    static class GLFW {
        static final int GLFW_MOUSE_BUTTON_LEFT = 0, GLFW_MOUSE_BUTTON_RIGHT = 1;
    }
    static class Screen {
        static boolean shift;
        static boolean hasShiftDown() { return shift; }
    }
    static class AbstractContainerMenu {
        ItemStack carried = new ItemStack("", "", "", 0);
        ItemStack getCarried() { return carried; }
    }
    static class ModularUIContainer extends AbstractContainerMenu {}
    static class Gui {
        ModularUIContainer menu = new ModularUIContainer();
        ModularUIContainer getModularUIContainer() { return menu; }
    }

    Gui gui;
    final boolean searchSlot;
    AEKey sample;
    boolean visible = true, active = true;
    int changes;

    MarkerProbe(Gui gui, boolean searchSlot) {
        this.gui = gui;
        this.searchSlot = searchSlot;
    }
    boolean isVisible() { return visible; }
    boolean isActive() { return active; }
    boolean isMouseOverElement(double x, double y) { return x >= 0 && x < 18 && y >= 0 && y < 18; }
    void choose(AEKey key) { sample = key; changes++; }

    __METHOD__

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var gui = new Gui();
        var input = new MarkerProbe(gui, true);
        var output = new MarkerProbe(gui, true);
        var carried = new ItemStack("tank", "water", "tag", 48);
        gui.menu.carried = carried;
        check(input.mouseClicked(1, 1, 0) && input.sample.equals(new AEKey("item", "tank", "tag")),
                "left-click records the real cursor item and its NBT");
        Screen.shift = true;
        check(output.mouseClicked(1, 1, 0) && output.sample.equals(new AEKey("fluid", "water", "tag")),
                "shift-left-click records contained fluid and its NBT");
        Screen.shift = false;
        check(gui.menu.carried == carried && carried.count == 48, "carried stack remains intact");
        check(input.mouseClicked(1, 1, 1) && input.sample == null && output.sample.kind().equals("fluid"),
                "right-click while holding a filled container clears only the clicked slot");
        check(input.mouseClicked(1, 1, 1) && input.changes == 2, "already-empty slot sends no extra search");
        gui.menu.carried = new ItemStack("", "", "", 0);
        check(output.mouseClicked(1, 1, 0) && output.sample.kind().equals("fluid"), "empty cursor cannot pick up the marker");
        check(output.mouseClicked(1, 1, 1) && output.sample == null, "right-click clears with an empty cursor");
        check(!output.mouseClicked(25, 1, 0) && !output.mouseClicked(1, 1, 2), "ignore outside and unrelated clicks");
        output.visible = false;
        check(!output.mouseClicked(1, 1, 1), "hidden slot does not capture clicks");
        output.visible = true;
        output.active = false;
        check(!output.mouseClicked(1, 1, 1), "inactive slot does not capture clicks");

        var existing = new MarkerProbe(gui, false);
        gui.menu.carried = carried;
        check(existing.mouseClicked(1, 1, 1) && existing.sample.kind().equals("fluid"), "other sample widgets retain right-click fluid selection");
        gui.menu.carried = new ItemStack("empty_tank", "", "", 1);
        check(existing.mouseClicked(1, 1, 1) && existing.sample.kind().equals("fluid"), "empty container does not replace the sample");
        gui.menu.carried.count = 0;
        check(existing.mouseClicked(1, 1, 1) && existing.sample == null, "other sample widgets still clear with empty cursor");
        System.out.println("PASS: real cursor item, independent right-click clear, contained fluid and NBT, no inventory mutation.");
    }
}
""".replace("__METHOD__", method)

jdk = Path("C:/Program Files/Java/jdk-22/bin")
with tempfile.TemporaryDirectory() as directory:
    probe = Path(directory) / "MarkerProbe.java"
    probe.write_text(fixture, encoding="utf-8")
    subprocess.run([jdk / "java.exe", probe], check=True)

if len(sys.argv) > 1:
    listing = subprocess.check_output([
        jdk / "javap.exe", "-p", "-c", "-classpath", sys.argv[1], f"{widget_package}.RecipePatternSampleWidget",
    ], text=True)
    assert "AbstractContainerMenu.m_142621_:()Lnet/minecraft/world/item/ItemStack;" in listing
    assert "ModularUIContainer.getCarried:" not in listing
    print("PASS: packaged cursor lookup resolves to the actual Forge runtime method.")
