    implementation("org.jitsi:libjitsi:1.1-34-gb93ce2ee")
    implementation("org.jitsi:fmj:1.0.2-jitsi")
    implementation("org.jitsi:jitsi-lgpl-dependencies:1.2-23-g7b49874:win32-x86-64")

tasks.named<JavaExec>("run") { 
    jvmArgs = listOf("-Djava.library.path=D:\\temp\\libjitsi-1.1-34-gb93ce2ee\\win32-x86-64\\")
    //args +=['--local-port-base=11111', '--remote-host=localhost', '--remote-port-base=22222'] // 接收入参；2分钟自动退出
    //args +=['--local-port-base=22222', '--remote-host=localhost', '--remote-port-base=11111'] // 发送入参；1分钟自动退出
}
            
            new PortAudioStreamCallback() {
                @Override
                public int callback(ByteBuffer bb, ByteBuffer bb1) {
                    System.out.println("callback");
                    //sourceDataLine2.write(bb1.array(), 0, bb1.array().length);//播放录制的声音
                    return PortAudioStreamCallback.RESULT_CONTINUE;
                }

                @Override
                public void finishedCallback() {
                    System.out.println("finishedCallback");
                }
            }


主机 API 是操作系统提供的音频驱动程序接口，例如 Windows 上的 WASAPI、DirectSound、ASIO，macOS 上的 Core Audio，Linux 上的 ALSA、JACK 等。
paHostApiNotFound (-9977)：
当调用 Pa_OpenStream() 或其他需要指定主机 API 的 PortAudio 函数时，如果 PortAudio 无法找到指定的主机 API，就会返回此错误。
这个错误，经常发生在，使用Pa_OpenStream()，但是没有使用Pa_OpenDefaultStream()，并且没有正确的设置PaStreamParameters。


//            var fpb = PortAudioStream.class.getDeclaredField("framesPerBuffer");
//            fpb.setAccessible(true);
//            framesPerBuffer = (int) fpb.get(p2);
//            System.out.println(framesPerBuffer);
//            var sf = PortAudioStream.class.getDeclaredField("stream");
//            sf.setAccessible(true);
//            stream = (long) sf.get(p2);
//            System.out.println(stream);
