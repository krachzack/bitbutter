package deuterium;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 
 * @author PhilippStadler
 * @see https://examples.javacodegeeks.com/core-java/nio/java-nio-socket-example/
 */
public class Server implements Runnable {
	
	public static final int PLAYER_VELOCITY_MAGNITUDE = 100;
	private static final int PLAYER_PARTICLE_COUNT = 100;

	private static final int BULLET_VELOCITY_MAGNITUDE = 3 * PLAYER_VELOCITY_MAGNITUDE;

	public static final InetSocketAddress SERVER_ADDR = new InetSocketAddress("0.0.0.0", 40000);

	/**
	 * Indicates how often the server should update world state and send it to clients.
	 */
	public static final float SERVER_UPDATE_INTERVAL = 0.03f;

	private static final float PARTICLE_SPAWN_INTERVAL = 0.03f;

	private static final float PARTICLE_SPREAD = 7.0f;

	private volatile boolean run = true;

	private Selector selector;
	private Set<SocketChannel> clientChannels = new HashSet<>();
	private Map<SocketChannel, Integer> clientIdentities = new WeakHashMap<>();
	private Map<SocketChannel, int[]> clientParticles = new WeakHashMap<>();
	private Map<SocketChannel, ByteBuffer> fromClientUpdateBufs = new WeakHashMap<>();
	private Map<SocketChannel, ByteBuffer> fromClientUpdateBufLens = new WeakHashMap<>();
	private Map<SocketChannel, ByteBuffer> toClientUpdateBufs = new WeakHashMap<>();
	private World world;
	private float nextParticleSpawnWaitTime;
	private int nextPlayerTexId = 0;

	public void terminate() {
		run = false;
	}

	@Override
	public void run() {
		try {
			initWorld();

			selector = Selector.open();

			ServerSocketChannel acceptChannel = ServerSocketChannel.open();
			acceptChannel.configureBlocking(false);
			acceptChannel.socket().bind(SERVER_ADDR);
			acceptChannel.register(selector, SelectionKey.OP_ACCEPT);

			System.out.println("Server up and running...");

			long lastFrameTime = System.nanoTime();
			while(run) {
				// TODO set timeout and do this to sync game loop
				handleNetworkData();

				long thisFrameTime = System.nanoTime();
				float dt = (thisFrameTime - lastFrameTime) / 1_000_000_000.0f;
				if(dt > SERVER_UPDATE_INTERVAL) {
					executeMechanics(dt);

					broadcastWorldState();

					lastFrameTime = thisFrameTime;
				}
			}


		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Server terminating...");
	}

	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel acceptChannel = (ServerSocketChannel) key.channel();
		SocketChannel channel = acceptChannel.accept();
		channel.configureBlocking(false);
		clientChannels.add(channel);
		System.out.println(clientChannels.size());
		channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

		int[] playerParticles = createPlayerParticles();
		clientParticles.put(channel, playerParticles);

		int playerID = createPlayer();
		clientIdentities.put(channel, playerID);

		ByteBuffer joinAcknowledgeBuf = new UniversalDTO(-1, "elohim", "join-acknowledge", new float[] { playerID }).asBuffer();
		channel.write(joinAcknowledgeBuf);

		if(joinAcknowledgeBuf.remaining() > 0) {
			toClientUpdateBufs.put(channel, joinAcknowledgeBuf);
		}
	}

