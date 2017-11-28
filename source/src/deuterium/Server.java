package deuterium;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 
 * @author PhilippStadler
 * @see https://examples.javacodegeeks.com/core-java/nio/java-nio-socket-example/
 */
public class Server implements Runnable {
	
	private static final int PLAYER_VELOCITY_MAGNITUDE = 100;
	private static final int PLAYER_PARTICLE_COUNT = 20;
	
	private static final int BULLET_VELOCITY_MAGNITUDE = 3 * PLAYER_VELOCITY_MAGNITUDE;

	public static final InetSocketAddress SERVER_ADDR = new InetSocketAddress("0.0.0.0", 40000);
	
	/**
	 * Indicates how often the server should update world state and send it to clients.
	 */
	private static final float SERVER_UPDATE_INTERVAL = 0.03f;
	
	private static final float PARTICLE_SPAWN_INTERVAL = 0.03f;
	
	private volatile boolean run = true;
	
	private Selector selector;
	private Set<SocketChannel> clientChannels = new HashSet<>();
	/**
	 * Maps socket channels against the ID of the corresponding player in the world.
	 * 
	 * FIXME Memory leak: once the weak reference to the channel signals that the channel was freed,
	 * the player ID should also be removed from the world, not only the weak hashmap data
	 */
	private Map<SocketChannel, Integer> clientIdentities = new WeakHashMap<>();
	private Map<SocketChannel, int[]> clientParticles = new WeakHashMap<>();
	private Map<SocketChannel, ByteBuffer> fromClientUpdateBufs = new WeakHashMap<>();
	private Map<SocketChannel, ByteBuffer> toClientUpdateBufs = new WeakHashMap<>();
	private World world;
	private float nextParticleSpawnWaitTime;
	
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
				executeMechanics(dt);
				
				broadcastWorldState();
				
//				long targetFrameTime = (long) (thisFrameTime + SERVER_UPDATE_INTERVAL * 1_000_000_000);
//				long remainingFrameNanos = targetFrameTime - System.nanoTime();
//				if(remainingFrameNanos > 0) {
//					try {
//						Thread.sleep(remainingFrameNanos / 1_000_000, (int) (remainingFrameNanos % 1_000_000));
//					} catch (InterruptedException e) {
//						e.printStackTrace();
//					}
//				}
				
				lastFrameTime = thisFrameTime;
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
		
		int playerID = createPlayer();
		clientIdentities.put(channel, playerID);
		
		int[] playerParticles = createPlayerParticles();
		clientParticles.put(channel, playerParticles);
		
		channel.write(new UniversalDTO(-1, "elohim", "join-acknowledge", new float[] { playerID }).asBuffer());
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
			ByteBuffer updateBuf = fromClientUpdateBufs.get(channel);
			readBuf.flip();
			
