package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.BreakAdjust;
import alchyr.taikoedit.editor.changes.BreakRemoval;
import alchyr.taikoedit.editor.changes.MapChange;
import alchyr.taikoedit.editor.changes.RepositionChange;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.ILongObject;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;
import alchyr.taikoedit.editor.tools.*;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.EditorTime;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static alchyr.taikoedit.TaikoEditor.*;
import static alchyr.taikoedit.core.layers.EditorLayer.viewScale;

public class GimmickView extends MapView {
    public static final String ID = "gmk";
    @Override
    public String typeString() {
        return ID;
    }


    public static final int HEIGHT = 150;
    public static final int MEDIUM_HEIGHT = 30;
    public static final int SMALL_HEIGHT = 15;

    //Selection
    private static final int MAX_SELECTION_DIST = 62;
    private static final int SMALL_SELECTION_DIST = 42;
    private static final int MAX_SELECTION_OFFSET = HEIGHT / 2 - MAX_SELECTION_DIST;
    private static final int SMALL_SELECTION_OFFSET = HEIGHT / 2 - SMALL_SELECTION_DIST;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.7f);
    private static final Color breakColor = new Color(0.6f, 0.6f, 0.6f, 0.3f);
    private static final Color faintBreakColor = new Color(0.5f, 0.5f, 0.5f, 0.2f);
    private static final Color fakeBreakColor = new Color(0.44f, 0.6f, 0.88f, 0.2f);

    private boolean breaks;

    //Base position values
    private int baseObjectY = 0;
    //private int baseTopBigY = 0;

    //Offset
    private int objectY = 0;
    //private int topBigY = 0;

    private long lastSounded;

    private SortedMap<Long, Snap> activeSnaps;

    private static final BiFunction<PositionalObject, PositionalObject, Boolean> replaceSameType = (placed, existing)->{
        if (placed instanceof HitObject && existing instanceof HitObject) {
            return ((HitObject) placed).type == ((HitObject) existing).type;
        }
        return false;
    };

    public GimmickView(EditorLayer parent, EditorBeatmap beatmap) {
        super(ViewType.GIMMICK_VIEW, parent, beatmap, HEIGHT);
        lastSounded = 0;

        filters = new ArrayList<>();
        hiddenTypes = new HashSet<>();
        filters.add((h)->hiddenTypes.contains(h.type));

        breaks = beatmap.autoBreaks;

        replaceTest = replaceSameType;

        addOverlayButton(new ImageButton(assetMaster.get("editor:exit"), assetMaster.get("editor:exith")).setClick(this::close).setAction("Close View"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:breaks"), assetMaster.get("editor:breaksh")).setClick(this::toggleBreaks).setAction("Toggle Break Visibility"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:circle"), assetMaster.get("editor:circleh")).setClick(this::toggleHits).setAction("Toggle Hit Visibility"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:slider"), assetMaster.get("editor:sliderh")).setClick(this::toggleSliders).setAction("Toggle Slider Visibility"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:spinner"), assetMaster.get("editor:spinnerh")).setClick(this::toggleSpinners).setAction("Toggle Spinner Visibility"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:position"), assetMaster.get("editor:positionh")).setClick(this::reposition).setAction("Reposition Objects"));
        addLockPositionButton();
    }

    public void close(int button)
    {
        if (button == Input.Buttons.LEFT)
        {
            parent.removeView(this);
        }
    }

    @Override
    public int setPos(int y) {
        super.setPos(y);

        baseObjectY = this.y + HEIGHT / 2;
        //baseTopBigY = this.y + HEIGHT - BIG_HEIGHT;

        return this.y;
    }

    public void setOffset(int offset)
    {
        super.setOffset(offset);

        objectY = baseObjectY + yOffset;
        //topBigY = baseTopBigY + yOffset;
    }

    //Object Filtering
    private boolean sliders = true, spinners = true;
    private final List<Predicate<HitObject>> filters;
    private final Set<HitObject.HitObjectType> hiddenTypes;
    private boolean visible(HitObject h) {
        for (Predicate<HitObject> filter : filters)
            if (filter.test(h))
                return false;

        return true;
    }

    @Override
    public void primaryUpdate(boolean isPlaying) {
        if (isPrimary && lockOffset == 0 && isPlaying && lastSounded < time && time - lastSounded < 25) //might have skipped backwards
        {
            for (ArrayList<HitObject> objects : map.objects.subMap(lastSounded, false, time, true).values())
            {
                for (HitObject o : objects)
                {
                    if (visible(o))
                        o.playSound();
                }
            }
        }
        lastSounded = time;
    }

    @Override
    public void update(double exactPos, long msPos, float elapsed, boolean canHover) {
        super.update(exactPos, msPos, elapsed, canHover);
        activeSnaps = map.getActiveSnaps(preciseTime - EditorLayer.viewTime, preciseTime + EditorLayer.viewTime);
    }

    @Override
    public void renderBase(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, bottom, SettingsMaster.getWidth(), height);

        //Breaks
        if (breaks) {
            Map.Entry<Long, ArrayList<HitObject>> stack;
            double start, end, breakStart, breakEnd;
            long startTime;
            Color startColor, endColor;
            Optional<HitObject> longest;

            for (Pair<Long, Long> breakSection : map.getBreaks()) {
                startColor = endColor = faintBreakColor;

                breakEnd = (breakSection.b - preciseTime) * viewScale + SettingsMaster.getMiddleX();

                stack = map.objects.ceilingEntry(breakSection.b);
                if (stack != null) {
                    //if distance is <= min distance, color is gray
                    //Otherwise, color is blue to denote extended break delay
                    //Do the same for start delay, except end delay is dynamic based on AR.
                    if (stack.getKey() - breakSection.b > map.getBreakEndDelay())
                        endColor = fakeBreakColor;

                    end = (stack.getKey() - preciseTime) * viewScale + SettingsMaster.getMiddleX();
                }
                else { //this is a cheaty break with no closing object.
                    end = SettingsMaster.getWidth();
                }

                if (end < 0)
                    continue;

                breakStart = (breakSection.a - preciseTime) * viewScale + SettingsMaster.getMiddleX();

                stack = map.objects.floorEntry(breakSection.a);
                if (stack != null) {
                    longest = stack.getValue().stream().max(Comparator.comparingLong(HitObject::getEndPos));
                    startTime = longest.map(HitObject::getEndPos).orElse(0L);

                    if (breakSection.a - startTime > 200)
                        startColor = fakeBreakColor;

                    start = (startTime - preciseTime) * viewScale + SettingsMaster.getMiddleX();
                }
                else {
                    start = 0;
                }

                if (start > SettingsMaster.getWidth())
                    break;

                //should render if start is anywhere >0 or end anywhere <width, or if start<0 and end>width?
                if (start <= SettingsMaster.getWidth() && end >= 0) {
                    start = Math.max(start, 0);
                    if (breakStart > 0) {
                        sb.setColor(startColor);
                        sb.draw(pix, (float)start, bottom, (float)(breakStart - start), height);
                    }
                    if (breakEnd <= SettingsMaster.getWidth()) {
                        sb.setColor(endColor);
                        sb.draw(pix, (float)breakEnd, bottom, (float)(end - breakEnd), height);
                    }
                    sb.setColor(breakColor);
                    sb.draw(pix, (float)breakStart, bottom, (float)(breakEnd - breakStart), height);
                }
            }
        }

        //Divisors.
        for (Snap s : activeSnaps.values())
        {
            s.render(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), bottom, HEIGHT);
        }
    }

    @Override
    public void renderObject(PositionalObject o, SpriteBatch sb, ShapeRenderer sr, float alpha) {
        o.render(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), objectY, alpha);
    }
    @Override
    public void renderSelection(PositionalObject o, SpriteBatch sb, ShapeRenderer sr) {
        o.renderSelection(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), objectY);
    }

    private NavigableMap<Long, ArrayList<HitObject>> prevObjects = null;
    private final PositionalObjectTreeMap<HitObject> filtered = new PositionalObjectTreeMap<>();
    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> prep() {
        NavigableMap<Long, ArrayList<HitObject>> objs = map.getEditObjects(time - EditorLayer.viewTime, time + EditorLayer.viewTime);

        /*if (objs.equals(prevObjects)) {
            return filtered.descendingMap();
        }*/
        prevObjects = objs;

        filtered.clear();
        for (Map.Entry<Long, ArrayList<HitObject>> entry : objs.entrySet()) {
            for (HitObject h : entry.getValue()) {
                if (visible(h))
                    filtered.add(h);
            }
        }

        return filtered.descendingMap();
    }

    @Override
    public void selectAll() {
        clearSelection();

        selectedObjects = new PositionalObjectTreeMap<>();

        for (Map.Entry<Long, ArrayList<HitObject>> entry : map.objects.entrySet()) {
            for (HitObject h : entry.getValue()) {
                if (visible(h)) {
                    selectedObjects.add(h);
                    h.selected = true;
                }
            }
        }
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> getVisibleRange(long start, long end) {
        NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> source = prep();

        if (source.isEmpty())
            return null;

        start = Math.max(start, source.lastKey());
        end = Math.min(end, source.firstKey());

        if (start >= end)
            return null;

        return source.subMap(end, true, start, true);
    }

    @Override
    public String getSelectionString() {
        if (hasSelection())
        {
            StringBuilder sb = new StringBuilder(new EditorTime(selectedObjects.firstKey()).toString()).append(" (");

            int comboCount = 0;
            boolean foundStart = false;

            NavigableMap<Long, ArrayList<HitObject>> precedingObjects = map.objects.descendingSubMap(selectedObjects.firstKey(), true);

            //Find initial combo count
            for (Map.Entry<Long, ArrayList<HitObject>> entry : precedingObjects.entrySet())
            {
                for (HitObject h : entry.getValue())
                {
                    if (foundStart)
                    {
                        ++comboCount;
                        if (h.newCombo)
                            break;
                    }
                    else
                    {
                        if (h.equals(selectedObjects.firstEntry().getValue().get(0)))
                        {
                            foundStart = true;
                            comboCount = 0;
                            if (h.newCombo)
                                break;
                        }
                    }
                }
            }

            Iterator<Map.Entry<Long, ArrayList<HitObject>>> allObjects = map.objects.subMap(selectedObjects.firstKey(), true, selectedObjects.lastKey(), true).entrySet().iterator();
            Iterator<Map.Entry<Long, ArrayList<PositionalObject>>> selectionObjects = selectedObjects.entrySet().iterator();

            Map.Entry<Long, ArrayList<HitObject>> currentList = null;
            Map.Entry<Long, ArrayList<PositionalObject>> selectedObjectList = null;

            if (allObjects.hasNext())
                currentList = allObjects.next();

            if (selectionObjects.hasNext())
                selectedObjectList = selectionObjects.next();

            while (currentList != null && selectedObjectList != null)
            {
                if (currentList.getKey().equals(selectedObjectList.getKey())) //Position of lists match.
                {
                    for (HitObject h : currentList.getValue()) //For each object in map. Have to go through all of them to track combo count.
                    {
                        ++comboCount;
                        if (h.newCombo)
                            comboCount = 1;

                        if (selectedObjectList.getValue().contains(h)) //This is a selected object, so add it to text
                        {
                            sb.append(comboCount).append(",");
                        }
                    }
                    //This part of selected objects is done, move to next list in selected objects.
                    if (selectionObjects.hasNext())
                        selectedObjectList = selectionObjects.next();
                    else
                        selectedObjectList = null;
                }
                else //This list has no selected objects, just track combo.
                {
                    for (HitObject h : currentList.getValue())
                    {
                        if (h.newCombo)
                        {
                            comboCount = 1;
                        }
                        ++comboCount;
                    }
                }

                //Move to next list in map.
                if (allObjects.hasNext())
                    currentList = allObjects.next();
                else
                    currentList = null;
            }

            //All done.
            sb.deleteCharAt(sb.length() - 1).append(") - ");

            return sb.toString();
        }

        return new EditorTime(time) + " - ";
    }

    @Override
    public void addSelectionRange(long startTime, long endTime)
    {
        if (startTime == endTime)
            return;

        PositionalObjectTreeMap<PositionalObject> newSelection;

        if (selectedObjects == null)
        {
            newSelection = new PositionalObjectTreeMap<>();
            if (startTime > endTime) {
                long tmp = startTime;
                startTime = endTime;
                endTime = tmp;
            }
            for (Map.Entry<Long, ArrayList<HitObject>> entry : map.getSubMap(startTime, endTime).entrySet()) {
                for (HitObject h : entry.getValue()) {
                    if (visible(h)) {
                        newSelection.add(h);
                    }
                }
            }

            selectedObjects = newSelection;
            for (ArrayList<PositionalObject> stuff : selectedObjects.values())
                for (PositionalObject o : stuff)
                    o.selected = true;
        }
        else
        {
            NavigableMap<Long, ArrayList<HitObject>> newSelected;

            if (startTime > endTime) {
                long tmp = startTime;
                startTime = endTime;
                endTime = tmp;
            }
            newSelected = map.getSubMap(startTime, endTime);

            for (Map.Entry<Long, ArrayList<HitObject>> entry : newSelected.entrySet()) {
                for (HitObject h : entry.getValue()) {
                    if (visible(h)) {
                        selectedObjects.add(h);
                        h.selected = true;
                    }
                }
            }
        }
    }

    @Override
    public MouseHoldObject click(float x, float y, int button)
    {
        MouseHoldObject o = super.click(x, y, button);
        if (o != null)
            return o;

        if (breaks) {
            double time = getTimeFromPosition(x);

            Iterator<Pair<Long, Long>> breakIterator = map.getBreaks().iterator();
            Pair<Long, Long> breakSection;
            long startTime, endTime;
            boolean adjustable;
            Map.Entry<Long, ArrayList<HitObject>> stack;
            Optional<HitObject> longest;

            while (breakIterator.hasNext()) {
                breakSection = breakIterator.next();
                adjustable = true;
                stack = map.objects.ceilingEntry(breakSection.b);
                if (stack != null) {
                    endTime = stack.getKey();
                }
                else { //this is a cheaty break with no closing object.
                    endTime = Long.MAX_VALUE;
                    adjustable = false;
                }

                stack = map.objects.floorEntry(breakSection.a);
                if (stack != null) {
                    longest = stack.getValue().stream().max(Comparator.comparingLong(HitObject::getEndPos));
                    startTime = longest.map(HitObject::getEndPos).orElse(Long.MIN_VALUE);
                }
                else {
                    startTime = Long.MIN_VALUE;
                    adjustable = false;
                }

                if (time <= endTime && time >= startTime) {
                    if (button == Input.Buttons.RIGHT) {
                        //Right clicking anywhere on a break attempts deletion.
                        long len = endTime - startTime;

                        if (len < 5000) {
                            map.registerChange(new BreakRemoval(map, breakSection));
                            breakIterator.remove();
                        }
                        else {
                            parent.showText("Gap is too long to remove break.");
                            return MouseHoldObject.nothing;
                        }
                    }
                    else if (button == Input.Buttons.LEFT && adjustable) {
                        //left clicking on one of the break's "transition" points allows adjustment.
                        double startDist, endDist;

                        startDist = Math.abs(time - breakSection.a) / viewScale;
                        endDist = Math.abs(time - breakSection.b) / viewScale;

                        //overlay is exceedingly unlikely since there's a minimum 650 ms gap between start and end, but in that case end is prioritized
                        if (endDist < 10) {
                            return new BreakAdjustingMouseHoldObject(this, breakSection, false, time, Math.max(breakSection.a + 650, endTime - 5000), endTime - map.getBreakEndDelay());
                        }
                        else if (startDist < 10) {
                            return new BreakAdjustingMouseHoldObject(this, breakSection, true, time, startTime + 200, Math.min(breakSection.b - 650, startTime + 5000));
                        }
                    }
                    return null;
                }
                else if (time < breakSection.a) {
                    return null;
                }
            }
        }

        return null;
    }

    public PositionalObject getObjectAt(float x, float y)
    {
        NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> selectable = prep();
        if (selectable == null || y < bottom + MAX_SELECTION_OFFSET || y > top - MAX_SELECTION_OFFSET)
            return null;

        double time = getTimeFromPosition(x);

        ArrayList<? extends PositionalObject> selectableObjects = selectable.get((long) time);
        if (selectableObjects != null) {
            if (selectableObjects.isEmpty())
            {
                editorLogger.error("WTF? Empty arraylist of objects in object map.");
            }
            else
            {
                //Select the first object.
                return selectableObjects.get(selectableObjects.size() - 1);
            }
        }

        Map.Entry<Long, ? extends ArrayList<? extends PositionalObject>> lower = selectable.higherEntry((long) time); //These are reversed because prep/editObjects returns a descending map.
        Map.Entry<Long, ? extends ArrayList<? extends PositionalObject>> higher = selectable.lowerEntry((long) time);
        double higherDist, lowerDist;
        //boolean isLower = true;

        if (lower == null && higher == null)
            return null;
        else if (lower == null)
        {
            higherDist = higher.getKey() - time;
            lowerDist = Double.MAX_VALUE;
        }
        else if (higher == null)
        {
            lowerDist = time - lower.getKey();
            higherDist = Double.MAX_VALUE;
        }
        else
        {
            //Neither are null. Determine which one is closer.
            lowerDist = time - lower.getKey();
            higherDist = higher.getKey() - time;
        }

        lowerDist *= viewScale;
        higherDist *= viewScale;

        //Check the closer objects first.
        if (lower != null && lowerDist < higherDist)
        {
            selectableObjects = lower.getValue();
            if (selectableObjects.isEmpty())
            {
                editorLogger.error("WTF? Empty arraylist of objects in object map.");
            }
            else if (lowerDist > MAX_SELECTION_DIST)
            {
                boolean needsFinish = y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET;

                for (int i = selectableObjects.size() - 1; i >= 0; --i)
                {
                    if (selectableObjects.get(i) instanceof ILongObject)
                    {
                        boolean finish = ((HitObject) selectableObjects.get(i)).type == HitObject.HitObjectType.SPINNER ||
                                ((HitObject) selectableObjects.get(i)).isFinish();
                        if (finish || !needsFinish)
                        {
                            if (((ILongObject) selectableObjects.get(i)).getEndPos() > time - (finish ? MAX_SELECTION_DIST : SMALL_SELECTION_DIST))
                            {
                                return selectableObjects.get(i);
                            }
                        }
                    }
                }
            }
            else if (lowerDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET)
            {
                //Can only select a finisher.
                for (int i = selectableObjects.size() - 1; i >= 0; --i)
                {
                    if (((HitObject) selectableObjects.get(i)).isFinish())
                    {
                        return selectableObjects.get(i);
                    }
                }
            }
            else //lower distance is within selection range.
            {
                //Select the first object.
                return selectableObjects.get(selectableObjects.size() - 1);
            }

            if (higher != null)
            {
                selectableObjects = higher.getValue();
                if (!selectableObjects.isEmpty() && !(higherDist > MAX_SELECTION_DIST)) {
                    if (higherDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET)
                    {
                        //Can only select a finisher.
                        for (int i = selectableObjects.size() - 1; i >= 0; --i)
                        {
                            if (((HitObject) selectableObjects.get(i)).isFinish())
                            {
                                return selectableObjects.get(i);
                            }
                        }
                    }
                    else
                    {
                        //Select the first object.
                        return selectableObjects.get(selectableObjects.size() - 1);
                    }
                } //else {
                    //Nothing to do here, just skip checking higher object.
               // }
            }
        }
        else if (higher != null && higherDist < lowerDist)
        {
            selectableObjects = higher.getValue();
            if (!selectableObjects.isEmpty() && !(higherDist > MAX_SELECTION_DIST)) {
                if (higherDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET)
                {
                    //Can only select a finisher.
                    for (int i = selectableObjects.size() - 1; i >= 0; --i)
                    {
                        if (((HitObject) selectableObjects.get(i)).isFinish())
                        {
                            return selectableObjects.get(i);
                        }
                    }
                }
                else
                {
                    //Select the first object.
                    return selectableObjects.get(selectableObjects.size() - 1);
                }
            } //else {
                //Nothing to do here, just skip checking higher object.
            //}

            if (lower != null)
            {
                selectableObjects = lower.getValue();
                if (selectableObjects.isEmpty())
                {
                    editorLogger.error("WTF? Empty arraylist of objects in object map.");
                }
                else if (lowerDist > MAX_SELECTION_DIST)
                {
                    boolean needsFinish = y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET;

                    for (int i = selectableObjects.size() - 1; i >= 0; --i)
                    {
                        if (selectableObjects.get(i) instanceof ILongObject)
                        {
                            boolean finish = ((HitObject) selectableObjects.get(i)).type == HitObject.HitObjectType.SPINNER ||
                                    ((HitObject) selectableObjects.get(i)).isFinish();
                            if (finish || !needsFinish)
                            {
                                if (((ILongObject) selectableObjects.get(i)).getEndPos() > time - (finish ? MAX_SELECTION_DIST : SMALL_SELECTION_DIST))
                                {
                                    return selectableObjects.get(i);
                                }
                            }
                        }
                    }
                }
                else if (lowerDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET)
                {
                    //Can only select a finisher.
                    for (int i = selectableObjects.size() - 1; i >= 0; --i)
                    {
                        if (((HitObject) selectableObjects.get(i)).isFinish())
                        {
                            return selectableObjects.get(i);
                        }
                    }
                }
                else //lower distance is within selection range.
                {
                    //Select the first object.
                    return selectableObjects.get(selectableObjects.size() - 1);
                }
            }
        }
        return null;
    }

    @Override
    public boolean clickedEnd(PositionalObject o, float x) {
        if (o instanceof ILongObject)
        {
            ILongObject obj = (ILongObject) o;

            if (((ILongObject) o).getDuration() <= 0)
                return false; //we gimmicking

            double time = getTimeFromPosition(x);

            double dist;
            if (time > obj.getEndPos())
            {
                dist = time - obj.getEndPos();
            }
            else
            {
                dist = obj.getEndPos() - time;
            }

            return dist < SMALL_SELECTION_DIST;
        }
        return false;
    }

    @Override
    public void dispose() {
        super.dispose();

        activeSnaps = null;
    }

    private void reposition() {
        if (hasSelection()) {
            map.registerChange(new RepositionChange(map, selectedObjects).perform());
            parent.showText("Repositioned selected objects.");
        }
        else {
            map.registerChange(new RepositionChange(map).perform());
            parent.showText("Repositioned all objects.");
        }
    }

    private void toggleBreaks() {
        if (breaks) {
            breaks = false;
            parent.showText("Breaks hidden.");
        }
        else {
            breaks = true;
            parent.showText("Breaks shown.");
        }
    }

    private static final Predicate<HitObject> noKats = (h)->h instanceof Hit && ((Hit) h).isRim();
    private static final Predicate<HitObject> noDons = (h)->h instanceof Hit && !((Hit) h).isRim();
    private int hitMode = 0; //0 = all, 1 = none, 2 = dons, 3 = kats
    private void toggleHits() {
        clearSelection();
        prevObjects = null; //refresh visible objects
        switch (hitMode) {
            case 0:
                ++hitMode;
                hiddenTypes.add(HitObject.HitObjectType.CIRCLE);
                parent.showText("Hits hidden.");
                break;
            case 1:
                ++hitMode;
                hiddenTypes.remove(HitObject.HitObjectType.CIRCLE);
                parent.showText("Dons shown.");
                filters.add(noKats);
                break;
            case 2:
                filters.remove(noKats);
                filters.add(noDons);
                parent.showText("Kats shown.");
                ++hitMode;
                break;
            default:
                hitMode = 0;
                filters.remove(noDons);
                parent.showText("Hits shown.");
                break;
        }
    }
    private void toggleSliders() {
        clearSelection();
        prevObjects = null; //refresh visible objects
        if (sliders) {
            sliders = false;
            hiddenTypes.add(HitObject.HitObjectType.SLIDER);
            parent.showText("Sliders hidden.");
        }
        else {
            sliders = true;
            hiddenTypes.remove(HitObject.HitObjectType.SLIDER);
            parent.showText("Sliders shown.");
        }
    }
    private void toggleSpinners() {
        clearSelection();
        prevObjects = null; //refresh visible objects
        if (spinners) {
            spinners = false;
            hiddenTypes.add(HitObject.HitObjectType.SPINNER);
            parent.showText("Spinners hidden.");
        }
        else {
            spinners = true;
            hiddenTypes.remove(HitObject.HitObjectType.SPINNER);
            parent.showText("Spinners shown.");
        }
    }

    @Override
    public void updatePositions(PositionalObjectTreeMap<PositionalObject> moved) {
        map.objects.removeAll(getSelection());
        getSelection().clear();
        map.objects.addAll(moved);
        getSelection().addAll(moved);
    }

    @Override
    public void deleteObject(PositionalObject o) {
        this.map.delete(o);
    }

    @Override
    public void pasteObjects(PositionalObjectTreeMap<PositionalObject> copyObjects) {
        //This should overwrite existing objects.

        //Make copies of the hitobjects (add a copy() method to the HitObject class) and shift their position appropriately
        //Find closest (1 ms before or after limit) snap (of any existing snap, not just the active one) and put objects at that position

        long offset, targetPos;

        Snap closest = getClosestSnap(preciseTime, 250);
        offset = closest == null ? time : closest.pos;
        offset -= copyObjects.firstKey();

        PositionalObjectTreeMap<PositionalObject> placementCopy = new PositionalObjectTreeMap<>();
        TreeMap<Long, Snap> snaps = map.getAllSnaps();

        for (Map.Entry<Long, ArrayList<PositionalObject>> entry : copyObjects.entrySet())
        {
            targetPos = entry.getKey() + offset;

            closest = snaps.get(targetPos);
            if (closest == null)
                closest = snaps.get(targetPos + 1);
            if (closest == null)
                closest = snaps.get(targetPos - 1);
            if (closest != null)
                targetPos = closest.pos;

            for (PositionalObject o : entry.getValue())
            {
                placementCopy.add(o.shiftedCopy(targetPos));
            }
        }

        this.map.paste(placementCopy, replaceTest);
    }

    @Override
    public void reverse() {
        if (!hasSelection())
            return;

        this.map.reverse(MapChange.ChangeType.OBJECTS, true, selectedObjects);
        refreshSelection();
    }

    @Override
    public void clearSelection() {
        super.clearSelection();

        for (MapView view : parent.getViewSet(map).getViews()) {
            if (view.type == ViewType.OBJECT_VIEW) {
                view.clearSelection();
            }
        }
    }

    @Override
    public void deleteSelection() {
        if (selectedObjects != null)
        {
            this.map.delete(selectedObjects);
            clearSelection();
        }
    }

    @Override
    public void registerMove(long totalMovement) {
        if (selectedObjects != null && totalMovement != 0)
        {
            PositionalObjectTreeMap<PositionalObject> movementCopy = new PositionalObjectTreeMap<>();
            movementCopy.addAll(selectedObjects); //use addAll to make a copy without sharing any references other than the positionalobjects themselves
            this.map.registerObjectMovement(movementCopy, totalMovement);
        }
    }


    private static final Toolset toolset = new Toolset(SelectionTool.get(), HitTool.don(), HitTool.kat(), SliderTool.get(), FakeSliderTool.get(), SpinnerTool.get());
    public Toolset getToolset()
    {
        return toolset;
    }


    private static class BreakAdjustingMouseHoldObject extends MouseHoldObject {
        GimmickView parent;
        Pair<Long, Long> breakSection;
        boolean start; //true, adjusting start. false, adjusting end.
        double initialClick;
        long initialPosition, min, max;
        long lastChange = 0;

        public BreakAdjustingMouseHoldObject(GimmickView parent, Pair<Long, Long> breakSection, boolean start, double initialClick, long min, long max) {
            super(null, null);

            this.parent = parent;
            this.breakSection = breakSection;
            this.start = start;

            this.initialClick = initialClick;
            this.initialPosition = start ? breakSection.a : breakSection.b;
            this.min = min;
            this.max = max;
        }

        @Override
        public void onRelease(float x, float y) {
            parent.map.registerChange(new BreakAdjust(parent.map, breakSection, start, initialPosition, start ? breakSection.a : breakSection.b));
        }

        @Override
        public void update(float elapsed) {
            long offsetChange = (long) (parent.getTimeFromPosition(Gdx.input.getX()) - initialClick);

            if (offsetChange == lastChange)
                return;

            Snap closest = parent.getClosestSnap(initialPosition + offsetChange, 500);

            //Just go to cursor position
            if (closest != null && !BindingGroup.alt()) { //Snap to closest snap unless alt is held
                offsetChange = closest.pos - initialPosition;
            }
            else {
                offsetChange = (long) parent.getTimeFromPosition(Gdx.input.getX()) - initialPosition;
            }

            long newPos = initialPosition + offsetChange;
            if (newPos < min)
                newPos = min;
            if (newPos > max)
                newPos = max;

            if (start) {
                breakSection.a = newPos;
            }
            else {
                breakSection.b = newPos;
            }

            //Should move slower than normal selection.
            if (Gdx.input.getX() <= 1)
            {
                music.seekSecond(music.getSecondTime() - elapsed * 2);
            }
            else if (Gdx.input.getX() >= SettingsMaster.getWidth() - 1)
            {
                music.seekSecond(music.getSecondTime() + elapsed * 2);
            }
        }
    }
}
