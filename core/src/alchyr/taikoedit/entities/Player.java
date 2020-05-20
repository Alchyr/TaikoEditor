package alchyr.taikoedit.entities;

import alchyr.taikoedit.entities.components.EntityRenderComponent;
import alchyr.taikoedit.entities.components.InputComponent;
import alchyr.taikoedit.entities.controllers.Controller;
import alchyr.taikoedit.entities.controllers.PlayerController;
import alchyr.taikoedit.core.input.entityinput.PlayerInput;
import alchyr.taikoedit.entities.components.UpdateComponent;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class Player extends ControllableEntity implements UpdatingEntity, RenderableEntity {
    private int width, height, xOffset, yOffset;

    private Texture t;

    private PlayerInput input = null;

    public Player(Controller c, float x, float y)
    {
        super(c);

        t = assetMaster.get("player:soul");

        if (c instanceof PlayerController)
            input = new PlayerInput((PlayerController)c);

        getPosition().x = x;
        getPosition().y = y;

        this.width = t.getWidth();
        this.height = t.getHeight();
        this.xOffset = this.width / 2;
        this.yOffset = this.height / 2;

        add(new UpdateComponent(this));
        add(new EntityRenderComponent(this));

        add(new InputComponent(input));
    }

    public void update(float elapsed)
    {

    }

    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(Color.WHITE);

        sb.draw(t, getPosition().x, getPosition().y, xOffset, yOffset, width, height, 1.0f, 1.0f, 0.0f, 0, 0, width, height, false, false);
    }
}
