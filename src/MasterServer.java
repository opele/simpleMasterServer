import java.io.File;
import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class MasterServer {

    private static final String IP_LIST_URL = "https://codeberg.org/elko/dm_server/raw/commit/b739f8a149b40dd79b6ae04ceab908687fe42998/gameServerIpsForMasterServer.txt";

    public static void main(String[] args) throws Exception {

        ByteBuffer ips = ByteBuffer.allocate(1048576 * 5); // 5mb

        SortedSet<Integer> lastChallenges = new TreeSet<>();

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

        while (true) {
            byte[] buf = new byte[256];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            if (packet.getLength() == 1  && packet.getData()[0] == 0x71) {
                System.out.println("Received join query");

                int challenge = new Random().nextInt();
                if (lastChallenges.size() > 100) {
                    lastChallenges.removeLast();
                }
                lastChallenges.add(challenge);

                joinResponse.putInt(6, challenge);

                InetAddress address = packet.getAddress();
                int remotePort = packet.getPort();

                byte[] resp = joinResponse.array().clone();
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
                    String challengePrefix = "\\challenge\\";
                    int idxChallengeStart = requestStr.indexOf(challengePrefix) + challengePrefix.length();

                    if (idxChallengeStart > challengePrefix.length()) {

                        int idxChallengeEnd = requestStr.indexOf("\\", idxChallengeStart);

                        if (idxChallengeEnd > idxChallengeStart) {

                            String challengeReturned = requestStr.substring(idxChallengeStart, idxChallengeEnd);
                            Integer parsedChallenge = Integer.reverseBytes(Integer.parseInt(challengeReturned));

                            if (lastChallenges.contains(parsedChallenge)) {
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

        List<String> ipList = new ArrayList<>();

        String remoteIps = loadRemoteIps();

        if (remoteIps != null) {
            ipList = Arrays.stream(remoteIps.split("\n")).toList();
        } else {
            System.out.println("Fallback to local ips.txt file");
            File file = new File("./ips.txt");
            if (!file.exists()) {
                file.createNewFile();
            } else if (file.length() > 0) {
                ipList = Files.readAllLines(file.toPath());
            }
        }

        System.out.println("IPs loaded: " + ipList.size());

        ipList.stream()
                .filter(ip -> ip != null && !ip.isBlank())
                .map(MasterServer::ipToByte)
                .filter(Objects::nonNull)
                .forEach(ip -> ips.put(toPrimitives(ip)));
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

    private static String loadRemoteIps() {
        System.out.println("Loading remote ips from " + IP_LIST_URL);

        try (InputStream remoteIps = new URI(IP_LIST_URL).toURL().openStream()) {
            String ips = new String(remoteIps.readAllBytes());

            if (ips.isBlank()) return null;

            return ips.trim();

        } catch (Exception e) {
            System.err.println("Failed to get remote IP list: " + e.getMessage());
        }

        return null;
    }

}