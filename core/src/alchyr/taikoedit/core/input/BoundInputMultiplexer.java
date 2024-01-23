package alchyr.taikoedit.core.input;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.SnapshotArray;

//Slightly modified version of InputMultiplexer (by Nathan Sweet) to also update the contained processors.
public class BoundInputMultiplexer implements InputProcessor {
    private final SnapshotArray<BoundInputProcessor> processors = new SnapshotArray<>(4);

    public BoundInputMultiplexer(BoundInputProcessor... processors) {
        this.processors.addAll(processors);
    }

    public void update(float elapsed) {
        Object[] items = processors.begin();
        try {
            for (int i = 0, n = processors.size; i < n; i++)
                ((BoundInputProcessor) items[i]).update(elapsed);
        } finally {
            processors.end();
        }
    }

    public void addProcessor(int index, BoundInputProcessor processor) {
        if (processor == null) throw new NullPointerException("processor cannot be null");
        processors.insert(index, processor);
    }

    public void removeProcessor(int index) {
        processors.removeIndex(index);
    }

    public void addProcessor(BoundInputProcessor processor) {
        if (processor == null) throw new NullPointerException("processor cannot be null");
        processors.add(processor);
    }

    public void removeProcessor(BoundInputProcessor processor) {
        processors.removeValue(processor, true);
    }

    /** @return the number of processors in this multiplexer */
    public int size() {
        return processors.size;
    }

    public void clear() {
        processors.clear();
    }

    public void setProcessors(BoundInputProcessor... processors) {
        this.processors.clear();
        this.processors.addAll(processors);
    }

    public void setProcessors(Array<BoundInputProcessor> processors) {
        this.processors.clear();
        this.processors.addAll(processors);
    }

    public SnapshotArray<BoundInputProcessor> getProcessors() {
        return processors;
    }

    public boolean keyDown(int keycode) {
        Object[] items = processors.begin();
        try {
            for (int i = 0, n = processors.size; i < n; i++)
                if (((BoundInputProcessor)items[i]).keyDown(keycode)) return true;
        } finally {
            processors.end();
        }
        return false;
    }

    public boolean keyUp(int keycode) {
        Object[] items = processors.begin();
        try {
            for (int i = 0, n = processors.size; i < n; i++)
                if (((BoundInputProcessor)items[i]).keyUp(keycode)) return true;
        } finally {
            processors.end();
        }
        return false;
    }

    public boolean keyTyped(char character) {
        Object[] items = processors.begin();
        try {
            for (int i = 0, n = processors.size; i < n; i++)
                if (((BoundInputProcessor)items[i]).keyTyped(character)) return true;
        } finally {
            processors.end();
        }
        return false;
    }

    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        Object[] items = processors.begin();
        try {
            for (int i = 0, n = processors.size; i < n; i++)
                if (((BoundInputProcessor)items[i]).touchDown(screenX, screenY, pointer, button)) return true;
        } finally {
            processors.end();
        }
        return false;
    }

    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        Object[] items = processors.begin();
        try {
            for (int i = 0, n = processors.size; i < n; i++)
                if (((BoundInputProcessor)items[i]).touchUp(screenX, screenY, pointer, button)) return true;
        } finally {
            processors.end();
        }
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        Object[] items = processors.begin();
        try {
            for (int i = 0, n = processors.size; i < n; i++)
                if (((BoundInputProcessor)items[i]).touchCancelled(screenX, screenY, pointer, button)) return true;
        } finally {
            processors.end();
        }
        return false;
    }

    public boolean touchDragged(int screenX, int screenY, int pointer) {
        Object[] items = processors.begin();
        try {
            for (int i = 0, n = processors.size; i < n; i++)
                if (((BoundInputProcessor)items[i]).touchDragged(screenX, screenY, pointer)) return true;
        } finally {
            processors.end();
        }
        return false;
    }

    public boolean mouseMoved(int screenX, int screenY) {
        Object[] items = processors.begin();
        try {
            for (int i = 0, n = processors.size; i < n; i++)
                if (((BoundInputProcessor)items[i]).mouseMoved(screenX, screenY)) return true;
        } finally {
            processors.end();
        }
        return false;
    }

    public boolean scrolled(float amountX, float amountY) {
        Object[] items = processors.begin();
        try {
            for (int i = 0, n = processors.size; i < n; i++)
                if (((BoundInputProcessor)items[i]).scrolled(amountX, amountY)) return true;
        } finally {
            processors.end();
        }
        return false;
    }
}
