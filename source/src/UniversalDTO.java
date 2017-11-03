import java.io.Serializable;

public class UniversalDTO implements Serializable {
	private static final long serialVersionUID = -4143190306637532691L;
	private int gameId;
	private String username;
	private String event;
	private float[][] positions;

	public UniversalDTO(int gameId, String username, String event, float[][] positions) {
		this.gameId = gameId;
		this.username = username;
		this.event = event;
		this.positions = positions;
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

	public float[][] getPositions() {
		return positions;
	}
}
