package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.core.UIElement;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

//Contains a set of things that can be rendered and scrolled
public class Page extends TextInputProcessor implements Scrollable {
    private static final float MIN_SCROLL = 0;
    private float baseMaximumScroll = 0, maximumScroll = 0, currentScroll = 0, targetScroll = 0;

    private final List<Label> labels = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private final List<ToggleButton> toggleButtons = new ArrayList<>();
    private final List<DropdownBox<?>> dropdownBoxes = new ArrayList<>();
    private final List<TextField> textFields = new ArrayList<>();
    private final List<UIElement> general = new ArrayList<>();

    private Scrollable subScrollable = null;

    private final Supplier<VoidMethod> exitMethod;

    public Page(Supplier<VoidMethod> exitMethod) {
        super(BindingMaster.getBindingGroupCopy("Basic"), false);

        this.exitMethod = exitMethod;

        bind();
    }

    public void hidden() {
        for (TextField f : textFields)
            f.disable();
        for (DropdownBox<?> d : dropdownBoxes)
            d.cancel();
    }

    public void setMaximumScroll(float maxScroll) {
        this.baseMaximumScroll = maxScroll;
        this.maximumScroll = Math.max(MIN_SCROLL, baseMaximumScroll);
    }
    public void adjustMaximumScroll(float adjust) {
        this.baseMaximumScroll += adjust;
        this.maximumScroll = Math.max(MIN_SCROLL, baseMaximumScroll);
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        scroll(amountY);

        return true;
    }

    public Label addLabel(float x, float y, BitmapFont font, String text) {
        return add(new Label(x, y, font, text));
    }
    public Label add(Label l) {
        labels.add(l);
        return l;
    }
    public TextField add(TextField f) {
        textFields.add(f);
        return f;
    }
    public Button add(Button b) {
        buttons.add(b);
        return b;
    }
    public ToggleButton add(ToggleButton b) {
        toggleButtons.add(b);
        return b;
    }
    public <T> DropdownBox<T> add(DropdownBox<T> d) {
        dropdownBoxes.add(d);
        return d;
    }
    public UIElement addUIElement(UIElement thing) {
        general.add(thing);
        return thing;
    }

    public void remove(Button b) {
        buttons.remove(b);
    }
    public void remove(ToggleButton b) {
        toggleButtons.remove(b);
    }
    public void remove(Label l) {
        labels.remove(l);
    }

    public List<Button> getButtons() {
        return buttons;
    }

    @Override
    public void update(float elapsed) {
        super.update(elapsed);

        for (DropdownBox<?> d : dropdownBoxes)
            d.update(elapsed);

        for (Button b : buttons)
            b.update(elapsed);

        for (ToggleButton b : toggleButtons)
            b.update(elapsed);

        for (TextField f : textFields)
            f.update(elapsed);

        for (UIElement thing : general)
            thing.update(elapsed);

        float scrollChange, scrollLimit = scrollChange = targetScroll - currentScroll;
        scrollChange *= elapsed * 8;
        if (scrollLimit < 0) {
            if (scrollChange < scrollLimit)
                scrollChange = scrollLimit;
        }
        else if (scrollLimit > 0) {
            if (scrollChange > scrollLimit)
                scrollChange = scrollLimit;
        }
        currentScroll += scrollChange;

        if (targetScroll < MIN_SCROLL) {
            targetScroll = Math.min(MIN_SCROLL, targetScroll - ((targetScroll + 10) * elapsed * 8));
        }
        if (targetScroll > maximumScroll) {
            targetScroll = Math.max(maximumScroll, targetScroll - ((targetScroll - maximumScroll) * elapsed * 8));
        }
    }
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        for (UIElement thing : general)
            thing.render(sb, sr, 0, currentScroll);

        for (Label l : labels)
            l.render(sb, sr, 0, currentScroll);

        for (TextField f : textFields)
            f.render(sb, sr, 0, currentScroll);

        for (Button b : buttons)
            b.render(sb, sr, 0, currentScroll);

        for (ToggleButton b : toggleButtons)
            b.render(sb, sr, 0, currentScroll);

        for (DropdownBox<?> d : dropdownBoxes)
            d.render(sb, sr, 0, currentScroll);
    }

    @Override
    public void scroll(float amount) {
        if (subScrollable != null) {
            subScrollable.scroll(amount);
            return;
        }

        if (maximumScroll != MIN_SCROLL) {
            amount *= 3;
            float bonus = 0;
            if (targetScroll != currentScroll) {
                bonus = MathUtils.log2(Math.abs(targetScroll - currentScroll));
                if (bonus < 0) {
                    bonus = 0;
                }
            }
            targetScroll += amount * (25.0f + bonus);
        }
    }
    @Override
    public void bind() {
        if (exitMethod != null)
            bindings.bind("Exit", exitMethod);

        bindings.bind("Up", ()->{
            scroll(-5);
        }, new ScrollKeyHold(this, -20));
        bindings.bind("Down", ()->{
            scroll(5);
        }, new ScrollKeyHold(this, 20));

        bindings.addMouseBind((x, y, b)-> {
                    for (DropdownBox<?> dropdownBox : dropdownBoxes) {
                        if (dropdownBox.tryClick(x, y))
                            return true;
                    }

                    for (TextField f : textFields) {
                        if (f.tryClick(x, y))
                            return true;
                    }

                    for (Button button : buttons) {
                        if (button.contains(x, y))
                            return true;
                    }

                    for (ToggleButton toggle : toggleButtons) {
                        if (toggle.contains(x, y))
                            return true;
                    }
                    return false;
                },
                (p, b)->{
                    boolean clickConsumed = false;
                    subScrollable = null;
                    for (DropdownBox<?> dropdownBox : dropdownBoxes) {
                        if (clickConsumed)
                            dropdownBox.cancel();
                        else if (dropdownBox.click(p.x, p.y)) {
                            if (dropdownBox.isOpen())
                                subScrollable = dropdownBox;
                            clickConsumed = true;
                        }
                    }

                    for (TextField f : textFields) {
                        if (clickConsumed)
                            f.disable();
                        else if (f.click(p.x, p.y, this))
                            clickConsumed = true;
                    }

                    if (clickConsumed)
                        return null;

                    for (Button button : buttons) {
                        if (button.click(p.x, p.y, b))
                            return null;
                    }

                    for (ToggleButton toggle : toggleButtons) {
                        if (toggle.click(p.x, p.y, b))
                            return null;
                    }
                    return null;
                });
    }
}
