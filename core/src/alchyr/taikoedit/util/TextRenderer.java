package alchyr.taikoedit.util;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.HashMap;

public class TextRenderer {
    private static final GlyphLayout[] layouts = new GlyphLayout[2];
    public static int layoutIndex = 0;
    static {
        for (int i = 0; i < layouts.length; ++i) {
            layouts[i] = new GlyphLayout();
        }
    }
    public static void swapLayouts() {
        layoutIndex = (layoutIndex + 1) % layouts.length;
    }

    private final HashMap<BitmapFont, BitmapFontCache> staticTextCaches;
    private final ArrayList<BitmapFontCache> staticTextCacheList;

    private BitmapFont currentRendering = null;
    private BitmapFontCache currentCache;

    public TextRenderer()
    {
        staticTextCaches = new HashMap<>();
        staticTextCacheList = new ArrayList<>();
    }

    public TextRenderer setFont(BitmapFont font)
    {
        currentRendering = font;
        if (!staticTextCaches.containsKey(font))
        {
            BitmapFontCache newCache = new BitmapFontCache(font);
            staticTextCacheList.add(newCache);
            staticTextCaches.put(font, newCache);
        }

        currentCache = staticTextCaches.get(font);
        return this;
    }
    public TextRenderer setScale(float scale)
    {
        currentRendering.getData().setScale(scale);
        return this;
    }
    public TextRenderer resetScale()
    {
        currentRendering.getData().setScale(1.0f);
        return this;
    }
    public TextRenderer renderText(SpriteBatch sb, String s, float x, float y)
    {
        currentRendering.draw(sb, s, x, y);
        return this;
    }
    public TextRenderer renderText(SpriteBatch sb, String s, float x, float y, Color c)
    {
        sb.setColor(c);
        currentRendering.draw(sb, s, x, y);
        return this;
    }
    public TextRenderer renderTextYCentered(SpriteBatch sb, String s, float x, float y)
    {
        return renderTextYCentered(sb, Color.WHITE, s, x, y);
    }
    public TextRenderer renderTextYCentered(SpriteBatch sb, Color c, String s, float x, float y)
    {
        int index = layoutIndex;
        layouts[index].reset();

        layouts[index].setText(currentRendering, s, c, 1, Align.left, false);

        currentRendering.draw(sb, layouts[index], x, y + layouts[index].height / 2.0f);

        return this;
    }
    public TextRenderer renderTextCentered(SpriteBatch sb, String s, float x, float y)
    {
        return renderTextCentered(sb, s, x, y, Color.WHITE);
    }
    public TextRenderer renderTextCentered(SpriteBatch sb, String s, float x, float y, Color c)
    {
        int index = layoutIndex;

        layouts[index].reset();
        layouts[index].setText(currentRendering, s, c, 1, Align.center, false);

        currentRendering.draw(sb, layouts[index], x, y + layouts[index].height / 2.0f);

        return this;
    }
    public TextRenderer renderTextRightAlign(SpriteBatch sb, String s, float x, float y, Color c)
    {
        int index = layoutIndex;

        layouts[index].reset();
        layouts[index].setText(currentRendering, s, c, 1, Align.right, false);

        currentRendering.draw(sb, layouts[index], x, y);

        return this;
    }

    public void render(SpriteBatch sb)
    {
        for (BitmapFontCache cache : staticTextCacheList)
        {
            cache.draw(sb);
        }
    }

    public float getWidth(String text)
    {
        int index = layoutIndex;
        layouts[index].reset();

        layouts[index].setText(currentRendering, text);

        return layouts[index].width;
    }
    public float getHeight(String text)
    {
        int index = layoutIndex;
        layouts[index].reset();

        layouts[index].setText(currentRendering, text);

        return layouts[index].height;
    }
}