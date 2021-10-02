package alchyr.taikoedit.editor;

import alchyr.taikoedit.editor.views.ObjectView;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.HashMap;
import java.util.Objects;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class Snap implements Comparable<Snap> {
    private static final HashMap<Integer, Color> divisorColors;
    private static final Color defaultColor = Color.CORAL.cpy();
    static {
        divisorColors = new HashMap<>();
        divisorColors.put(0, Color.WHITE.cpy()); //Barline
        divisorColors.put(1, Color.WHITE.cpy()); //Normal 1/1
        divisorColors.put(2, Color.RED.cpy());
        divisorColors.put(3, Color.PURPLE.cpy());
        divisorColors.put(4, Color.BLUE.cpy());
        divisorColors.put(5, Color.ORANGE.cpy());
        divisorColors.put(6, Color.PURPLE.cpy());
        divisorColors.put(7, Color.MAGENTA.cpy());
        divisorColors.put(8, Color.YELLOW.cpy());
        divisorColors.put(12, Color.LIGHT_GRAY.cpy());
        divisorColors.put(16, Color.VIOLET.cpy());
    }
    private static Color getDivisorColor(int divisor)
    {
        return divisorColors.getOrDefault(divisor, defaultColor);
    }

    private Texture pix;

    public final int divisor; //1 = 1/1, 2 = 1/2, 3 = 1/3, etc.
    public final double pos;

    private int hash;

    public Snap(double pos, int divisor)
    {
        this.pos = pos;
        this.divisor = divisor;
        this.hash = 0;

        pix = assetMaster.get("ui:pixel");
    }

    //DUMMY CONSTRUCTOR
    public Snap(double pos)
    {
        this.pos = pos;
        this.divisor = 0;
    }

    public void render(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, int viewHeight)
    {
        sb.setColor(getDivisorColor(divisor));
        x = x + (float) (this.pos - pos) * viewScale;
        sb.draw(pix, x, y, 1, getHeight(viewHeight));
        if (divisor != 0) //mirrored line on top
            sb.draw(pix, x, y + viewHeight - getHeight(viewHeight), 1, getHeight(viewHeight));
    }

    public void halfRender(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, int max)
    {
        sb.setColor(getDivisorColor(divisor));
        x = x + (float) (this.pos - pos) * viewScale;
        sb.draw(pix, x, y, 1, getHeight(max));
    }

    private float getHeight(float max)
    {
        switch (divisor)
        {
            case 0:
                return max;
            case 1:
                return ObjectView.MEDIUM_HEIGHT;
            default:
                return ObjectView.SMALL_HEIGHT;
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Snap snap = (Snap) o;
        return pos == snap.pos;
    }

    @Override
    public int hashCode() {
        if (hash == 0) //may occur more than once, but the likelihood is low.
            hash = Objects.hash(pos);

        return hash;
    }

    @Override
    public int compareTo(Snap o) {
        return Double.compare(this.pos, o.pos);
    }
}
