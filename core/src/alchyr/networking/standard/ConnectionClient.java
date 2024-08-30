package alchyr.networking.standard;

import alchyr.taikoedit.util.TextRenderer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;

import static alchyr.networking.standard.Message.FILE;
import static alchyr.networking.standard.Message.UTF;

public class ConnectionClient implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger("connection client");

    public String name;
    private final Color nameRenderingColor = Color.WHITE.cpy();

    public final List<String> connectionList = new ArrayList<>();

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    public String failure = null;

    public boolean validated = false;
    public int ID = 0;

    private final Thread senderThread;
    private Thread receiverThread;

    public final ConcurrentLinkedQueue<Message> receivedMessages = new ConcurrentLinkedQueue<>(); //Messages received by the "standard" receiverThread are stored here
    public final ConcurrentLinkedQueue<MessageSender> messageSendQueue = new ConcurrentLinkedQueue<>(); //Messages received by the "standard" receiverThread are stored here

    //Message format:
    /*
        a byte signifier of message type
        message (probably starting with size of message)

        message type bytes in Message class

        message formats:
        UTF - just writeUTF and readUTF
        File - 4 char identifier, file length (long), file name (UTF), file contents (byte data)
     */

    private static final Map<Integer, Function<Object[], MessageSender>> messageBuilders = new HashMap<>();
    private static final Map<Integer, MessageReceiver> messageReceivers = new HashMap<>();

    static {
        registerMessageType(UTF, (params)->new MessageSender(params) {
            @Override
            public void send(DataOutputStream out, Object[] params) throws IOException {
                out.write(UTF);
                out.writeUTF(params[0].toString());
                logger.info("Sending: \"" + params[0] + "\"");
                out.flush();
            }
        }, (in)->new Message(UTF, in.readUTF()));

        registerMessageType(FILE, (params)->new MessageSender(params) {
            @Override
            public void send(DataOutputStream out, Object[] params) throws IOException {
                out.write(FILE);
                out.writeUTF(params[0].toString()); //pass

                FileHandle handle = (FileHandle) params[1];
                if (!handle.exists()) {
                    out.writeLong(-1L); //file not found, -1 length
                    out.flush();
                    return;
                }

                File file = handle.file();
                logger.info("Sending file: \"" + file.getName() + "\"");

                long sent = 0, length = file.length();

                if (length > 25000000) { //~25mb limit
                    out.writeLong(-2L); //file too Big
                    out.flush();
                    return;
                }

                byte[] buffer = new byte[4 * 1024];

                try {
                    MessageDigest md = MessageDigest.getInstance("MD5");

                    try (FileInputStream fileReader = new FileInputStream(file)) {
                        DigestInputStream mdFileReader = new DigestInputStream(fileReader, md);
                        out.writeLong(length);
                        out.writeUTF(file.getName());
                        out.flush();

                        int bytes;

                        while ((bytes = mdFileReader.read(buffer)) != -1) {
                            out.write(buffer, 0, bytes);
                            sent += bytes;
                        }

                        logger.info("Finished sending file; sending hash");

                        byte[] digest = md.digest();
                        out.writeInt(digest.length);
                        out.write(digest);

                        out.flush();
                    }
                    catch (Exception e) {
                        logger.error("Exception occurred sending file, sending empty data to compensate", e);
                        Arrays.fill(buffer, (byte) 0);
                        int amt;
                        while (sent < length) {
                            amt = (int) Math.min(length - sent, buffer.length);
                            out.write(buffer, 0, amt);
                            sent += amt;
                        }
                    }
                }
                catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException("Failed to find algorithm", e);
                }
            }
        }, (in)->{
            String pass = in.readUTF(); //pass
            long fileLength = in.readLong();

            if (fileLength == -1) {
                return new Message(FILE, pass, "No file received", null);
            }
            else if (fileLength == -2) {
                return new Message(FILE, pass, "File too large", null);
            }

            String filename = in.readUTF();

            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                DigestInputStream mdInputReader = new DigestInputStream(in, md);

                int bytes;
                List<byte[]> fileData = new ArrayList<>();

                while (fileLength > 0) {
                    byte[] buffer = new byte[4 * 1024];
                    bytes = mdInputReader.read(buffer, 0, (int)Math.min(buffer.length, fileLength));
                    if (bytes == -1) {
                        break;
                    }

                    if (bytes < buffer.length) {
                        byte[] temp = new byte[bytes];
                        System.arraycopy(buffer, 0, temp, 0, bytes);
                        //nextBuffer = buffer;
                        buffer = temp;
                    }

                    fileData.add(buffer);
                    fileLength -= bytes;
                }

                logger.info("Finished receiving file. Checking hash.");
                byte[] digest = md.digest();
                int len = in.readInt();
                byte[] receivedHash = new byte[len];
                int read = in.read(receivedHash);
                if (read != len) {
                    return new Message(FILE, pass, "Failed to verify received file.", null);
                }

                if (!Arrays.equals(digest, receivedHash)) {
                    return new Message(FILE, pass, "Hash of received file does not match.", null);
                }

                logger.info("Verified file successfully.");

                return new Message(FILE, pass, filename, fileData);
            }
            catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Failed to find algorithm", e);
            }
        });
    }

    public static void registerMessageType(int identifier, Function<Object[], MessageSender> messageBuilder, MessageReceiver messageReceiver) {
        messageBuilders.put(identifier, messageBuilder);
        messageReceivers.put(identifier, messageReceiver);
    }

    public ConnectionClient(String name, Socket socket) throws IOException {
        this.name = name;
        this.socket = socket;

        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        ConnectionClient o = this;
        senderThread = new Thread(()->{
            try {
                while (socket.isConnected() && !socket.isClosed()) {
                    synchronized (o.senderThread) {
                        o.senderThread.wait(500);
                    }

                    while (!messageSendQueue.isEmpty()) {
                        MessageSender msg = messageSendQueue.poll();

                        try {
                            msg.send(out);
                        } catch (IOException e) {
                            logger.error("Exception occurred sending message", e);
                        }
                    }
                }
                logger.info("Socket closed, client message sender thread terminating");
            } catch (InterruptedException e) {
                logger.info("Client message sender thread interrupted");
            } catch (Exception e) {
                logger.error("Exception occurred in client message sender thread", e);
            }
        });
        senderThread.setName(socket.getInetAddress().toString() + " sender");
        senderThread.setDaemon(true);
        senderThread.start();
    }

    public void send(String msg) {
        messageSendQueue.add(messageBuilders.get(UTF).apply(new Object[] { msg }));
        synchronized (senderThread) {
            senderThread.notify();
        }
    }

    public void send(int identifier, Object... params) {
        messageSendQueue.add(messageBuilders.get(identifier).apply(params));
        synchronized (senderThread) {
            senderThread.notify();
        }
    }

    public void sendFile(String pass, FileHandle file) {
        send(FILE, pass, file);
    }

    public Message receiveMessage() throws IOException {
        int identifier;
        do {
            identifier = in.read();
        } while (identifier == 0);

        if (identifier == -1) {
            try {
                logger.info("Socket closed while waiting for identifier.");
                close();
            }
            catch (Exception e) {
                logger.error("Exception occurred while closing", e);
            }
            return null;
        }

        MessageReceiver receiver = messageReceivers.get(identifier);

        if (receiver == null) {
            logger.warn("Received unknown message type identifier: 0x" + Integer.toString(identifier, 16));
            return new Message(UTF, "");
        }

        return receiver.receiveMessage(in);
    }

    public void checkClient(String pass, ConcurrentLinkedQueue<ConnectionClient> incomingClients) {
        if (receiverThread == null || !receiverThread.isAlive()) {
            receiverThread = new Thread(()->{
                try {
                    logger.info("Waiting for password from client.");
                    String testPass = "";
                    for (int tries = 3; tries > 0; --tries) {
                        Message msg = receiveMessage();
                        if (msg == null) {
                            logger.info("Null message, password wait cancelled.");
                            return;
                        }
                        else if (msg.identifier == UTF) {
                            String text = msg.contents[0].toString();
                            if (text.startsWith("$")) {
                                logger.info("Received client name.");
                                name = text.substring(1);
                                ++tries;
                                continue;
                            }
                            else {
                                testPass = msg.contents[0].toString();
                            }
                            break;
                        }
                    }

                    if (pass.equals(testPass)) {
                        logger.info("Client successfully validated.");
                        this.validated = true;
                        incomingClients.add(this);
                    }
                    else {
                        logger.info("Incorrect password.");
                    }
                }
                catch (SocketException e) {
                    logger.info("Socket closed while waiting for pass.");
                }
                catch (Exception e) {
                    logger.error("Exception occurred while waiting for pass", e);
                }
            });
            receiverThread.setName(socket.getInetAddress().toString() + " password listener");
            receiverThread.setDaemon(true);
            receiverThread.start();

            Thread passwordTimeout = new Thread(()->{
                try {
                    Thread.sleep(45 * 1000);
                } catch (InterruptedException e) {
                    logger.error("Exception occurred in password timeout thread", e);
                }
                finally {
                    try {
                        if (!this.validated) {
                            logger.info("Password wait timed out and client not validated, closing");
                            close();
                        }
                    } catch (Exception e) {
                        logger.error("Exception occurred while closing client", e);
                    }
                }
            });
            passwordTimeout.setName(socket.getInetAddress().toString() + " password timeout");
            passwordTimeout.setDaemon(true);
            passwordTimeout.start();
        }
    }

    public boolean waitValidation(String pass) {
        try {
            send("$" + name);
            send(pass);
            Message message = receiveMessage();
            if (message != null && message.identifier == UTF) {
                String contents = message.contents[0].toString();
                if (contents.startsWith("success")) {
                    ID = Integer.parseInt(contents.substring(7));
                    validated = true;
                    logger.info("Client validated with ID " + ID);
                    return true;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private Consumer<ConnectionClient> onDeath = null;
    private void onDeath() {
        Consumer<ConnectionClient> temp = onDeath;
        onDeath = null;
        if (temp != null) {
            temp.accept(this);
        }
    }
    public void startStandardReceiver() {
        if (receiverThread == null || !receiverThread.isAlive()) {
            receiverThread = new Thread(() -> {
                try {
                    while (socket.isConnected() && !socket.isClosed()) {
                        Message msg = receiveMessage();
                        if (msg == null) {
                            logger.info("Received null message, standard receiver cancelled.");
                            break;
                        }

                        if (msg.identifier == UTF) {
                            String text = msg.contents[0].toString();
                            if (text.startsWith(ConnectionServer.SERVER_MSG)) {
                                if (processServerMessage(text.substring(5))) {
                                    continue;
                                }
                            }
                        }

                        receivedMessages.add(msg);
                    }
                }
                catch (SocketException | NullPointerException e) {
                    logger.info("Standard receiver cancelled, socket closed.");
                }
                catch (IOException e) {
                    logger.error("Exception occurred while listening for messages", e);
                }
                finally {
                    onDeath();
                }
                try {
                    close();
                } catch (Exception ignored) { }
            });
            receiverThread.setName(socket.getInetAddress().toString() + " receiver");
            receiverThread.setDaemon(true);
            receiverThread.start();
        }
    }

    private boolean processServerMessage(String msg) {
        logger.info("Received server message: " + msg);
        switch (msg.substring(0, 6)) {
            case "MEMBER":
                connectionList.add(msg.substring(6));
                return true;
            case "REMOVE":
                connectionList.remove(msg.substring(6));
                return true;
        }
        return false;
    }

    /**
     * Sets an action to be performed if the socket is closed or another exception occurs in the standard receiver.
     * @param action The action to be performed
     */
    public void onDeath(Consumer<ConnectionClient> action) {
        this.onDeath = action;
    }

    public void fail(String reason) {
        failure = reason;
        try {
            close();
        }
        catch (Exception e) {
            logger.error("Exception occurred while closing connection.", e);
        }
    }

    @Override
    public void close() throws Exception {
        socket.close();

        try {
            if (receiverThread != null && receiverThread.isAlive()) {
                receiverThread.interrupt();
                receiverThread = null;
            }
        }
        catch (Exception ignored) { }

        try {
            out.close();
        }
        catch (Exception ignored) { }
        try {
            in.close();
        }
        catch (Exception ignored) { }
        try {
            onDeath();
        }
        catch (Exception e) {
            logger.info("Exception occurred in ConnectionClient onDeath:", e);
        }
    }

    public boolean isConnected() {
        return socket.isConnected();
    }

    public boolean isAlive() {
        return socket.isConnected() && !socket.isClosed();
    }

    public void renderConnectedNames(TextRenderer textRenderer, SpriteBatch sb, Texture connectedTex, BitmapFont font, float rightX, float opacity) {
        nameRenderingColor.a = opacity;

        rightX -= connectedTex.getWidth();
        float y = (connectionList.size() + 1) * 32;
        float texY = y - connectedTex.getHeight();

        textRenderer.setFont(font);
        sb.setColor(nameRenderingColor);

        sb.draw(connectedTex, rightX, texY);
        textRenderer.renderTextRightAlign(sb, name, rightX - 5, y, nameRenderingColor);
        for (String name : connectionList) {
            y -= 32;
            texY -= 32;

            sb.draw(connectedTex, rightX, texY);
            textRenderer.renderTextRightAlign(sb, name, rightX - 5, y, nameRenderingColor);
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
