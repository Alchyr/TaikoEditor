package alchyr.taikoedit.core.input.entityinput;

import alchyr.taikoedit.entities.controllers.PlayerController;
import alchyr.taikoedit.management.SettingsMaster;

import static alchyr.taikoedit.management.SettingsMaster.KeyBindings.*;
import static alchyr.taikoedit.management.SettingsMaster.KeyBindings.FIRE;

public class PlayerInput extends EntityInput {
    public PlayerController controller;

    public static final float SLOW_PLAYER_RATE = 160.0f;
    public static final float FAST_PLAYER_RATE = 240.0f;

    public PlayerInput(PlayerController c)
    {
        this.controller = c;
        this.controller.speed = SettingsMaster.autoSprint ? FAST_PLAYER_RATE : SLOW_PLAYER_RATE;
    }

    @Override
    public int getInputType() {
        return KEYDOWN | KEYUP | TOUCHDOWN | TOUCHUP;
    }

    @Override
    public boolean keyDown(int keycode) {
        switch (SettingsMaster.KeyBindings.getPlayerBinding(SettingsMaster.KeyBindings.KEYBOARD, keycode))
        {
            case SPEED_MODIFIER:
                controller.speed = SettingsMaster.autoSprint ? SLOW_PLAYER_RATE : FAST_PLAYER_RATE;
                return true;
            case UP:
                controller.removeAfter.remove(PlayerController.InputDirection.UP);
                controller.keyboardDirections.remove(PlayerController.InputDirection.UP);
                controller.keyboardDirections.add(PlayerController.InputDirection.UP);
                return true;
            case DOWN:
                controller.removeAfter.remove(PlayerController.InputDirection.DOWN);
                controller.keyboardDirections.remove(PlayerController.InputDirection.DOWN);
                controller.keyboardDirections.add(PlayerController.InputDirection.DOWN);
                return true;
            case LEFT:
                controller.removeAfter.remove(PlayerController.InputDirection.LEFT);
                controller.keyboardDirections.remove(PlayerController.InputDirection.LEFT);
                controller.keyboardDirections.add(PlayerController.InputDirection.LEFT);
                return true;
            case RIGHT:
                controller.removeAfter.remove(PlayerController.InputDirection.RIGHT);
                controller.keyboardDirections.remove(PlayerController.InputDirection.RIGHT);
                controller.keyboardDirections.add(PlayerController.InputDirection.RIGHT);
                return true;
            case FIRE:

                return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        switch (SettingsMaster.KeyBindings.getPlayerBinding(SettingsMaster.KeyBindings.KEYBOARD, keycode))
        {
            case SPEED_MODIFIER:
                controller.speed = SettingsMaster.autoSprint ? FAST_PLAYER_RATE : SLOW_PLAYER_RATE;
                break;
            case UP:
                controller.removeAfter.add(PlayerController.InputDirection.UP);
                break;
            case DOWN:
                controller.removeAfter.add(PlayerController.InputDirection.DOWN);
                break;
            case LEFT:
                controller.removeAfter.add(PlayerController.InputDirection.LEFT);
                break;
            case RIGHT:
                controller.removeAfter.add(PlayerController.InputDirection.RIGHT);
                break;
            case FIRE:

                break;
        }
        return false;
    }
}
