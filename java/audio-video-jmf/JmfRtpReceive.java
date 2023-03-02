package gradleproject11;

import java.awt.HeadlessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.media.Buffer;
import javax.media.ConfigureCompleteEvent;
import javax.media.DataSink;
import javax.media.Format;
import javax.media.Manager;
import javax.media.MediaLocator;
import javax.media.NoDataSinkException;
import javax.media.NoPlayerException;
import javax.media.NoProcessorException;
import javax.media.NotConfiguredError;
import javax.media.Processor;
import javax.media.RealizeCompleteEvent;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.protocol.FileTypeDescriptor;
import javax.media.protocol.PushBufferDataSource;
import javax.media.rtp.InvalidSessionAddressException;
import javax.media.rtp.RTPManager;
import javax.media.rtp.SessionAddress;
import javax.media.rtp.event.NewReceiveStreamEvent;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.jitsi.service.libjitsi.LibJitsi;

/**
 *  // RTP - 精简大小可去掉dll/so库平台名:win32-x86-64.
 *
 * 'org.jitsi:fmj:1.0.2-jitsi'
 * 'org.jitsi:jitsi-lgpl-dependencies:1.2-22-gb6050e7:win32-x86-64'
 */
public class JmfRtpReceive {

    public void start() {
        System.out.println("JmfRtpReceive.start()");
        //  注意 - 必须先接收再执行发送，否则会导致pushBuffer保存的wav文件0字节；SourceDataLine边接边播不能用！
        //  接收侧若使用createDataSink来存为wav文件，还必须执行以下两行进行初始化：
        LibJitsi.start();
        LibJitsi.getMediaService();

        var rtpm = RTPManager.newInstance();

        rtpm.addReceiveStreamListener((evt) -> {
            System.out.println("evt - " + evt);
            if (evt instanceof NewReceiveStreamEvent nrse) {
                //  每个数据源似乎只能被1个Processor或Player独占处理
                var ds = nrse.getReceiveStream().getDataSource();
                try {
                    ds.start();
                } catch (IOException ex) {
                    System.err.println(ex);
                }
                if (true) {
                    //pushBuffer(ds);
                    newProcessor(ds);
                } else {
                    try {
                        var p = Manager.createPlayer(ds); //  阻塞型 - createRealizedPlayer
                        p.addControllerListener((ce) -> {
                            System.out.println("ce - " + ce);
                            if (ce instanceof RealizeCompleteEvent) {
                                p.start();
                            }
                        });
                        p.realize();
                    } catch (IOException | NoPlayerException ex) {
                        System.err.println(ex);
                    }
                }
            }
        });
        try {
            var sa = new SessionAddress(InetAddress
                    .getByName("localhost"), 11111);
            rtpm.initialize(sa);
            sa.setDataPort(22222);
            rtpm.addTarget(sa);
        } catch (InvalidSessionAddressException | IOException ex) {
            System.err.println(ex);
        }
    }

    static ByteArrayOutputStream baos = new ByteArrayOutputStream();