			while(readBuf.remaining() > 0) {
				if(updateBuf == null) {
					// FIXME this throws an exception if less than four bytes are available
					int updateLen = readBuf.getInt();
					updateBuf = ByteBuffer.allocate(updateLen);
				} else {
					if(updateBuf.remaining() > 0) {
						updateBuf.put(readBuf.get());
					} else {
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
		// Wait for IO event but never longer than the target frame interval
		selector.select((long) (SERVER_UPDATE_INTERVAL * 1_000));
		
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
			
			if(key.isReadable()) {
				read(key);
			}
			
			if(key.isWritable()) {
				write(key);
			}
		}
	}

	private void handleClientDTO(int clientID, UniversalDTO dto) {
		String evt = dto.getEvent();
		
		if(evt.equals("request-steer")) {
			world.set(clientID, World.VELOCITY_X, dto.getData()[0] * PLAYER_VELOCITY_MAGNITUDE);
			world.set(clientID, World.VELOCITY_Y, dto.getData()[1] * PLAYER_VELOCITY_MAGNITUDE);
		} else if(evt.equals("request-shoot")) {
			float playerPosX = world.get(clientID, World.POSITION_X);
			float playerPosY = world.get(clientID, World.POSITION_Y);
			float playerRed = world.get(clientID, World.COLOR_R);
			float playerGreen = world.get(clientID, World.COLOR_G);
			float playerBlue = world.get(clientID, World.COLOR_B);
			
			float bulletStartPosX = playerPosX;
			float bulletStartPosY = playerPosY;
			float bulletVelX = dto.getData()[0] * BULLET_VELOCITY_MAGNITUDE;
			float bulletVelY = dto.getData()[1] * BULLET_VELOCITY_MAGNITUDE;
			
			int bullet = world.addEntity();
			world.set(bullet, World.KIND, World.KIND_VAL_BULLET);
			world.set(bullet, World.DIMENSION_X, 10.0f);
			world.set(bullet, World.DIMENSION_Y, 10.0f);
			world.set(bullet, World.POSITION_X, bulletStartPosX);
			world.set(bullet, World.POSITION_Y, bulletStartPosY);
			world.set(bullet, World.VELOCITY_X, bulletVelX);
			world.set(bullet, World.VELOCITY_Y, bulletVelY);
			world.set(bullet, World.COLOR_R, playerRed * 0.7f);
			world.set(bullet, World.COLOR_G, playerGreen * 0.7f);
			world.set(bullet, World.COLOR_B, playerBlue * 0.7f);
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
		for(int i = 0; i < 30; ++i) {
			int trap = world.addEntity();
			
			float radius = (float) (20.0 * Math.min(Math.random() + 0.2, 1.0));
			float x = (float) ((Math.random() - 0.5) * Shell.WIDTH);
			float y = Shell.HEIGHT/2 + radius;
			float vx = (float) ((2.0 * Math.random() - 1.0) * 30);
			float vy = (float) ((-Math.random() - 0.1) * 30);
			
			world.set(trap, World.DIMENSION_X, 2*radius);
			world.set(trap, World.DIMENSION_Y, 2*radius);
			world.set(trap, World.POSITION_X, x);
			world.set(trap, World.POSITION_Y, y);
			world.set(trap, World.VELOCITY_X, vx);
			world.set(trap, World.VELOCITY_Y, vy);
			world.set(trap, World.COLOR_R, 0.1f);
			world.set(trap, World.COLOR_G, 0.1f);
			world.set(trap, World.COLOR_B, 0.1f);
			world.set(trap, World.KIND, World.KIND_VAL_TRAP);
			world.set(trap, World.COLLISION_ENABLED, 1.0f);
		}
	}

	private int createPlayer() {
		int playerID = world.addEntity();
		
		world.set(playerID, World.DIMENSION_X, 10.0f);
		world.set(playerID, World.DIMENSION_Y, 10.0f);
		world.set(playerID, World.COLOR_R, (float) Math.random());
		world.set(playerID, World.COLOR_G, (float) 0.2f);
		world.set(playerID, World.COLOR_B, (float) Math.random());
		world.set(playerID, World.KIND, World.KIND_VAL_PLAYER);
		world.set(playerID, World.COLLISION_ENABLED, 1.0f);
		
		return playerID;
	}

	private int[] createPlayerParticles() {
		int[] playerParticles = new int[PLAYER_PARTICLE_COUNT];
		
		for(int i = 0; i < playerParticles.length; ++i) {
			playerParticles[i] = world.addEntity();
			world.set(playerParticles[i], World.DIMENSION_X, 2.0f);
			world.set(playerParticles[i], World.DIMENSION_Y, 2.0f);
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
				
				int anyParticleID = particleIDs[(int) (Math.random() * particleIDs.length)];
				world.set(anyParticleID, World.POSITION_X, clientPosX);
				world.set(anyParticleID, World.POSITION_Y, clientPosY);
			}
		} else {
			nextParticleSpawnWaitTime -= dt;
		}
	}

}
