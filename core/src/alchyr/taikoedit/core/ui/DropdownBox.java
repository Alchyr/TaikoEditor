package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.core.UIElement;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.management.assets.RenderComponent;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;

import java.util.List;
import java.util.function.BiConsumer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

//Box with an active selection from a list that drops down
public class DropdownBox<T> implements UIElement, Scrollable {
    private static final Color highlightColor = new Color(1.0f, 1.0f, 1.0f, 0.4f);

    private final Texture pix = assetMaster.get("ui:pixel");
    private final RenderComponent<?> arrow = assetMaster.getRenderComponent("ui:small_arrow", Texture.class);
    private final BitmapFont font;

    private static final float MIN_SCROLL = 0;
    private float baseMaximumScroll = MIN_SCROLL, maximumScroll = MIN_SCROLL, currentScroll = MIN_SCROLL, targetScroll = MIN_SCROLL;

    private final float width, height;
    private float x, y, textX, x2, y2, dx, dy;
    private final Rectangle dropdownBounds, renderBounds = new Rectangle();
    private int centerY;
    private final int maxDropdownCount;

    private T currentOption;
    private T hovered;
    private boolean hoveringMain = false;
    private String optionText;
    private final List<T> options;
    private boolean open = false;

    private BiConsumer<T, T> onSelect;

    private Color foreground = Color.WHITE.cpy();
    private Color background = Color.BLACK.cpy();
    private Color highlight = highlightColor.cpy();


    public DropdownBox(float leftX, float centerY, float width, float maxDropdownHeight, List<T> options, BitmapFont font) {
        this.font = font;

        this.options = options;

        if (options.isEmpty()) {
            currentOption = null;
            optionText = "";
        }
        else {
            setCurrentOption(options.get(0));
        }

        this.width = width;
        height = textRenderer.setFont(font).getHeight("Pp") + 6;
        maxDropdownCount = (int) (maxDropdownHeight / height);
        float dropdownHeight = height * Math.min(Math.max(1, options.size()), maxDropdownCount);
        if (options.size() > maxDropdownCount) {
            dropdownHeight += height / 2;
            maximumScroll = height * (options.size() - maxDropdownCount) - height / 2;
        }

        x = leftX;
        y = centerY - height / 2.0f;
        textX = x + 4;
        x2 = x + width;
        y2 = y + height;
        this.centerY = (int) ((y + y2) / 2);
        dropdownBounds = new Rectangle(leftX, y - dropdownHeight, width, dropdownHeight);

        dx = 0;
        dy = 0;
    }
    public DropdownBox(float leftX, float centerY, float width, int dropdownCount, List<T> options, BitmapFont font) {
        this.font = font;

        this.options = options;

        if (options.isEmpty()) {
            currentOption = null;
            optionText = "";
        }
        else {
            setCurrentOption(options.get(0));
        }

        this.width = width;
        height = textRenderer.setFont(font).getHeight("Pp") + 2;
        this.maxDropdownCount = dropdownCount;
        float dropdownHeight = height * Math.min(Math.max(1, options.size()), maxDropdownCount);
        if (options.size() > maxDropdownCount) {
            dropdownHeight += height / 2;
            maximumScroll = height * (options.size() - maxDropdownCount) - height / 2;
        }

        x = leftX;
        y = centerY - height / 2.0f;
        textX = x + 4;
        x2 = x + width;
        y2 = y + height;
        this.centerY = (int) ((y + y2) / 2);
        dropdownBounds = new Rectangle(leftX, y - dropdownHeight, width, dropdownHeight);

        dx = 0;
        dy = 0;
    }
    public DropdownBox<T> setOption(T option) {
        int i = options.indexOf(option);
        if (i >= 0) {
            setCurrentOption(options.get(i));
        }
        return this;
    }
    //First parameter is previous, second parameter is new
    public DropdownBox<T> setOnSelect(BiConsumer<T, T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }
    public DropdownBox<T> setForeground(Color fore) {
        foreground = fore;
        return this;
    }
    public DropdownBox<T> setBackground(Color back) {
        background = back;
        return this;
    }
    public DropdownBox<T> setHighlight(Color highlight) {
        this.highlight = highlight;
        return this;
    }

    public void removeOption(T option) {
        if (options.remove(option)) {
            float dropdownHeight = height * Math.min(Math.max(1, options.size()), maxDropdownCount);
            if (options.size() > maxDropdownCount) {
                dropdownHeight += height / 2;
                maximumScroll = height * (options.size() - maxDropdownCount) - height / 2;
            }
            else {
                maximumScroll = MIN_SCROLL;
            }
            dropdownBounds.set(x, y - dropdownHeight, width, dropdownHeight);
        }
    }

    private void setCurrentOption(T option) {
        currentOption = option;
        optionText = currentOption.toString();
    }

    @Override
    public void move(float dx, float dy) {
        x += dx;
        x2 += dx;
        textX += dx;

        y += dy;
        y2 += dy;
        this.centerY = (int) ((y + y2) / 2);

        dropdownBounds.setPosition(dropdownBounds.x + dx, dropdownBounds.y + dy);
    }

    public void cancel() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean tryClick(float clickX, float clickY) {
        if (open) {
            if (x + dx < clickX && dropdownBounds.y + dy < clickY && clickX < x2 + dx && clickY < y2 + dy)
            {
                return true;
            }
            else {
                cancel();
                return false;
            }
        }
        else {
            return x + dx < clickX && y + dy < clickY && clickX < x2 + dx && clickY < y2 + dy;
        }
    }

