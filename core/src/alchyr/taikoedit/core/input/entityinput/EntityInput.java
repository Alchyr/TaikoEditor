package alchyr.taikoedit.core.input.entityinput;

public abstract class EntityInput {
    //Input Flags
    public static final int KEYDOWN = 1;
    public static final int KEYUP = 2;
    public static final int KEYTYPED = 4;
    public static final int TOUCHDOWN = 8;
    public static final int TOUCHUP = 16;
    public static final int TOUCHDRAGGED = 32;
    public static final int MOUSEMOVED = 64;
    public static final int SCROLLED = 128;

    public abstract int getInputType();

    public boolean keyDown(int keycode) { return false; }

    public boolean keyUp(int keycode) { return false; }

    public boolean keyTyped(char character) {
        return false;
    }

    public boolean onTouchDown(int gameX, int gameY, int pointer, int button)
    {
        return false;
    }

    public boolean onTouchUp(int gameX, int gameY, int pointer, int button) {
        return false;
    }

    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    public boolean scrolled(int amount) {
        return false;
    }
}
