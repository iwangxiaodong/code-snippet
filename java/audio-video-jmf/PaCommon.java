package gradleproject11;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.media.Buffer;
import javax.media.Format;
import javax.media.GainControl;
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
import org.jitsi.impl.neomedia.device.MediaDeviceImpl;
import org.jitsi.impl.neomedia.device.UpdateAvailableDeviceListListener;
import org.jitsi.impl.neomedia.portaudio.Pa;
import org.jitsi.impl.neomedia.portaudio.PortAudioException;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.BasicVolumeControl;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaStream;
import org.jitsi.service.neomedia.MediaUseCase;
import org.jitsi.service.neomedia.device.MediaDevice;
import org.jitsi.utils.MediaType;

/**
 *  // RTP - 精简大小可去掉dll/so库平台名:win32-x86-64.
 *
 * 'org.jitsi:fmj:1.0.2-jitsi'
 * 'org.jitsi:jitsi-lgpl-dependencies:1.2-22-gb6050e7:win32-x86-64'
 * https://github.com/jitsi/libjitsi/blob/master/src/main/java/org/jitsi/impl/neomedia/jmfext/media/protocol/portaudio/PortAudioStream.java
 */
/*
tasks.withType(JavaExec) { 
    jvmArgs += ['-Djava.library.path=D:\\temp\\libjitsi-1.1-22-g5c9346c5\\win32-x86-64\\']
    //args +=['--local-port-base=11111', '--remote-host=localhost', '--remote-port-base=22222'] // 接收入参
    //args +=['--local-port-base=22222', '--remote-host=localhost', '--remote-port-base=11111'] // 发送入参 
}
 */
public class PaCommon {

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

    public PaCommon() {
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
        MediaService mediaService = LibJitsi.getMediaService();
        MediaDevice device = mediaService.getDefaultDevice(MediaType.AUDIO, MediaUseCase.CALL);
        MediaStream mediaStream = mediaService.createMediaStream(device);
        var deviceSession = ((MediaStreamImpl) mediaStream).getDeviceSession();

        var cdi = ((MediaDeviceImpl) device).getCaptureDeviceInfo();
        System.out.println(cdi);

        var loc = deviceSession.getCaptureDevice().getLocator();
        System.out.println("loc - " + loc); //  loc - wasapi:{0.0.1.00000000}.{084d8472-0f33-4d29-9e15-b260989801e5}
        System.out.println(loc.getProtocol());

        //var devicdId = DataSource.getDeviceID(loc);
        System.out.println("======================----");
        String remainder = loc.getRemainder();
        System.out.println("remainder - " + remainder);

        var obj = new PaCommon();
        obj.connect();
        obj.start();
        obj.getAudio_A();
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
                        Logger.getLogger(PaCommon.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }).start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    public void connect() throws IOException {
        int samplingRate = (int) format2.getSampleRate();
        int samplingSize = format2.getSampleSizeInBits();
        int channels = 1;
        format = new AudioFormat(javax.media.format.AudioFormat.LINEAR,
                samplingRate, samplingSize, channels);
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

            stream = Pa.OpenStream(
                    inputParameters,
                    0 /* outputParameters */,
                    sampleRate,
                    framesPerBuffer,
                    Pa.STREAM_FLAGS_CLIP_OFF | Pa.STREAM_FLAGS_DITHER_OFF,
                    null /* streamCallback */);
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
        format
                = new AudioFormat(
                        javax.media.format.AudioFormat.LINEAR,
                        sampleRate,
                        sampleSizeInBits,
                        channels,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED /* frameSizeInBits */,
                        Format.NOT_SPECIFIED /* frameRate */,
                        Format.byteArray);

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
            synchronized (PaCommon.this) {
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
            synchronized (PaCommon.this) {
                waitWhileStreamIsBusy();
                if (stream == 0) {
                    deviceID = null;
                    start = false;
                } else {
                    deviceID = PaCommon.this.deviceID;
                    start = PaCommon.this.started;

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
