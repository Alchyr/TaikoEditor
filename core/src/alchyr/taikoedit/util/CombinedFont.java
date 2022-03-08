package alchyr.taikoedit.util;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;

import java.util.List;

public class CombinedFont extends BitmapFont {
    private List<BitmapFont> subFonts;

    @Override
    public void dispose() {
        super.dispose();
        for (BitmapFont f : subFonts)
            f.dispose();
    }

    @Override
    public BitmapFontCache newFontCache() {
        return new BitmapFontCache(this, super.usesIntegerPositions());
    }
}
