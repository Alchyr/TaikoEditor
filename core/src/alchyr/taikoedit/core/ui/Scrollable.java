package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.core.input.KeyHoldObject;

import static alchyr.taikoedit.core.input.AdjustedInputProcessor.NORMAL_FIRST_DELAY;
import static alchyr.taikoedit.core.input.AdjustedInputProcessor.NORMAL_REPEAT_DELAY;

public interface Scrollable {
    void scroll(float amt);

    class ScrollKeyHold extends KeyHoldObject {
        private final Scrollable scrollable;
        private final float rate;

        public ScrollKeyHold(Scrollable scrollable, float rate) {
            super(NORMAL_FIRST_DELAY, NORMAL_REPEAT_DELAY, null);
            this.scrollable = scrollable;
            this.rate = rate;
        }

        @Override
        public void update(float elapsed) {
            scrollable.scroll(rate * elapsed);
        }
    }
}
