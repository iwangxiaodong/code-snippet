package com.example.jmf.rtp.examples;

import static com.example.jmf.rtp.examples.EchoCancelUtils.par;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.media.Buffer;
import javax.media.GainControl;
import javax.media.ResourceUnavailableException;
import javax.media.control.FormatControl;
import javax.media.format.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import org.jitsi.impl.neomedia.MediaServiceImpl;
import static org.jitsi.impl.neomedia.MediaServiceImpl.DISABLE_VIDEO_SUPPORT_PNAME;
import org.jitsi.impl.neomedia.MediaStreamImpl;
import org.jitsi.impl.neomedia.NeomediaServiceUtils;
import org.jitsi.impl.neomedia.codec.AbstractCodec2;
import static org.jitsi.impl.neomedia.control.DiagnosticsControl.NEVER;
import org.jitsi.impl.neomedia.device.AudioSystem;
import org.jitsi.impl.neomedia.device.AudioSystem2;
import org.jitsi.impl.neomedia.device.DeviceConfiguration;
import org.jitsi.impl.neomedia.device.PortAudioSystem;
import org.jitsi.impl.neomedia.device.UpdateAvailableDeviceListListener;
import org.jitsi.impl.neomedia.jmfext.media.protocol.portaudio.DataSource;
import org.jitsi.impl.neomedia.jmfext.media.protocol.portaudio.PortAudioStream;
import org.jitsi.impl.neomedia.portaudio.Pa;
import org.jitsi.impl.neomedia.portaudio.PortAudioException;
import org.jitsi.impl.neomedia.portaudio.PortAudioStreamCallback;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.BasicVolumeControl;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaStream;
import org.jitsi.service.neomedia.MediaUseCase;
import org.jitsi.service.neomedia.device.MediaDevice;
import org.jitsi.utils.MediaType;

//  Callback里能接到bytes数据，但无法播放，后续研究。
public class PaCommonForCallback {

    private long inputParameters = 0;
    private boolean started = false;
    private long stream;
    private AudioFormat format;
    private int bytesPerBuffer;
    private int framesPerBuffer;
    private boolean streamIsBusy = false;
    private long readIsMalfunctioningSince = NEVER;
    private final GainControl gainControl;
    private int sequenceNumber = 0;
    private String deviceID;

