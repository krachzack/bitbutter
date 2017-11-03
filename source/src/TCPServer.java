import java.io.*;
import java.net.*;

class TCPServer {
	private static final int UPDATE_INTERVAL = 1000;
	private static float[][][] positions = new float[1000][100][2];

	public static void main(String argv[]) throws Exception {
		ServerSocket sSocket = null;
		try {
			sSocket = new ServerSocket(4096);
			while (true) {
				Socket cSocket = sSocket.accept();
				UniversalDTO request = (UniversalDTO) new ObjectInputStream(cSocket.getInputStream()).readObject();
				if (request.getEvent().equals("Join")) threadedUpdate(cSocket, request);
			}
		} finally { sSocket.close(); }
	}

	public static void threadedUpdate(Socket cSocket, UniversalDTO request) {
		new Thread(() -> {
			try {
				System.out.println(request.getGameId() + "/" + request.getUsername() + ": Joined");
				while (true) {
					new ObjectOutputStream(cSocket.getOutputStream()).writeObject(new UniversalDTO(request.getGameId(), "Server", "Update", positions[request.getGameId()]));
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
}