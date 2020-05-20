package alchyr.taikoedit.core;

import com.badlogic.gdx.InputProcessor;

public interface InputLayer {
    InputProcessor getProcessor(); //should return an instance that is unique to the layer, not a new one each time
}