	private void read(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();

		ByteBuffer readBuf = ByteBuffer.allocate(4096);
		int bytesRead;
		try {
			bytesRead = channel.read(readBuf);
		} catch (IOException e) {
			bytesRead = -1;
		}

		if(bytesRead == -1) {
			// Connection was closed by client
			clientChannels.remove(channel);
			channel.close();
			key.cancel();
		} else {
			readBuf.flip();
			ByteBuffer updateBuf = fromClientUpdateBufs.get(channel);

			while(readBuf.remaining() > 0) {
				if(updateBuf == null) {
					ByteBuffer updateBufLenBuf = fromClientUpdateBufLens.get(channel);
					if(updateBufLenBuf == null) {
						updateBufLenBuf = ByteBuffer.allocate(4);
						fromClientUpdateBufLens.put(channel, updateBufLenBuf);
					}

					updateBufLenBuf.put(readBuf.get());
					if(updateBufLenBuf.remaining() == 0) {
						updateBufLenBuf.flip();
						int len = updateBufLenBuf.getInt();
						fromClientUpdateBufLens.remove(channel);
						updateBuf = ByteBuffer.allocate(len);
					}
				} else {
					updateBuf.put(readBuf.get());
					if(updateBuf.remaining() == 0) {
						UniversalDTO dto;
						try {
							int clientID = clientIdentities.get(channel);
							dto = (UniversalDTO) new ObjectInputStream(new ByteArrayInputStream(updateBuf.array())).readObject();
							handleClientDTO(clientID, dto);
							updateBuf = null;
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
						}
					}
				}
			}

			// If there is an unfinished updateBuf, keep it for the next read,
			// if it is finished and decoded, put the null so it isnt read twice
			fromClientUpdateBufs.put(channel, updateBuf);
		}
	}

