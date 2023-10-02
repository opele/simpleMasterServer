import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {

    public static void main(String[] args) throws Exception {

        //testByteToString();

        ByteBuffer ips = ByteBuffer.allocate(1048576 * 5); // 5mb

        SortedSet<String> lastChallenges = new TreeSet<>();

        byte[] footer = new byte[] {(byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0};

        int port = 27010;

        InetAddress inetAddress;
        if (args.length >= 1) {
            inetAddress = InetAddress.getByName(args[0]);
        } else {
            inetAddress = InetAddress.getLocalHost();
        }

        DatagramSocket socket = new DatagramSocket(port, inetAddress);

        loadIps(ips);

        ByteBuffer joinResponse = ByteBuffer.allocate(10);
        joinResponse.put(new byte []{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x73, (byte) 0x0A});

        System.out.println("Start listening at port " + port + " host: " + inetAddress.toString());

        while(true) {
            byte[] buf = new byte[256];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            if (packet.getLength() == 1  && packet.getData()[0] == 0x71) {
                System.out.println("Received join query");

                int challenge = new Random().nextInt();
                if (lastChallenges.size() > 100) {
                    lastChallenges.removeLast();
                }
                lastChallenges.add("" + challenge);

                joinResponse.put(6, ByteBuffer.allocate(4).putInt(challenge), 0, 4);

                InetAddress address = packet.getAddress();
                int remotePort = packet.getPort();

                byte[] resp = joinResponse.array();
                packet = new DatagramPacket(resp, resp.length, address, remotePort);
                socket.send(packet);

            } else if (packet.getLength() > 7 && packet.getData()[0] == 0x31) {
                System.out.println("Received ip list query");
                InetAddress address = packet.getAddress();
                int remotePort = packet.getPort();

                // no need to put the footer in as ByteBuffer allocates with 0s
                ByteBuffer ipsResponse = ByteBuffer.allocate(ips.position() + footer.length);
                ips.get(0, ipsResponse.array());

                packet = new DatagramPacket(ipsResponse.array(), ipsResponse.array().length, address, remotePort);
                socket.send(packet);

            } else if (packet.getLength() > 50) {
                ByteBuffer request = ByteBuffer.allocate(50).put(packet.getData(), 0, 50);
                String requestStr = new String(request.array(), StandardCharsets.US_ASCII);
                if (requestStr.startsWith("0\n\\protocol\\")) {

                    System.out.println("Received challenge response");
                    int idxChallengeStart = requestStr.indexOf("\\challenge\\") + 1;

                    if (idxChallengeStart > 1) {

                        int idxChallengeEnd = requestStr.indexOf("\\", idxChallengeStart);

                        if (idxChallengeEnd > idxChallengeStart) {

                            String challengeReturned = requestStr.substring(idxChallengeStart, idxChallengeEnd);

                            if (lastChallenges.contains(challengeReturned)) {
                                byte[] address = packet.getAddress().getAddress();
                                int remotePort = packet.getPort();

                                ByteBuffer ipNew = ByteBuffer
                                        .allocate(address.length + 2)
                                        .put(address)
                                        .put((byte)((remotePort >> 8) & 0xff))
                                        .put((byte)(remotePort & 0xff));

                                ips.put(ipNew.array());

                            } else {
                                System.out.println("Challenge failed: wrong");
                            }

                        } else {
                            System.out.println("Challenge failed: empty");
                        }

                    } else {
                        System.out.println("Challenge failed: not returned");
                    }
                }
            }
        }
    }

    private static void loadIps(ByteBuffer ips) throws Exception {
        // fixed header
        ips.put(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x66, (byte) 0x0A });

        File file = new File("./ips.txt");
        if (!file.exists()) {
            file.createNewFile();
        } else if (file.length() > 0) {
            List<String> ipList = Files.readAllLines(file.toPath());

            ipList.stream()
                    .filter(ip -> ip != null && !ip.trim().isBlank())
                    .map(Main::ipToByte)
                    .filter(Objects::nonNull)
                    .forEach(ip -> ips.put(toPrimitives(ip)));
        }

    }



    static Byte[] ipToByte(String ip) {
        try {
            String ipStr = ip.split(":")[0];
            String[] ipOctet = ipStr.split("\\.");
            if (ipOctet.length != 4) throw new RuntimeException("invalid ip");
            short port = Short.parseShort(ip.split(":")[1]);

            List<Byte> ipBytes = new ArrayList<>();
            ipBytes.add((byte)Integer.parseInt(ipOctet[0]));
            ipBytes.add((byte)Integer.parseInt(ipOctet[1]));
            ipBytes.add((byte)Integer.parseInt(ipOctet[2]));
            ipBytes.add((byte)Integer.parseInt(ipOctet[3]));
            ipBytes.add((byte)((port >> 8) & 0xff));
            ipBytes.add((byte)(port & 0xff));

            return ipBytes.toArray(Byte[]::new);

        } catch (Exception e) { }
        return null;
    }

    static byte[] toPrimitives(Byte[] oBytes) {

        if (oBytes == null) {
            return new byte[0];
        }

        byte[] bytes = new byte[oBytes.length];

        for(int i = 0; i < oBytes.length; i++) {
            bytes[i] = oBytes[i];
        }

        return bytes;
    }

    static void testByteToString() {
        byte[] data = new byte[] {0x30, 0x0a, 0x5c, 0x70, 0x72, 0x6f};
        System.out.println(new String(data, StandardCharsets.US_ASCII));
    }

    static void testIpToByte() {
        byte[] expectedResult = new byte[] {45, (byte) 141, 0, (byte) 252,  0x69, (byte) 0x88};
        byte[] actual = toPrimitives(ipToByte("45.141.0.252:27016"));
        System.out.println("Equal: " + Arrays.equals(expectedResult, actual));
        System.out.println("Expected: " + Arrays.toString(expectedResult));
        System.out.println("Actual: " + Arrays.toString(actual));
    }
}