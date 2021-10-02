package alchyr.taikoedit.core.input.sub;

import com.badlogic.gdx.graphics.g2d.BitmapFont;

import java.util.function.Function;

public interface TextInputReceiver {
    int getCharLimit();
    String getInitialText();
    void setText(String newText);
    boolean blockInput(int key);
    default boolean acceptCharacter(char c) {
        return true;
    }

    BitmapFont getFont();
    Function<String, Boolean> onPressEnter();
}
