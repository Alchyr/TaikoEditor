package alchyr.taikoedit.entities.components;

import alchyr.taikoedit.entities.controllers.Controller;
import com.badlogic.ashley.core.Component;

public class ControllerComponent implements Component {
    private Controller controller;

    public ControllerComponent(Controller c)
    {
        this.controller = c;
    }

    public Controller getController() {
        return controller;
    }

    public void update(float deltaTime)
    {
        controller.update(deltaTime);
    }
}
