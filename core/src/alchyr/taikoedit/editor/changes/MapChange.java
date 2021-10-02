package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;

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


    public enum ChangeType {
        OBJECTS,
        EFFECT
    }
}
