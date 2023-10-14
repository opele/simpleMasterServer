import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Tests {

    public static void main(String args[]) throws Exception {
        // test sending to master server
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName("135.125.202.170");
        byte[] buf = new byte[]{1, 2, 3};
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, 27010);
        socket.send(packet);
    }

    static void testByteToString() {
        byte[] data = new byte[] {0x30, 0x0a, 0x5c, 0x70, 0x72, 0x6f};
        System.out.println(new String(data, StandardCharsets.US_ASCII));
    }

    static void testIpToByte() {
        byte[] expectedResult = new byte[] {45, (byte) 141, 0, (byte) 252,  0x69, (byte) 0x88};
        byte[] actual = MasterServer.toPrimitives(MasterServer.ipToByte("45.141.0.252:27016"));
        System.out.println("Equal: " + Arrays.equals(expectedResult, actual));
        System.out.println("Expected: " + Arrays.toString(expectedResult));
        System.out.println("Actual: " + Arrays.toString(actual));
    }

}
