package alchyr.networking.standard;

import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.util.TextRenderer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

import static alchyr.networking.NetworkUtil.getPublicIP;
import static alchyr.networking.standard.Message.UTF;

//requires port forwarding
public class ConnectionServer implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger("connection server");

    public String name;
    private final Color nameRenderingColor = Color.WHITE.cpy();

    private final ServerSocket serverSocket;
    private final int clientLimit;

    private final String hostAddress;

    private final String pass;

    private final ConcurrentLinkedQueue<ConnectionClient> incomingClients = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ConnectionClient> deadClients = new ConcurrentLinkedQueue<>();
    private final List<ConnectionClient> clients;
    private int lowestUnusedID() {
        //clients should always be in sorted order.
        for (int i = 0; i < clients.size(); ++i) {
            if (clients.get(i).ID != i) {
                return i;
            }
        }
        return clients.size();
    }

    private Thread clientReceiver;

    private ServerMessageHandler messageHandler = null;
    private final Map<String, List<Function<Object[], Boolean>>> eventTriggers = new HashMap<>();

    public static final String SERVER_MSG = "SVMSG";

    //Events
    public static final String EVENT_NEW_CLIENT = "EV_NC"; //When a new client is accepted. Client is parameter.
    public static final String EVENT_SENT = "EV_ST"; //When a message is received that starts with EVT, rest of message is appended as event key. Client is parameter.
    public static final String EVENT_FILE_REQ = "EV_FL"; //When a message requests a file. Params: client, 4 character "key", request info

    public ConnectionServer(String name, int port, int clientLimit) throws IOException {
        if (port < 30000 || port > 60000) throw new IllegalArgumentException("Expected a port between 30000 and 60000");

        this.name = name;

        this.clientLimit = clientLimit;
        pass = GeneralUtils.generateCode(64);

        logger.info(InetAddress.getLocalHost().getHostAddress());
        logger.info(getPublicIP());
        hostAddress = getPublicIP();

        //Temp for local non-port-forwarding test
        //hostAddress = InetAddress.getLocalHost().getHostAddress();

        serverSocket = new ServerSocket(port, 20);
        clients = new ArrayList<>();

        clientReceiver = null;
        tryStartClientReceiver();
    }

    //Called on initialization and when a client disconnects
    private void tryStartClientReceiver() {
        if (clients.size() < clientLimit && (clientReceiver == null || !clientReceiver.isAlive())) {
            clientReceiver = new Thread(()->{
                boolean live = true;
                while (live) {
                    try {
                        if (clients.size() + incomingClients.size() >= clientLimit) {
                            live = false;
                            return;
                        }

                        logger.info("Waiting for connection attempt...");
                        Socket clientSocket = serverSocket.accept();

                        logger.info("Received connection attempt.");

                        if (clients.size() + incomingClients.size() >= clientLimit) {
                            logger.info("Client limit reached, ignored.");
                            clientSocket.close();
                            live = false;
                            return;
                        }

                        ConnectionClient tempClient = new ConnectionClient("???", clientSocket);
                        tempClient.checkClient(pass, incomingClients);
                    }
                    catch (SocketException e) {
                        live = false;
                        logger.info("Server closed, clientReceiver interrupted");
                    }
                    catch (IOException e) {
                        logger.error("Exception occurred in clientReceiver thread", e);
                    }
                }
            });
            clientReceiver.setDaemon(true);
            clientReceiver.start();
        }
    }

    public List<ConnectionClient> getClients() {
        return clients;
    }

    public void update() {
        while (!incomingClients.isEmpty()) {
            ConnectionClient client = incomingClients.poll();
            if (clients.size() < clientLimit) {
                logger.info("Added client: " + client);
                client.ID = lowestUnusedID();
                GeneralUtils.insertSorted(clients, client, (c)->c.ID);

                client.onDeath(deadClients::add);
                client.startStandardReceiver();
                client.send("success" + client.ID);
                triggerEvent(EVENT_NEW_CLIENT, client);

                client.send(SERVER_MSG + "MEMBER" + name); //server name
                for (ConnectionClient existingClient : clients) {
                    if (!existingClient.equals(client)) {
                        existingClient.send(SERVER_MSG + "MEMBER" + client); //notify existing client of new client
                        client.send(SERVER_MSG + "MEMBER" + existingClient); //notify new client of existing clients
                    }
                }
            }
            else {
                logger.info("Client limit reached, dropping client: " + client);
            }
        }

        if (!deadClients.isEmpty()) {
            List<String> dead = new ArrayList<>();
            while (!deadClients.isEmpty()) {
                ConnectionClient client = deadClients.poll();
                dead.add(client.toString());
                logger.info("Client disconnected, removing: " + client);
                clients.remove(client);
                try {
                    client.close();
                } catch (Exception e) {
                    logger.error("Exception occurred while closing client", e);
                }
            }

            for (String clientName : dead) {
                for (ConnectionClient client : clients) {
                    client.send(SERVER_MSG + "REMOVE" + clientName);
                }
            }
        }

        for (ConnectionClient client : clients) {
            try {
                while (!client.receivedMessages.isEmpty()) {
                    Message msg = client.receivedMessages.poll();

                    if (messageHandler != null) {
                        if (messageHandler.handleMessage(client, msg)) {
                            continue;
                        }
                    }

                    switch (msg.identifier) {
                        case UTF:
                            String text = msg.contents[0].toString();
                            logger.info("Received message: [" + client + "]: " + text);
                            switch (text.substring(0, 5)) {
                                case EVENT_SENT:
                                    triggerEvent(text.substring(5), client);
                                    break;
                                case EVENT_FILE_REQ:
                                    triggerEvent(EVENT_FILE_REQ, client, text.substring(5, 9), text.substring(9));
                                    break;
                            }
                            break;
                        default:
                            logger.warn("Message type unhandled: 0x" + Integer.toString(msg.identifier, 16));
                            break;
                    }
                }
            }
            catch (Exception e) {
                logger.warn("Error occurred processing message from client " + client + ", disconnecting.");
                client.fail("Received invalid message."); //On client end it'll just be "disconnected".
            }
        }
    }


    @Override
    public void close() throws Exception {
        if (serverSocket != null) {
            serverSocket.close();
        }
        try {
            for (ConnectionClient client : clients) {
                client.close();
            }
        }
        catch (Exception ignored) {}
    }

    public String getConnectionText() {
        return String.format("%s|%d|%s", hostAddress, serverSocket.getLocalPort(), pass);
    }

    public void setMessageHandler(ServerMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    //Only rule: Events should only be triggered on "main" thread (update)
    public void registerEventTrigger(String key, Function<Object[], Boolean> action) {
        if (!eventTriggers.containsKey(key)) eventTriggers.put(key, new ArrayList<>());

        eventTriggers.get(key).add(action);
    }

    public void triggerEvent(String key, Object... params) {
        List<Function<Object[], Boolean>> triggers = eventTriggers.get(key);
        if (triggers != null) {
            triggers.removeIf(trigger->trigger.apply(params));
        }
        else {
            logger.info("Received event with no triggers: " + key);
        }
    }

    public boolean isAlive() {
        return !serverSocket.isClosed();
    }

    public void renderConnectedNames(TextRenderer textRenderer, SpriteBatch sb, Texture connectedTex, BitmapFont font, float rightX, float opacity) {
        nameRenderingColor.a = opacity;

        rightX -= connectedTex.getWidth();
        float y = (clients.size() + 1) * 32;
        float texY = y - connectedTex.getHeight();

        textRenderer.setFont(font);
        sb.setColor(nameRenderingColor);

        sb.draw(connectedTex, rightX, texY);
        textRenderer.renderTextRightAlign(sb, name, rightX - 5, y, nameRenderingColor);

        for (ConnectionClient client : clients) {
            y -= 32;
            texY -= 32;
            sb.draw(connectedTex, rightX, texY);
            textRenderer.renderTextRightAlign(sb, client.toString(), rightX - 5, y, nameRenderingColor);
        }
    }

    @Override
    public String toString() {
        return name;
    }

}
