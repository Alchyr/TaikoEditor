package alchyr.taikoedit.audio;


import alchyr.taikoedit.audio.decoders.LayerIDecoder;
import alchyr.taikoedit.audio.decoders.LayerIIDecoder;
import alchyr.taikoedit.audio.decoders.LayerIIIDecoder;
import com.badlogic.gdx.utils.GdxRuntimeException;
import javazoom.jl.decoder.*;

import java.io.*;
import java.util.*;

public class PreloadMp3Bitstream {
    /* * * Track Data * * */
    private String album = ""; //TALB
    private String composer = ""; //TCOM
    private String title = ""; //TIT2
    private String leadArtists = ""; //TPE1
    private String band = ""; //TPE2

    public String getAlbum() {
        return album;
    }
    public String getComposer() {
        return composer;
    }
    public String getTitle() {
        return title;
    }
    public String getLeadArtists() {
        return leadArtists;
    }
    public String getBand() {
        return band;
    }

    public int channels;
    public int sampleRate;

    /* * * Bitstream fields * * */
    private static final int CHANNELSIZE = 2 * 1152; // max. 2 * 1152 samples per frame

    private final List<byte[]> frames = new ArrayList<>();
    private final List<Float> frameTimes = new ArrayList<>();

    private int currentFrame;

    private float maxSecondsPerFrame;
    public float length;

    /**
     * Synchronization control constant for the initial synchronization to the start of a frame.
     */
    static byte INITIAL_SYNC = 0;

    /**
     * Synchronization control constant for non-initial frame synchronizations.
     */
    static byte STRICT_SYNC = 1;

    // max. 1730 bytes per frame: 144 * 384kbit/s / 32000 Hz + 2 Bytes CRC
    /**
     * Maximum size of the frame buffer.
     */
    private static final int BUFFER_INT_SIZE = 433;

    /**
     * The frame buffer that holds the data for the current frame.
     */
    private final int[] framebuffer = new int[BUFFER_INT_SIZE];

    /**
     * Number of valid bytes in the frame buffer.
     */
    private int framesize;

    /**
     * The bytes read from the stream.
     */
    private byte[] frame_bytes = new byte[BUFFER_INT_SIZE * 4];

    /**
     * Index into <code>framebuffer</code> where the next bits are retrieved.
     */
    private int wordpointer;

    /**
     * Number (0-31, from MSB to LSB) of next bit for get_bits()
     */
    private int bitindex;

    /**
     * The current specified syncword
     */
    private int syncword;

    /**
     * Audio header position in stream.
     */
    private int header_pos = 0;

    private Float replayGainScale;

    /**
     *
     */
    private boolean single_ch_mode;
    // private int current_frame_number;
    // private int last_frame_number;

    private final int bitmask[] = {
            0, // dummy
            0x00000001, 0x00000003, 0x00000007, 0x0000000F, 0x0000001F, 0x0000003F, 0x0000007F, 0x000000FF, 0x000001FF, 0x000003FF,
            0x000007FF, 0x00000FFF, 0x00001FFF, 0x00003FFF, 0x00007FFF, 0x0000FFFF, 0x0001FFFF};

    private final PushbackInputStream source;

    private final Header header = new Header();

    private final byte syncbuf[] = new byte[4];

    private Crc16[] crc = new Crc16[1];

    private byte[] rawid3v2 = null;

    private boolean firstframe = true;


    /* * * Decoder fields * * */

    /**
     * The Obuffer instance that will receive the decoded PCM samples.
     */
    private OutputBuffer output;

    /**
     * Synthesis filter for the left channel.
     */
    private SynthesisFilter filter1;

    /**
     * Sythesis filter for the right channel.
     */
    private SynthesisFilter filter2;

    private LayerIIIDecoder l3decoder;
    private LayerIIDecoder l2decoder;
    private LayerIDecoder l1decoder;

    private int outputFrequency;
    private int outputChannels;

    private boolean initialized;

