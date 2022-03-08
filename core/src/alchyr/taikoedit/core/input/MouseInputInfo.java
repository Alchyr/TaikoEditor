package alchyr.taikoedit.core.input;

import alchyr.taikoedit.util.interfaces.functional.TriFunction;
import com.badlogic.gdx.math.Vector2;

import java.util.function.BiFunction;

public class MouseInputInfo {
    public TriFunction<Integer, Integer, Integer, Boolean> condition;
    public BiFunction<Vector2, Integer, MouseHoldObject> onPress;

    public MouseInputInfo(TriFunction<Integer, Integer, Integer, Boolean> isValidClick, BiFunction<Vector2, Integer, MouseHoldObject> onPress) {
        this.condition = isValidClick;
        this.onPress = onPress;
    }
}
