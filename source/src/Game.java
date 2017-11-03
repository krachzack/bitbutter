import java.awt.Graphics2D;

public class Game {

	public static final float PLAYER_SPEED = 200;
	
	private World world;
	private int playerID;
	
	public Game() {
		initWorld();
	}
	
	private void initWorld() {
		world = new World();
		
		playerID = world.addEntity();
		world.set(playerID, World.DIMENSION_X, 10.0f);
		world.set(playerID, World.DIMENSION_Y, 10.0f);
		world.set(playerID, World.COLOR_R, 1.0f);
	}
	
	public void update(float dt) {
		world.update(dt);
		
		float playerX = world.get(playerID, World.POSITION_X);
		float playerY = world.get(playerID, World.POSITION_Y);
		
		float deltaX = Shell.mouseX - playerX;
		float deltaY = Shell.mouseY - playerY;
		float dist = (float) Math.sqrt(deltaX*deltaX + deltaY*deltaY);
		float velX = (deltaX / dist) * PLAYER_SPEED;
		float velY = (deltaY / dist) * PLAYER_SPEED;
		
		world.set(playerID, World.VELOCITY_X, velX);
		world.set(playerID, World.VELOCITY_Y, velY);
	}

	public void draw(float dt, Graphics2D g) {
		world.draw(dt, g);
	}
	
}
