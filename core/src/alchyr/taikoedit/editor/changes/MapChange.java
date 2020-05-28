package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.maps.EditorBeatmap;

//For undo support
public abstract class MapChange {
    protected EditorBeatmap map;

    public MapChange(EditorBeatmap map)
    {
        this.map = map;
    }

    public abstract MapChange undo();
    public abstract MapChange perform();


    public enum ChangeType {
        OBJECTS,
        TIMING,
        EFFECT
    }
}
