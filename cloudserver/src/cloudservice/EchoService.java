package cloudservice;

import communication.Communication;
import cloudServiceCrypto.EchoSessionKeyDecComm;
import cloudServiceCrypto.EchoTktDecryption;
import cloudServiceCrypto.EchoSessionKeyEncryption;
import cloudServiceCrypto.EchoSessionKeyDecryption;
import cloudServiceCrypto.EchoSessionKeyEncComm;
import cloudservice.config.Config;
import cloudservice.config.SSLConfig;
import java.io.FileNotFoundException;
import java.net.Socket;
import java.net.ServerSocket;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import merrimackutil.cli.LongOption;
import merrimackutil.cli.OptionParser;
import merrimackutil.util.NonceCache;
import merrimackutil.util.Tuple;
import packets.ClientHello;
import packets.ClientResponse;
import packets.CommPhase;
import packets.Packet;
import packets.HandshakeStatus;
import packets.PacketType;
import static packets.PacketType.ClientHello;
import static packets.PacketType.CommPhase;
import packets.ServerHello;
import packets.Ticket;

public class EchoService {

    private static Config config;
    private static SSLConfig sslconfig;
    private static SSLServerSocket server;
    private static long val = 0;
    private static long sTime = 0;
    private static byte[] serverSidesessionKey;
    private static boolean handshakeStatus = false;
    private static NonceCache nc = new NonceCache(32, 30);