    public boolean click(float clickX, float clickY)
    {
        if (open) {
            if (x + dx < clickX && dropdownBounds.y + dy < clickY && clickX < x2 + dx && clickY < y2 + dy)
            {
                //Determine hovered
                if (clickY < y + dy) {
                    int index = (int) (((y + dy - clickY) - currentScroll) / height);
                    if (index >= 0 && index < options.size()) {
                        T old = currentOption;
                        setCurrentOption(options.get(index));
                        onSelect.accept(old, currentOption);
                        return true;
                    }
                }
                cancel();
                return true;
            }
            else {
                cancel();
                return false;
            }
        }
        else {
            if (x + dx < clickX && y + dy < clickY && clickX < x2 + dx && clickY < y2 + dy)
            {
                open = true;
                return true;
            }
        }
        cancel();
        return false;
    }

    @Override
    public void update(float elapsed)
    {
        hovered = null;
        hoveringMain = false;
        float pos = Gdx.input.getX();
        if (pos > this.x + dx && pos < this.x2 + dx) {
            pos = SettingsMaster.gameY();
            //Determine hovered
            if (pos >= y + dy && pos < y2 + dy) {
                hoveringMain = true;
            }
            else if (open && pos > dropdownBounds.y + dy && pos < y + dy) {
                int index = (int) ((currentScroll + y + dy - pos) / height);
                if (index >= 0 && index < options.size())
                    hovered = options.get(index);
            }
        }

        if (open) {
            float scrollChange, scrollLimit = scrollChange = targetScroll - currentScroll;
            scrollChange *= elapsed * 8;
            if (scrollLimit < 0) {
                if (scrollChange < scrollLimit)
                    scrollChange = scrollLimit;
            }
            else if (scrollLimit > 0) {
                if (scrollChange > scrollLimit)
                    scrollChange = scrollLimit;
            }
            currentScroll += scrollChange;

            if (targetScroll < MIN_SCROLL) {
                targetScroll = Math.min(MIN_SCROLL, targetScroll - ((targetScroll + 10) * elapsed * 8));
            }
            if (targetScroll > maximumScroll) {
                targetScroll = Math.max(maximumScroll, targetScroll - ((targetScroll - maximumScroll) * elapsed * 8));
            }
        }
        else {
            currentScroll = targetScroll;
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        render(sb, sr, 0, 0);
    }

    public void render(SpriteBatch sb, ShapeRenderer sr, float x, float y) {
        dx = x; //adjustment to hover/click check position
        dy = y;

        textRenderer.setFont(font).resetScale().renderTextYCentered(sb, Color.WHITE, optionText, this.textX + x, this.centerY);
        if (hoveringMain) {
            sb.setColor(highlight);
            sb.draw(pix, this.x + x, this.y + y, width, height);
        }
        sb.setColor(Color.WHITE);
        arrow.renderC(sb, sr, this.x2 + x - 9, this.centerY + y, Color.WHITE);

        if (open) {
            sb.flush();
            renderBounds.set(dropdownBounds.x + x, dropdownBounds.y + y, dropdownBounds.width, dropdownBounds.height);
            if (ScissorStack.pushScissors(renderBounds)) {
                float textY = centerY - height + currentScroll + y;
                T option;
                for (int i = 0; textY > renderBounds.y - height && i < options.size(); ++i) {
                    option = options.get(i);
                    textRenderer.renderTextYCentered(sb, Color.WHITE, option.toString(), this.textX + x, textY);
                    if (option.equals(hovered)) {
                        sb.setColor(highlight);
                        sb.draw(pix, this.x + x, textY - height / 2, width, height);
                    }
                    textY -= height;
                }

                sb.flush();
                ScissorStack.popScissors();
            }

            sb.setColor(Color.WHITE);

            //top/bottom
            sb.draw(pix, x + this.x - 1, y + y2 - 1, width + 2, 2);
            sb.draw(pix, x + this.x - 1, y + this.y - 1, width + 2, 2);
            sb.draw(pix, x + this.x - 1, y + dropdownBounds.y - 1, width + 2, 2);
            //left/right
            sb.draw(pix, x + this.x - 1, y + dropdownBounds.y - 1, 2, height + dropdownBounds.height + 2);
            sb.draw(pix, x + x2 - 1, y + dropdownBounds.y - 1, 2, height + dropdownBounds.height + 2);
        }
        else {
            //top/bottom
            sb.draw(pix, x + this.x - 1, y + y2 - 1, width + 2, 2);
            sb.draw(pix, x + this.x - 1, y + this.y - 1, width + 2, 2);
            //left/right
            sb.draw(pix, x + this.x - 1, y + this.y - 1, 2, height + 2);
            sb.draw(pix, x + x2 - 1, y + this.y - 1, 2, height + 2);
        }
    }

    @Override
    public void scroll(float amt) {
        if (maximumScroll != MIN_SCROLL) {
            float bonus = 0;
            if (targetScroll != currentScroll) {
                bonus = MathUtils.log2(Math.abs(targetScroll - currentScroll));
                if (bonus < 0) {
                    bonus = 0;
                }
            }
            targetScroll += amt * (20.0f + bonus);
        }
    }
}
