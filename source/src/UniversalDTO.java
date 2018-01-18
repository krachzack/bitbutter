
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * Encompasses the data transferred between the TCP server and client
 */

public class UniversalDTO implements Serializable {
	private static final long serialVersionUID = -4143190306637532691L;
	private int gameId;
	private String username;
	private String event;
	private float[] data;

	public UniversalDTO(int gameId, String username, String event, float[] data) {
		this.gameId = gameId;
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

	public float[] getData() {
		return data;
	}

	public ByteBuffer asBuffer() {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		try {
			new ObjectOutputStream(byteOut).writeObject(this);
		} catch (IOException e) {
			e.printStackTrace();
		}
		ByteBuffer buf = ByteBuffer.wrap(byteOut.toByteArray());
		return buf;
	}
}