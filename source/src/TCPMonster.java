import java.io.*;
import java.net.*;
import java.util.Scanner;

class TCPMonster {
	private static final int UPDATE_INTERVAL = 1000;
	private static final int SERVER_PORT = 40000;
	
	// Placeholder data structure; Better divide data into more than one array
	// Should contain all relevant position data in the game
	private static float[][][] data = new float[1000][][];
	
	// Query user input for those two
	private static int gameId = 1;
	private static String username = "Shaendro";

	public static void main(String argv[]) throws Exception {
		System.out.println("Are you trying to start a client? (true/false)");
		Scanner s = new Scanner(System.in);
		boolean isClient = Boolean.parseBoolean(s.nextLine());
		s.close();
		
		if (isClient) startServerSync();
		else {
			ServerSocket sSocket = null;
			try {
				sSocket = new ServerSocket(SERVER_PORT);
				while (true) {
					Socket cSocket = sSocket.accept();
					UniversalDTO request = (UniversalDTO) new ObjectInputStream(cSocket.getInputStream()).readObject();
					if (request.getEvent().equals("Join")) threadedUpdate(cSocket, request);
				}
			} finally { sSocket.close(); }
		}
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
			new ObjectOutputStream(clientSocket.getOutputStream()).writeObject(new UniversalDTO(gameId, username, event, data[gameId]));
			response = (UniversalDTO) new ObjectInputStream(clientSocket.getInputStream()).readObject();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try { clientSocket.close(); } catch (IOException e) {}
			if (response == null) response = new UniversalDTO(0, "Server", "Connection Failure", null);
		}
		return response;
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
				new ObjectOutputStream(clientSocket.getOutputStream()).writeObject(new UniversalDTO(gameId, username, "Join", null));
				while (true) {
					response = (UniversalDTO) new ObjectInputStream(clientSocket.getInputStream()).readObject();				
					data[gameId] = response.getData();
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
	 * Starts a {@link Thread} for a single client
	 * @param cSocket The {@link Socket} connected to the client
	 * @param request The {@link UniversalDTO} the client sent
	 */
	public static void threadedUpdate(Socket cSocket, UniversalDTO request) {
		new Thread(() -> {
			try {
				System.out.println(request.getGameId() + "/" + request.getUsername() + ": Joined");
				while (true) {
					new ObjectOutputStream(cSocket.getOutputStream()).writeObject(new UniversalDTO(request.getGameId(), "Server", "Update", data[request.getGameId()]));
					System.out.println(request.getGameId() + "/" + request.getUsername() + ": Update");
					Thread.sleep(UPDATE_INTERVAL);
				}
			} catch (SocketException e) {
				System.out.println(request.getGameId() + "/" + request.getUsername() + ": Left");
			} catch (Exception e) { 
				e.printStackTrace();
			}
		}).start();
	}
	
	/**
	 * Encompasses the data transferred between the TCP server and client
	 */
	
	private static class UniversalDTO implements Serializable {
		private static final long serialVersionUID = -4143190306637532691L;
		private int gameId;
		private String username;
		private String event;
		private float[][] data;

		public UniversalDTO(int gameId, String username, String event, float[][] data) {
			this.gameId = gameId;
			this.username = username;
			this.event = event;
			this.data = data;
		}
		
		public int getGameId() {
			return gameId;
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