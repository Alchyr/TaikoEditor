package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.core.ProgramLayer;

public abstract class LoadedLayer extends ProgramLayer {
    public abstract LoadingLayer getLoader();
    public LoadingLayer getReturnLoader() {
        return getLoader();
    }
}
