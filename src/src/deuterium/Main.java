package deuterium;

import java.util.Arrays;

public class Main {

	/**
	 * If no arguments, start server and connect to local server.
	 * 
	 * If one argument, try to parse it as server IP.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("deuterium0.0.1 --- " + Arrays.toString(args));

		
		String serverUrl = "localhost";
		
		if(args.length == 0) {
			Server server = new Server();
			new Thread(server).start();
		} else {
			serverUrl = args[0];
		}
		
		Client client = new Client(serverUrl);
		new Thread(client).start();
		Shell.run(client.receivedFromServerQueue, client.willSendToServerQueue);
	}
	
}
