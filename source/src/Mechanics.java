public class Mechanics {

	public static final float PLAYER_SPEED = 200;
	
	private World world;
	private int playerID;
	private int[] particles = new int[80];
	
	public Mechanics(World world) {
		this.world = world;
		initWorld();
		initParticles();
	}

	private void initWorld() {
		playerID = world.addEntity();
		world.set(playerID, World.DIMENSION_X, 10.0f);
		world.set(playerID, World.DIMENSION_Y, 10.0f);
		world.set(playerID, World.COLOR_R, 1.0f);
	}
	
	private void initParticles() {
		for(int i = 0; i < particles.length; ++i) {
			particles[i] = world.addEntity();
			world.set(particles[i], World.DIMENSION_X, 2.0f);
			world.set(particles[i], World.DIMENSION_Y, 2.0f);
		}
	}
	
	public void update(float dt) {
		if(Shell.mousePressed) {
			world.setTimeReverse(true);
		} else {
			world.setTimeReverse(false);
			movePlayerTowardsMouse();
			updateParticles();
		}
		
		world.update(dt);
	}

	private void updateParticles() {
		float playerX = world.get(playerID, World.POSITION_X);
		float playerY = world.get(playerID, World.POSITION_Y);
		int idx = (int) (Math.random() * particles.length);
		world.set(particles[idx], World.POSITION_X, playerX);
		world.set(particles[idx], World.POSITION_Y, playerY);
	}

	private void movePlayerTowardsMouse() {
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
	
}
