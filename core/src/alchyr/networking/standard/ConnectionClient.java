package alchyr.networking.standard;

import com.badlogic.gdx.files.FileHandle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;

import static alchyr.networking.standard.Message.FILE;
import static alchyr.networking.standard.Message.UTF;

public class ConnectionClient implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger("connection client");

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

                try (FileInputStream fileReader = new FileInputStream(file)) {
                    out.writeLong(length);
                    out.writeUTF(file.getName());
                    out.flush();

                    int bytes;

                    while ((bytes = fileReader.read(buffer)) != -1) {
                        out.write(buffer, 0, bytes);
                        sent += bytes;
                    }
                    out.flush();

                    logger.info("Finished sending file.");
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

            int bytes;
            List<byte[]> fileData = new ArrayList<>();

            while (fileLength > 0) {
                byte[] buffer = new byte[4 * 1024];
                bytes = in.read(buffer, 0, (int)Math.min(buffer.length, fileLength));
                if (bytes == -1) {
                    break;
                }

                /*if (!GeneralUtils.arraySectionEquals(buffer, nextBuffer, 0, bytes)) {
                    logger.warn("Data confirmation failed, error occurred in transfer");
                    return new Message(FILE, pass, "Data lost in transfer", null);
                }*/

                if (bytes < buffer.length) {
                    byte[] temp = new byte[bytes];
                    System.arraycopy(buffer, 0, temp, 0, bytes);
                    //nextBuffer = buffer;
                    buffer = temp;
                }

                fileData.add(buffer);
                fileLength -= bytes;
            }

            return new Message(FILE, pass, filename, fileData);
        });
    }

    public static void registerMessageType(int identifier, Function<Object[], MessageSender> messageBuilder, MessageReceiver messageReceiver) {
        messageBuilders.put(identifier, messageBuilder);
        messageReceivers.put(identifier, messageReceiver);
    }

    public ConnectionClient(Socket socket) throws IOException {
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

    public void waitPass(String pass, ConcurrentLinkedQueue<ConnectionClient> incomingClients) {
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
                            testPass = msg.contents[0].toString();
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

    public boolean waitValidation() {
        try {
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

    @Override
    public String toString() {
        return socket.getInetAddress().toString();
    }
}
