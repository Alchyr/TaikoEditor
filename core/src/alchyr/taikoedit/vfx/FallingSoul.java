package alchyr.taikoedit.vfx;

import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class FallingSoul extends Effect {
    private static final float MIN_SPEED = 40f;
    private static final float MAX_SPEED = 80f;

    private static int width, height, xOffset, yOffset;

    static {
        width = 32;
        height = 32;
        xOffset = 16;
        yOffset = 16;
    }

    private Texture t;

    private float state;
    private float fallSpeed;

    private float rateModifier;
    private float speedModifier;

    private boolean flipped;

    private float x, y;

    public FallingSoul(float x)
    {
        super();

        t = assetMaster.get("player:soul");

        this.x = x;
        this.y = SettingsMaster.getHeight() + 40.0f;

        this.state = MathUtils.random(0.0f, 2.0f);

        this.fallSpeed = 0;

        this.flipped = MathUtils.randomBoolean();

        this.rateModifier = MathUtils.random(0.6f, 1.4f);
        this.speedModifier = MathUtils.random(0.5f, 1.5f);
    }

    @Override
    public void update(float elapsed) {
        state = (state + elapsed * rateModifier) % 2;

        if (state >= 1)
        {
            fallSpeed = Interpolation.linear.apply(MIN_SPEED, MAX_SPEED, state - 1);
        }
        else
        {
            fallSpeed = Interpolation.linear.apply(MAX_SPEED, MIN_SPEED, state);
        }

        this.y -= fallSpeed * elapsed * speedModifier;

        if (y < -100)
            this.isDone = true;
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(Color.WHITE);

        sb.draw(t, x, y, xOffset, yOffset, width, height, 1.0f, 1.0f, 0.0f, 0, 0, width, height, flipped, false);
    }
}
