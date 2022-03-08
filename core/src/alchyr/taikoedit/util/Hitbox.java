package alchyr.taikoedit.util;

import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/*
    All hitboxes utilize x/y as bottom left, x2/y2 as top right, and cx/cy.

    These along with width and height represent a rectangular area.
    Subclasses can have a smaller actual area, in which case this represents a bounding box of that smaller area.

    These smaller actual areas should be implemented through overriding the collision checking methods.
 */
public class Hitbox {
    protected float width, height;
    protected float x, y, x2, y2, dx, dy, cx, cy;

    public Hitbox(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        this.x2 = x + width;
        this.y2 = y + height;

        cx = (x + x2) / 2.0f;
        cy = (y + y2) / 2.0f;

        dx = 0;
        dy = 0;
    }

    public void setOffset(float dx, float dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public boolean contains(float tx, float ty) {
        return (x + dx < tx && y + dy < ty && tx < x2 + dx && ty < y2 + dy);
    }
    public boolean containsExtended(float tx, float ty, float ax, float ay, float ax2, float ay2) {
        return (x + ax + dx < tx && y + ay + dy < ty && tx < x2 + ax2 + dx && ty < y2 + ay2 + dy);
    }
    public boolean containsRaw(float screenX, float screenY) {
        screenY = SettingsMaster.screenToGameY(screenY);
        return (x + dx < screenX && y + dy < screenY && screenX < x2 + dx && screenY < y2 + dy);
    }

    public void set(Hitbox cpy) {
        set(cpy.x(), cpy.y(), cpy.getWidth(), cpy.getHeight());
    }
    public void set(float x, float y, float width, float height) {
        this.x = x - dx;
        this.y = y - dy;
        this.width = width;
        this.height = height;

        this.x2 = x + width;
        this.y2 = y + height;

        cx = (x + x2) / 2.0f;
        cy = (y + y2) / 2.0f;
    }
    public float x() {
        return x + dx;
    }
    public float x2() {
        return x2 + dx;
    }
    public void setX(float x) {
        this.x = x - dx;
        this.x2 = x + width;
        cx = (x + x2) / 2.0f;
    }
    public float y() {
        return y + dy;
    }
    public float y2() {
        return y2 + dy;
    }
    public void setY(float y) {
        this.y = y - dy;
        this.y2 = y + height;
        cy = (y + y2) / 2.0f;
    }
    public void setY2(float y2) {
        this.y2 = y2 - dy;
        this.y = y2 - height;
        cy = (y + y2) / 2.0f;
    }
    public float centerX() {
        return cx + dx;
    }
    public float centerY() {
        return cy + dy;
    }
    public float getWidth() {
        return width;
    }
    public float getHeight() {
        return height;
    }
    public void setWidth(float width) {
        this.width = width;
        this.x2 = x + width;
        cx = (x + x2) / 2.0f;
    }
    public void setHeight(float height) {
        this.height = height;
        this.y2 = y + height;
        cy = (y + y2) / 2.0f;
    }

    //For Debugging purposes
    public void render(ShapeRenderer sr) {
        sr.rect(x(), y(), width, height);
    }
}
