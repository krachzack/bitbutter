package deuterium;

import java.util.Arrays;

public class Main {

	public static void main(String[] args) {
		System.out.println(Arrays.toString(args));
		if(args.length == 0 || args[0].equals("--client")) {
			Client client = new Client();
			new Thread(client).start();
			Shell.run(client.receivedFromServerQueue, client.willSendToServerQueue);
		} else if(args[0].equals("--server")) {
			new Server().run();
		}
	}
	
}
