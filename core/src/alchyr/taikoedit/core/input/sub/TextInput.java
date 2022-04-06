package alchyr.taikoedit.core.input.sub;

import alchyr.taikoedit.core.input.BindingGroup;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Timer;

import java.util.function.Function;

import static alchyr.taikoedit.TaikoEditor.textRenderer;

//A class for reading key input as typing.
public class TextInput {
    static private final char BACKSPACE = 8;
    static private final char ENTER_DESKTOP = '\r';
    static private final char ENTER_ANDROID = '\n';
    static private final char TAB = '\t';
    static private final char DELETE = 127;
    static private final char BULLET = 149;

    private static char lastTyped = '\n';

    public int cap;

    public String text = "";
    public BitmapFont font;
    public boolean ctrlBackspace;

    private Function<String, Boolean> onPushEnter;

    public TextInput(int cap, BitmapFont font, boolean ctrlBackspace) {
        this.cap = cap;
        this.font = font;
        this.ctrlBackspace = ctrlBackspace;
    }
    public TextInput(int cap, BitmapFont font)
    {
        this(cap, font, false);
    }

    public void render(SpriteBatch sb, float x, float y)
    {
        textRenderer.setFont(font).renderText(sb, text, x, y);
    }

    public void setOnEnter(Function<String, Boolean> onEnter)
    {
        onPushEnter = onEnter;
    }

    public boolean keyTyped(char character)
    {
        // Disallow "typing" most ASCII control characters, which would show up as a space when onlyFontChars is true.
        lastTyped = '\n';
        switch (character) {
            case ENTER_ANDROID:
            case ENTER_DESKTOP:
                if (onPushEnter != null)
                {
                    if (onPushEnter.apply(text))
                        clear();
                    return true;
                }
            case TAB:
            case DELETE:
            case BULLET:
                return false;
            case BACKSPACE:
                break;
            default:
                if (character < 32) return false;
        }

        if (UIUtils.isMac && Gdx.input.isKeyPressed(Input.Keys.SYM)) return false;

        boolean backspace = character == BACKSPACE;
        boolean add = font == null || font.getData().hasGlyph(character);

        if (backspace && text.length() > 1) {
            lastTyped = BACKSPACE;
            if (ctrlBackspace && BindingGroup.ctrl()) {
                int gap = text.lastIndexOf(' ');
                if (gap == -1)
                    text = "";
                else
                    text = text.substring(0, gap);
            }
            else {
                text = text.substring(0, text.length() - 1);
            }
            //scheduleKeyRepeatTask()
            return true;
        }
        else if (backspace)
        {
            lastTyped = BACKSPACE;
            text = "";
            return true;
        }
        if (add) {
            if (text.length() < cap)
            {
                lastTyped = character;
                text = text.concat(String.valueOf(character));
                return true;
            }
        }
        return false;
    }

    public void clear()
    {
        text = "";
    }
}
