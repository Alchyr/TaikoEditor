package alchyr.taikoedit.core.input;

import alchyr.taikoedit.util.interfaces.functional.TriFunction;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import com.badlogic.gdx.math.Vector2;

import java.util.Collection;
import java.util.function.Supplier;

public class MouseInputInfo<T> {
    public TriFunction<Integer, Integer, Integer, T> condition;
    public TriFunction<T, Vector2, Integer, MouseHoldObject> onPress;

    public MouseInputInfo(TriFunction<Integer, Integer, Integer, T> isValidClick, TriFunction<T, Vector2, Integer, MouseHoldObject> onPress) {
        this.condition = isValidClick;
        this.onPress = onPress;
    }

    public Supplier<MouseHoldObject> getActionIfValid(int gameX, int gameY, int button) {
        T o = condition.apply(gameX, gameY, button);
        if (o != null) {
            return ()->onPress.apply(o, new Vector2(gameX, gameY), button);
        }
        return null;
    }
}