    //  字节加wavHeader后存为wav文件可正常播放，但纯PCM bytes无法被SourceDataLine播放
    public void pushBuffer(DataSource ds) {
        new Thread(() -> {
            try {
                Thread.sleep(Duration.ofSeconds(5));
            } catch (InterruptedException ex) {
                Logger.getLogger(JmfRtpReceive.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (baos != null && baos.size() > 0) {
                System.err.println("voice end：" + baos.size());
                new Thread(() -> {
                    var adFormat = new javax.sound.sampled.AudioFormat(AudioFormat.Encoding.ULAW, 8000, 8, 1,
                            160, Format.NOT_SPECIFIED, true);
                    var path = saveAudioFile(baos.toByteArray(), adFormat);
                    System.out.println(path);
                }).start();
            }
        }).start();

        var pbds = ((PushBufferDataSource) ds).getStreams();
        System.out.println("Stream length: " + pbds.length);
        System.out.println("Stream format: " + pbds[0].getFormat());
        pbds[0].setTransferHandler((push) -> {
            var buf = new Buffer();
            try {
                push.read(buf);
            } catch (IOException ex) {
                System.err.println(ex);
            }
            var dt = buf.getFormat().getDataType();
            //System.out.println("buffer length: " + buf.getLength() + ", timestamp: " + buf.getTimeStamp());
            System.out.println("buffer format: " + buf.getFormat().toString());
            if (dt == Format.byteArray) {
                var bytes = (byte[]) buf.getData();
                byte[] bytesWithoutHeader = Arrays.copyOfRange(bytes, 12, buf.getLength() + 12);
                printBytes(bytesWithoutHeader);
                try {
                    baos.write(bytesWithoutHeader);
                } catch (IOException ex) {
                    Logger.getLogger(JmfRtpReceive.class.getName()).log(Level.SEVERE, null, ex);
                }
                //sdl.write(bytesWithoutHeader, 0, bytesWithoutHeader.length);
            }
        });
    }

    //  for RTP ULAW - 将正数整体从1开始数起
    //  var lastByte = Math.abs(processByteForRTPULAW(fragment[fragment.length - 1]));
    static byte processByteForRTPULAW(byte a) {
        int r = (int) a;
        if (r > -1) {
            r = 128 - r;
        }
        return (byte) r;
    }

    public void printBytes(byte[] bytes) {
        var sb = new StringBuilder();
        for (int i = 0; i < ((bytes.length > 30) ? 30 : bytes.length); ++i) {
            byte b = bytes[i];
            sb.append(String.format("%02X ", b));
        }
        System.out.println("Begin bytes hex: " + sb.toString());
    }

    public void newProcessor(DataSource ds) {
        try {
            var p = Manager.createProcessor(ds);
            p.addControllerListener((event) -> {
                System.out.println("event - " + event);
                if (event instanceof ConfigureCompleteEvent) {
                    var sc = (Processor) event.getSourceController();
                    if (sc != null) {
                        try {
                            sc.setContentDescriptor(new ContentDescriptor(FileTypeDescriptor.WAVE));
                        } catch (NotConfiguredError nce) {
                            System.err.println("Failed to set ContentDescriptor to Player." + nce);
                            return;
                        }
                        sc.realize();
                    }
                } else if (event instanceof RealizeCompleteEvent) {
                    var sc = (Processor) event.getSourceController();
                    if (sc != null) {
                        sc.start();
                        System.out.println("sc.start();");

                        DataSink sink = null;
                        try {
                            //ds.start();
                            sink = Manager.createDataSink(sc.getDataOutput(),
                                    new MediaLocator(new File("D:\\temp\\voice-" + Instant.now().getEpochSecond() + ".wav").toURI().toURL()));
                            sink.open();
                            sink.start();

                        } catch (IOException | SecurityException | NoDataSinkException e) {
                            e.printStackTrace();
                        }

                        try {
                            Thread.sleep(5_000);
                            if (sink != null) {
                                sink.stop();
                                sink.close();
                            }
                            p.close();
                            sc.close();
                        } catch (InterruptedException | IOException ex) {
                            Logger.getLogger(JmfRtpReceive.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        System.out.println("sink over.");
                    }
                }
            });
            p.configure();
        } catch (IOException | NoProcessorException ex) {
            Logger.getLogger(JmfRtpReceive.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //  以VoiceBytesCommon的saveAudioFile方法为准
    public static Path saveAudioFile(byte[] bytes, AudioFormat audioFormat) throws HeadlessException, NumberFormatException {
        try {
            var audioData = bytes;
            //  从bais流向ais
            try (var bais = new ByteArrayInputStream(audioData); var ais = new AudioInputStream(bais, audioFormat, audioData.length / audioFormat.getFrameSize())) {
                var audioFile = File.createTempFile("asr", ".wav");
                //定义最终保存的文件名
                System.out.println("开始生成语音文件 - " + audioFile.toURI());
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, audioFile);
                System.out.println();
                return audioFile.toPath();
            }
        } catch (IOException e) {
            System.err.println(e);
        }

        return null;
    }
}
