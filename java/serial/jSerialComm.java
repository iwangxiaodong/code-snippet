import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Formatter;

public class jSerialComm {

    public static void main(String[] args) throws IOException {
        SerialPort serial = SerialPort.getCommPorts()[0];
        serial.setComPortParameters(115200, 8, 1, 0);
        //serial.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0, 0);
        if (serial.openPort()) {
            System.out.println("Serial port is open");

            serial.addDataListener(new SerialPortDataListener() {
                @Override
                public int getListeningEvents() {
                    return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
                }

                @Override
                public void serialEvent(SerialPortEvent event) {
                    byte[] newData = event.getReceivedData();
                    System.out.println("Received data of size: " + newData.length);
                    System.out.println(bytesToHex(newData)+"\n");
                }
            });

            byte[] c1 = new byte[]{(byte) 0x2A, (byte) 0x2D, (byte) 0x2E};
            serial.getOutputStream().write(c1);
            serial.getOutputStream().flush();
            System.out.println("c1");

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                System.err.println(ex);
            }

            System.out.println("c2");
            byte[] c2 = new byte[]{(byte) 0xFD, (byte) 0x00, (byte) 0x12, (byte) 0xFF};
            serial.getOutputStream().write(c2);
            serial.getOutputStream().flush();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                System.err.println(ex);
            }

            serial.removeDataListener();
            serial.closePort();
        } else {
            System.out.println("Serial port is not open");
        }
    }

    public static String bytesToHex(final byte[] bytes) {
        String result;
        try ( Formatter formatter = new Formatter()) {
            for (byte b : bytes) {
                // 格式化并追加,全小写且无空格, %02X则返回全大写
                formatter.format("%02x", b);
            }
            result = formatter.toString();
        }
        return result;
    }

    // 支持大小写及带空格入参
    public static byte[] hexToBytes(String hex) {
        if (hex.contains(" ")) {
            hex = hex.replace(" ", "");
        }

        // 等同 Integer.valueOf(hexString, 16);
        byte[] bytes = new BigInteger(hex, 16).toByteArray();
        int srcPos = (bytes[0] == 0) ? 1 : 0;   //  数组首个值表示正负标志，需要移除
        return Arrays.copyOfRange(bytes, srcPos, bytes.length);
    }
}
