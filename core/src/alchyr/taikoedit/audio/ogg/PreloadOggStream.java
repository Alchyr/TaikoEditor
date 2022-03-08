package alchyr.taikoedit.audio.ogg;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.StreamUtils;
import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class PreloadOggStream extends InputStream {
    private final static int BUFFER_SIZE = 512;

    /** The conversion buffer size */
    private int convsize = BUFFER_SIZE * 4;
    /** The buffer used to read OGG file */
    private byte[] convbuffer;
    /** The stream we're reading the OGG file from */
    private InputStream input;
    /** The audio information from the OGG header */
    private Info oggInfo = new Info(); // struct that stores all the static vorbis bitstream settings
    /** True if we're at the end of the available data */
    private boolean endOfStream;

    /** The Vorbis SyncState used to decode the OGG */
    private SyncState syncState = new SyncState(); // sync and verify incoming physical bitstream
    /** The Vorbis Stream State used to decode the OGG */
    private StreamState streamState = new StreamState(); // take physical pages, weld into a logical stream of packets
    /** The current OGG page */
    private Page page = new Page(); // one Ogg bitstream page. Vorbis packets are inside
    /** The current packet page */
    private Packet packet = new Packet(); // one raw packet of data for decode

    /** The comment read from the OGG file */
    private Comment comment = new Comment(); // struct that stores all the bitstream user comments
    /** The Vorbis DSP stat eused to decode the OGG */
    private DspState dspState = new DspState(); // central working state for the packet->PCM decoder
    /** The OGG block we're currently working with to convert PCM */
    private Block vorbisBlock = new Block(dspState); // local working space for packet->PCM decode

    /** Temporary scratch buffer */
    byte[] buffer;
    /** The number of bytes read */
    int bytes = 0;
    /** The true if we should be reading big endian */
    boolean bigEndian = ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);
    /** True if we're reached the end of the current bit stream */
    boolean endOfBitStream = true;
    /** True if we're initialise the OGG info block */
    boolean inited = false;

    /** The index into the byte array we currently read from */
    private int readIndex;
    /** The byte array store used to hold the data read from the ogg */
    private ByteBuffer pcmBuffer;
    /** The total number of bytes */
    private int total;


    //preload progress
    public static float progress = 0;

    //post preload data
    private float length = 0;
    private int totalUncompressedBytes = 0;
    private List<byte[]> segmentedData;
    private float secondsPerSegment;
    private int currentSegment = 0;
    private int dataIndex = 0;



    /** Create a new stream to decode OGG data
     *
     * @param input The input stream from which to read the OGG file */
    public PreloadOggStream(InputStream input) {
        this(input, null);
    }

    /** Create a new stream to decode OGG data, reusing buffers from another stream.
     *
     * It's not a good idea to use the old stream instance afterwards.
     *
     * @param input The input stream from which to read the OGG file
     * @param previousStream The stream instance to reuse buffers from, may be null */
    public PreloadOggStream(InputStream input, PreloadOggStream previousStream) {
        if (previousStream == null) {
            convbuffer = new byte[convsize];
            pcmBuffer = BufferUtils.createByteBuffer(4096 * 500);
        } else {
            convbuffer = previousStream.convbuffer;
            pcmBuffer = previousStream.pcmBuffer;
        }

        this.input = input;
        try {
            total = input.available();
            preload();
            secondsPerSegment = (float) SEGMENT_SIZE / (2.0f * oggInfo.channels * oggInfo.rate);
            //Will be used to calculate correct index based on a given position
            length = (float) (secondsPerSegment * ((double) totalUncompressedBytes / SEGMENT_SIZE));
        } catch (IOException ex) {
            throw new GdxRuntimeException(ex);
        }

        close();
    }

    /** Get the number of bytes on the stream
     *
     * @return The number of the bytes on the stream */
    public int getTotalBytes() {
        return total;
    }
    //playback duration
    public float getLength() { return length; }

    public int getChannels() {
        return oggInfo.channels;
    }

    public int getSampleRate() {
        return oggInfo.rate;
    }

    private static final int SEGMENT_SIZE = 4096 * 4;
    private void preload() {
        initVorbis(); //prepare for decoding

        progress = 0;

        try {
            readPCM(); //init (reading first 3 header packets)

            //From here, each read() call returns a single byte in int form.
            segmentedData = new ArrayList<>();
            byte[] temp;
            int n = 0, value;
            totalUncompressedBytes = 0;

            while (n >= 0) {
                progress = 1.0f - (input.available() / (float)this.total);
                temp = new byte[SEGMENT_SIZE];

                n = SEGMENT_SIZE;
                for (int i = 0; i < SEGMENT_SIZE; ++i) {
                    if (readIndex >= pcmBuffer.position()) {
                        pcmBuffer.clear();
                        readPCM();
                        readIndex = 0;
                    }
                    if (readIndex >= pcmBuffer.position()) {
                        value = -1;
                    }
                    else {
                        value = pcmBuffer.get(readIndex);
                        if (value < 0) {
                            value = 256 + value;
                        }
                        ++readIndex;
                    }

                    if (value >= 0) {
                        temp[i] = (byte)value;
                    } else {
                        if (i == 0) {
                            n = -1; //nothing read, end of stream
                        } else {
                            n = i;
                        }
                        break;
                    }
                }

                totalUncompressedBytes += n;

                if (n == SEGMENT_SIZE) {
                    segmentedData.add(temp);
                }
                else if (n > 0) {
                    segmentedData.add(new byte[n]);
                    System.arraycopy(temp, 0, segmentedData.get(segmentedData.size() - 1), 0, n);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** @see java.io.InputStream#available() */
    public int available () {
        return endOfStream ? 0 : 1;
    }

    /** Initialise the vorbis decoding */
    private void initVorbis () {
        syncState.init();
    }

    /** Get a page and packet from that page
     *
     * @return True if there was a page available */
    private boolean getPageAndPacket() {
        // grab some data at the head of the stream. We want the first page
        // (which is guaranteed to be small and only contain the Vorbis
        // stream initial header) We need the first page to get the stream
        // serialno.

        // submit a 4k block to libvorbis' Ogg layer
        int index = syncState.buffer(BUFFER_SIZE);
        if (index == -1) return false;

        buffer = syncState.data;
        if (buffer == null) {
            endOfStream = true;
            return false;
        }

        try {
            bytes = input.read(buffer, index, BUFFER_SIZE);
        } catch (Exception e) {
            throw new GdxRuntimeException("Failure reading Vorbis.", e);
        }
        syncState.wrote(bytes);

        // Get the first page.
        if (syncState.pageout(page) != 1) {
            // have we simply run out of data? If so, we're done.
            if (bytes < BUFFER_SIZE) return false;

            // error case. Must not be Vorbis data
            throw new GdxRuntimeException("Input does not appear to be an Ogg bitstream.");
        }

        // Get the serial number and set up the rest of decode.
        // serialno first; use it to set up a logical stream
        streamState.init(page.serialno());

        // extract the initial header from the first page and verify that the
        // Ogg bitstream is in fact Vorbis data

        // I handle the initial header first instead of just having the code
        // read all three Vorbis headers at once because reading the initial
        // header is an easy way to identify a Vorbis bitstream and it's
        // useful to see that functionality seperated out.

        oggInfo.init();
        comment.init();
        if (streamState.pagein(page) < 0) {
            // error; stream version mismatch perhaps
            throw new GdxRuntimeException("Error reading first page of Ogg bitstream.");
        }

        if (streamState.packetout(packet) != 1) {
            // no page? must not be vorbis
            throw new GdxRuntimeException("Error reading initial header packet.");
        }

        if (oggInfo.synthesis_headerin(comment, packet) < 0) {
            // error case; not a vorbis header
            throw new GdxRuntimeException("Ogg bitstream does not contain Vorbis audio data.");
        }

        // At this point, we're sure we're Vorbis. We've set up the logical
        // (Ogg) bitstream decoder. Get the comment and codebook headers and
        // set up the Vorbis decoder

        // The next two packets in order are the comment and codebook headers.
        // They're likely large and may span multiple pages. Thus we reead
        // and submit data until we get our two pacakets, watching that no
        // pages are missing. If a page is missing, error out; losing a
        // header page is the only place where missing data is fatal. */

        int i = 0;
        while (i < 2) {
            while (i < 2) {
                int result = syncState.pageout(page);
                if (result == 0) break; // Need more data
                // Don't complain about missing or corrupt data yet. We'll
                // catch it at the packet output phase

                if (result == 1) {
                    streamState.pagein(page); // we can ignore any errors here
                    // as they'll also become apparent
                    // at packetout
                    while (i < 2) {
                        result = streamState.packetout(packet);
                        if (result == 0) break;
                        if (result == -1) {
                            // Uh oh; data at some point was corrupted or missing!
                            // We can't tolerate that in a header. Die.
                            throw new GdxRuntimeException("Corrupt secondary header.");
                        }

                        oggInfo.synthesis_headerin(comment, packet);
                        i++;
                    }
                }
            }
            // no harm in not checking before adding more
            index = syncState.buffer(BUFFER_SIZE);
            if (index == -1) return false;
            buffer = syncState.data;
            try {
                bytes = input.read(buffer, index, BUFFER_SIZE);
            } catch (Exception e) {
                throw new GdxRuntimeException("Failed to read Vorbis.", e);
            }
            if (bytes == 0 && i < 2) {
                throw new GdxRuntimeException("End of file before finding all Vorbis headers.");
            }
            syncState.wrote(bytes);
        }

        convsize = BUFFER_SIZE / oggInfo.channels;

        // OK, got and parsed all three headers. Initialize the Vorbis
        // packet->PCM decoder.
        dspState.synthesis_init(oggInfo); // central decode state
        vorbisBlock.init(dspState); // local state for most of the decode
        // so multiple block decodes can
        // proceed in parallel. We could init
        // multiple vorbis_block structures
        // for vd here

        return true;
    }

    /** Decode the OGG file as shown in the jogg/jorbis examples */
    private void readPCM() {
        boolean wrote = false;

        while (true) { // we repeat if the bitstream is chained
            if (endOfBitStream) {
                if (!getPageAndPacket()) {
                    break;
                }
                endOfBitStream = false;
            }

            if (!inited) {
                inited = true;
                return;
            }

            float[][][] _pcm = new float[1][][];
            int[] _index = new int[oggInfo.channels];
            // The rest is just a straight decode loop until end of stream
            while (!endOfBitStream) {
                while (!endOfBitStream) {
                    int result = syncState.pageout(page);

                    if (result == 0) {
                        break; // need more data
                    }

                    if (result == -1) { // missing or corrupt data at this page position
                        // throw new GdxRuntimeException("Corrupt or missing data in bitstream.");
                        Gdx.app.log("gdx-audio", "Error reading OGG: Corrupt or missing data in bitstream.");
                    } else {
                        streamState.pagein(page); // can safely ignore errors at
                        // this point
                        while (true) {
                            result = streamState.packetout(packet);

                            if (result == 0) break; // need more data
                            if (result == -1) { // missing or corrupt data at this page position
                                // no reason to complain; already complained above
                            } else {
                                // we have a packet. Decode it
                                int samples;
                                if (vorbisBlock.synthesis(packet) == 0) { // test for success!
                                    dspState.synthesis_blockin(vorbisBlock);
                                }

                                // **pcm is a multichannel float vector. In stereo, for
                                // example, pcm[0] is left, and pcm[1] is right. samples is
                                // the size of each channel. Convert the float values
                                // (-1.<=range<=1.) to whatever PCM format and write it out

                                while ((samples = dspState.synthesis_pcmout(_pcm, _index)) > 0) {
                                    float[][] pcm = _pcm[0];
                                    // boolean clipflag = false;
                                    int bout = (samples < convsize ? samples : convsize);

                                    // convert floats to 16 bit signed ints (host order) and
                                    // interleave
                                    for (int i = 0; i < oggInfo.channels; i++) {
                                        int ptr = i * 2;
                                        // int ptr=i;
                                        int mono = _index[i];
                                        for (int j = 0; j < bout; j++) {
                                            int val = (int)(pcm[i][mono + j] * 32767.);
                                            // might as well guard against clipping
                                            if (val > 32767) {
                                                val = 32767;
                                            }
                                            if (val < -32768) {
                                                val = -32768;
                                            }
                                            if (val < 0) val = val | 0x8000;

                                            if (bigEndian) {
                                                convbuffer[ptr] = (byte)(val >>> 8);
                                                convbuffer[ptr + 1] = (byte)(val);
                                            } else {
                                                convbuffer[ptr] = (byte)(val);
                                                convbuffer[ptr + 1] = (byte)(val >>> 8);
                                            }
                                            ptr += 2 * (oggInfo.channels);
                                        }
                                    }

                                    int bytesToWrite = 2 * oggInfo.channels * bout;
                                    if (bytesToWrite > pcmBuffer.remaining()) {
                                        throw new GdxRuntimeException("Ogg block too big to be buffered: " + bytesToWrite + " :: " + pcmBuffer.remaining());
                                    } else {
                                        pcmBuffer.put(convbuffer, 0, bytesToWrite);
                                    }

                                    wrote = true;
                                    dspState.synthesis_read(bout); // tell libvorbis how
                                    // many samples we
                                    // actually consumed
                                }
                            }
                        }
                        if (page.eos() != 0) {
                            endOfBitStream = true;
                        }

                        if ((!endOfBitStream) && (wrote)) {
                            return;
                        }
                    }
                }

                if (!endOfBitStream) {
                    bytes = 0;
                    int index = syncState.buffer(BUFFER_SIZE);
                    if (index >= 0) {
                        buffer = syncState.data;
                        try {
                            bytes = input.read(buffer, index, BUFFER_SIZE);
                        } catch (Exception e) {
                            throw new GdxRuntimeException("Error during Vorbis decoding.", e);
                        }
                    } else {
                        bytes = 0;
                    }
                    syncState.wrote(bytes);
                    if (bytes == 0) {
                        endOfBitStream = true;
                    }
                }
            }

            // clean up this logical bitstream; before exit we see if we're
            // followed by another [chained]
            streamState.clear();

            // ogg_page and ogg_packet structs always point to storage in
            // libvorbis. They're never freed or manipulated directly

            vorbisBlock.clear();
            dspState.clear();
            oggInfo.clear(); // must be called last
        }

        // OK, clean up the framer
        syncState.clear();
        endOfStream = true;
    }



    //Read methods
    @Override
    public boolean markSupported() {
        return true;
    }
    private int markedSegment = 0;
    private int markedIndex = 0;
    @Override
    public synchronized void mark(int readlimit) {
        markedSegment = currentSegment;
        markedIndex = dataIndex;
    }
    @Override
    public synchronized void reset() throws IOException {
        currentSegment = markedSegment;
        dataIndex = markedIndex;
    }

    public void restart() {
        currentSegment = 0;
        dataIndex = 0;
    }

    public float seekTime(float pos) {
        if (pos < 0) {
            restart();
            return 0;
        }

        currentSegment = (int) (pos / secondsPerSegment);
        dataIndex = 0; //(int) ((pos - secondsPerSegment * currentSegment) * SEGMENT_SIZE) % 2;

        if (currentSegment >= segmentedData.size()) {
            currentSegment = segmentedData.size();
            return length; //seeked past end of song, return end of song
        }

        /*if (dataIndex > segmentedData.get(currentSegment).length) {
            dataIndex = segmentedData.get(currentSegment).length - 1;
        }*/

        return secondsPerSegment * (currentSegment + (float)dataIndex / SEGMENT_SIZE);
    }

    public int read() {
        if (currentSegment >= segmentedData.size()) {
            return -1;
        }
        else {
            if (dataIndex >= segmentedData.get(currentSegment).length) {
                dataIndex = 0;
                ++currentSegment;
                if (currentSegment >= segmentedData.size()) {
                    return -1;
                }
            }
            return segmentedData.get(currentSegment)[dataIndex++] + 256;
        }
    }

    public boolean atEnd() {
        return currentSegment >= segmentedData.size();
    }

    public int read(byte[] b, int off, int len) {
        for (int i = 0; i < len; ++i) {
            int value = read();
            if (value >= 0) {
                b[i] = (byte)value;
            } else {
                if (i == 0) {
                    return -1; //nothing read, end of stream
                } else {
                    return i;
                }
            }
        }

        return len;
    }

    public int read(byte[] b) {
        return read(b, 0, b.length);
    }

    public void close() {
        StreamUtils.closeQuietly(input);
    }
}
