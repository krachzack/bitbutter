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
	
	public static final InetSocketAddress SERVER_ADDR = new InetSocketAddress("0.0.0.0", 40000);
	
	/**
	 * Indicates how often the server should update world state and send it to clients.
	 */
	private static final float SERVER_UPDATE_INTERVAL = 0.03f;
	
	private volatile boolean run = true;
	
	private Selector selector;
	private Set<SocketChannel> clientChannels = new HashSet<>();
	/**
	 * Maps socket channels against the ID of the corresponding player in the world.
	 * 
	 * FIXME Memory leak: once the weak reference to the channel signals that the channel was freed,
	 * the player ID should also be removed from the world
	 */
	private Map<SocketChannel, Integer> clientIdentities = new WeakHashMap<>();
	private Map<SocketChannel, ByteBuffer> clientUpdateBufs = new WeakHashMap<>();
	private Map<SocketChannel, int[]> clientParticles = new WeakHashMap<>();
	private World world;
	
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
				handleIncomingNetworkData();
				
				long thisFrameTime = System.nanoTime();
				float dt = (thisFrameTime - lastFrameTime) / 1_000_000_000.0f;
				executeMechanics(dt);
				
				broadcastWorldState();
				
				lastFrameTime = thisFrameTime;
			}
			
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("Server terminating...");
	}

	private void broadcastWorldState() {
		ByteBuffer state = world.getFullStateUpdateDTO().asBuffer();
		broadcast(state);
	}

	private void executeMechanics(float dt) {
		updateParticles();
		
		world.update(dt);
	}

	private void updateParticles() {
		for(SocketChannel clientChannel: clientChannels) {
			int clientID = clientIdentities.get(clientChannel);
			int[] particleIDs = clientParticles.get(clientChannel);
			
			float clientPosX = world.get(clientID, World.POSITION_X);
			float clientPosY = world.get(clientID, World.POSITION_Y);
			
			int anyParticleID = particleIDs[(int) (Math.random() * particleIDs.length)];
			world.set(anyParticleID, World.POSITION_X, clientPosX);
			world.set(anyParticleID, World.POSITION_Y, clientPosY);
		}
	}

	private void handleIncomingNetworkData() throws IOException {
		selector.select((long) (SERVER_UPDATE_INTERVAL * 1_000)); // Wait for IO event
		
		Iterator<SelectionKey> it = selector.selectedKeys().iterator();
		while(it.hasNext()) {
			SelectionKey key = it.next();
			it.remove();
			
			if(!key.isValid()) {
				continue;
			} else if(key.isAcceptable()) {
				accept(key);
			} else if(key.isReadable()) {
				read(key);
			}
		}
	}

	private void initWorld() {
		world = new World();
		int hole = world.addEntity();
		
		world.set(hole, World.DIMENSION_X, 20);
		world.set(hole, World.DIMENSION_Y, 20);
		world.set(hole, World.POSITION_X, 0);
		world.set(hole, World.POSITION_Y, 0);
		world.set(hole, World.VELOCITY_X, 5);
		world.set(hole, World.VELOCITY_Y, 5);
		world.set(hole, World.COLOR_R, 0.1f);
		world.set(hole, World.COLOR_G, 0.1f);
		world.set(hole, World.COLOR_B, 0.1f);
		world.set(hole, World.COLLISION_ENABLED, 1.0f);
		world.set(hole, World.KIND, World.KIND_VAL_TRAP);
		
		world.set(hole, World.DIMENSION_X, 20);
		world.set(hole, World.DIMENSION_Y, 20);
		world.set(hole, World.POSITION_X, 0.5f);
		world.set(hole, World.POSITION_Y, 0.5f);
		world.set(hole, World.COLOR_R, 1.0f);
		world.set(hole, World.COLLISION_ENABLED, 0.0f);
		world.set(hole, World.KIND, World.KIND_VAL_TRAP);
	}

	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel acceptChannel = (ServerSocketChannel) key.channel();
		SocketChannel channel = acceptChannel.accept();
		channel.configureBlocking(false);
		clientChannels.add(channel);
		System.out.println(clientChannels.size());
		channel.register(selector, SelectionKey.OP_READ);
		
		int playerID = world.addEntity();
		world.set(playerID, World.DIMENSION_X, 10.0f);
		world.set(playerID, World.DIMENSION_Y, 10.0f);
		world.set(playerID, World.COLOR_R, (float) Math.random());
		world.set(playerID, World.COLOR_G, (float) 0.2f);
		world.set(playerID, World.COLOR_B, (float) Math.random());
		
		clientIdentities.put(channel, playerID);
		
		int[] playerParticles = new int[50];
		for(int i = 0; i < playerParticles.length; ++i) {
			playerParticles[i] = world.addEntity();
			world.set(playerParticles[i], World.DIMENSION_X, 2.0f);
			world.set(playerParticles[i], World.DIMENSION_Y, 2.0f);
		}
		
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
			ByteBuffer updateBuf = clientUpdateBufs.get(channel);
			readBuf.flip();
			
			while(readBuf.remaining() > 0) {
				if(updateBuf == null) {
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
			clientUpdateBufs.put(channel, updateBuf);
		}
	}
	
	private void handleClientDTO(int clientID, UniversalDTO dto) {
		if(dto.getEvent().equals("request-steer")) {
			world.set(clientID, World.VELOCITY_X, dto.getData()[0] * 50);
			world.set(clientID, World.VELOCITY_Y, dto.getData()[1] * 50);
		}
	}

	private void broadcast(ByteBuffer buf) {
		for(SocketChannel aClient : clientChannels) {
        	try {
				aClient.write(buf.asReadOnlyBuffer());
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
	}

}
