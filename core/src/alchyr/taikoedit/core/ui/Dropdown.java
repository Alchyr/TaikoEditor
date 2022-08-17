package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static alchyr.taikoedit.TaikoEditor.*;

public class Dropdown<T> {
    private final Texture pix = assetMaster.get("ui:pixel");

    private float x = 0, y = 0, width;
    private final Rectangle dropdownBounds;

    public String id = "";
    private boolean open = false;
    private final List<DropdownElement<T>> elements = new ArrayList<>();

    public Dropdown(float width) {
        dropdownBounds = new Rectangle();

        this.width = width;
        //height is determined by number of elements.
    }
    //x, y should be the top left corner of the dropdown.
    public Dropdown<T> setPos(float x, float y) {
        this.x = x;
        this.y = y;
        dropdownBounds.setPosition(x, y - dropdownBounds.height);
        return this;
    }
    public Dropdown<T> setWidth(float width) {
        this.width = width;
        dropdownBounds.setWidth(width);
        return this;
    }

    @SafeVarargs
    public final Dropdown<T> setElements(List<DropdownElement<T>>... newElements) {
        this.elements.clear();
        for (List<DropdownElement<T>> elementSet : newElements)
            this.elements.addAll(elementSet);
        return this;
    }

    @SafeVarargs
    public final void open(List<DropdownElement<T>>... newElements) {
        setElements(newElements);
        open();
    }
    public void open() {
        if (elements.isEmpty()) {
            open = false;
        }
        else {
            open = true;
            float height = 0;
            for (DropdownElement<T> element : elements) {
                element.onOpen();
                height += element.getHeight();
            }

            dropdownBounds.set(x, y - height, width, height);
        }
    }
    public void close() {
        open = false;
    }
    public boolean isOpen() {
        return open;
    }

    public boolean contains(int x, int y) {
        return open && dropdownBounds.contains(x, y);
    }

    public void click(float tx, float ty) {
        if (open) {
            float nextY = y;

            for (DropdownElement<T> element : elements) {
                nextY -= element.getHeight();

                if (element.testHover(x, nextY, width, tx, ty)) {
                    if (element.click()) {
                        close();
                    }
                }
            }
        }
    }

    public boolean update() {
        if (open) {
            float nextY = y, tx = Gdx.input.getX(), ty = SettingsMaster.gameY();
            boolean canHover = dropdownBounds.contains(tx, ty);
            boolean foundHovered = false;

            for (DropdownElement<T> element : elements) {
                nextY -= element.getHeight();

                if (!canHover || foundHovered) {
                    element.setHovered(false);
                }
                else if (element.testHover(x, nextY, width, tx, ty)) {
                    foundHovered = true;
                }
            }
            return canHover;
        }
        else {
            for (DropdownElement<T> element : elements) {
                element.setHovered(false);
            }
            return false;
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (open) {
            sb.setColor(Color.BLACK);
            sb.draw(pix, dropdownBounds.x, dropdownBounds.y, dropdownBounds.width, dropdownBounds.height);

            sb.flush();
            if (ScissorStack.pushScissors(dropdownBounds)) {
                float nextY = y;
                for (DropdownElement<T> element : elements) {
                    nextY -= element.getHeight();
                    element.render(sb, x, nextY, width);

                    if (element.hovered) {
                        element.renderHover(sb, x, nextY, width);
                    }
                }

                sb.setColor(Color.WHITE);

                //top/bottom
                sb.draw(pix, this.x, this.y - 2, width, 2);
                sb.draw(pix, this.x, dropdownBounds.y, width, 2);
                //left/right
                sb.draw(pix, this.x, dropdownBounds.y, 2, dropdownBounds.height);
                sb.draw(pix, this.x + dropdownBounds.width - 2, dropdownBounds.y, 2, dropdownBounds.height);

                sb.flush();
                ScissorStack.popScissors();
            }
        }
    }

    public static abstract class DropdownElement<U> {
        private static final Color highlightColor = new Color(1.0f, 1.0f, 1.0f, 0.4f);
        private final Texture pix = assetMaster.get("ui:pixel");
        public boolean hovered;

        abstract public U getItem();
        abstract public float getHeight();
        abstract public void render(SpriteBatch sb, float x, float y, float width);
        public boolean testHover(float x, float y, float width, float tx, float ty) {
            hovered = (x < tx && y < ty && tx < x + width && ty < y + getHeight());
            return hovered;
        }
        public void setHovered(boolean hovered) {
            this.hovered = hovered;
        }
        public void renderHover(SpriteBatch sb, float x, float y, float width) {
            sb.setColor(highlightColor);
            sb.draw(pix, x, y, width, getHeight());
        }

        public void onOpen() { }
        public abstract boolean click(); //Return true to close the dropdown when clicked. False it stays open.
    }

    public static class ItemElement<U> extends DropdownElement<U> {
        private U item;
        private final String display;
        private final BitmapFont font;
        private final float height;

        public boolean enabled;

        private String hoverText = null;

        private Function<ItemElement<U>, Boolean> condition = (e) -> true;
        private Function<ItemElement<U>, Boolean> onClick = (e) -> true;

        public ItemElement(U item, BitmapFont font) {
            this.item = item;
            this.display = item.toString();
            this.font = font;
            this.height = textRenderer.setFont(font).getHeight(display) + 6;
        }
        public ItemElement<U> setOnClick(Function<ItemElement<U>, Boolean> onClick) {
            this.onClick = onClick;
            return this;
        }
        public ItemElement<U> setCondition(Function<ItemElement<U>, Boolean> condition) {
            this.condition = condition;
            return this;
        }
        public ItemElement<U> setHoverText(String text) {
            this.hoverText = text;
            return this;
        }

        @Override
        public U getItem() {
            return item;
        }

        @Override
        public float getHeight() {
            return height;
        }

        @Override
        public boolean testHover(float x, float y, float width, float tx, float ty) {
            if (super.testHover(x, y, width, tx, ty)) {
                if (hoverText != null) {
                    TaikoEditor.hoverText.setText(hoverText);
                }
                return true;
            }
            return false;
        }

        @Override
        public void render(SpriteBatch sb, float x, float y, float width) {
            textRenderer.renderTextYCentered(sb, enabled ? Color.WHITE : Color.DARK_GRAY, display, x + 4, y + height / 2);
        }

        @Override
        public void renderHover(SpriteBatch sb, float x, float y, float width) {
            if (enabled) {
                super.renderHover(sb, x, y, width);
            }
        }

        @Override
        public void onOpen() {
            enabled = condition.apply(this);
        }

        @Override
        public boolean click() {
            if (enabled) {
                return onClick.apply(this);
            }
            return false;
        }
    }

    public static class SeparatorElement<U> extends DropdownElement<U> {
        private final Texture pix = assetMaster.get("ui:pixel");

        @Override
        public U getItem() {
            return null;
        }

        @Override
        public float getHeight() {
            return 5;
        }

        @Override
        public void render(SpriteBatch sb, float x, float y, float width) {
            sb.setColor(Color.LIGHT_GRAY);
            sb.draw(pix, x + 4, y + 2, width - 8, 1);
        }

        @Override
        public void renderHover(SpriteBatch sb, float x, float y, float width) {
        }

        @Override
        public boolean click() {
            return false;
        }
    }
}
