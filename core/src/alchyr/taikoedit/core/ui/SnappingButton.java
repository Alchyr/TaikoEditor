package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.editor.DivisorOptions;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class SnappingButton extends ImageButton implements DivisorOptions.IDivisorListener {
    private static final Color DEFAULT_COLOR = Color.WHITE.cpy();
    private static final Color HOVER_COLOR = new Color(200f/255f, 186f/255f, 1f, 1f);

    private final DivisorOptions divisorOptions;

    public SnappingButton(int x, int y, DivisorOptions divisorOptions, TextOverlay display) {
        super(x, y, (Texture) assetMaster.get("editor:snapping"), (Texture) assetMaster.get("editor:snapping"));

        this.divisorOptions = divisorOptions;
        divisorOptions.addDependent(this);

        setClick((i)->{
            if (i == Input.Buttons.LEFT) {
                int snap = divisorOptions.getCurrentSnapping();
                divisorOptions.adjust(-1, false);
                if (divisorOptions.getCurrentSnapping() == snap) {
                    divisorOptions.set(1);
                }
            }
            else {
                divisorOptions.swapMode();
                //Swap mode
                //All
                //Common
                //Even
                //Swing
                switch (divisorOptions.getMode()) {
                    case ALL:
                        display.setText("All Snaps", 1.5f);
                        break;
                    case COMMON:
                        display.setText("Common Snaps", 1.5f);
                        break;
                    case EVEN:
                        display.setText("Even Snaps", 1.5f);
                        break;
                    case SWING:
                        display.setText("Swing Snaps", 1.5f);
                        break;
                }
            }
        });

        refresh();
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        render(sb, sr, hovered ? HOVER_COLOR : DEFAULT_COLOR);
    }


    @Override
    public void refresh() {
        setText(divisorOptions.toString());
    }
}
