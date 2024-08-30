package alchyr.taikoedit.editor;

import alchyr.networking.standard.ConnectionClient;
import alchyr.networking.standard.ConnectionServer;
import alchyr.networking.standard.Message;
import alchyr.networking.standard.ClientMessageHandler;
import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.layers.sub.WaitLayer;
import alchyr.taikoedit.editor.changes.MapChange;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.BooleanWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class ClientEditorMessageHandler extends ClientMessageHandler {
    private static final Logger logger = LogManager.getLogger();

    private final EditorLayer editor;

    private final Map<Integer, BooleanWrapper> waitBooleans = new HashMap<>();

    public ClientEditorMessageHandler(EditorLayer editorLayer, ConnectionClient client) {
        super(client);

        this.editor = editorLayer;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.identifier) {
            case Message.UTF:
                String text = msg.contents[0].toString();
                //logger.info("Received message: [" + client + "]: " + text);
                switch (text.substring(0, 5)) {
                    case ConnectionServer.EVENT_SENT:
                        triggerEvent(ConnectionServer.EVENT_SENT, text.substring(5), client);
                        break;
                    case ConnectionServer.EVENT_FILE_REQ:
                        triggerEvent(ConnectionServer.EVENT_FILE_REQ, client, text.substring(5, 9), text.substring(9));
                        break;
                }
                break;
            case Message.FILE:

                break;
            case MapChange.MAP_CHANGE:
                handleMapChange(msg);
                break;
            case MapChange.MAP_CHANGE_DENIAL:
                handleDenial(msg);
                break;
            case MapChange.MAP_STATE_CHANGE:
                handleStateChange(msg);
                break;
        }
    }

    private void handleMapChange(Message msg) {
        MapChange.ChangeBuilder changeBuilder = (MapChange.ChangeBuilder) msg.contents[0];
        changeBuilder.map.receiveNetworkChange(client, changeBuilder);
    }

    private void handleDenial(Message msg) {
        editorLogger.warn("DENIALS CURRENTLY UNHANDLED");
        /*EditorBeatmap map = (EditorBeatmap) msg.contents[0];
        int changeIndex = (int) msg.contents[1];
        int changeBranch = (int) msg.contents[2];
        map.cancelChange(changeIndex, changeBranch);*/
    }

    private void handleStateChange(Message msg) {
        EditorBeatmap map = (EditorBeatmap) msg.contents[0];
        int stateKey = (int) msg.contents[1];
        int newStateKey = (int) msg.contents[2];
        map.networkChangeState(client, stateKey, newStateKey);
    }

    private void triggerEvent(String key, Object... params) {
        switch (key) {
            case ConnectionServer.EVENT_SENT:
                String[] splitParams = params[0].toString().split("\\|");
                switch (splitParams[0]) {
                    case "WAITJOIN": //waiting for another client to join
                        //add wait layer to editor
                        int waitID = Integer.parseInt(splitParams[1]);
                        BooleanWrapper waitBoolean = new BooleanWrapper(false);
                        waitBooleans.put(waitID, waitBoolean);
                        TaikoEditor.addLayer(new WaitLayer("Waiting for another client to join...", ()->waitBoolean.value)
                                .onCancel(()->{
                                    try {
                                        client.close();
                                    }
                                    catch (Exception e) {
                                        logger.error("Failed to close client", e);
                                    }
                                }));
                        break;
                    case "JOIN_SUCCESS": //cancel wait
                    case "JOIN_FAIL": //cancel wait
                        //remove wait layer from editor
                        int joinID = Integer.parseInt(splitParams[1]);
                        BooleanWrapper hopefullyWaitBoolean = waitBooleans.remove(joinID);
                        hopefullyWaitBoolean.value = true;
                        break;
                    case "RESET_STATE":
                        //clear state of all maps
                        for (EditorBeatmap map : editor.getActiveMaps()) {
                            map.clearState();
                            map.keyMapObjects();
                        }
                    case "FAIL":
                        String cause = "DESYNC";
                        if (splitParams.length >= 2) cause = splitParams[1];
                        client.fail(cause);
                        break;
                }
                break;
            case ConnectionServer.EVENT_FILE_REQ:
                editorLogger.info("Clients cannot handle file requests?");
                break;
        }
    }
}