    public PaCommonForCallback() {
        MediaServiceImpl mediaServiceImpl
                = NeomediaServiceUtils.getMediaServiceImpl();

        gainControl
                = (mediaServiceImpl == null)
                        ? null
                        : (GainControl) mediaServiceImpl.getInputVolumeControl();

        AudioSystem2 audioSystem
                = (AudioSystem2) AudioSystem.getAudioSystem(
                        AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO);

        if (audioSystem != null) {
            audioSystem.addUpdateAvailableDeviceListListener(
                    paUpdateAvailableDeviceListListener);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(new App().getGreeting());
        LibJitsi.start();
        LibJitsi.getConfigurationService().setProperty(DISABLE_VIDEO_SUPPORT_PNAME, true);
        LibJitsi.getConfigurationService().setProperty("net.java.sip.communicator.impl.neomedia.echocancel.filterLengthInMillis", 10000);

        MediaService mediaService = LibJitsi.getMediaService();
        MediaServiceImpl mediaServiceImpl
                = NeomediaServiceUtils.getMediaServiceImpl();

        if (mediaServiceImpl != null) {
            DeviceConfiguration devCfg
                    = mediaServiceImpl.getDeviceConfiguration();

            if (devCfg != null) {
                var echoCancelFilterLengthInMillis
                        = devCfg.getEchoCancelFilterLengthInMillis();
                System.out.println("echoCancelFilterLengthInMillis - " + echoCancelFilterLengthInMillis);
            }
        }

        mediaService.getDevices(MediaType.AUDIO, MediaUseCase.ANY).forEach(x -> {
            System.out.println(x);
        });
        MediaDevice device = mediaService.getDevices(MediaType.AUDIO, MediaUseCase.ANY).get(0);
        System.out.println(device);
        MediaStream mediaStream = mediaService.createMediaStream(device);
        System.out.println(mediaStream);
        var deviceSession = ((MediaStreamImpl) mediaStream).getDeviceSession();
        System.out.println(deviceSession);
        System.out.println(deviceSession.getDevice());

        var cls = Class.forName("org.jitsi.impl.neomedia.device.PortAudioSystem");
        var c = cls.getDeclaredConstructor();
        c.setAccessible(true);
        pas = (PortAudioSystem) c.newInstance();
        System.out.println(pas);

        var d = pas.getDevices(AudioSystem.DataFlow.CAPTURE);

        System.out.println(d);
        System.out.println("================");
        System.out.println(d.getFirst());

        var ds = new DataSource(d.getFirst().getLocator());
        var x = ds.getFormatControls();
        System.out.println(Arrays.deepToString(x));
        var cs = DataSource.class.getDeclaredMethod("createStream", int.class, FormatControl.class);
        cs.setAccessible(true);

        System.out.println(Arrays.deepToString(x[0].getSupportedFormats()));
        p2 = (PortAudioStream) cs.invoke(ds, 0, x[0]);
        System.out.println(p2);

        var obj = new PaCommonForCallback();
        obj.connect();
        obj.start();
        //obj.getAudio_A();
        new CountDownLatch(1).await(10, TimeUnit.SECONDS);
    }
    static javax.sound.sampled.AudioFormat format2 = new javax.sound.sampled.AudioFormat(
            16000, 16, 1, true, false);
    static ByteArrayOutputStream baos = new ByteArrayOutputStream();

    public void getAudio_A() {//提供wave格式信息
        try {
            TargetDataLine targetDataLine = javax.sound.sampled.AudioSystem.getTargetDataLine(format2);
            targetDataLine.open(format2);
            targetDataLine.start();
            System.out.println("录音中");
            //直接播放出来 
            SourceDataLine sourceDataLine = javax.sound.sampled.AudioSystem.getSourceDataLine(format2);
            sourceDataLine.open(format2);
            sourceDataLine.start();
            System.out.println("播放中");
            //开子线程进行播放

            new Thread(() -> {
                byte[] b = new byte[bytesPerBuffer];//缓存音频数据
                int a = 0;
                while (a != -1) {
                    //System.out.println("录制中");
                    a = targetDataLine.read(b, 0, b.length);//捕获录音数据

                    try {
                        if (true) {
                            Pa.ReadStream(stream, b, framesPerBuffer);
                        }
                    } catch (PortAudioException pae) {
                        System.err.println("Failed to read from PortAudio stream." + pae);
                    }

                    try {
                        baos.write(b);
                        //  边录边播
                        if (a != -1) {
                            sourceDataLine.write(b, 0, a);//播放录制的声音
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(PaCommonForCallback.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }).start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }
    static PortAudioSystem pas = null;
    static PortAudioStream p2;

    public static String bytesToHex(ByteBuffer buffer) {
        final StringBuilder hexString = new StringBuilder(2 * buffer.remaining());
        while (buffer.hasRemaining()) {
            int b = buffer.get() & 0xff;
            if (b < 0x10) {
                hexString.append('0');
            }
            hexString.append(Integer.toHexString(b));
        }
        return hexString.toString();
    }

    public void connect() throws IOException {
        int samplingRate = (int) format2.getSampleRate();
        int samplingSize = format2.getSampleSizeInBits();
        int channels = 1;
        format = new AudioFormat(javax.media.format.AudioFormat.LINEAR,
                samplingRate, samplingSize, channels);
        format = (AudioFormat) p2.getFormat();

        //AudioFormat format = (AudioFormat) getFormat();
        //int channels = format.getChannels();
//        if (channels == Format.NOT_SPECIFIED) {
//            channels = 1;
//        }
        int sampleSizeInBits = format.getSampleSizeInBits();
        long sampleFormat = Pa.getPaSampleFormat(sampleSizeInBits);
        double sampleRate = format.getSampleRate();
        framesPerBuffer = (int) ((sampleRate * Pa.DEFAULT_MILLIS_PER_BUFFER)
                / (channels * 1000));

        var deviceIndex = 0; // note - 暂写死第一个

        try {
            inputParameters
                    = Pa.StreamParameters_new(
                            deviceIndex,
                            channels,
                            sampleFormat,
                            Pa.getSuggestedLatency());//  Pa.getSuggestedLatency()

//            SourceDataLine sourceDataLine = null;
//            try {
//                sourceDataLine = javax.sound.sampled.AudioSystem.getSourceDataLine(format2);
//                sourceDataLine.open(format2);
//            } catch (LineUnavailableException ex) {
//                Logger.getLogger(PaCommon.class.getName()).log(Level.SEVERE, null, ex);
//            }
//            sourceDataLine.start();
//            final SourceDataLine sourceDataLine2 = sourceDataLine;
            System.out.println(pas);
            par = pas.createRenderer();
            System.out.println(par);
            par.setInputFormat(p2.getFormat());
            System.out.println(Arrays.deepToString(par.getSupportedInputFormats()));
            try {
                par.open();
            } catch (ResourceUnavailableException ex) {
                Logger.getLogger(EchoCancelUtils.class.getName()).log(Level.SEVERE, null, ex);
            }
            par.start();

            System.out.println("播放中");

            stream = Pa.OpenStream(
                    inputParameters,
                    inputParameters /* outputParameters */,
                    sampleRate,
                    framesPerBuffer,
                    Pa.STREAM_FLAGS_CLIP_OFF | Pa.STREAM_FLAGS_DITHER_OFF,
                    new PortAudioStreamCallback() {
                @Override
                public int callback(ByteBuffer bb, ByteBuffer bb1) {
                    System.out.println("callback - " + bytesToHex(bb));

                    var bytes = new Buffer();
                    bytes.setData(bb.array());
                    bytes.setLength(bb.array().length);
                    par.process(bytes);
                    //sourceDataLine2.write(bb.array(), 0, bb.array().length);//播放录制的声音
                    return PortAudioStreamCallback.RESULT_CONTINUE;
                }

                @Override
                public void finishedCallback() {
                    System.out.println("finishedCallback");
                }
            }
            /* streamCallback */);
        } catch (PortAudioException paex) {
            System.err.println("Failed to open " + paex);
            //logger.error("Failed to open " + getClass().getSimpleName(), paex);

            IOException ioex = new IOException(paex.getLocalizedMessage());

            ioex.initCause(paex);
            throw ioex;
        } finally {
            if ((stream == 0) && (inputParameters != 0)) {
                Pa.StreamParameters_free(inputParameters);
                inputParameters = 0;
            }
        }
        if (stream == 0) {
            throw new IOException("Pa_OpenStream");
        }

        //this.framesPerBuffer = framesPerBuffer;
        bytesPerBuffer = Pa.GetSampleSize(sampleFormat) * channels * framesPerBuffer;
        //bytesPerBuffer=640;
        System.out.println("bytesPerBuffer - " + bytesPerBuffer);
        /*
         * Know the Format in which this PortAudioStream will output audio
         * data so that it can report it without going through its
         * DataSource.
         */
//        format
//                = new AudioFormat(
//                        javax.media.format.AudioFormat.LINEAR,
//                        sampleRate,
//                        sampleSizeInBits,
//                        channels,
//                        AudioFormat.LITTLE_ENDIAN,
//                        AudioFormat.SIGNED,
//                        Format.NOT_SPECIFIED /* frameSizeInBits */,
//                        Format.NOT_SPECIFIED /* frameRate */,
//                        Format.byteArray);

        boolean denoise = false;
        boolean echoCancel = false;
        long echoCancelFilterLengthInMillis
                = DeviceConfiguration.DEFAULT_AUDIO_ECHOCANCEL_FILTER_LENGTH_IN_MILLIS;

        boolean audioQualityImprovement = true;
        if (audioQualityImprovement) {
            AudioSystem audioSystem
                    = AudioSystem.getAudioSystem(
                            AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO);

            if (audioSystem != null) {
                denoise = audioSystem.isDenoise();
                echoCancel = audioSystem.isEchoCancel();
                System.out.println("isEchoCancel - " + echoCancel);
                if (echoCancel) {
                    MediaServiceImpl mediaServiceImpl
                            = NeomediaServiceUtils.getMediaServiceImpl();

                    if (mediaServiceImpl != null) {
                        DeviceConfiguration devCfg
                                = mediaServiceImpl.getDeviceConfiguration();

                        if (devCfg != null) {
                            echoCancelFilterLengthInMillis
                                    = devCfg.getEchoCancelFilterLengthInMillis();
                        }
                    }
                }
            }
        }

        //echoCancelFilterLengthInMillis=42;
        var ms = echoCancel ? echoCancelFilterLengthInMillis : 0;
        System.out.println("ms - " + ms);
        //ms = 33;

        Pa.setDenoise(stream, denoise);
        Pa.setEchoFilterLengthInMillis(stream, ms);

//        // Pa_ReadStream has not been invoked yet.
//        if (readIsMalfunctioningSince != NEVER) {
//            setReadIsMalfunctioning(false);
//        }
        System.out.println("over.");
    }

    public synchronized void start()
            throws IOException {
        if (stream != 0) {
            waitWhileStreamIsBusy();

            try {
                Pa.StartStream(stream);
                started = true;
            } catch (PortAudioException paex) {
//                logger.error(
//                        "Failed to start " + getClass().getSimpleName(),
//                        paex);

                System.out.println("Failed to start " + paex);
                IOException ioex = new IOException(paex.getLocalizedMessage());

                ioex.initCause(paex);
                throw ioex;
            }
        }
    }

    private void setReadIsMalfunctioning(boolean malfunctioning) {
        if (malfunctioning) {
            if (readIsMalfunctioningSince == NEVER) {
                readIsMalfunctioningSince = System.currentTimeMillis();
            }
        } else {
            readIsMalfunctioningSince = NEVER;
        }
    }

    private void waitWhileStreamIsBusy() {
        boolean interrupted = false;

        while ((stream != 0) && streamIsBusy) {
            try {
                wait();
            } catch (InterruptedException iex) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public static void paSleep() {
        try {
            Thread.sleep(Pa.DEFAULT_MILLIS_PER_BUFFER);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public void read(Buffer buffer)
            throws IOException {
        String message;

        synchronized (this) {
            if (stream == 0) {
                message = getClass().getName() + " is disconnected.";
            } else if (!started) {
                message = getClass().getName() + " is stopped.";
            } else {
                message = null;
                streamIsBusy = true;
            }

            if (message != null) {
                /*
                 * There is certainly a problem but it is other than a
                 * malfunction in Pa_ReadStream.
                 */
                if (readIsMalfunctioningSince != NEVER) {
                    setReadIsMalfunctioning(false);
                }
            }
        }

        /*
         * The caller shouldn't call #read(Buffer) if this instance is
         * disconnected or stopped. Additionally, if she does, she may be
         * persistent. If we do not slow her down, she may hog the CPU.
         */
        if (message != null) {
            paSleep();
            throw new IOException(message);
        }

        long errorCode = Pa.paNoError;
        Pa.HostApiTypeId hostApiType = null;

        try {
            /*
             * Reuse the data of buffer in order to not perform unnecessary
             * allocations.
             */
            byte[] data
                    = AbstractCodec2.validateByteArraySize(
                            buffer,
                            bytesPerBuffer,
                            false);

            try {
                Pa.ReadStream(stream, data, framesPerBuffer);
            } catch (PortAudioException pae) {
                errorCode = pae.getErrorCode();
                hostApiType = pae.getHostApiType();

                //logger.error("Failed to read from PortAudio stream.", pae);
                System.err.println("Failed to read from PortAudio stream." + pae);

                IOException ioe = new IOException(pae.getLocalizedMessage());

                ioe.initCause(pae);
                throw ioe;
            }

            /*
             * Take into account the user's preferences with respect to the
             * input volume.
             */
            if (gainControl != null) {
                BasicVolumeControl.applyGain(
                        gainControl,
                        data, 0, bytesPerBuffer);
            }

            long bufferTimeStamp = System.nanoTime();

            buffer.setFlags(Buffer.FLAG_SYSTEM_TIME);
            if (format != null) {
                buffer.setFormat(format);
            }
            buffer.setHeader(null);
            buffer.setLength(bytesPerBuffer);
            buffer.setOffset(0);
            buffer.setSequenceNumber(sequenceNumber++);
            buffer.setTimeStamp(bufferTimeStamp);
        } finally {
            /*
             * If a timeout has occurred in the method Pa.ReadStream, give the
             * application a little time to allow it to possibly get its act
             * together. The same treatment sounds appropriate on Windows as
             * soon as the wmme host API starts reporting that no device driver
             * is present.
             */
            boolean yield = false;

            synchronized (this) {
                streamIsBusy = false;
                notifyAll();

                if (errorCode == Pa.paNoError) {
                    // Pa_ReadStream appears to function normally.
                    if (readIsMalfunctioningSince != NEVER) {
                        setReadIsMalfunctioning(false);
                    }
                } else if ((Pa.paTimedOut == errorCode)
                        || (Pa.HostApiTypeId.paMME.equals(hostApiType)
                        && (Pa.MMSYSERR_NODRIVER == errorCode))) {
                    if (readIsMalfunctioningSince == NEVER) {
                        setReadIsMalfunctioning(true);
                    }
                    yield = true;
                }
            }

            if (yield) {
                paSleep();
            }
        }
    }

    synchronized void setDeviceID(String deviceID)
            throws IOException {
        /*
         * We should better not short-circuit because the deviceID may be the
         * same but it eventually resolves to a deviceIndex and may have changed
         * after hotplugging.
         */

        // DataSource#disconnect
        if (this.deviceID != null) {
            /*
             * Just to be on the safe side, make sure #read(Buffer) is not
             * currently executing.
             */
            waitWhileStreamIsBusy();

            if (stream != 0) {
                /*
                 * For the sake of completeness, attempt to stop this instance
                 * before disconnecting it.
                 */
                if (started) {
                    try {
                        stop();
                    } catch (IOException ioe) {
                        /*
                         * The exception should have already been logged by the
                         * method #stop(). Additionally and as said above, we
                         * attempted it out of courtesy.
                         */
                    }
                }

                boolean closed = false;

                try {
                    Pa.CloseStream(stream);
                    closed = true;
                } catch (PortAudioException pae) {
                    /*
                     * The function Pa_CloseStream is not supposed to time out
                     * under normal execution. However, we have modified it to
                     * do so under exceptional circumstances on Windows at least
                     * in order to overcome endless loops related to
                     * hotplugging. In such a case, presume the native PortAudio
                     * stream closed in order to maybe avoid a crash at the risk
                     * of a memory leak.
                     */
                    long errorCode = pae.getErrorCode();

                    if ((errorCode == Pa.paTimedOut)
                            || (Pa.HostApiTypeId.paMME.equals(
                                    pae.getHostApiType())
                            && (errorCode == Pa.MMSYSERR_NODRIVER))) {
                        closed = true;
                    }

                    if (!closed) {
//                        logger.error(
//                                "Failed to close " + getClass().getSimpleName(),
//                                pae);
                        System.err.println("Failed to close " + getClass().getSimpleName() + pae);
                        IOException ioe
                                = new IOException(pae.getLocalizedMessage());

                        ioe.initCause(pae);
                        throw ioe;
                    }
                } finally {
                    if (closed) {
                        stream = 0;

                        if (inputParameters != 0) {
                            Pa.StreamParameters_free(inputParameters);
                            inputParameters = 0;
                        }

                        /*
                         * Make sure this AbstractPullBufferStream asks its
                         * DataSource for the Format in which it is supposed to
                         * output audio data the next time it is opened instead
                         * of using its Format from a previous open.
                         */
                        this.format = null;

                        if (readIsMalfunctioningSince != NEVER) {
                            setReadIsMalfunctioning(false);
                        }
                    }
                }
            }
        }

        this.deviceID = deviceID;
        this.started = false;

        // DataSource#connect
        if (this.deviceID != null) {
            AudioSystem2 audioSystem
                    = (AudioSystem2) AudioSystem.getAudioSystem(
                            AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO);

            if (audioSystem != null) {
                audioSystem.willOpenStream();
            }
            try {
                connect();
            } finally {
                if (audioSystem != null) {
                    audioSystem.didOpenStream();
                }
            }
        }
    }

    public synchronized void stop()
            throws IOException {
        if (stream != 0) {
            waitWhileStreamIsBusy();

            try {
                Pa.StopStream(stream);
                started = false;

                if (readIsMalfunctioningSince != NEVER) {
                    setReadIsMalfunctioning(false);
                }
            } catch (PortAudioException paex) {
//                logger.error(
//                        "Failed to stop " + getClass().getSimpleName(),
//                        paex);
                System.err.println("Failed to stop " + getClass().getSimpleName() + paex);

                IOException ioex = new IOException(paex.getLocalizedMessage());

                ioex.initCause(paex);
                throw ioex;
            }
        }
    }

    private final UpdateAvailableDeviceListListener paUpdateAvailableDeviceListListener
            = new UpdateAvailableDeviceListListener() {
        /**
         * The device ID (could be deviceUID or name but that is not really of
         * concern to PortAudioStream) used before and after (if still
         * available) the update.
         */
        private String deviceID = null;

        private boolean start = false;

        @Override
        public void didUpdateAvailableDeviceList()
                throws Exception {
            synchronized (PaCommonForCallback.this) {
                try {
                    waitWhileStreamIsBusy();
                    /*
                             * The stream should be closed. If it is not, then
                             * something else happened in the meantime and we
                             * cannot be sure that restoring the old state of
                             * this PortAudioStream is the right thing to do in
                             * its new state.
                     */
                    if (stream == 0) {
                        setDeviceID(deviceID);
                        if (start) {
                            start();
                        }
                    }
                } finally {
                    /*
                             * If we had to attempt to restore the state of
                             * this PortAudioStream, we just did attempt to.
                     */
                    deviceID = null;
                    start = false;
                }
            }
        }

        @Override
        public void willUpdateAvailableDeviceList()
                throws Exception {
            synchronized (PaCommonForCallback.this) {
                waitWhileStreamIsBusy();
                if (stream == 0) {
                    deviceID = null;
                    start = false;
                } else {
                    deviceID = PaCommonForCallback.this.deviceID;
                    start = PaCommonForCallback.this.started;

                    boolean disconnected = false;

                    try {
                        setDeviceID(null);
                        disconnected = true;
                    } finally {
                        /*
                                 * If we failed to disconnect this
                                 * PortAudioStream, we will not attempt to
                                 * restore its state later on.
                         */
                        if (!disconnected) {
                            deviceID = null;
                            start = false;
                        }
                    }
                }
            }
        }
    };

}
