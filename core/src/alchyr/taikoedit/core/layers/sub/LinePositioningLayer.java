package alchyr.taikoedit.core.layers.sub;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.UIElement;
import alchyr.taikoedit.core.input.BoundInputMultiplexer;
import alchyr.taikoedit.core.input.BoundInputProcessor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.*;
import alchyr.taikoedit.editor.changes.RepositionChange;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.interfaces.Weighted;
import alchyr.taikoedit.util.structures.Pair;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import java.util.*;

import static alchyr.taikoedit.TaikoEditor.*;

public class LinePositioningLayer  extends ProgramLayer implements InputLayer {
    private static final float AREA_X_MID = SettingsMaster.getMiddleX(), AREA_Y_MID = SettingsMaster.getHeight() / 2;

    private final EditorLayer src;
    private final EditorBeatmap map;

    private LinePositionProcessor mainProcessor;
    private BoundInputMultiplexer processor;

    private Page page;

    private BitmapFont font;

    public LinePositioningLayer(EditorLayer src, EditorBeatmap map)
    {
        this.type = LAYER_TYPE.FULL_STOP;

        this.src = src;
        this.map = map;
    }

    @Override
    public void initialize() {
        font = assetMaster.getFont("aller medium");

        this.mainProcessor = new LinePositionProcessor(this);
        page = mainPage();

        this.processor = new BoundInputMultiplexer(page, mainProcessor);

        mainProcessor.bind();
        page.bind();
    }
    private void close() {
        page.releaseInput(false);
        page.hidden();
        TaikoEditor.removeLayer(this);
    }

