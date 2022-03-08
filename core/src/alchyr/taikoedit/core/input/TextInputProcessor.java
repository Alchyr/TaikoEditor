package alchyr.taikoedit.core.input;

import alchyr.taikoedit.core.input.sub.TextInput;
import alchyr.taikoedit.core.input.sub.TextInputReceiver;

public abstract class TextInputProcessor extends BoundInputProcessor {
    private final TextInput inputReader;

    private TextInputReceiver activeReceiver;
    private boolean hasReceiver;

    public TextInputProcessor(BindingGroup bindings, boolean defaultReturn) {
        super(bindings, defaultReturn);

        inputReader = new TextInput(0, null);
        hasReceiver = false;
    }

    @Override
    public boolean keyTyped(char character) {
        if (hasReceiver)
        {
            if (activeReceiver.acceptCharacter(character)) {
                boolean returnVal = inputReader.keyTyped(character);
                if (activeReceiver != null)
                    this.activeReceiver.setText(inputReader.text);
                return returnVal;
            }
            return true;
        }
        return super.keyTyped(character);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (hasReceiver && activeReceiver.blockInput(keycode))
            return true;
        return super.keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        if (hasReceiver && activeReceiver.blockInput(keycode))
            return true;
        return super.keyUp(keycode);
    }

    public void setTextReceiver(TextInputReceiver textReceiver)
    {
        this.activeReceiver = textReceiver;
        hasReceiver = textReceiver != null;

        if (hasReceiver)
        {
            inputReader.cap = this.activeReceiver.getCharLimit();
            inputReader.text = this.activeReceiver.getInitialText();
            inputReader.font = this.activeReceiver.getFont();
            inputReader.setOnEnter(this.activeReceiver.onPressEnter());
        }
    }

    public void disableTextReceiver(TextInputReceiver receiver)
    {
        if (this.activeReceiver == receiver) {
            this.activeReceiver = null;
            hasReceiver = false;

            inputReader.cap = 0;
            inputReader.text = "";
            inputReader.font = null;
            inputReader.setOnEnter(null);
        }
    }

    public void disableTextReceiver()
    {
        this.activeReceiver = null;
        hasReceiver = false;

        inputReader.cap = 0;
        inputReader.text = "";
        inputReader.font = null;
        inputReader.setOnEnter(null);
    }
}