    /**
     * Construct a IBitstream that reads data from a given InputStream.
     *
     * @param in The InputStream to read from.
     */
    public PreloadMp3Bitstream (InputStream in) {
        if (in == null) throw new NullPointerException("in");
        in = new BufferedInputStream(in);
        loadID3v2(in); //After loading ID3v2, position will be at first music frame
        firstframe = true;
        // source = new PushbackInputStream(in, 1024);
        source = new PushbackInputStream(in, BUFFER_INT_SIZE * 4);

        closeFrame();
        // current_frame_number = -1;
        // last_frame_number = -1;
        preload();

        try
        {
            close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    /* * * * * * * * * * * * PRELOADED * * * * * * * * * * * */

    private void preload()
    {
        int totalBytes = 0;
        int bufferSize = 1;
        try
        {
            if (readFrame() == null) //First header is just data
            {
                throw new GdxRuntimeException("Empty MP3");
            }
            channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
            sampleRate = header.getSampleRate();
            bufferSize = CHANNELSIZE * channels;

            maxSecondsPerFrame = (float)bufferSize / (2 * channels * sampleRate); //2 is number of bytes per sample

            OutputBuffer outputBuffer = new OutputBuffer(channels, false);
            setOutputBuffer(outputBuffer);

            frameTimes.add(0.0f); //The first frame STARTS at 0.

            Header h = header;
            while (h != null)
            {
                if ((h = readFrame()) == null) //Reads frame using header
                {
                    //frame is null, means stream is done.
                    continue;
                }
                decodeFrame(); //Decodes frame into output buffer

                int length = outputBuffer.reset();
                totalBytes += length;
                byte[] buffer = new byte[bufferSize];
                System.arraycopy(outputBuffer.getBuffer(), 0, buffer, 0, length);

                frames.add(buffer);
                frameTimes.add(maxSecondsPerFrame * (float)totalBytes / bufferSize); //This is the start time of the NEXT frame. (the end time of the current frame)

                closeFrame(); //Close frame to prepare to read next frame
            }
        } catch (BitstreamException | DecoderException e) {
            e.printStackTrace();
        }

        //Calculate total length using total number of bytes
        length = maxSecondsPerFrame * (float)totalBytes / bufferSize;
    }

    public byte[] getNextFrame()
    {
        if (currentFrame < frames.size())
            return frames.get(currentFrame++);
        return null;
    }
    public boolean hasNextFrame()
    {
        return currentFrame < frames.size();
    }

    public void setFrame(int frame)
    {
        if (frame > frames.size())
            frame = frames.size();
        currentFrame = frame;
    }

    public float seekTime(float time)
    {
        int index = Collections.binarySearch(frameTimes, time);

        if (index >= 0)
        {
            currentFrame = index;
        }
        else
        {
            currentFrame = -index - 1;
        }

        if (currentFrame < frameTimes.size())
        {
            while (currentFrame > 0 && frameTimes.get(currentFrame) > time)
            {
                --currentFrame;
            }
            return frameTimes.get(currentFrame);
        }
        return length;
    }

    public void clear()
    {
        frames.clear();
    }

    /* * * * * * Decoder * * * * * */

    /**
     * Decodes one frame from an MPEG audio bitstream.
     *
     */
    private void decodeFrame () throws DecoderException {
        if (!initialized) initialize(header);

        int layer = header.layer();

        FrameDecoder decoder = retrieveDecoder(header, layer);

        decoder.decodeFrame();

    }

    /**
     * Changes the output buffer. This will take effect the next time decodeFrame() is called.
     */
    public void setOutputBuffer (OutputBuffer out) {
        output = out;
    }

    /**
     * Retrieves the sample frequency of the PCM samples output by this decoder. This typically corresponds to the sample rate
     * encoded in the MPEG audio stream.
     *
     * @return the sample rate (in Hz) of the samples written to the output buffer when decoding.
     */
    public int getOutputFrequency () {
        return outputFrequency;
    }

    /**
     * Retrieves the number of channels of PCM samples output by this decoder. This usually corresponds to the number of channels
     * in the MPEG audio stream, although it may differ.
     *
     * @return The number of output channels in the decoded samples: 1 for mono, or 2 for stereo.
     *
     */
    public int getOutputChannels () {
        return outputChannels;
    }

    protected DecoderException newDecoderException (int errorcode) {
        return new DecoderException(errorcode, null);
    }

    protected DecoderException newDecoderException (int errorcode, Throwable throwable) {
        return new DecoderException(errorcode, throwable);
    }

    protected FrameDecoder retrieveDecoder (Header header, int layer) throws DecoderException {
        FrameDecoder decoder = null;

        // REVIEW: allow channel output selection type
        // (LEFT, RIGHT, BOTH, DOWNMIX)
        switch (layer) {
            case 3:
                if (l3decoder == null)
                    l3decoder = new LayerIIIDecoder(this, header, filter1, filter2, output, OutputChannels.BOTH_CHANNELS);

                decoder = l3decoder;
                break;
            case 2:
                if (l2decoder == null) {
                    l2decoder = new LayerIIDecoder();
                    l2decoder.create(this, header, filter1, filter2, output, OutputChannels.BOTH_CHANNELS);
                }
                decoder = l2decoder;
                break;
            case 1:
                if (l1decoder == null) {
                    l1decoder = new LayerIDecoder();
                    l1decoder.create(this, header, filter1, filter2, output, OutputChannels.BOTH_CHANNELS);
                }
                decoder = l1decoder;
                break;
        }

        if (decoder == null) throw newDecoderException(UNSUPPORTED_LAYER, null);

        return decoder;
    }

    private void initialize (Header header) throws DecoderException {

        // REVIEW: allow customizable scale factor
        float scalefactor = 32700.0f;

        int mode = header.mode();
        header.layer();
        int channels = mode == javazoom.jl.decoder.Header.SINGLE_CHANNEL ? 1 : 2;

        // set up output buffer if not set up by client.
        if (output == null) throw new RuntimeException("Output buffer was not set.");

        filter1 = new SynthesisFilter(0, scalefactor, null);

        // REVIEW: allow mono output for stereo
        if (channels == 2) filter2 = new SynthesisFilter(1, scalefactor, null);

        outputChannels = channels;
        outputFrequency = header.frequency();

        initialized = true;
    }










    /* * * * * * Bitstream * * * * * */

    /**
     * Return position of the first audio header.
     * @return size of ID3v2 tag frames.
     */
    public int header_pos () {
        return header_pos;
    }

    /**
     * Load ID3v2 frames.
     * @param in MP3 InputStream.
     * @author JavaZOOM
     */
    private void loadID3v2 (InputStream in) {
        int size = -1;
        try {
            // Read ID3v2 header (10 bytes).
            in.mark(10);
            size = readID3v2Header(in);
            header_pos = size;
        } catch (IOException e) {
        } finally {
            try {
                // Unread ID3v2 header (10 bytes).
                in.reset();
            } catch (IOException e) {
            }
        }
        // Load ID3v2 tags.
        try {
            if (size > 0) {
                rawid3v2 = new byte[size];
                in.read(rawid3v2, 0, rawid3v2.length);
                parseID3v2Frames(rawid3v2);
            }
        } catch (IOException e) {
        }
    }

    /**
     * Parse ID3v2 tag header to find out size of ID3v2 frames.
     * @param in MP3 InputStream
     * @return size of ID3v2 frames + header
     * @throws IOException
     * @author JavaZOOM
     */
    private int readID3v2Header (InputStream in) throws IOException {
        byte[] id3header = new byte[4];
        int size = -10;
        in.read(id3header, 0, 3); //IF ID3v2, first three bytes are identifier
        // Look for ID3v2
        if (id3header[0] == 'I' && id3header[1] == 'D' && id3header[2] == '3') {
            in.read(id3header, 0, 3); //version + flags
            in.read(id3header, 0, 4); //size not including header
            size = (id3header[0] << 21) + (id3header[1] << 14) + (id3header[2] << 7) + id3header[3];
        }
        return size + 10; //Include header in size
    }

    /**
     * Return raw ID3v2 frames + header.
     * @return ID3v2 InputStream or null if ID3v2 frames are not available.
     */
    public InputStream getRawID3v2 () {
        if (rawid3v2 == null)
            return null;
        else {
            ByteArrayInputStream bain = new ByteArrayInputStream(rawid3v2);
            return bain;
        }
    }

    private void parseID3v2Frames (byte[] bframes) {
        if (bframes == null) return;
        if (!"ID3".equals(new String(bframes, 0, 3))) return; //Should include header
        int v2version = (int)(bframes[3] & 0xFF); //ID3v2 version
        if (v2version < 2 || v2version > 4) { //unhandled
            return;
        }
        try {
            Float replayGain = null, replayGainPeak = null;
            int size;
            String value = null;
            for (int i = 10; i < bframes.length && bframes[i] > 0; i += size) { //Start from 10, end of header
                if (v2version == 3 || v2version == 4) {
                    // ID3v2.3 & ID3v2.4
                    String code = new String(bframes, i, 4); //First 10 bytes are header. First 4 for code.
                    size = (bframes[i + 4] << 24 & 0xFF000000 | bframes[i + 5] << 16 & 0x00FF0000 | bframes[i + 6] << 8 //Next 4 for size.
                            & 0x0000FF00 | bframes[i + 7] & 0x000000FF); //Size does NOT include header, so it's total frame size - 10.
                    //Last 2 for flags.
                    if ((bframes[i + 9] & 0xFF000000) != 0) //these two bits are whether the frame is compressed/ecrypted. I can't deal with it if it is, so skip it.
                        continue;
                    i += 10; //Move past header. By adding on size to this in the for loop, that results in moving to next frame.

                    String[] values;

                    switch (code)
                    {
                        case "TXXX":
                            value = parseText(bframes, i, size, 1);
                            values = value.split("\0");
                            if (values.length == 2) {
                                String name = values[0];
                                value = values[1];
                                if (name.equals("replaygain_track_peak")) {
                                    replayGainPeak = Float.parseFloat(value);
                                } else if (name.equals("replaygain_track_gain")) {
                                    replayGain = Float.parseFloat(value.replace(" dB", "")) + 3;
                                }
                            }
                            break;
                        case "TALB": //Album
                            value = parseText(bframes, i, size, 1);
                            values = value.split("\0");
                            if (values.length > 0) {
                                album = values[0];
                            }
                            break;
                        case "TCOM": //Composer
                            value = parseText(bframes, i, size, 1);
                            values = value.split("\0");
                            if (values.length > 0) {
                                composer = values[0];
                            }
                        case "TIT2": //Title
                            value = parseText(bframes, i, size, 1);
                            values = value.split("\0");
                            if (values.length > 0) {
                                title = values[0];
                            }
                            break;
                        case "TPE1": //Lead artists
                            value = parseText(bframes, i, size, 1);
                            values = value.split("\0");
                            if (values.length > 0) {
                                leadArtists = values[0];
                            }
                        case "TPE2": //Band
                            value = parseText(bframes, i, size, 1);
                            values = value.split("\0");
                            if (values.length > 0) {
                                band = values[0];
                            }
                    }
                } else {
                    // ID3v2.2
                    String scode = new String(bframes, i, 3);
                    size = (bframes[i + 3] << 16) + (bframes[i + 4] << 8) + bframes[i + 5];
                    i += 6;
                    String[] values;

                    switch (scode)
                    {
                        case "TXXX":
                            value = parseText(bframes, i, size, 1);
                            values = value.split("\0");
                            if (values.length == 2) {
                                String name = values[0];
                                value = values[1];
                                if (name.equals("replaygain_track_peak")) {
                                    replayGainPeak = Float.parseFloat(value);
                                } else if (name.equals("replaygain_track_gain")) {
                                    replayGain = Float.parseFloat(value.replace(" dB", "")) + 3;
                                }
                            }
                            break;
                        case "TALB": //Album
                            value = parseText(bframes, i, size, 1);
                            values = value.split("\0");
                            if (values.length > 0) {
                                album = values[0];
                            }
                            break;
                        case "TCOM": //Composer
                            value = parseText(bframes, i, size, 1);
                            values = value.split("\0");
                            if (values.length > 0) {
                                composer = values[0];
                            }
                        case "TIT2": //Title
                            value = parseText(bframes, i, size, 1);
                            values = value.split("\0");
                            if (values.length > 0) {
                                title = values[0];
                            }
                            break;
                        case "TPE1": //Lead artists
                            value = parseText(bframes, i, size, 1);
                            values = value.split("\0");
                            if (values.length > 0) {
                                leadArtists = values[0];
                            }
                        case "TPE2": //Band
                            value = parseText(bframes, i, size, 1);
                            values = value.split("\0");
                            if (values.length > 0) {
                                band = values[0];
                            }
                    }
                }
            }
            if (replayGain != null && replayGainPeak != null) {
                replayGainScale = (float)Math.pow(10, replayGain / 20f);
                // If scale * peak > 1 then reduce scale (preamp) to prevent clipping.
                replayGainScale = Math.min(1 / replayGainPeak, replayGainScale);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private String parseText (byte[] bframes, int offset, int size, int skip) {
        String value = null;
        try {
            String[] ENC_TYPES = {"ISO-8859-1", "UTF16", "UTF-16BE", "UTF-8"};
            value = new String(bframes, offset + skip, size - skip, ENC_TYPES[bframes[offset]]);
        } catch (UnsupportedEncodingException e) {
        }
        return value;
    }

    public Float getReplayGainScale () {
        return replayGainScale;
    }

    /**
     * Close the Bitstream.
     * @throws BitstreamException
     */
    public void close () throws BitstreamException {
        try {
            source.close();
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR, ex);
        }
    }

    /**
     * Reads and parses the next frame from the input source.
     * @return the Header describing details of the frame read, or null if the end of the stream has been reached.
     */
    private Header readFrame () throws BitstreamException {
        Header result = null;
        try {
            result = readNextFrame();
            // E.B, Parse VBR (if any) first frame.
            if (firstframe == true) {
                result.parseVBR(frame_bytes);
                firstframe = false;
            }
        } catch (BitstreamException ex) {
            if (ex.getErrorCode() == INVALIDFRAME)
                // Try to skip this frame.
                // System.out.println("INVALIDFRAME");
                try {
                    closeFrame();
                    result = readNextFrame();
                } catch (BitstreamException e) {
                    if (e.getErrorCode() != STREAM_EOF) // wrap original exception so stack trace is maintained.
                        throw newBitstreamException(e.getErrorCode(), e);
                }
            else if (ex.getErrorCode() != STREAM_EOF) // wrap original exception so stack trace is maintained.
                throw newBitstreamException(ex.getErrorCode(), ex);
        }
        return result;
    }

    /**
     * Read next MP3 frame.
     * @return MP3 frame header.
     * @throws BitstreamException
     */
    private Header readNextFrame () throws BitstreamException {
        if (framesize == -1) nextFrame();
        return header;
    }

    /**
     * Read next MP3 frame.
     * @throws BitstreamException
     */
    private void nextFrame () throws BitstreamException {
        // entire frame is read by the header class.
        header.read_header(this, crc);
    }

    /**
     * Unreads the bytes read from the frame.
     * @throws BitstreamException
     */
    // REVIEW: add new error codes for this.
    public void unreadFrame () throws BitstreamException {
        if (wordpointer == -1 && bitindex == -1 && framesize > 0) try {
            source.unread(frame_bytes, 0, framesize);
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR);
        }
    }

    /**
     * Close MP3 frame.
     */
    private void closeFrame () {
        framesize = -1;
        wordpointer = -1;
        bitindex = -1;
    }

    /**
     * Determines if the next 4 bytes of the stream represent a frame header.
     * Only used while reading the header to process the stream.
     */
    public boolean isSyncCurrentPosition (int syncmode) throws BitstreamException {
        int read = readBytes(syncbuf, 0, 4);
        int headerstring = syncbuf[0] << 24 & 0xFF000000 | syncbuf[1] << 16 & 0x00FF0000 | syncbuf[2] << 8 & 0x0000FF00
                | syncbuf[3] << 0 & 0x000000FF;

        try {
            source.unread(syncbuf, 0, read);
        } catch (IOException ex) {
        }

        boolean sync = false;
        switch (read) {
            case 0:
                sync = true;
                break;
            case 4:
                sync = isSyncMark(headerstring, syncmode, syncword);
                break;
        }

        return sync;
    }

    // REVIEW: this class should provide inner classes to
    // parse the frame contents. Eventually, readBits will
    // be removed.
    public int readBits (int n) {
        return get_bits(n);
    }

    public int readCheckedBits (int n) {
        // REVIEW: implement CRC check.
        return get_bits(n);
    }

    protected BitstreamException newBitstreamException (int errorcode) {
        return new BitstreamException(errorcode, null);
    }

    protected BitstreamException newBitstreamException (int errorcode, Throwable throwable) {
        return new BitstreamException(errorcode, throwable);
    }

    /**
     * Get next 32 bits from bitstream. They are stored in the headerstring. syncmod allows Synchro flag ID The returned value is
     * False at the end of stream.
     */

    int syncHeader (byte syncmode) throws BitstreamException {
        boolean sync;
        int headerstring;
        // read additional 2 bytes
        int bytesRead = readBytes(syncbuf, 0, 3);

        if (bytesRead != 3) throw newBitstreamException(STREAM_EOF, null);

        headerstring = syncbuf[0] << 16 & 0x00FF0000 | syncbuf[1] << 8 & 0x0000FF00 | syncbuf[2] << 0 & 0x000000FF;

        do {
            headerstring <<= 8;

            if (readBytes(syncbuf, 3, 1) != 1) throw newBitstreamException(STREAM_EOF, null);

            headerstring |= syncbuf[3] & 0x000000FF;

            sync = isSyncMark(headerstring, syncmode, syncword);
        } while (!sync);

        // current_frame_number++;
        // if (last_frame_number < current_frame_number) last_frame_number = current_frame_number;

        return headerstring;
    }

    public boolean isSyncMark (int headerstring, int syncmode, int word) {
        boolean sync = false;

        if (syncmode == INITIAL_SYNC) // sync = ((headerstring & 0xFFF00000) == 0xFFF00000);
            sync = (headerstring & 0xFFE00000) == 0xFFE00000; // SZD: MPEG 2.5
        else
            sync = (headerstring & 0xFFF80C00) == word && (headerstring & 0x000000C0) == 0x000000C0 == single_ch_mode;

        // filter out invalid sample rate
        if (sync) sync = (headerstring >>> 10 & 3) != 3;
        // filter out invalid layer
        if (sync) sync = (headerstring >>> 17 & 3) != 0;
        // filter out invalid version
        if (sync) sync = (headerstring >>> 19 & 3) != 1;

        return sync;
    }

    /**
     * Reads the data for the next frame. The frame is not parsed until parse frame is called.
     */
    int read_frame_data (int bytesize) throws BitstreamException {
        int numread = 0;
        numread = readFully(frame_bytes, 0, bytesize);
        framesize = bytesize;
        wordpointer = -1;
        bitindex = -1;
        return numread;
    }

    /**
     * Parses the data previously read with read_frame_data().
     */
    void parse_frame () throws BitstreamException {
        // Convert Bytes read to int
        int b = 0;
        byte[] byteread = frame_bytes;
        int bytesize = framesize;

        // Check ID3v1 TAG (True only if last frame).
        // for (int t=0;t<(byteread.length)-2;t++)
        // {
        // if ((byteread[t]=='T') && (byteread[t+1]=='A') && (byteread[t+2]=='G'))
        // {
        // System.out.println("ID3v1 detected at offset "+t);
        // throw newBitstreamException(INVALIDFRAME, null);
        // }
        // }

        for (int k = 0; k < bytesize; k = k + 4) {
            byte b0 = 0;
            byte b1 = 0;
            byte b2 = 0;
            byte b3 = 0;
            b0 = byteread[k];
            if (k + 1 < bytesize) b1 = byteread[k + 1];
            if (k + 2 < bytesize) b2 = byteread[k + 2];
            if (k + 3 < bytesize) b3 = byteread[k + 3];
            framebuffer[b++] = b0 << 24 & 0xFF000000 | b1 << 16 & 0x00FF0000 | b2 << 8 & 0x0000FF00 | b3 & 0x000000FF;
        }
        wordpointer = 0;
        bitindex = 0;
    }

    /**
     * Read bits from buffer into the lower bits of an unsigned int. The LSB contains the latest read bit of the stream. (1 <=
     * number_of_bits <= 16)
     */
    public int get_bits (int number_of_bits) {
        int returnvalue = 0;
        int sum = bitindex + number_of_bits;

        // E.B
        // There is a problem here, wordpointer could be -1 ?!
        if (wordpointer < 0) wordpointer = 0;
        // E.B : End.

        if (sum <= 32) {
            // all bits contained in *wordpointer
            returnvalue = framebuffer[wordpointer] >>> 32 - sum & bitmask[number_of_bits];
            // returnvalue = (wordpointer[0] >> (32 - sum)) & bitmask[number_of_bits];
            if ((bitindex += number_of_bits) == 32) {
                bitindex = 0;
                wordpointer++; // added by me!
            }
            return returnvalue;
        }

        // E.B : Check that ?
        // ((short[])&returnvalue)[0] = ((short[])wordpointer + 1)[0];
        // wordpointer++; // Added by me!
        // ((short[])&returnvalue + 1)[0] = ((short[])wordpointer)[0];
        int Right = framebuffer[wordpointer] & 0x0000FFFF;
        wordpointer++;
        int Left = framebuffer[wordpointer] & 0xFFFF0000;
        returnvalue = Right << 16 & 0xFFFF0000 | Left >>> 16 & 0x0000FFFF;

        returnvalue >>>= 48 - sum; // returnvalue >>= 16 - (number_of_bits - (32 - bitindex))
        returnvalue &= bitmask[number_of_bits];
        bitindex = sum - 32;
        return returnvalue;
    }

    /**
     * Set the word we want to sync the header to. In Big-Endian byte order
     */
    void set_syncword (int syncword0) {
        syncword = syncword0 & 0xFFFFFF3F;
        single_ch_mode = (syncword0 & 0x000000C0) == 0x000000C0;
    }

    /**
     * Reads the exact number of bytes from the source input stream into a byte array.
     *
     * @param b The byte array to read the specified number of bytes into.
     * @param offs The index in the array where the first byte read should be stored.
     * @param len the number of bytes to read.
     *
     * @exception BitstreamException is thrown if the specified number of bytes could not be read from the stream.
     */
    private int readFully (byte[] b, int offs, int len) throws BitstreamException {
        int nRead = 0;
        try {
            while (len > 0) {
                int bytesread = source.read(b, offs, len);
                if (bytesread == -1) {
                    while (len-- > 0)
                        b[offs++] = 0;
                    break;
                    // throw newBitstreamException(UNEXPECTED_EOF, new EOFException());
                }
                nRead = nRead + bytesread;
                offs += bytesread;
                len -= bytesread;
            }
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR, ex);
        }
        return nRead;
    }

    /**
     * Simlar to readFully, but doesn't throw exception when EOF is reached.
     */
    private int readBytes (byte[] b, int offs, int len) throws BitstreamException {
        int totalBytesRead = 0;
        try {
            while (len > 0) {
                int bytesread = source.read(b, offs, len);
                if (bytesread == -1) break;
                totalBytesRead += bytesread;
                offs += bytesread;
                len -= bytesread;
            }
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR, ex);
        }
        return totalBytesRead;
    }

    /**
     * The first bitstream error code.
     */
    static public final int BITSTREAM_ERROR = 0x100;

    /**
     * An undeterminable error occurred.
     */
    static public final int UNKNOWN_ERROR = BITSTREAM_ERROR + 0;

    /**
     * The header describes an unknown sample rate.
     */
    static public final int UNKNOWN_SAMPLE_RATE = BITSTREAM_ERROR + 1;

    /**
     * A problem occurred reading from the stream.
     */
    static public final int STREAM_ERROR = BITSTREAM_ERROR + 2;

    /**
     * The end of the stream was reached prematurely.
     */
    static public final int UNEXPECTED_EOF = BITSTREAM_ERROR + 3;

    /**
     * The end of the stream was reached.
     */
    static public final int STREAM_EOF = BITSTREAM_ERROR + 4;

    /**
     * Frame data are missing.
     */
    static public final int INVALIDFRAME = BITSTREAM_ERROR + 5;

    /**
     *
     */
    static public final int BITSTREAM_LAST = 0x1ff;



    /**
     * The first decoder error code.
     */
    static public final int DECODER_ERROR = 0x200;

    static public final int UNKNOWN_DECODER_ERROR = DECODER_ERROR + 0;

    /**
     * Layer not supported by the decoder.
     */
    static public final int UNSUPPORTED_LAYER = DECODER_ERROR + 1;

    /**
     * Illegal allocation in subband layer. Indicates a corrupt stream.
     */
    static public final int ILLEGAL_SUBBAND_ALLOCATION = DECODER_ERROR + 2;
}