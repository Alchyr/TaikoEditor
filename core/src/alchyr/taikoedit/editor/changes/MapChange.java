package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.structures.PositionalObject;

//For undo support
public abstract class MapChange {
    protected EditorBeatmap map;
    public boolean invalidateSelection = false; //Should be true for changes that would cause the PositionalObjectMap for selected objects to have incorrect positions

    public MapChange(EditorBeatmap map)
    {
        this.map = map;
    }

    public abstract MapChange undo();
    public abstract MapChange perform();


    public static ChangeType getChangeType(PositionalObject o) {
        if (o instanceof TimingPoint) {
            if (((TimingPoint) o).uninherited)
                return ChangeType.RED_LINE;
            else
                return ChangeType.GREEN_LINE;
        }
        return ChangeType.OBJECTS;
    }
    public enum ChangeType {
        OBJECTS,
        GREEN_LINE,
        RED_LINE
    }
}
