package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.editor.maps.MapInfo;
import alchyr.taikoedit.editor.maps.Mapset;
import alchyr.taikoedit.management.MapMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.Hitbox;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.util.*;

import static alchyr.taikoedit.TaikoEditor.*;

public class MapSelect {
    //Hashed map of mapper name to "group"
    //Arraylist of groups that will be rendered
    //Each group is a list of maps by a single mapper

    //Able to minimize mappers?
    //Kinda pointless but it probably wouldn't be that hard...

    //Interaction:
    //Click mapper to dropdown/un-drop down
    //Click map to select
    //Click selected map to open in editor
    //Right side displays map info
    //Click a difficulty to open in editor

    //assets
    private final BitmapFont font;

    private final Texture pix;
    private final Texture arrow;

    private final Color faintHighlight = new Color(1.0f, 1.0f, 1.0f, 0.2f);
    private final Color highlight = new Color(1.0f, 1.0f, 1.0f, 0.4f);

    //Positions
    private static final int LINE_GAP = 22;
    private final float divider = SettingsMaster.getWidth() * 0.75f;
    private final float infoX = divider + 2;
    private final float infoWidth = SettingsMaster.getWidth() - (infoX);
    private final float thumbnailMaxHeight = 0.5625f * infoWidth;

    private final int top;
    private final float infoCenterX, thumbnailCenterY;
    private final int infoDivider1, difficultyTitleY, difficultyY;
    private float scrollPos = -10, targetScrollPos = -10, scrollBottom = 0;

    //Song Select
    private final List<MapperGroup> activeGroups = new ArrayList<>();
    private int firstRender = -1;
    private Mapset selected = null;

    //map info
    private Texture thumbnail;
    private int thumbnailX, thumbnailY, thumbnailWidth, thumbnailHeight, thumbnailOffsetX, thumbnailOffsetY,
            thumbnailSrcWidth, thumbnailSrcHeight, thumbnailSrcOffsetX, thumbnailSrcOffsetY;
    private boolean updateThumbnail = false;

    //General stuff
    private final Map<String, MapperGroup> allMappers = new HashMap<>();
    private boolean hovering = false;
    private final Hitbox hovered = new Hitbox(0, 0, 1, 1);


    public MapSelect(int top) {
        this.font = assetMaster.getFont("aller medium");
        pix = assetMaster.get("ui:pixel");
        arrow = assetMaster.get("ui:arrow");

        this.top = top;

        infoCenterX = infoX + infoWidth / 2.0f;
        thumbnailCenterY = top - thumbnailMaxHeight / 2.0f;
        infoDivider1 = (int) (top - thumbnailMaxHeight);
        difficultyTitleY = infoDivider1 - 25;
        difficultyY = difficultyTitleY - 25;

        refreshMaps();
    }

    public void refreshMaps() {
        //Reloads all maps from database.
        targetScrollPos = scrollPos = -10;
        scrollBottom = top * -0.8f;
        activeGroups.clear();
        Set<MapperGroup> cleared = new HashSet<>();

        Mapset set;
        MapperGroup group;
        for (Map.Entry<String, Mapset> setEntry : MapMaster.mapDatabase.mapsets.entrySet())
        {
            set = setEntry.getValue();
            group = allMappers.computeIfAbsent(set.getCreator(), MapperGroup::new);
            if (!cleared.contains(group)) {
                group.clear();
                cleared.add(group);
                activeGroups.add(group);
                scrollBottom += LINE_GAP;
            }
            group.add(set);
            scrollBottom += LINE_GAP;
        }
        if (scrollBottom < 0)
            scrollBottom = 0;

        activeGroups.sort(Comparator.comparing((u)->u.mapper));
        for (MapperGroup mapperGroup : activeGroups) {
            mapperGroup.sort();
        }
    }
    //Sets to a specific list of maps, which will be displayed in order.
    public void setMaps(List<Mapset> maps) {
        targetScrollPos = scrollPos = -10;
        scrollBottom = top * -0.9f;
        activeGroups.clear();
        Set<MapperGroup> cleared = new HashSet<>();

        MapperGroup group;
        for (Mapset set : maps) {
            group = allMappers.computeIfAbsent(set.getCreator(), MapperGroup::new);
            if (!cleared.contains(group)) {
                group.clear();
                cleared.add(group);
                activeGroups.add(group);
                scrollBottom += LINE_GAP;
            }
            group.add(set);
            scrollBottom += LINE_GAP;
        }
        if (scrollBottom < 0)
            scrollBottom = 0;
    }

