package alchyr.taikoedit.entities.controllers;

import alchyr.taikoedit.core.scenes.topdown.Room;
import alchyr.taikoedit.core.scenes.topdown.TopDownLayer;

public abstract class TopDownController extends Controller {
    //anything based on top down controller should respect (or at least know of) the collision objects within the current room.
    private TopDownLayer gameLayer;

    private Room getRoom()
    {
        return gameLayer.activeRoom ? gameLayer.room : null;
    }

    public TopDownController(TopDownLayer gameLayer)
    {

    }
}
