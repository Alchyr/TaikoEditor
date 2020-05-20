package alchyr.taikoedit.entities.controllers;

import alchyr.taikoedit.entities.ControllableEntity;

import java.util.ArrayList;

public abstract class Controller {
    //Interface for things that can control entities.
    protected ArrayList<ControllableEntity> entities;

    public Controller()
    {
        entities = new ArrayList<>();
    }

    public void setEntity(ControllableEntity controllableEntity)
    {
        entities.clear();
        entities.add(controllableEntity);
        initializeValues();
    }

    public void initializeValues()
    {

    }

    public abstract void update(float elapsed);
}
