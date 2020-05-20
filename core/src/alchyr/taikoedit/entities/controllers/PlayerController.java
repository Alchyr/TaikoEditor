package alchyr.taikoedit.entities.controllers;

import alchyr.taikoedit.entities.ControllableEntity;
import alchyr.taikoedit.entities.Player;
import com.badlogic.gdx.math.MathUtils;

import java.util.*;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class PlayerController extends Controller {
    //private static final float MIN_SPEED_CHANGE = 0.5f;
    //private static final float NEG_MIN_SPEED_CHANGE = -MIN_SPEED_CHANGE;

    public float xTarget, yTarget; //From 0 to 1, based on intended direction of movement based on input.
    public float speed; //multiplied by x and y modifier to get actual rate of movement

    public ArrayList<InputDirection> keyboardDirections;

    public EnumSet<InputDirection> removeAfter;

    public enum InputDirection
    {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    public PlayerController()
    {
        xTarget = 0;
        yTarget = 0;
        speed = 0;
        keyboardDirections = new ArrayList<>();
        removeAfter = EnumSet.noneOf(InputDirection.class);
    }

    @Override
    public void setEntity(ControllableEntity controllableEntity) {
        if (controllableEntity instanceof Player)
            super.setEntity(controllableEntity);
        else
        {
            editorLogger.error("Attempted to give a PlayerController a non-player entity to control.");
        }
    }

    @Override
    public void update(float elapsed) {
        xTarget = 0;
        yTarget = 0;

        boolean horizontalInput = false;
        boolean verticalInput = false;

        float direction = 0;

        for (int i = keyboardDirections.size() - 1; i >= 0; --i)
        {
            InputDirection d = keyboardDirections.get(i);

            switch (d)
            {
                case RIGHT:
                    if (!horizontalInput)
                    {
                        if (verticalInput)
                        {
                            direction /= 2;
                        }
                    }
                    horizontalInput = true;
                    break;
                case LEFT:
                    if (!horizontalInput)
                    {
                        if (!verticalInput)
                        {
                            direction = 180;
                        }
                        else
                        {
                            direction *= 1.5;
                        }
                    }
                    horizontalInput = true;
                    break;
                case UP:
                    if (!verticalInput)
                    {
                        direction += 90;
                        if (horizontalInput)
                        {
                            direction /= 2;
                        }
                    }
                    verticalInput = true;
                    break;
                case DOWN:
                    if (!verticalInput)
                    {
                        if (horizontalInput)
                            direction *= -1;
                        direction += -90;
                        if (horizontalInput)
                            direction /= 2;
                    }
                    verticalInput = true;
                    break;
            }
        }

        keyboardDirections.removeAll(removeAfter);
        removeAfter.clear();

        if (horizontalInput || verticalInput)
        {
            xTarget = MathUtils.cosDeg(direction);
            yTarget = MathUtils.sinDeg(direction);
        }

        for (ControllableEntity e : entities)
        {
            float difference = xTarget - e.getVelocity().x;

            e.getVelocity().x += difference * Math.min(1.0f, elapsed * (horizontalInput ? 16.0f : 8.0f));

            difference = yTarget - e.getVelocity().y;

            e.getVelocity().y += difference * Math.min(1.0f, elapsed * (verticalInput ? 16.0f : 8.0f));

            e.getPosition().x += e.getVelocity().x * speed * elapsed;
            e.getPosition().y += e.getVelocity().y * speed * elapsed;
        }
    }
}
