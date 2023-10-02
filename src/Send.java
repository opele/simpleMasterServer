import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Send {

    public static void main(String args[]) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName("135.125.202.170");
        byte[] buf = new byte[]{1, 2, 3};
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, 27010);
        socket.send(packet);
    }

}