    private void setSelected(Mapset set) {
        selected = set;

        thumbnail = null;
        updateThumbnail = true;
    }

    public void update(float elapsed) {
        float mouseX = Gdx.input.getX(), mouseY = SettingsMaster.gameY();
        hovering = false;

        float y = top + scrollPos;
        firstRender = -1;
        MapperGroup group;
        for (int i = 0; i < activeGroups.size(); ++i) {
            group = activeGroups.get(i);
            y = group.update(elapsed, y);
            if (y < top) { //bottom of last updated group is below top of select area
                if (firstRender == -1)
                    firstRender = i;
                
                if (!hovering && mouseX < divider && group.hb.y2() >= mouseY) { //cursor could be within this group
                    if (group.hb.y() - 2.5f < mouseY) {
                        hovering = true;
                        hovered.set(group.hb.x(), group.hb.y() - 1, group.hb.getWidth(), group.hb.getHeight() + 2);
                    }
                    else if (y - 2.5f < mouseY && group.expandPos > 0) {
                        int index = (int) (((mouseY - (group.hb.y() - 2.5f)) * -1) / (LINE_GAP * group.expandPos));
                        if (index >= 0 && index < group.sets.size() || (index-- == group.sets.size() && ((int)(((mouseY - (group.hb.y() - 2.5f)) * -1) / (LINE_GAP * group.expandPos)) % (int)(LINE_GAP * group.expandPos)) < 3)) {
                            hovering = true;
                            hovered.set(0, group.hb.y2() - 30 - ((index * LINE_GAP + 13) * group.expandPos) + (9 * (1.0f - group.expandPos)), divider, LINE_GAP);
                        }
                    }
                }
            }
        }

        if (!hovering && selected != null && mouseX > infoX && mouseY < infoDivider1) {
            int index = MathUtils.floor(((mouseY - (difficultyY + 10)) * -1) / LINE_GAP);
            if (index >= 0 && index < selected.getMaps().size()) {
                hovering = true;
                hovered.set(infoX, difficultyY - 12 - (index * LINE_GAP), infoWidth, LINE_GAP);
            }
        }

        float scrollChange, scrollLimit = scrollChange = targetScrollPos - scrollPos;
        scrollChange *= elapsed * 8;
        if (scrollLimit < 0) {
            if (scrollChange < scrollLimit)
                scrollChange = scrollLimit;
        }
        else if (scrollLimit > 0) {
            if (scrollChange > scrollLimit)
                scrollChange = scrollLimit;
        }
        scrollPos += scrollChange;

        if (targetScrollPos < -10) {
            targetScrollPos = Math.min(-10, targetScrollPos - ((targetScrollPos + 10) * elapsed * 8));
        }
        if (targetScrollPos > scrollBottom) {
            targetScrollPos = Math.max(scrollBottom, targetScrollPos - ((targetScrollPos - scrollBottom) * elapsed * 8));
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (updateThumbnail && selected != null) {
            updateThumbnail = false;
            String bg = selected.getBackground();
            if (bg != null && !bg.isEmpty()) {
                thumbnail = new Texture(Gdx.files.absolute(bg), true); //these song folders have quite high odds of containing characters libgdx doesn't like. assetMaster.get(backgroundImg.toLowerCase());
                thumbnail.setFilter(Texture.TextureFilter.MipMapLinearNearest, Texture.TextureFilter.MipMapLinearNearest);

                float bgScale = Math.max(infoWidth / thumbnail.getWidth(), thumbnailMaxHeight / thumbnail.getHeight());

                thumbnailWidth = (int) Math.ceil(thumbnail.getWidth() * bgScale);
                thumbnailHeight = (int) Math.ceil(thumbnail.getHeight() * bgScale);

                if (thumbnailWidth > infoWidth) {
                    thumbnailSrcHeight = thumbnail.getHeight();
                    thumbnailSrcOffsetY = 0;

                    thumbnailSrcWidth = (int) (thumbnail.getWidth() * (infoWidth / thumbnailWidth));
                    thumbnailSrcOffsetX = (int) ((thumbnail.getWidth() - thumbnailSrcWidth) * 0.5f);
                    thumbnailWidth = (int) infoWidth;
                }
                else if (thumbnailHeight > thumbnailMaxHeight) {
                    thumbnailSrcWidth = thumbnail.getWidth();
                    thumbnailSrcOffsetX = 0;

                    thumbnailSrcHeight = (int) (thumbnail.getHeight() * (thumbnailMaxHeight / thumbnailHeight));
                    thumbnailSrcOffsetY = (int) ((thumbnail.getHeight() - thumbnailSrcHeight) * 0.5f);
                    thumbnailHeight = (int) thumbnailMaxHeight;
                }
                else {
                    thumbnailSrcWidth = thumbnail.getWidth();
                    thumbnailSrcHeight = thumbnail.getHeight();
                    thumbnailSrcOffsetX = 0;
                    thumbnailSrcOffsetY = 0;
                }

                thumbnailOffsetX = thumbnailWidth / 2;
                thumbnailOffsetY = thumbnailHeight / 2;

                thumbnailX = (int) (infoCenterX - thumbnailWidth / 2.0f);
                thumbnailY = (int) (thumbnailCenterY - thumbnailHeight / 2.0f);
            }
        }

        textRenderer.setFont(font);
        //render map select
        if (firstRender > -1) {
            for (int i = firstRender; i < activeGroups.size();  ++i) {
                if (activeGroups.get(i).render(sb, sr)) {
                    break;
                }
            }
        }

        //render info area
        sb.setColor(Color.WHITE);
        //bg thumbnail
        //set info
        textRenderer.renderTextCentered(sb, "Difficulties:", infoCenterX, difficultyTitleY, Color.WHITE);
        //difficulties
        if (selected != null) {
            if (thumbnail != null) {
                sb.draw(thumbnail, thumbnailX, thumbnailY, thumbnailOffsetX, thumbnailOffsetY, thumbnailWidth, thumbnailHeight, 1, 1, 0, thumbnailSrcOffsetX, thumbnailSrcOffsetY, thumbnailSrcWidth, thumbnailSrcHeight, false, false);
            }
            else {
                textRenderer.renderTextCentered(sb, "(No Background)", infoCenterX, thumbnailCenterY, Color.WHITE);
            }

            int y = difficultyY;
            for (MapInfo info : selected.getMaps()) {
                textRenderer.renderTextYCentered(sb, Color.WHITE, info.getDifficultyName(), infoX + 5, y);
                y -= LINE_GAP;
            }
        }

        if (hovering) {
            sb.setColor(faintHighlight);
            sb.draw(pix, hovered.x(), hovered.y(), hovered.getWidth(), hovered.getHeight());
        }

        //render overlay lines
        sb.setColor(Color.WHITE);
        sb.draw(pix, divider, 0, 2, top);
        sb.draw(pix, infoX, infoDivider1, infoWidth, 2);
    }

    public MapOpenInfo click(float gameX, float gameY) {
        if (gameX < divider) {
            if (firstRender > -1) { //map select area
                MapperGroup group;
                MapOpenInfo info = null;
                for (int i = firstRender; i < activeGroups.size(); ++i) {
                    group = activeGroups.get(i);
                    info = group.click(gameX, gameY);
                    if (info != null || gameY > group.hb.y2()) {
                        break;
                    }
                }
                return info;
            }
            else {
                return null;
            }
        }
        else if (selected != null && gameX > infoX && gameY < infoDivider1) {
            //map info area
            int index = MathUtils.floor(((gameY - (difficultyY + 10)) * -1) / LINE_GAP);
            if (index >= 0 && index < selected.getMaps().size()) {
                return new MapOpenInfo(selected).setInitial(selected.getMaps().get(index));
            }
        }
        return null;
    }

    public void scrolled(float amount) {
        float bonus = 0;
        if (targetScrollPos != scrollPos) {
            bonus = MathUtils.log2(Math.abs(targetScrollPos - scrollPos));
            if (bonus < 0) {
                bonus = 0;
            }
        }
        targetScrollPos += amount * (25.0f + bonus);
    }

    //22 pixels per line.
    private class MapperGroup {
        static final float setX = 50;
        static final float mapperX = 35;
        static final float arrowX = mapperX - 25;

        float setWidth = divider - setX;

        Hitbox hb;

        String mapper;
        String displayName;
        List<Mapset> sets;
        Color setColor = Color.WHITE.cpy();

        boolean expanded = true;
        float expandPos = 1; //from 0 to 1, multiplies offset of children and total height, as well as opacity of children.
        float arrowAngle = 0; //0 is down, 90 is right

        MapperGroup(String mapper) {
            if (mapper.isEmpty())
                mapper = "(No Mapper)";
            this.displayName = this.mapper = mapper;
            this.sets = new ArrayList<>();

            hb = new Hitbox(0, 0, divider - 1, 20);
            float width = textRenderer.setFont(font).getWidth(displayName);

            if (width >= divider && mapper.length() > 102) { //avoid excessive looping if name is for some reason excessively long
                displayName = mapper.substring(0, 100) + "...";
                width = textRenderer.getWidth(displayName);
            }

            if (width >= divider) {
                int len = mapper.length() - 2;

                do
                {
                    displayName = mapper.substring(0, len) + "...";
                    width = textRenderer.getWidth(displayName);

                    --len;
                    if (mapper.charAt(len - 1) == ' ')
                        --len;
                } while (width >= divider);
            }
        }

        float update(float elapsed, float y) {
            hb.setY2(y);

            if (expanded) {
                expandPos = Math.min(1, expandPos + 5 * elapsed);
                arrowAngle = Math.max(0, arrowAngle - 450 * elapsed);
            }
            else {
                expandPos = Math.max(0, expandPos - 5 * elapsed);
                arrowAngle = Math.min(90, arrowAngle + 450 * elapsed);
            }

            return y - getHeight();
        }
        boolean render(SpriteBatch sb, ShapeRenderer sr) {
            //render sets then mapper
            setColor.a = expandPos;
            float y = hb.y() - 10;

            int i = 0;
            for (Mapset set : sets) {
                if (set == selected) {
                    sb.setColor(highlight);
                    sb.draw(pix, 0, y - ((i * LINE_GAP + 13) * expandPos) + (9 * (1.0f - expandPos)), divider, LINE_GAP);
                }
                if (expandPos > 0)
                    textRenderer.renderTextYCentered(sb, setColor, set.songMeta(font, setWidth), setX, y - (i * LINE_GAP * expandPos));
                ++i;
            }

            //render mapper
            sb.setColor(Color.WHITE);
            sb.draw(arrow, arrowX, hb.y(), 10, 10, 20, 20, 1, 1, arrowAngle, 0, 0, 20, 20, false, false);
            textRenderer.renderTextYCentered(sb, Color.WHITE, displayName, mapperX, hb.y2() - 9);

            /*sb.setColor(Color.RED);
            sb.draw(pix, 0, hb.y2(), 10, 1);
            sb.setColor(Color.BLUE);
            sb.draw(pix, 0, hb.y(), 10, 1);*/

            return hb.y2() < 0;
        }

        MapOpenInfo click(float gameX, float gameY) {
            if (hb.containsExtended(gameX, gameY, 0, -2.5f, 0, 1)) {
                expanded = !expanded;
                return null;
            }
            else if (expandPos > 0 && gameY < hb.y() && gameY > hb.y2() - getHeight() - 2.5f) {
                if (sets.isEmpty())
                    return new MapOpenInfo(null);

                int index = (int) (((gameY - (hb.y() - 2.5f)) * -1) / (LINE_GAP * expandPos));
                if (index >= 0 && index <= sets.size()) {
                    if (index == sets.size())
                        --index;

                    if (sets.get(index) == selected) {
                        return new MapOpenInfo(sets.get(index));
                    }
                    else {
                        setSelected(sets.get(index));
                        return new MapOpenInfo(null);
                    }
                }
            }
            return null;
        }

        float getHeight() {
            return 2 + hb.getHeight() + sets.size() * LINE_GAP * expandPos;
        }

        public void clear() {
            sets.clear();
        }
        public void add(Mapset set) {
            sets.add(set);
        }
        public void sort() {
            sets.sort(Comparator.comparing(Mapset::getArtist));
        }
    }

    public static class MapOpenInfo {
        Mapset set;
        MapInfo open = null;

        public MapOpenInfo(Mapset set) {
            this.set = set;
        }

        public MapOpenInfo setInitial(MapInfo initial) {
            open = initial;
            return this;
        }

        public Mapset getSet() {
            return set;
        }

        public MapInfo getInitialDifficulty() {
            return open;
        }
    }
}
