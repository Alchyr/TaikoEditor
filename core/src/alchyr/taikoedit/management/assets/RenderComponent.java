package alchyr.taikoedit.management.assets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;

import java.util.HashMap;

//A renderable object which can be chained and is center-based.
public class RenderComponent<T> {
    private static final HashMap<Class<?>, Renderer<?>> renderers;

    private static <U> void registerRenderer(Class<U> clazz, Renderer<U> renderer) {
        renderers.put(clazz, renderer);
    }
    static {
        renderers = new HashMap<>();
        registerRenderer(Texture.class, new TextureRenderer());
    }

    private final T obj;
    private final Renderer<T> renderer;

    private float[] floatData = null;
    private int[] intData = null;

    private RenderComponent<?> child = null;

    @SuppressWarnings("unchecked")
    public RenderComponent(T obj, Class<T> clazz) {
        this.obj = obj;
        this.renderer = (Renderer<T>) renderers.get(clazz);
        this.renderer.prep(this);
    }
    public RenderComponent(T obj, Renderer<T> renderer) {
        this.obj = obj;
        this.renderer = renderer;
        this.renderer.prep(this);
    }

    /*
        Renders something at the given position.
        The color can influence the appearance, but should not be itself modified.
     */
    public void renderC(SpriteBatch sb, ShapeRenderer sr, float x, float y, float scaleX, float scaleY, float rotation, boolean flipX, boolean flipY, Color c) {
        this.renderer.render(this, sb, sr, x, y, scaleX, scaleY, rotation, flipX, flipY, c);
        if (child != null)
            this.child.renderC(sb, sr, x, y, scaleX, scaleY, rotation, flipX, flipY, c);
    }

    public void renderC(SpriteBatch sb, ShapeRenderer sr, float x, float y, float scaleX, float scaleY, float rotation, Color c) {
        this.renderer.render(this, sb, sr, x, y, scaleX, scaleY, rotation, false, false, c);
        if (child != null)
            this.child.renderC(sb, sr, x, y, scaleX, scaleY, rotation, c);
    }

    public void renderC(SpriteBatch sb, ShapeRenderer sr, float x, float y, float scale, float rotation, Color c) {
        this.renderer.render(this, sb, sr, x, y, scale, scale, rotation, false, false, c);
        if (child != null)
            this.child.renderC(sb, sr, x, y, scale, rotation, c);
    }

    public void renderC(SpriteBatch sb, ShapeRenderer sr, float x, float y, float scale, Color c) {
        this.renderer.render(this, sb, sr, x, y, scale, scale, 0, false, false, c);
        if (child != null)
            this.child.renderC(sb, sr, x, y, scale, c);
    }

    public void renderC(SpriteBatch sb, ShapeRenderer sr, float x, float y, float scale) {
        this.renderer.render(this, sb, sr, x, y, scale, scale, 0, false, false, Color.WHITE);
        if (child != null)
            this.child.renderC(sb, sr, x, y, scale);
    }

    public void renderC(SpriteBatch sb, ShapeRenderer sr, float x, float y, Color c) {
        this.renderer.render(this, sb, sr, x, y, 1, 1, 0, false, false, c);
        if (child != null)
            this.child.renderC(sb, sr, x, y, c);
    }

    public void renderC(SpriteBatch sb, ShapeRenderer sr, float x, float y) {
        renderC(sb, sr, x, y, Color.WHITE);
    }

    public RenderComponent<?> chain(RenderComponent<?> child) {
        if (this.child == null)
            this.child = child;
        else
            this.child.chain(child);
        return this;
    }

    public float getWidth() {
        return renderer.getWidth(obj);
    }
    public float getHeight() {
        return renderer.getHeight(obj);
    }


    private interface Renderer<T> {
        void prep(RenderComponent<T> component);
        void render(RenderComponent<T> component, SpriteBatch sb, ShapeRenderer sr, float x, float y, float scaleX, float scaleY, float rotation, boolean flipX, boolean flipY, Color c);

        float getWidth(T obj);
        float getHeight(T obj);
    }

    /*------------------Renderers------------------*/

    public static class TextureRenderer implements Renderer<Texture> {
        private final int alignment;

        public TextureRenderer() {
            this(Align.center);
        }

        public TextureRenderer(int alignment) {
            this.alignment = alignment;
        }

        @Override
        public void prep(RenderComponent<Texture> component) {
            component.floatData = new float[4];
            if (Align.isLeft(alignment))
                component.floatData[0] = 0;
            else if (Align.isCenterHorizontal(alignment))
                component.floatData[0] = component.obj.getWidth() / 2.0f;
            else
                component.floatData[0] = component.obj.getWidth();

            if (Align.isBottom(alignment))
                component.floatData[1] = 0;
            else if (Align.isCenterVertical(alignment))
                component.floatData[1] = component.obj.getHeight() / 2.0f;
            else
                component.floatData[1] = component.obj.getHeight();

            component.floatData[2] = component.obj.getWidth();
            component.floatData[3] = component.obj.getHeight();

            component.intData = new int[2];
            component.intData[0] = component.obj.getWidth();
            component.intData[1] = component.obj.getHeight();
        }

        @Override
        public void render(RenderComponent<Texture> component, SpriteBatch sb, ShapeRenderer sr, float x, float y, float scaleX, float scaleY, float rotation, boolean flipX, boolean flipY, Color c) {
            sb.setColor(c);
            sb.draw(component.obj, x - component.floatData[0], y - component.floatData[1], component.floatData[0], component.floatData[1],
                    component.floatData[2], component.floatData[3], scaleX, scaleY, rotation,
                    0, 0, component.intData[0], component.intData[1], flipX, flipY);
        }

        @Override
        public float getWidth(Texture obj) {
            return obj.getWidth();
        }

        @Override
        public float getHeight(Texture obj) {
            return obj.getHeight();
        }
    }
    public static class FixedColorTexture extends TextureRenderer {
        private final Color c;
        public FixedColorTexture(Color c) {
            this.c = c;
        }
        public FixedColorTexture(Color c, int align) {
            super(align);
            this.c = c;
        }

        @Override
        public void render(RenderComponent<Texture> component, SpriteBatch sb, ShapeRenderer sr, float x, float y, float scaleX, float scaleY, float rotation, boolean flipX, boolean flipY, Color c) {
            sb.setColor(this.c);
            sb.draw(component.obj, x - component.floatData[0], y - component.floatData[1], component.floatData[0], component.floatData[1],
                    component.floatData[2], component.floatData[3], scaleX, scaleY, rotation,
                    0, 0, component.intData[0], component.intData[1], flipX, flipY);
        }
    }
}
