package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.core.UIElement;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

//Contains a set of things that can be rendered and scrolled
public class Page extends TextInputProcessor {
    private float maximumScroll = 0, currentScroll = 0;

    private List<Label> labels = new ArrayList<>();
    private List<Button> buttons = new ArrayList<>();
    private List<TextField> textFields = new ArrayList<>();
    private List<UIElement> general = new ArrayList<>();

    private final Supplier<VoidMethod> exitMethod;

    public Page(Supplier<VoidMethod> exitMethod) {
        super(BindingMaster.getBindingGroup("Basic"), false);

        this.exitMethod = exitMethod;

        bind();
    }
    @Override
    public void bind() {
        if (exitMethod != null)
            bindings.bind("Exit", exitMethod);

        bindings.addMouseBind((x, y, b)-> {
                    for (Button button : buttons) {
                        if (button.contains(x, y))
                            return true;
                    }

                    for (TextField f : textFields) {
                        if (f.tryClick(x, y))
                            return true;
                    }
                    return false;
                },
                (p, b)->{
                    boolean fieldClicked = false;
                    for (TextField f : textFields) {
                        if (f.click(p.x, p.y, this))
                            fieldClicked = true;
                    }

                    if (fieldClicked)
                        return null;

                    for (Button button : buttons) {
                        if (button.click(p.x, p.y, b))
                            return null;
                    }
                    return null;
                });
    }

    public void addLabel(int x, int y, BitmapFont font, String text) {
        addLabel(new Label(x, y, font, text));
    }
    public void addLabel(Label l) {
        labels.add(l);
    }
    public void addTextField(TextField f) {
        textFields.add(f);
    }
    public void addButton(Button b) {
        buttons.add(b);
    }
    public void addUIElement(UIElement thing) {
        general.add(thing);
    }

    @Override
    public void update(float elapsed) {
        super.update(elapsed);

        for (Button b : buttons)
            b.update(elapsed);

        for (TextField f : textFields)
            f.update(elapsed);

        for (UIElement thing : general)
            thing.update(elapsed);
    }
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        for (Button b : buttons)
            b.render(sb, sr, 0, currentScroll);

        for (Label l : labels)
            l.render(sb, sr, 0, currentScroll);

        for (TextField f : textFields)
            f.render(sb, sr, 0, currentScroll);

        for (UIElement thing : general)
            thing.render(sb, sr, 0, currentScroll);
    }
}
