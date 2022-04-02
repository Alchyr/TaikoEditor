package alchyr.taikoedit.editor.maps.components;

import alchyr.taikoedit.editor.views.EffectView;
import alchyr.taikoedit.util.structures.PositionalObject;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class PreviewLine extends PositionalObject {
    public static final Color c = new Color(0.2f, 0.6f, 0.9f, 1.0f);

    private static final Color selection = new Color(1.0f, 0.6f, 0.0f, 1.0f);

    private static Texture pix;

    public PreviewLine(long pos)
    {
        setPos(pos);
    }

    public static void loadTexture()
    {
        pix = assetMaster.get("ui:pixel");
    }

    @Override
    public String toString()
    {
        return Long.toString(getPos());
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, float alpha) {
        if (selected)
        {
            renderSelection(sb, sr, pos, viewScale, x, y);
        }

        c.a = alpha;
        sb.setColor(c);

        sb.draw(pix, x + (float) (this.getPos() - pos) * viewScale, y, 1, EffectView.HEIGHT);
        c.a = 1;
    }

    @Override
    public void renderSelection(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y) {
        sb.setColor(selection);

        sb.draw(pix, x + (float) (this.getPos() - pos) * viewScale - 1, y, 3, EffectView.HEIGHT);
    }

    @Override
    public PositionalObject shiftedCopy(long newPos) {
        PreviewLine copy = new PreviewLine(this.getPos());
        copy.setPos(newPos);
        return copy;
    }
}