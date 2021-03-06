

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Client implements Runnable {

	public BlockingQueue<UniversalDTO> receivedFromServerQueue = new ArrayBlockingQueue<>(1024);
	public BlockingQueue<UniversalDTO> willSendToServerQueue = new ArrayBlockingQueue<>(1024);
	
	private String serverAddr;
	
	public Client(String serverAddr) {
		this.serverAddr = serverAddr;
	}

	@Override
	public void run() {
		Socket sock = null;
		try {
			sock = new Socket(serverAddr, Server.SERVER_ADDR.getPort());
			
			while(true) {
				ObjectInputStream inStream = new ObjectInputStream(sock.getInputStream());
				UniversalDTO dto = (UniversalDTO) inStream.readObject();
				receivedFromServerQueue.put(dto);
				
				UniversalDTO toServerDTO;
				while((toServerDTO = willSendToServerQueue.poll()) != null) {
					ByteBuffer toServerDTOBuf = toServerDTO.asBuffer();
					int dtoLen = toServerDTOBuf.limit();
					
					ByteBuffer buf = ByteBuffer.allocate(4 + dtoLen);
					buf.putInt(dtoLen);
					buf.put(toServerDTOBuf);
					
					sock.getOutputStream().write(buf.array());
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		} finally {
			try {
				sock.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