	private void write(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();
		ByteBuffer pendingBuf = toClientUpdateBufs.get(channel);

		if(pendingBuf != null) {
			try {
				channel.write(pendingBuf);

				if(pendingBuf.remaining() == 0) {
					toClientUpdateBufs.remove(channel);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void handleNetworkData() throws IOException {
		selector.selectNow();

		Iterator<SelectionKey> it = selector.selectedKeys().iterator();
		while(it.hasNext()) {
			SelectionKey key = it.next();
			it.remove();

			if(!key.isValid()) {
				continue;
			}

			if(key.isAcceptable()) {
				accept(key);
			}

			try {
				if(key.isReadable()) {
					read(key);
				}

				if(key.isWritable()) {
					write(key);
				}
			} catch (CancelledKeyException | IOException e) {
				// When error occurs with communication, remove the player and associated data
				e.printStackTrace();
				killPlayer(key.channel());
				clientChannels.remove(key.channel());
				key.channel().close();
				key.cancel();
			}
		}
	}

	private void handleClientDTO(int clientID, UniversalDTO dto) {
		String evt = dto.getEvent();

		// Freeze controls if time reversed
		if(world.get(clientID, World.REVERSED) > 0) {
			return;
		}

		if(evt.equals("request-steer")) {
			world.set(clientID, World.VELOCITY_X, dto.getData()[0] * PLAYER_VELOCITY_MAGNITUDE);
			world.set(clientID, World.VELOCITY_Y, dto.getData()[1] * PLAYER_VELOCITY_MAGNITUDE);
		} else if(evt.equals("request-shoot")) {
			float playerPosX = world.get(clientID, World.POSITION_X);
			float playerPosY = world.get(clientID, World.POSITION_Y);
			float playerDimX = world.get(clientID, World.DIMENSION_X);
			float playerDimY = world.get(clientID, World.DIMENSION_Y);

			float bulletDirX = dto.getData()[0];
			float bulletDirY = dto.getData()[1];
			float bulletDiameter = 10.0f;
			// Offset the bullet a little so it cannot collide with the shooting player
			float bulletStartPosX = playerPosX + 0.5f * bulletDirX * (playerDimX + bulletDiameter);
			float bulletStartPosY = playerPosY + 0.5f * bulletDirY * (playerDimY + bulletDiameter);
			float bulletVelX = bulletDirX * BULLET_VELOCITY_MAGNITUDE;
			float bulletVelY = bulletDirY * BULLET_VELOCITY_MAGNITUDE;

			int bullet = world.addEntity();
			world.set(bullet, World.KIND, World.KIND_VAL_BULLET);
			world.set(bullet, World.COLLISION_ENABLED, 1.0f);
			world.set(bullet, World.DIMENSION_X, bulletDiameter);
			world.set(bullet, World.DIMENSION_Y, bulletDiameter);
			world.set(bullet, World.POSITION_X, bulletStartPosX);
			world.set(bullet, World.POSITION_Y, bulletStartPosY);
			world.set(bullet, World.VELOCITY_X, bulletVelX);
			world.set(bullet, World.VELOCITY_Y, bulletVelY);
			world.set(bullet, World.LIFETIME, 2.0f);
			world.set(bullet, World.COLOR_R, 178.0f/255.0f);
			world.set(bullet, World.COLOR_G, 123.0f/255.0f);
			world.set(bullet, World.COLOR_B, 13.0f/255.0f);
		}
	}

	private void broadcastWorldState() {
		ByteBuffer state = world.getFullStateUpdateDTO().asBuffer();
		broadcast(state);
	}

	private void broadcast(ByteBuffer buf) {
		for(SocketChannel aClient : clientChannels) {
			ByteBuffer updateBuf = toClientUpdateBufs.get(aClient);

			if(updateBuf == null) {
				toClientUpdateBufs.put(aClient, buf.asReadOnlyBuffer());
			}
		}
	}

	private void initWorld() {
		world = new World();

		initTraps();
	}

	private void initTraps() {
		int[] trapIDs = new int[10];
		
		for(int i = 0; i < trapIDs.length; ++i) {
			int trap = world.addEntity();
			
			float x, y;
			final float diameter = 150.0f;

			// Choose non-colliding start position with rejection sampling
			boolean hasInitialColission;
			do {
				hasInitialColission = false;
				
				x = (float) ((Math.random() - 0.5) * Shell.WIDTH);
				y = (float) ((Math.random() - 0.5) * Shell.HEIGHT);
				
				for(int trapIDIdx = 0; trapIDIdx < i; ++trapIDIdx) {
					int other = trapIDs[trapIDIdx];
					
					float distX = world.get(other, World.POSITION_X) - x;
					float distY = world.get(other, World.POSITION_Y) - y;
					float distSqr = distX*distX + distY*distY;
					
					if(distSqr < (diameter*diameter)) {
						hasInitialColission = true;
					}
				}
			} while(hasInitialColission);
			
			float vx = (float) (Math.random() - 0.5);
			float vy = (float) (Math.random() - 0.5);
			vx /= Math.sqrt(vx*vx + vy*vy);
			vy /= Math.sqrt(vx*vx + vy*vy);
			vx *= 200;
			vy *= 200;
//			vx = 0;
//			vy = 0;

			world.set(trap, World.DIMENSION_X, diameter);
			world.set(trap, World.DIMENSION_Y, diameter);
			world.set(trap, World.POSITION_X, x);
			world.set(trap, World.POSITION_Y, y);
			world.set(trap, World.VELOCITY_X, vx);
			world.set(trap, World.VELOCITY_Y, vy);
			world.set(trap, World.TEX_INDEX, 3);
			//world.set(trap, World.COLOR_R, 77.0f/255.0f);
			//world.set(trap, World.COLOR_G, 16.0f/255.0f);
			//world.set(trap, World.COLOR_B, 121.0f/255.0f);
			world.set(trap, World.KIND, World.KIND_VAL_TRAP);
			world.set(trap, World.COLLISION_ENABLED, 1.0f);
			
			trapIDs[i] = trap;
		}
	}

	private int createPlayer() {
		int playerID = world.addPlayer(generateName());

		// By randomizing spawn position the chance for players spawning on top of each
		// other are reduced. It can still happen though, and then they are in rewind forever
		float posX = (float) ((Math.random() - 0.5) * (Shell.WIDTH - 70.0f));
		float posY = (float) ((Math.random() - 0.5) * (Shell.HEIGHT - 70.0f));

		world.set(playerID, World.POSITION_X, posX);
		world.set(playerID, World.POSITION_X, posY);
		world.set(playerID, World.DIMENSION_X, 70.0f);
		world.set(playerID, World.DIMENSION_Y, 70.0f);
		world.set(playerID, World.COLOR_R, (float) 1.0f);
		world.set(playerID, World.COLOR_G, (float) 1.0f);
		world.set(playerID, World.COLOR_B, (float) 1.0f);
		world.set(playerID, World.TEX_INDEX, (nextPlayerTexId++ % 2) + 1);
		world.set(playerID, World.KIND, World.KIND_VAL_PLAYER);
		world.set(playerID, World.COLLISION_ENABLED, 1.0f);

		return playerID;
	}

	private void killPlayer(SelectableChannel channel) {
		int id = clientIdentities.get(channel);
		world.removePlayer(id);

		for(int particleId: clientParticles.get(channel)) {
			world.removeEntity(particleId);
		}
	}

	private int[] createPlayerParticles() {
		int[] playerParticles = new int[PLAYER_PARTICLE_COUNT];

		for(int i = 0; i < playerParticles.length; ++i) {
			playerParticles[i] = world.addEntity();
			world.set(playerParticles[i], World.DIMENSION_X, 2.0f);
			world.set(playerParticles[i], World.DIMENSION_Y, 2.0f);
			world.set(playerParticles[i], World.COLOR_R, 1.0f);
			world.set(playerParticles[i], World.COLOR_G, 1.0f);
			world.set(playerParticles[i], World.COLOR_B, 1.0f);
		}

		return playerParticles;
	}

	private void executeMechanics(float dt) {
		updateParticles(dt);

		world.update(dt);
	}

	private void updateParticles(float dt) {
		if(nextParticleSpawnWaitTime <= 0) {
			nextParticleSpawnWaitTime += PARTICLE_SPAWN_INTERVAL;

			for(SocketChannel clientChannel: clientChannels) {
				int clientID = clientIdentities.get(clientChannel);
				int[] particleIDs = clientParticles.get(clientChannel);

				float clientPosX = world.get(clientID, World.POSITION_X);
				float clientPosY = world.get(clientID, World.POSITION_Y);

				for (int i = 0; i < 5; i++) {
					float yMod = (i - 2) * PARTICLE_SPREAD;
					int iterations = 5 - 2 * Math.abs(i - 3);
					for (int j = 0; j < iterations; j++) {
						float xMod = (j - (iterations - 1) / 2) * PARTICLE_SPREAD;
						int anyParticleID = particleIDs[(int) (Math.random() * particleIDs.length)];
						world.set(anyParticleID, World.POSITION_X, clientPosX + xMod);
						world.set(anyParticleID, World.POSITION_Y, clientPosY + yMod);
					}
				}
			}
		} else {
			nextParticleSpawnWaitTime -= dt;
		}
	}
	
	/**
	 * @see http://www.java-gaming.org/index.php?topic=35802.0
	 */
//	public class NameGenerator {

		private static String[] Beginning = { "Kr", "Ca", "Ra", "Mrok", "Cru", "Ray", "Bre", "Zed", "Drak", "Mor", "Jag",
				"Mer", "Jar", "Mjol", "Zork", "Mad", "Cry", "Zur", "Creo", "Azak", "Azur", "Rei", "Cro", "Mar", "Luk" };
		private static String[] Middle = { "air", "ir", "mi", "sor", "mee", "clo", "red", "cra", "ark", "arc", "miri",
				"lori", "cres", "mur", "zer", "marac", "zoir", "slamar", "salmar", "urak" };
		private static String[] End = { "d", "ed", "ark", "arc", "es", "er", "der", "tron", "med", "ure", "zur", "cred",
				"mur" };

		private static Random rand = new Random();

		public static String generateName() {
			return Beginning[rand.nextInt(Beginning.length)] + Middle[rand.nextInt(Middle.length)]
					+ End[rand.nextInt(End.length)];
		}

//	}
}
