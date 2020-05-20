package alchyr.taikoedit.entities.components;

import alchyr.taikoedit.core.input.entityinput.EntityInput;
import com.badlogic.ashley.core.Component;

public class InputComponent implements Component {
    private EntityInput input;

    public InputComponent(EntityInput input)
    {
        this.input = input;
    }

    public EntityInput getInput() {
        return input;
    }
}
