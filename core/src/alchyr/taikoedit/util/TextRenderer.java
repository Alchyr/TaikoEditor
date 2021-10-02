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
    private static GlyphLayout layout = new GlyphLayout();

    private HashMap<BitmapFont, BitmapFontCache> staticTextCaches;
    private ArrayList<BitmapFontCache> staticTextCacheList;

    private BitmapFont currentRendering;
    private BitmapFontCache currentCache;

    public TextRenderer(BitmapFont initialFont)
    {
        staticTextCaches = new HashMap<>();
        staticTextCacheList = new ArrayList<>();

        currentRendering = initialFont;
        currentCache = new BitmapFontCache(initialFont);

        staticTextCacheList.add(currentCache);
        staticTextCaches.put(currentRendering, currentCache);
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
    public TextRenderer renderText(SpriteBatch sb, Color c, String s, float x, float y)
    {
        sb.setColor(c);
        currentRendering.draw(sb, s, x, y);
        return this;
    }
    public TextRenderer renderTextYCentered(SpriteBatch sb, String s, float x, float y)
    {
        layout.reset();

        layout.setText(currentRendering, s, Color.WHITE.cpy(), 1, Align.left, false);

        currentRendering.draw(sb, layout, x, y + layout.height / 2.0f);

        return this;
    }
    public TextRenderer renderTextCentered(SpriteBatch sb, String s, float x, float y)
    {
        return renderTextCentered(sb, s, x, y, Color.WHITE);
    }
    public TextRenderer renderTextCentered(SpriteBatch sb, String s, float x, float y, Color c)
    {
        layout.reset();

        layout.setText(currentRendering, s, c.cpy(), 1, Align.center, false);

        currentRendering.draw(sb, layout, x, y + layout.height / 2.0f);

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
        layout.setText(currentRendering, text);

        return layout.width;
    }
    public float getHeight(String text)
    {
        layout.setText(currentRendering, text);

        return layout.height;
    }
}
