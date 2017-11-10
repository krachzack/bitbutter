public class Mechanics {

	public static final float PLAYER_SPEED = 200;
	
	private World world;
	private int playerID;
	private int[] particles = new int[80];
	private int[] traps = new int[10];
	private float trapSpawnTime;
	
	public Mechanics(World world) {
		this.world = world;
		initPlayer();
		initParticles();
		//initTraps();
	}

	private void initPlayer() {
		playerID = world.addEntity();
		
		world.set(playerID, World.DIMENSION_X, 10.0f);
		world.set(playerID, World.DIMENSION_Y, 10.0f);
		world.set(playerID, World.COLOR_R, 1.0f);
		world.set(playerID, World.COLOR_G, 0.3f);
		world.set(playerID, World.COLOR_B, 0.3f);
		world.set(playerID, World.COLLISION_ENABLED, 1.0f);
		world.set(playerID, World.KIND, World.KIND_VAL_PLAYER);
	}
	
	private void initParticles() {
		for(int i = 0; i < particles.length; ++i) {
			particles[i] = world.addEntity();
			world.set(particles[i], World.DIMENSION_X, 2.0f);
			world.set(particles[i], World.DIMENSION_Y, 2.0f);
		}
	}
	
	private void initTraps() {
		for(int i = 0; i < traps.length; ++i) {
			if(traps[i] != 0) {
				world.removeEntity(traps[i]);
			}
			traps[i] = world.addEntity();
			
			float radius = (float) (20.0 * Math.min(Math.random() + 0.2, 1.0));
			float x = (float) ((Math.random() - 0.5) * Shell.WIDTH);
			float y = Shell.HEIGHT/2 + radius;
			float vx = (float) ((2.0 * Math.random() - 1.0) * 30);
			float vy = (float) ((-Math.random() - 0.1) * 30);
			
			world.set(traps[i], World.DIMENSION_X, 2*radius);
			world.set(traps[i], World.DIMENSION_Y, 2*radius);
			world.set(traps[i], World.POSITION_X, x);
			world.set(traps[i], World.POSITION_Y, y);
			world.set(traps[i], World.VELOCITY_X, vx);
			world.set(traps[i], World.VELOCITY_Y, vy);
			world.set(traps[i], World.COLOR_R, 0.1f);
			world.set(traps[i], World.COLOR_G, 0.1f);
			world.set(traps[i], World.COLOR_B, 0.1f);
			world.set(traps[i], World.POSITION_Y, y);
			world.set(traps[i], World.COLLISION_ENABLED, 1.0f);
			world.set(traps[i], World.KIND, World.KIND_VAL_TRAP);
		}
	}

	public void update(float dt) {
		if(Shell.mousePressed) {
			world.setTimeReverse(true);
		} else if(Shell.rightMousePressed) {
			setPlayerReversed(true);
		} else {
			world.setTimeReverse(false);
			setPlayerReversed(false);
			
			movePlayerTowardsMouse();
			updateParticles();
			
			trapSpawnTime -= dt;
			if(trapSpawnTime < 0) {
				trapSpawnTime = 15.0f;
				initTraps();
			}
		}
		
		world.update(dt);
	}
	
	private void setPlayerReversed(boolean yes) {
		float val = yes ? 1.0f : 0.0f;
		
		world.set(playerID, World.REVERSED, val);
		for(int particle: particles) {
			world.set(particle, World.REVERSED, val);
		}
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