    private void confirm() {
        if (positioningArea.lines.isEmpty()) {
            close();
            return;
        }

        List<WeightedLine> converted = new ArrayList<>();
        for (Pair<Pair<Float, Float>, Pair<Float, Float>> line : positioningArea.lines) {
            converted.add(new WeightedLine(line, positioningArea));
        }

        map.registerChange(new RepositionChange(map).perform((hit -> {
            WeightedLine target = Weighted.roll(converted, null);
            Pair<Integer, Integer> randomPos = target.randomPos();
            hit.x = randomPos.a;
            hit.y = randomPos.b;
        })));
        src.showText("Repositioned all objects.");

        close();
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        page.render(sb, sr);
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    @Override
    public void dispose() {
        super.dispose();
        mainProcessor.dispose();
        page.dispose();
    }

    private static class LinePositionProcessor extends BoundInputProcessor {
        private final LinePositioningLayer sourceLayer;

        public LinePositionProcessor(LinePositioningLayer source)
        {
            super(BindingMaster.getBindingGroupCopy("Basic"), true);
            this.sourceLayer = source;
        }

        @Override
        public void bind() {
            bindings.bind("Exit", sourceLayer::close);
            bindings.bind("Confirm", sourceLayer::confirm);

            bindings.addMouseBind((x, y, b)->sourceLayer.positioningArea.contains(x, y),
                    (p, b)-> {
                        sourceLayer.positioningArea.position(p.x, p.y);
                        return null;
                    });
        }
    }




    // Page Generation
    private Page mainPage() {
        Page positioningPage = new Page(null);

        positioningArea = new LineDrawingArea(AREA_X_MID, AREA_Y_MID, SettingsMaster.getWidth() * 0.8f, SettingsMaster.getHeight() * 0.8f);
        positioningPage.addUIElement(positioningArea);

        return positioningPage;
    }


    private LineDrawingArea positioningArea;
    private static class LineDrawingArea implements UIElement {
        private float cX, cY, width, height, scale, dx, dy;

        private boolean midLine = false;
        private Pair<Float, Float> lineStart;

        private final List<Pair<Pair<Float, Float>, Pair<Float, Float>>> lines = new ArrayList<>();

        public LineDrawingArea(float cX, float cY, float maxWidth, float maxHeight) {
            height = maxWidth * 12 / 16; //height if using maxWidth
            if (height > maxHeight) { //too big
                width = maxHeight * 16 / 12;
                height = maxHeight;
            }
            else {
                width = maxWidth;
            }
            scale = width / 512.0f;

            this.cX = cX;
            this.cY = cY;

            dx = 0;
            dy = 0;
        }

        public int x(Pair<Float, Float> point) {
            return xFromGameX(point.a);
        }
        public int y(Pair<Float, Float> point) {
            return yFromGameY(point.b);
        }
        public int xFromGameX(float gameX) {
            return Math.max(0, (Math.min(512, Math.round((gameX - (cX - width / 2.0f)) / scale))));
        }
        public int yFromGameY(float gameY) {
            return Math.max(0, (Math.min(384, Math.round(((cY + height / 2.0f) - gameY) / scale))));
        }

        public boolean position(float gameX, float gameY) {
            if (gameX < cX - width / 2.0f || gameY < cY - height / 2.0f || gameX > cX + width / 2.0f || gameY > cY + height / 2.0f) {
                return false;
            }

            if (midLine && lineStart != null) {
                midLine = false;
                lines.add(new Pair<>(lineStart, new Pair<>(gameX, gameY)));
                lineStart = null;
            }
            else {
                midLine = true;
                lineStart = new Pair<>(gameX, gameY);
            }

            return true;
        }

        @Override
        public void move(float dx, float dy) {
            cX += dx;
            cY += dy;
        }

        @Override
        public void update(float elapsed) {

        }

        @Override
        public void render(SpriteBatch sb, ShapeRenderer sr) {
            float left = cX - width / 2 + dx, right = cX + width / 2 + dx, top = cY + height / 2 + dy, bottom = cY - height / 2 + dy, temp = top;

            sb.end();
            //grid first
            sr.begin(ShapeRenderer.ShapeType.Line);

            sr.setColor(Color.WHITE);
            sr.rect(left, bottom, width, height);

            sr.setColor(Color.GRAY);
            //horizontal lines
            temp -= height / 6.0f;
            sr.line(left, temp, right, temp);
            temp -= height / 6.0f;
            sr.line(left, temp, right, temp);
            temp -= height / 6.0f;
            sr.line(left, temp, right, temp);
            temp -= height / 6.0f;
            sr.line(left, temp, right, temp);
            temp -= height / 6.0f;
            sr.line(left, temp, right, temp);

            //vertical lines
            temp = left + width / 8.0f;
            sr.line(temp, top, temp, bottom);
            temp += width / 8.0f;
            sr.line(temp, top, temp, bottom);
            temp += width / 8.0f;
            sr.line(temp, top, temp, bottom);
            temp += width / 8.0f;
            sr.line(temp, top, temp, bottom);
            temp += width / 8.0f;
            sr.line(temp, top, temp, bottom);
            temp += width / 8.0f;
            sr.line(temp, top, temp, bottom);
            temp += width / 8.0f;
            sr.line(temp, top, temp, bottom);

            sr.setColor(Color.MAROON);
            for (Pair<Pair<Float, Float>, Pair<Float, Float>> line : lines) {
                sr.line(line.a.a, line.a.b, line.b.a, line.b.b);
            }

            sr.end();
            sb.begin();
        }

        @Override
        public void render(SpriteBatch sb, ShapeRenderer sr, float dx, float dy) {
            this.dx = dx;
            this.dy = dy;

            render(sb, sr);
        }

        public boolean contains(int x, int y) {
            return !(x < cX - width / 2.0f) && !(y < cY - height / 2.0f) && !(x > cX + width / 2.0f) && !(y > cY + height / 2.0f);
        }
    }
    private static class WeightedLine implements Weighted {
        int x1, y1, x2, y2;
        final float weight;
        public WeightedLine(Pair<Pair<Float, Float>, Pair<Float, Float>> gameCoordLine, LineDrawingArea converter) {
            x1 = converter.x(gameCoordLine.a);
            y1 = converter.y(gameCoordLine.a);
            x2 = converter.x(gameCoordLine.b);
            y2 = converter.y(gameCoordLine.b);

            weight = Vector2.dst(x1, y1, x2, y2);
        }

        private static final int POSVAR = 1, NEGVAR = -POSVAR;
        public Pair<Integer, Integer> randomPos() {
            float prog = MathUtils.random();
            Pair<Integer, Integer> pos = new Pair<>(Math.round(MathUtils.lerp(x1, x2, prog)), Math.round(MathUtils.lerp(y1, y2, prog)));
            pos.a += MathUtils.random(NEGVAR, POSVAR) + MathUtils.random(NEGVAR, POSVAR);
            pos.b += MathUtils.random(NEGVAR, POSVAR) + MathUtils.random(NEGVAR, POSVAR);
            return pos;
        }

        @Override
        public float getWeight() {
            return weight;
        }
    }
}