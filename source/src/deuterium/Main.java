package deuterium;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Enumeration;

public class Main {

	public static final InetSocketAddress DISCOVERY_MULTICAST_GROUP = new InetSocketAddress("239.255.10.200", 50160);
	public static final int DISCOERY_TIMEOUT_MS = 1000;
	
	/**
	 * If no arguments, start server and connect to local server.
	 * 
	 * If one argument, try to parse it as server IP.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("deuterium0.0.1 --- " + Arrays.toString(args));

		String serverUrl;
		
		if(args.length == 0) {
			// No arguments, try to find server in local network
			serverUrl = discoverServer();
			
			if(serverUrl == null) {
				// No server was found, start one on this host
				System.out.println("No server discovered in local network. Starting server on this host...");
				
				Server server = new Server();
				new Thread(server).start();
				makeLocalServerDiscoverable();
				
				// Give the server some time to initialize, so the connection won't be denied if the client thread starts before the server
				// This should be properly synchronized instead of just waiting
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} else {
			// If argument was given, assume it is the hostname of the server
			serverUrl = args[0];
		}
		
		
		Client client = new Client(serverUrl);
		new Thread(client).start();
		Shell.run(client.receivedFromServerQueue, client.willSendToServerQueue);
	}

	private static String discoverServer() {
		try {
			MulticastSocket discoverSocket = new MulticastSocket(DISCOVERY_MULTICAST_GROUP.getPort());
			
			setNetworkInterfaceWithIPv4Multicast(discoverSocket);
			
			discoverSocket.joinGroup(DISCOVERY_MULTICAST_GROUP.getAddress());
			discoverSocket.setSoTimeout(DISCOERY_TIMEOUT_MS);
			
			DatagramPacket packet = new DatagramPacket(new byte[128], 0);
			discoverSocket.receive(packet);
			
			String senderAddr = packet.getAddress().getHostAddress();
			System.out.println("Discovered server at " + senderAddr);
			
			discoverSocket.close();
			
			return senderAddr;
		} catch (SocketTimeoutException e) {
			return null; // Intentionally return null, since no server responded in time
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}

	/**
	 * This makes sure the discovery will work even if the default network interface supports
	 * IPv6 only.
	 * 
	 * @param discoverSocket
	 * @throws SocketException
	 */
	private static void setNetworkInterfaceWithIPv4Multicast(MulticastSocket discoverSocket) throws SocketException {
		Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
		while (e.hasMoreElements()) {
		    NetworkInterface n = (NetworkInterface) e.nextElement();
		    Enumeration<InetAddress> ee = n.getInetAddresses();
		    while (ee.hasMoreElements()) {
		        InetAddress i = (InetAddress) ee.nextElement();
		        if (i.isSiteLocalAddress() && !i.isAnyLocalAddress() && !i.isLinkLocalAddress()
		                && !i.isLoopbackAddress() && !i.isMulticastAddress()) {
		        	discoverSocket.setNetworkInterface(NetworkInterface.getByName(n.getName()));
		        }
		    }
		}
	}

	private static void makeLocalServerDiscoverable() {
		new Thread(() -> {
			MulticastSocket publishSock = null;
			try {
				publishSock = new MulticastSocket(DISCOVERY_MULTICAST_GROUP.getPort());
				
				while(true) {
					publishSock.send(new DatagramPacket(new byte[] { 42, 24 }, 2, DISCOVERY_MULTICAST_GROUP));
					Thread.sleep(DISCOERY_TIMEOUT_MS / 4);
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				if (publishSock != null) publishSock.close();
			}
		}).start();
	}
	
}
