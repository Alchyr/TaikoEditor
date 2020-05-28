package alchyr.taikoedit.util.structures;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public abstract class PositionalObject implements Comparable<PositionalObject> {
    public int pos = 0;
    public boolean selected = false;

    //pos - time within song
    //viewScale - just a scaling of object distance
    //x - Where an object with the same time as pos should be
    //y - y
    public abstract void render(SpriteBatch sb, ShapeRenderer sr, int pos, float viewScale, float x, float y, float alpha);
    public void render(SpriteBatch sb, ShapeRenderer sr, int pos, float viewScale, float x, float y) {
        render(sb, sr, pos, viewScale, x, y, 1.0f);
    }
    public abstract void renderSelection(SpriteBatch sb, ShapeRenderer sr, int pos, float viewScale, float x, float y);

    public void setPosition(int newPos) {
        pos = newPos;
    }

    @Override
    public int compareTo(PositionalObject o) {
        return Integer.compare(pos, o.pos);
    }
}
