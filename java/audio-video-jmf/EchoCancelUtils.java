package com.example.jmf.rtp.examples;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.media.Buffer;
import javax.media.Renderer;
import javax.media.ResourceUnavailableException;
import javax.media.control.FormatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import static org.jitsi.impl.neomedia.MediaServiceImpl.DISABLE_VIDEO_SUPPORT_PNAME;
import org.jitsi.impl.neomedia.device.AudioSystem;
import static org.jitsi.impl.neomedia.device.AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO;
import org.jitsi.impl.neomedia.device.PortAudioSystem;
import org.jitsi.impl.neomedia.jmfext.media.protocol.portaudio.DataSource;
import org.jitsi.impl.neomedia.jmfext.media.protocol.portaudio.PortAudioStream;
import org.jitsi.service.libjitsi.LibJitsi;

//  无回声消除效果，但使用了PortAudioRenderer进行播放。
public class EchoCancelUtils {

    public static void main(String[] args) throws ReflectiveOperationException, IOException {
        LibJitsi.start();
        LibJitsi.getConfigurationService().setProperty(DISABLE_VIDEO_SUPPORT_PNAME, true);
        //LibJitsi.getConfigurationService().setProperty("net.java.sip.communicator.impl.neomedia.echocancel.filterLengthInMillis", 10000);

        var cls = Class.forName("org.jitsi.impl.neomedia.device.PortAudioSystem");
        var c = cls.getDeclaredConstructor();
        c.setAccessible(true);
        var pas = (PortAudioSystem) c.newInstance();
        System.out.println(pas);

        var d = pas.getDevices(AudioSystem.DataFlow.CAPTURE);
        System.out.println(d);
        System.out.println(d.getFirst());

        var ds = new DataSource(d.getFirst().getLocator());
        var x = ds.getFormatControls();
        System.out.println(Arrays.deepToString(x));
        var cs = DataSource.class.getDeclaredMethod("createStream", int.class, FormatControl.class);
        cs.setAccessible(true);

        //System.out.println(Arrays.deepToString(d.getFirst().getFormats()));
        var devicdId = DataSource.getDeviceID(d.getFirst().getLocator());
        System.out.println(devicdId);

        var as = AudioSystem.getAudioSystem(LOCATOR_PROTOCOL_PORTAUDIO);
        System.out.println(as);
        System.out.println(as.isEchoCancel());
        System.out.println(as.isDenoise());

        var dconn = DataSource.class.getDeclaredMethod("doConnect");
        dconn.setAccessible(true);
        dconn.invoke(ds);

        var did = DataSource.class.getDeclaredMethod("getDeviceID");
        did.setAccessible(true);
        System.out.println("did");
        var dId = did.invoke(ds);
        System.out.println(dId);

        System.out.println(Arrays.deepToString(x[0].getSupportedFormats()));
        p2 = (PortAudioStream) cs.invoke(ds, 0, x[0]);
        System.out.println(p2);

        var setId = PortAudioStream.class.getDeclaredMethod("setDeviceID", String.class);
        setId.setAccessible(true);
        setId.invoke(p2, dId);

        var conn2 = PortAudioStream.class.getDeclaredMethod("connect");
        conn2.setAccessible(true);
        conn2.invoke(p2);

        try {
            var bpb = PortAudioStream.class.getDeclaredField("bytesPerBuffer");
            bpb.setAccessible(true);
            bytesPerBuffer = (int) bpb.get(p2);
            System.out.println(bytesPerBuffer);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            Logger.getLogger(EchoCancelUtils.class.getName()).log(Level.SEVERE, null, ex);
        }

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

        var af = (javax.media.format.AudioFormat) p2.getFormat();
        System.out.println("af - " + af.getSigned() + "|" + af.getEndian());
        format2 = new javax.sound.sampled.AudioFormat(
                (float) af.getSampleRate(), af.getSampleSizeInBits(), af.getChannels(), af.getSigned() == 1, af.getEndian() == 1);

        p2.start();

        getAudio_A();
    }
    static Renderer par;
    static PortAudioStream p2;
    static javax.sound.sampled.AudioFormat format2 = new javax.sound.sampled.AudioFormat(
            44100, 16, 1, true, false);
    static private int bytesPerBuffer;// = 640;

    static public void getAudio_A() {
        try {
            TargetDataLine targetDataLine = javax.sound.sampled.AudioSystem.getTargetDataLine(format2);
            targetDataLine.open(format2);
            targetDataLine.start();

            new Thread(() -> {
                byte[] b = new byte[bytesPerBuffer];//缓存音频数据
                int a = 0;
                while (a != -1) {
                    a = targetDataLine.read(b, 0, b.length);//捕获录音数据

                    Buffer buffer = new Buffer();
                    buffer.setData(b);
                    buffer.setLength(b.length);

                    try {
                        p2.read(buffer);
                    } catch (IOException ex) {
                        ex.printStackTrace(System.err);
                    }
                    //  边录边播
                    if (a != -1) {
                        //sourceDataLine.write(b, 0, a);//播放录制的声音
                        par.process(buffer);
                    }
                }
            }).start();
        } catch (LineUnavailableException e) {
            e.printStackTrace(System.err);
        }
    }
}
