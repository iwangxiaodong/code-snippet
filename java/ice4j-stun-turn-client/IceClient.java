import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.List;
import java.util.Random;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.NominationStrategy;
import org.ice4j.ice.RemoteCandidate;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TurnCandidateHarvester;
import org.ice4j.security.LongTermCredential;

/*
用法：
  启动两个main函数做为通讯Peer客户端
  然后手动互相复制彼此的SDPDescription字符串
  回车两次即能直接在控制台输入聊天信息了

依赖：
  implementation 'org.jitsi:ice4j:3.0-60-g28a23e1'
  https://github.com/jitsi/ice4j/blob/master/src/test/java/test/SdpUtils.java
  源项目 - https://github.com/hankcs/IceNAT
  代码原理讲解 - https://www.hankcs.com/program/network/test-udp-holes-penetrating-nat.html
 */
public class IceClient {

    public static void main(String[] args) throws Throwable {
        try {
            IceClient client = new IceClient(new Random().nextInt(10000, 10100), "text");
            client.init();
            client.exchangeSdpWithPeer();
            client.startConnect();

            final DatagramSocket socket = client.getDatagramSocket();
            final SocketAddress remoteAddress = client
                    .getRemotePeerSocketAddress();
            System.out.println(socket.toString());
            new Thread(() -> {
                while (true) {
                    try {
                        byte[] buf = new byte[1024];
                        DatagramPacket packet = new DatagramPacket(buf,
                                buf.length);
                        socket.receive(packet);
                        System.out.println(packet.getAddress() + ":" + packet.getPort() + " says: " + new String(packet.getData(), 0, packet.getLength()));
                    } catch (IOException e) {
                        System.err.println(e);
                    }
                }
            }).start();

            new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    String line;
                    // 从键盘读取
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.length() == 0) {
                            break;
                        }
                        byte[] buf = (line).getBytes();
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        packet.setSocketAddress(remoteAddress);
                        socket.send(packet);
                    }
                } catch (IOException e) {
                    System.err.println(e);
                }
            }).start();
        } catch (Exception e) {
            System.err.println(e);
        }

    }

    private final int port;

    private final String streamName;

    private Agent agent;

    private String localSdp;

    private String remoteSdp;

    private final String[] stunServers;
    private final String[] turnServers; // 中继转发
    private final String username = "openrelayproject";
    private final String password = "openrelayproject";

    private final IceProcessingListener listener;

    public IceClient(int port, String streamName) {
        this.turnServers = new String[]{"openrelay.metered.ca:80"};
        this.stunServers = new String[]{"openrelay.metered.ca:443"};
        this.port = port;
        this.streamName = streamName;
        this.listener = new IceProcessingListener();
    }

    public void init() throws Throwable {

        agent = createAgent(port, streamName);

        agent.setNominationStrategy(NominationStrategy.NOMINATE_HIGHEST_PRIO);

        agent.addStateChangeListener(listener);

        agent.setControlling(false);

        agent.setTa(10000);

        localSdp = SdpUtils.createSDPDescription(agent);

        System.out.println("=================== feed the following"
                + " to the remote agent ===================");

        System.out.println(localSdp);

        System.out.println("==============================================================================\n");
    }

    public DatagramSocket getDatagramSocket() throws Throwable {

        IceMediaStream stream = agent.getStream(streamName);
        if (stream != null) {
            List<Component> components = stream.getComponents();
            for (Component c : components) {
                System.out.println(c);
            }

            Component component = stream.getComponent(Component.RTP);
            if (component != null) {
                return component.getSocket();
            }
        }

        return null;

//        LocalCandidate localCandidate = agent
//                .getSelectedLocalCandidate(streamName);
//        System.out.println(localCandidate.toString());
//        LocalCandidate candidate = (LocalCandidate) localCandidate;
//        return candidate.getDatagramSocket();
    }

    public SocketAddress getRemotePeerSocketAddress() {
        RemoteCandidate remoteCandidate = agent
                .getSelectedRemoteCandidate(streamName);
        System.out.println("Remote candinate transport address:"
                + remoteCandidate.getTransportAddress());
        System.out.println("Remote candinate host address:"
                + remoteCandidate.getHostAddress());
        System.out.println("Remote candinate mapped address:"
                + remoteCandidate.getMappedAddress());
        System.out.println("Remote candinate relayed address:"
                + remoteCandidate.getRelayedAddress());
        System.out.println("Remote candinate reflexive address:"
                + remoteCandidate.getReflexiveAddress());
        return remoteCandidate.getTransportAddress();
    }

    /**
     * Reads an SDP description from the standard input.In production
     * environment that we can exchange SDP with peer through signaling
     * server(SIP server)
     *
     * @throws java.lang.Throwable
     */
    public void exchangeSdpWithPeer() throws Throwable {
        System.out.println("Paste remote SDP here. Enter an empty line to proceed:");
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                System.in));

        StringBuilder buff = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) {
                break;
            }
            buff.append(line);
            buff.append("\r\n");
        }

        remoteSdp = buff.toString();

        SdpUtils.parseSDP(agent, remoteSdp);
    }

    public void startConnect() throws InterruptedException {

        if (remoteSdp.isBlank()) {
            throw new NullPointerException(
                    "Please exchange sdp information with peer before start connect! ");
        }

        agent.startConnectivityEstablishment();

        // agent.runInStunKeepAliveThread();
        synchronized (listener) {
            listener.wait();
        }

    }

    private Agent createAgent(int rtpPort, String streamName) throws Throwable {
        return createAgent(rtpPort, streamName, false);
    }

    private Agent createAgent(int rtpPort, String streamName,
            boolean isTrickling) throws Throwable {

        long startTime = System.currentTimeMillis();

        //  依赖了kotlin.reflect.KProperty
        Agent agentNew = new Agent();

        agentNew.setTrickling(isTrickling);

        // STUN
        for (String server : stunServers) {
            String[] pair = server.split(":");
            agentNew.addCandidateHarvester(new StunCandidateHarvester(
                    new TransportAddress(pair[0], Integer.parseInt(pair[1]),
                            Transport.UDP)));
        }

        // TURN
        LongTermCredential longTermCredential = new LongTermCredential(username,
                password);

        for (String server : turnServers) {
            String[] pair = server.split(":");
            agentNew.addCandidateHarvester(new TurnCandidateHarvester(
                    new TransportAddress(pair[0], Integer.parseInt(pair[1]), Transport.UDP),
                    longTermCredential));
        }
        // STREAMS
        createStream(rtpPort, streamName, agentNew);

        long endTime = System.currentTimeMillis();
        long total = endTime - startTime;

        System.out.println("Total harvesting time: " + total + "ms.");

        return agentNew;
    }

    private IceMediaStream createStream(int rtpPort, String streamName,
            Agent agent) throws Throwable {
        long startTime = System.currentTimeMillis();
        IceMediaStream stream = agent.createMediaStream(streamName);
        // rtp
        Component component = agent.createComponent(stream,
                rtpPort, rtpPort, rtpPort + 100);

        long endTime = System.currentTimeMillis();
        System.out.println("Component Name:" + component.getName());
        System.out.println("RTP Component created in " + (endTime - startTime) + " ms");

        return stream;
    }

    /**
     * Receive notify event when ice processing state has changed.
     */
    public static final class IceProcessingListener implements
            PropertyChangeListener {

        private final long startTime = System.currentTimeMillis();

        @Override
        public void propertyChange(PropertyChangeEvent event) {

            Object state = event.getNewValue();

            System.out.println("Agent entered the " + state + " state.");
            if (state == IceProcessingState.COMPLETED) {
                long processingEndTime = System.currentTimeMillis();
                System.out.println("Total ICE processing time: "
                        + (processingEndTime - startTime) + "ms");
                Agent agent = (Agent) event.getSource();
                List<IceMediaStream> streams = agent.getStreams();

                for (IceMediaStream stream : streams) {
                    System.out.println("Stream name: " + stream.getName());
                    List<Component> components = stream.getComponents();
                    for (Component c : components) {
                        System.out.println("------------------------------------------");
                        System.out.println("Component of stream:" + c.getName()
                                + ",selected of pair:" + c.getSelectedPair());
                        System.out.println("------------------------------------------");
                    }
                }

                System.out.println("Printing the completed check lists:");
                for (IceMediaStream stream : streams) {

                    System.out.println("Check list for  stream: " + stream.getName());

                    System.out.println("nominated check list:" + stream.getCheckList());
                }
                synchronized (this) {
                    this.notifyAll();
                }
            } else if (state == IceProcessingState.TERMINATED) {
                System.out.println("ice processing TERMINATED");
            } else if (state == IceProcessingState.FAILED) {
                System.out.println("ice processing FAILED");
                ((Agent) event.getSource()).free();
            }
        }
    }
}
