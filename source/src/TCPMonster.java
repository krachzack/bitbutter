import java.io.*;
import java.net.*;
import java.util.Scanner;

class TCPMonster {
	private static final int UPDATE_INTERVAL = 1000;
	private static final int SERVER_PORT = 40000;
	
	private static float[][] data = new float[100][];
	
	private static String username = "User";

	public static void main(String argv[]) {
		System.out.println("Are you trying to start a client? (true/false)");
		Scanner s = new Scanner(System.in);
		boolean isClient = Boolean.parseBoolean(s.nextLine());
		s.close();
		
		if (isClient) startServerSync();
		else startServer();
	}
	
	public static void startServer() {
		new Thread(() -> {
			ServerSocket sSocket = null;
			try {
				sSocket = new ServerSocket(SERVER_PORT);
				while (true) {
					Socket cSocket = sSocket.accept();
					UniversalDTO request = (UniversalDTO) new ObjectInputStream(cSocket.getInputStream()).readObject();
					if (request.getEvent().equals("Join")) threadedUpdate(cSocket, request);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally { 
				try { sSocket.close(); } catch (IOException e) {}
			}
		}).start();
	}

	/**
	 * Starts a {@link Thread} for a single client
	 * @param cSocket The {@link Socket} connected to the client
	 * @param request The {@link UniversalDTO} the client sent
	 */
	public static void threadedUpdate(Socket cSocket, UniversalDTO request) {
		new Thread(() -> {
			try {
				System.out.println(request.getUsername() + ": Joined");
				while (true) {
					new ObjectOutputStream(cSocket.getOutputStream()).writeObject(new UniversalDTO("Server", "Update", data));
					System.out.println(request.getUsername() + ": Update");
					Thread.sleep(UPDATE_INTERVAL);
				}
			} catch (SocketException e) {
				System.out.println(request.getUsername() + ": Left");
			} catch (Exception e) { 
				e.printStackTrace();
			}
		}).start();
	}

	/**
	 * Joins a session on the server and continually processes the {@link UniversalDTO}s sent by the server
	 */
	public static void startServerSync() {
		new Thread(() -> {					
			Socket clientSocket = null;
			UniversalDTO response = null;
			try {
				clientSocket = new Socket("localhost", SERVER_PORT);
				new ObjectOutputStream(clientSocket.getOutputStream()).writeObject(new UniversalDTO(username, "Join", null));
				while (true) {
					response = (UniversalDTO) new ObjectInputStream(clientSocket.getInputStream()).readObject();				
					data = response.getData();
					System.out.println(response.getEvent());
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try { clientSocket.close(); } catch (IOException e) {}
			}
		}).start();
	}
	
	/**
	 * Notifies the server about an event that happened to the player
	 * @param event A {@link String} that describes the event
	 * @return The {@link UniversalDTO} returned by the server
	 */
	public static UniversalDTO notifyServer(String event) {
		Socket clientSocket = null;
		UniversalDTO response = null;
		try {
			clientSocket = new Socket("localhost", SERVER_PORT);
			new ObjectOutputStream(clientSocket.getOutputStream()).writeObject(new UniversalDTO(username, event, data));
			response = (UniversalDTO) new ObjectInputStream(clientSocket.getInputStream()).readObject();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try { clientSocket.close(); } catch (IOException e) {}
			if (response == null) response = new UniversalDTO("Server", "Connection Failure", null);
		}
		return response;
	}
	
	/**
	 * Encompasses the data transferred between the TCP server and client
	 */
	private static class UniversalDTO implements Serializable {
		private static final long serialVersionUID = -4143190306637532691L;
		private String username;
		private String event;
		private float[][] data;

		public UniversalDTO(String username, String event, float[][] data) {
			this.username = username;
			this.event = event;
			this.data = data;
		}
		public String getUsername() {
			return username;
		}

		public String getEvent() {
			return event;
		}

		public float[][] getData() {
			return data;
		}
	}
}