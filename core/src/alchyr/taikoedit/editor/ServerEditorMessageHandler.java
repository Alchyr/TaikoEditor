package alchyr.taikoedit.editor;

import alchyr.networking.standard.ConnectionClient;
import alchyr.networking.standard.Message;
import alchyr.networking.standard.ServerMessageHandler;
import alchyr.taikoedit.editor.changes.MapChange;
import alchyr.taikoedit.editor.maps.EditorBeatmap;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class ServerEditorMessageHandler extends ServerMessageHandler {
    @Override
    public boolean handleMessage(ConnectionClient client, Message msg) {
        switch (msg.identifier) {
            case MapChange.MAP_CHANGE:
                handleMapChange(client, msg);
                return true;
            case MapChange.MAP_CHANGE_DENIAL:
                handleDenial(client, msg);
                return true;
            case MapChange.MAP_STATE_CHANGE:
                handleStateChange(client, msg);
                return true;
        }
        return false;
    }

    private void handleMapChange(ConnectionClient client, Message msg) {
        MapChange.ChangeBuilder changeBuilder = (MapChange.ChangeBuilder) msg.contents[0];
        changeBuilder.map.receiveNetworkChange(client, changeBuilder);
    }

    private void handleDenial(ConnectionClient client, Message msg) {
        editorLogger.warn("DENIALS CURRENTLY UNHANDLED");
        /*EditorBeatmap map = (EditorBeatmap) msg.contents[0];
        int changeIndex = (int) msg.contents[1];
        int changeBranch = (int) msg.contents[2];
        map.cancelChange(changeIndex, changeBranch);*/
    }

    private void handleStateChange(ConnectionClient client, Message msg) {
        EditorBeatmap map = (EditorBeatmap) msg.contents[0];
        int stateKey = (int) msg.contents[1];
        int newStateKey = (int) msg.contents[2];
        map.networkChangeState(client, stateKey, newStateKey);
    }
}
