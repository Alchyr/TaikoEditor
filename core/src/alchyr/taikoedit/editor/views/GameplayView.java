package alchyr.taikoedit.editor.views;

/*
 * For gameplay view logic:
 *
 * Determining what objects should be rendered:
 * A list of beginnings and ends, mapped to the object they're the beginning/end of.
 * Beginnings and ends are the time when the object will become visible until the startTime of the object, which is pos.
 * By collecting all objects within a range of that list, you know what objects should be rendered.
 * To know the order they should be rendered, sort them based on pos.
 * Then, use gameplayRender method in hitobject to render them.
 * Hitobjects should be aware of their SV (bpm, base sv, and sv multiplier at their position combined)
 * While playing, maps should be iterated through/updated continuously rather than fully re-collecting each frame for efficiency.
 * When paused, lists will be reset.
 * Seeking should also reset.
 */
public class GameplayView {
    private boolean wasPlaying; //Used to tell if playback BEGAN on this frame, to re-prepare for iteration.

}
