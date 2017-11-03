import java.io.*;
import java.net.*;

class TCPClient {
	private static float[][] positions = new float[100][2];

	public static void main(String argv[]) throws Exception {
		startServerSync(1, "Shaendro");
	}

	public static UniversalDTO sendToServer(UniversalDTO request) throws Exception {
		Socket clientSocket = null;
		UniversalDTO response = null;
		try {
			clientSocket = new Socket("localhost", 4096);
			new ObjectOutputStream(clientSocket.getOutputStream()).writeObject(request);
			response = (UniversalDTO) new ObjectInputStream(clientSocket.getInputStream()).readObject();
		} finally {
			if (clientSocket != null) clientSocket.close();
			if (response == null) new UniversalDTO(0, "Server", "Connection Failure", null);
		}
		return response;
	}

	public static void startServerSync(int gameId, String username) {
		new Thread(() -> {					
			Socket clientSocket = null;
			UniversalDTO response = null;
			try {
				clientSocket = new Socket("localhost", 4096);
				new ObjectOutputStream(clientSocket.getOutputStream()).writeObject(new UniversalDTO(gameId, username, "Join", null));
				while (true) {
					response = (UniversalDTO) new ObjectInputStream(clientSocket.getInputStream()).readObject();				
					positions = response.getPositions();
					System.out.println(response.getEvent());
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try { clientSocket.close(); } catch (IOException e) {}
			}
		}).start();
	}
}