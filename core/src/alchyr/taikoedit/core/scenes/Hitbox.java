package alchyr.taikoedit.core.scenes;

import com.badlogic.gdx.math.Vector2;

public abstract class Hitbox {
    public abstract void testCollision(Vector2 pos); //test if pos is within hitbox
    public abstract Vector2 getValidPos(Vector2 source, Vector2 pos); //if pos is within hitbox, return a point closer to source outside of hitbox
}
