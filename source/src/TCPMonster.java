import java.io.*;
import java.net.*;
import java.util.HashMap;

class TCPMonster {
	private static final int UPDATE_INTERVAL = 1000;
	private static final int SERVER_PORT = 40000;
	private static final int MAX_PLAYERS = 100;
	
	private static float[][] data = new float[100][];
	private static HashMap<String, Integer> users = new HashMap<String, Integer>();
	
	private static String username = "User";
	
	/**
	 * Starts the a server in a separate {@link Thread} and waits for requests
	 */
	public static void startServer() {
		new Thread(() -> {
			ServerSocket sSocket = null;
			try {
				sSocket = new ServerSocket(SERVER_PORT);
				while (true) {
					Socket cSocket = sSocket.accept();
					UniversalDTO request = (UniversalDTO) new ObjectInputStream(cSocket.getInputStream()).readObject();
					if (request.getEvent().equals("Join")) threadedUpdate(cSocket, request);
					else if (request.getEvent().equals("CollisionTrap")) {
						// Lower player score
						// Spawn collectibles around trap
						// Remove trap
						// (Respawn player)
						int userId = users.get(request.getUsername());
						int trapId = (int) request.getData()[0][0];
					} else if (request.getEvent().equals("CollisionProjectile")) {
						// Remove bullet
						// Initiate time reversal for the player
						int userId = users.get(request.getUsername());
						int bulletId = (int) request.getData()[0][0];
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally { 
				try { sSocket.close(); } catch (IOException e) {}
			}
		}).start();
	}

	/**
	 * Starts a {@link Thread} to respond to a single client
	 * @param cSocket The {@link Socket} connected to the client
	 * @param request The {@link UniversalDTO} the client sent
	 */
	public static void threadedUpdate(Socket cSocket, UniversalDTO request) {
		new Thread(() -> {
			try {
				System.out.println(request.getUsername() + ": Joined");
				storeUser(request.getUsername());
				while (true) {
					new ObjectOutputStream(cSocket.getOutputStream()).writeObject(new UniversalDTO("Server", "Update", data));
					System.out.println(request.getUsername() + ": Update");
					Thread.sleep(UPDATE_INTERVAL);
				}
			} catch (SocketException e) {
				System.out.println(request.getUsername() + ": Left");
				users.remove(request.getUsername());
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
	public static void notifyServer(String event) {
		Socket clientSocket = null;
		try {
			clientSocket = new Socket("localhost", SERVER_PORT);
			new ObjectOutputStream(clientSocket.getOutputStream()).writeObject(new UniversalDTO(username, event, data));
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try { clientSocket.close(); } catch (IOException e) {}
		}
	}
	
	/**
	 * Finds a cozy spot for a new user inside the user {@link HashMap}
	 * @param username The username specified by the user
	 */
	private static void storeUser(String username) {
		for (int i = 0; i < MAX_PLAYERS; i++) {
			if (!users.containsValue(i)) {
				users.put(username, i);
				return;
			}
		}
		System.out.println("Player maximum reached. New player could not be added.");
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