    public static void main(String[] args) throws FileNotFoundException, InvalidObjectException, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException, IllegalBlockSizeException, InvalidAlgorithmParameterException, BadPaddingException {
        OptionParser op = new OptionParser(args);
        LongOption[] ar = new LongOption[3];
        ar[0] = new LongOption("config", true, 'c');
        ar[1] = new LongOption("auth", true, 'a');
        ar[2] = new LongOption("help", false, 'h');
        op.setLongOpts(ar);
        op.setOptString("hc:a:");
        Tuple<Character, String> opt = op.getLongOpt(false);
        if (opt == null || Objects.equals(opt.getFirst(), 'h')) {
            System.out.println("usage:\n"
                    + "cloudserver\n"
                    + " cloudserver --config <configfile>\n"
                    + " cloudserver --help\n"
                    + "options:\n"
                    + " -c, --config Set the config file.\n"
                    + " -a, --auth Set the auth config file.\n"
                    + " -h, --help Display the help.");
            System.exit(0);
        } else if (Objects.equals(opt.getFirst(), 'c')) {
            // Initialize config
            config = new Config(opt.getSecond());
            opt = op.getLongOpt(false);
            if (Objects.equals(opt.getFirst(), 'a')) {
                sslconfig = new SSLConfig(opt.getSecond());
                try {
                    System.setProperty("javax.net.ssl.keyStore", sslconfig.getKeystore_file());
                    System.setProperty("javax.net.ssl.keyStorePassword", sslconfig.getKeystore_pass());
                    SSLServerSocketFactory sslFact;
                    // Get a copy of the deafult factory. This is what ever the
                    // configured provider gives.
                    sslFact = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();

                    // Set up the server socket using the specified port number.
                    server = (SSLServerSocket) sslFact.createServerSocket(config.getPort());

                    // Set the protocol to 1.3
                    server.setEnabledProtocols(new String[]{"TLSv1.3"});
                    // Create the server
                    System.out.println("running on port " + config.getPort());
                    // Poll for input
                    while (poll() != true) {

                    }

                    // Close the server when completed or error is thrown.
                    server.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                } catch (NoSuchMethodException ex) {
                    System.out.println("EchoService No Such Method Exception");
                    Logger.getLogger(EchoService.class.getName()).log(Level.SEVERE, null, ex);
                } catch (NoSuchAlgorithmException ex) {
                    System.out.println("EchoService No Such Algorithm Exception");
                    Logger.getLogger(EchoService.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    /**
     * Waits for a connection with a peer socket, then polls for a message being
     * sent. Each iteration of the loop operates for one message, as not to
     * block multi-peer communication.
     *
     * @throws IOException
     */
    private static boolean poll() throws IOException, NoSuchMethodException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException, IllegalBlockSizeException, InvalidAlgorithmParameterException, BadPaddingException {
        while (true) { // Consistently accept connections

            // Establish the connection & read the message
            Socket peer = server.accept();
            sTime = System.currentTimeMillis();
            if (val != 0) {
                if (sTime - System.currentTimeMillis() >= val) {
                    return true;
                }
            }

            // Determine the packet type.
            System.out.println("Waiting for a packet...");
            final Packet packet = Communication.read(peer);

            System.out.println("Packet Recieved: [" + packet.getType().name() + "]");

            // Switch statement only goes over packets expected by the KDC, any other packet will be ignored.
            switch (packet.getType()) {
                // ClientHello package
                case ClientHello: {
                    // MESSAGE 2:  decrypt ticket + send fresh nonceS, iv, and encryption of fresh nonceS
                    ClientHello ClientHello_packet = (ClientHello) packet;
                    // tkt in string format
                    String tkt = ClientHello_packet.getTkt();
                    // Break apart ticket to grab data inside
                    Ticket ticket = new Ticket(tkt, PacketType.Ticket);
                    if (val == 0) {

                        val = ticket.getValidityTime();
                    }
                    // Grab and and add nonce to cache
                    String usedNonceC = ClientHello_packet.getNonce();
                    byte[] bytesUsedNonceC = Base64.getDecoder().decode(usedNonceC);
                    nc.addNonce(bytesUsedNonceC);

                    // Config strings
                    String serviceName = config.getService_name();
                    String serviceSecret = config.getService_secret();

                    // Perform decryption with info from tkt, this gives us the session key
                    serverSidesessionKey = EchoTktDecryption.decrypt(ticket.geteSKey(), ticket.getIv(), serviceName, serviceSecret, ticket.getCreateTime(), ticket.getValidityTime(), ticket.getsName());

                    System.out.println("EchoService session key: " + Base64.getEncoder().encodeToString(serverSidesessionKey));

                    // Fresh nonce S
                    byte[] nonceSBytes = nc.getNonce();
                    String nonceSString = Base64.getEncoder().encodeToString(nonceSBytes);

                    // Encrypt nonce C 
                    byte[] EncNonceC = EchoSessionKeyEncryption.encrypt(serverSidesessionKey, bytesUsedNonceC, ticket.getValidityTime(), ticket.getCreateTime(), serviceName, ticket.getsName());
                    System.out.println(nonceSString);
                    System.out.println("ct: " + Base64.getEncoder().encodeToString(EncNonceC));
                    System.out.println("iv: " + Base64.getEncoder().encodeToString(EchoSessionKeyEncryption.getRawIv()));
                    System.out.println("session name : " + serviceName);
                    System.out.println("session key: " + Base64.getEncoder().encodeToString(serverSidesessionKey));
                    // Create the packet and send
                    ServerHello ServerHello_packet = new ServerHello(nonceSString, serviceName, Base64.getEncoder().encodeToString(EchoSessionKeyEncryption.getRawIv()), Base64.getEncoder().encodeToString(EncNonceC));
                    Communication.send(peer, ServerHello_packet);
                }
                break;
                // Client Response package
                case ClientResponse: {
                    //MESSAGE 4: Received client response, let's check nonce validity and give a status
                    ClientResponse ClientResponse_packet = (ClientResponse) packet;

                    //check nonce S is same
                    byte[] receivedNonceS = EchoSessionKeyDecryption.decrypt(ClientResponse_packet.geteSKey(), ClientResponse_packet.getIv(), ClientResponse_packet.getcName(), serverSidesessionKey);
                    System.out.println(Base64.getEncoder().encodeToString(receivedNonceS));
                    if (nc.containsNonce(receivedNonceS)) {
                        System.out.println("Nonce matched");
                        handshakeStatus = true; // set status true
                        // Create packet containing status
                        HandshakeStatus handshakeStatus_packet = new HandshakeStatus(handshakeStatus);
                        Communication.send(peer, handshakeStatus_packet); // Send packet
                    } else {
                        System.out.println("Nonce doesn't macth, possible replay attack");
                        HandshakeStatus handshakeStatus_packet = new HandshakeStatus(handshakeStatus); // Status remains false
                        Communication.send(peer, handshakeStatus_packet); // Send packet

                    }

                }
                ;
                break;
                case CommPhase: {
                    CommPhase commPhase_packet = (CommPhase) packet;
                    commPhase_packet.getNonce();
                    commPhase_packet.getIv();
                    commPhase_packet.getcName();
                    commPhase_packet.geteMsg();
                    commPhase_packet.getsName();

                    byte[] receivecNonceBytes = Base64.getDecoder().decode(commPhase_packet.getNonce());
                    nc.addNonce(Base64.getDecoder().decode(commPhase_packet.getNonce()));

                    byte[] newNonce = nc.getNonce();
                    String newNonceString = Base64.getEncoder().encodeToString(newNonce);
                    byte[] receivedMsg = EchoSessionKeyDecComm.decrypt(commPhase_packet.geteMsg(), commPhase_packet.getIv(), commPhase_packet.getcName(), commPhase_packet.getsName(), serverSidesessionKey, receivecNonceBytes);

                    String receivedMsgString = new String(receivedMsg, StandardCharsets.UTF_8);

                    String uppercase = receivedMsgString.toUpperCase();
                    System.out.println("sending");
                    byte[] uppercaseBytes = Base64.getDecoder().decode(uppercase);

                    byte[] encMsg = EchoSessionKeyEncComm.encrypt(serverSidesessionKey, uppercase, commPhase_packet.getcName(), commPhase_packet.getsName(), newNonce);

                    String encMsgToString = Base64.getEncoder().encodeToString(encMsg);

                    CommPhase CommPhase_packet = new CommPhase(newNonceString, commPhase_packet.getcName(), commPhase_packet.getsName(), Base64.getEncoder().encodeToString(EchoSessionKeyEncComm.getRawIv()), encMsgToString);

                    Communication.send(peer, CommPhase_packet); // Send packet

                }
                ;
                break;

            }
        }

    }

}