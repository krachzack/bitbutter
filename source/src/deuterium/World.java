package deuterium;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.ImageIO;

public class World {
	public static final float GAME_DURATION =  2 * 60.0f;
	public static final float PREPARE_TIME = 10.0f;
	
	private static final int DRAINED_STAR_SPEED = 110;
	public static final int POSITION_X = 0;
	public static final int POSITION_Y = 1;
	
	public static final int VELOCITY_X = 2;
	public static final int VELOCITY_Y = 3;
	
	public static final int COLOR_R = 4;
	public static final int COLOR_G = 5;
	public static final int COLOR_B = 6;
	
	public static final int DIMENSION_X = 7;
	public static final int DIMENSION_Y = 8;
	
	public static final int IN_USE = 9;
	
	public static final int REVERSED = 10;
	
	public static final int COLLISION_ENABLED = 11;
	
	public static final int KIND = 12;
	
	public static final int TEX_INDEX = 13;
	
	public static final int LIFETIME = 14;
	
	public static final float KIND_VAL_PLAYER = 0.0f;
	public static final float KIND_VAL_TRAP = 1.0f;
	public static final float KIND_VAL_BULLET = 2.0f;
	public static final float KIND_VAL_STAR = 3.0f;
	
	private static final int POSITION_SIZE = 2;
	private static final int VELOCITY_SIZE = 2;
	private static final int COLOR_SIZE = 3;
	private static final int DIMENSION_SIZE = 2;
	private static final int IN_USE_SIZE = 1;
	private static final int REVERSED_SIZE = 1;
	private static final int COLLISION_ENABLED_SIZE = 1;
	private static final int KIND_SIZE = 1;
	private static final int TEX_INDEX_SIZE = 1;
	private static final int LIFETIME_SIZE = 1;
	private static final int ENTITY_SIZE = POSITION_SIZE + VELOCITY_SIZE + COLOR_SIZE + DIMENSION_SIZE + IN_USE_SIZE + REVERSED_SIZE + COLLISION_ENABLED_SIZE + KIND_SIZE + TEX_INDEX_SIZE + LIFETIME_SIZE;
	
	private static final int ENTITY_COUNT_MAX = 512;
	private static final int PAST_FRAMES_MAX = 500;
	private static final int PARTICLE_COUNT_MAX = 1024;
	private static final float PARTICLE_SPAWN_INTERVAL = 0.03f;
	private static final float PARTICLE_SPREAD = 10.0f;
	
	/** Amount of stars at least in the game world, if drops below that, will spawn */
	private static final int MINIMUM_STAR_COUNT = 60;
	
	/**
	 * Indicates how often a player will lose points while inside a black hole.
	 */
	private static final float DRAIN_INTERVAL = 0.1f;
	/**
	 * Drain a star containing 10% of the players points every DRAIN_INTERVAL
	 */
	private static final float DRAIN_FACTOR = 0.1f;
//	private static final float DRAIN_STAR_LIFETIME = 2.0f;
	
	// The world is four times the area of the window, that is a rectangle with double sidelengths
	private static final float MAX_POSITION_X = Shell.WIDTH;
	private static final float MIN_POSITION_X = -MAX_POSITION_X;
	private static final float MAX_POSITION_Y = Shell.HEIGHT;
	private static final float MIN_POSITION_Y = -MAX_POSITION_Y;
	
	private static final BufferedImage[] textures;
	private static double angle = 0;
	
	static {
		BufferedImage[] texturesToSave = null;
		try {
			texturesToSave = new BufferedImage[] {
				null,
				ImageIO.read(World.class.getResource("/resources/earth.png")),
				ImageIO.read(World.class.getResource("/resources/moon_small.png")),
				ImageIO.read(World.class.getResource("/resources/black_hole_soak.png"))
			
			};
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		textures = texturesToSave;
				
	}
	
	public float[] entities = new float[ENTITY_SIZE * ENTITY_COUNT_MAX * PAST_FRAMES_MAX];
	public float[] particles = new float[ENTITY_SIZE * PARTICLE_COUNT_MAX];
	public int localPlayerID = -1;
	
	private float remainingGameDuration = GAME_DURATION + PREPARE_TIME;
	
	/** Names of all logged in users, sorted by score descending */
	private String[] usernames = new String[0];
	private int[] userIDs = new int[0];
	private int[] scores = new int[0];
	/**
	 * Holds time in seconds of staying inside a black hole until the next lifepoint is drained,
	 * not synced to the client world.
	 */
	private float[] drainTimeouts = new float[0];
	
	private int pastFrameCount = 0;
	private boolean timeReverse;
	private float nextParticleSpawnWaitTime;
	
	/**
	 * Called from the server to serialize everything that a client could possible draw
	 * 
	 * @return a DTO to send to the client
	 */
	public UniversalDTO getFullStateUpdateDTO() {
		
		// Abusing username field to store a \0 separated list of all usernames
		StringBuilder joinedUsernames = new StringBuilder();
		if(usernames.length > 0) {
			joinedUsernames.append(usernames[0]);
			for(int i = 1; i < usernames.length; ++i) {
				joinedUsernames.append("\0"); // Separate by triple pipes
				joinedUsernames.append(usernames[i]);
			}
		}
		
		// The first few floats are the scores corresponding to the usernames
		float[] data = new float[usernames.length + usernames.length + 1 + ENTITY_SIZE * ENTITY_COUNT_MAX];
		for(int i = 0; i < scores.length; ++i) {
			data[i] = scores[i];
		}
		
		// Next few are the IDs corresponding to the user names
		for(int i = 0; i < scores.length; ++i) {
			data[scores.length + i] = userIDs[i];
		}
		
		// Then, the remaining game time in seconds
		data[scores.length + scores.length + 0] = remainingGameDuration;
		
		// Then comes the actual world data
		System.arraycopy(entities, 0, data, scores.length + scores.length + 1, ENTITY_SIZE * ENTITY_COUNT_MAX);
		
		return new UniversalDTO(-1, joinedUsernames.toString(), "update-full", data);
	}
	
	/**
	 * Called by the client with data from the server to work server updates into the world.
	 * 
	 * @param dto
	 */
	public void handleDTO(UniversalDTO dto) {
		if(dto.getEvent().equals("update-full")) {
			float[] data = dto.getData();
			
			usernames = dto.getUsername().split("\0");
			
			// First few floats are the scores
			scores = new int[usernames.length];
			for(int i = 0; i < usernames.length; ++i) {
				scores[i] = Math.round(data[i]);
			}
			
			// Next few floats are the corresponding player entity IDs
			userIDs = new int[usernames.length];
			for(int i = 0; i < usernames.length; ++i) {
				userIDs[i] = Math.round(data[usernames.length + i]);
			}
			
			// Then remaining game time
			remainingGameDuration = data[scores.length + scores.length];
			
			System.arraycopy(data, scores.length+scores.length+1, entities, 0, ENTITY_SIZE * ENTITY_COUNT_MAX);
		} else if(dto.getEvent().equals("join-acknowledge")) {
			localPlayerID = (int) dto.getData()[0];
			System.out.println("Server acknowledged this player joining and assigned UID: " + localPlayerID);
		}
	}

	public int addPlayer(String name) {
		int id = addEntity();
		
		String[] newUsernames = new String[usernames.length + 1];
		int[] newUserIds = new int[usernames.length + 1];
		int[] newScores = new int[usernames.length + 1];
		float[] newDrainTimeouts = new float[usernames.length + 1];
		
		newUsernames[0] = name;
		newUserIds[0] = id;
		newScores[0] = 0;
		newDrainTimeouts[0] = DRAIN_INTERVAL;
		
		System.arraycopy(usernames, 0, newUsernames, 1, usernames.length);
		System.arraycopy(userIDs, 0, newUserIds, 1, usernames.length);
		System.arraycopy(scores, 0, newScores, 1, usernames.length);
		System.arraycopy(drainTimeouts, 0, newDrainTimeouts, 1, usernames.length);
		
		usernames = newUsernames;
		userIDs = newUserIds;
		scores = newScores;
		drainTimeouts = newDrainTimeouts;
		
		return id;
	}
	
	public void removePlayer(int id) {
		int idx = -1;
		
		for(int i = 0; i < userIDs.length; ++i) {
			if(userIDs[i] == id) {
				idx = i;
				break;
			}
		}
		
		if(idx != -1) {
			String[] newUsernames = new String[usernames.length - 1];
			int[] newUserIds = new int[usernames.length - 1];
			int[] newScores = new int[usernames.length - 1];
			float[] newDrainTimeouts = new float[usernames.length - 1];
			
			System.arraycopy(scores, 0, newScores, 0, idx);
			System.arraycopy(scores, idx+1, newScores, idx, newScores.length - idx);
			
			System.arraycopy(userIDs, 0, newUserIds, 0, idx);
			System.arraycopy(userIDs, idx+1, newUserIds, idx, newScores.length - idx);
			
			System.arraycopy(usernames, 0, newUsernames, 0, idx);
			System.arraycopy(usernames, idx+1, newUsernames, idx, newScores.length - idx);
			
			System.arraycopy(drainTimeouts, 0, newDrainTimeouts, 0, idx);
			System.arraycopy(drainTimeouts, idx+1, newDrainTimeouts, idx, newScores.length - idx);
			
			scores = newScores;
			userIDs = newUserIds;
			usernames = newUsernames;
			drainTimeouts = newDrainTimeouts;
			
			removeEntity(id);
		} else {
			throw new RuntimeException("Tried to remove player with id " + id + " but found no corresponding name and score");
		}
	}
	
	public void setTimeReverse(boolean timeReverse) {
		this.timeReverse = timeReverse && pastFrameCount > 0;
	}
	
	public int addEntity() {
		int id = -1;
		
		for(int inUseIdx = IN_USE; inUseIdx < (ENTITY_SIZE * ENTITY_COUNT_MAX); inUseIdx += ENTITY_SIZE) {
			if(entities[inUseIdx] == 0.0f) {
				// Is not in use, use or re-use the ID
				id = inUseIdx / ENTITY_SIZE;
				Arrays.fill(entities, id*ENTITY_SIZE, (id+1)*ENTITY_SIZE, 0.0f);
				break;
			}
		}
		
		if(id == -1) {
			throw new OutOfMemoryError("Exceeded maximum entity count of " + ENTITY_COUNT_MAX);
		}
		
		// Mark as in use
		entities[id*ENTITY_SIZE + IN_USE] = 1.0f;
		
		return id;
	}
	
	public void removeEntity(int id) {
		entities[id * ENTITY_SIZE + IN_USE] = 0.0f; 
	}
	
	public void set(int entityID, int component, float val) {
		entities[entityID * ENTITY_SIZE + component] = val; 
	}
	
	public float get(int entityID, int component) {
		return entities[entityID * ENTITY_SIZE + component]; 
	}
	
	public void update(float dt) {
		remainingGameDuration -= dt;
		if(remainingGameDuration > GAME_DURATION) {
			// Still preparing, only update after preparation time
			addStarsIfMissing();
		} else if(remainingGameDuration > 0) {
			if(timeReverse) {
				// Restore the old frame
				System.arraycopy(entities, ENTITY_COUNT_MAX*ENTITY_SIZE, entities, 0, entities.length - (ENTITY_COUNT_MAX*ENTITY_SIZE));
				--pastFrameCount;
				
				if(pastFrameCount == 0) {
					timeReverse = false;
				}
			} else {
				integratePosition(dt);
				timeReverse(dt);
				detectAndRespondToCollisions(dt);
				addStarsIfMissing();
				sortScores();
				handleLifetimes(dt);
				
				// Archive the old frame
				System.arraycopy(entities, 0, entities, ENTITY_COUNT_MAX*ENTITY_SIZE, entities.length - (ENTITY_COUNT_MAX*ENTITY_SIZE));
				++pastFrameCount;
			}
		}
	}

	private void handleLifetimes(float dt) {
		for(int entOffset = 0; entOffset < (ENTITY_COUNT_MAX*ENTITY_SIZE); entOffset += ENTITY_SIZE) {
			if(entities[entOffset + IN_USE] == 1.0 && entities[entOffset + LIFETIME] > 0) {
				entities[entOffset + LIFETIME] -= dt;
				if(entities[entOffset + LIFETIME] <= 0.0f) {
					entities[entOffset + IN_USE] = 0.0f;
					entities[entOffset + LIFETIME] = 0.0f;
				}
			}
		}
	}

	private void updateParticles(float dt) {
		if(nextParticleSpawnWaitTime <= 0) {
			nextParticleSpawnWaitTime += PARTICLE_SPAWN_INTERVAL;

			for(int clientID: userIDs) {
				float clientPosX = get(clientID, World.POSITION_X);
				float clientPosY = get(clientID, World.POSITION_Y);

				for (int i = 0; i < 7; i++) {
					float yMod = (i - 3) * PARTICLE_SPREAD;
					int iterations = 7 - (2 * Math.abs(i - 3));
					for (int j = 0; j < iterations; j++) {
						float xMod = (j - (iterations - 1) / 2) * PARTICLE_SPREAD;
						int anyParticleID = (int) (Math.random() * PARTICLE_COUNT_MAX);
						particles[anyParticleID * ENTITY_SIZE + World.POSITION_X] = clientPosX + xMod;
						particles[anyParticleID * ENTITY_SIZE + World.POSITION_Y] = clientPosY + yMod;
						particles[anyParticleID * ENTITY_SIZE + World.DIMENSION_X] = 2.0f;
						particles[anyParticleID * ENTITY_SIZE + World.DIMENSION_Y] = 2.0f;
						particles[anyParticleID * ENTITY_SIZE + World.COLOR_R] = 1.0f;
						particles[anyParticleID * ENTITY_SIZE + World.COLOR_G] = 1.0f;
						particles[anyParticleID * ENTITY_SIZE + World.COLOR_B] = 1.0f;
					}
				}
			}
		} else {
			nextParticleSpawnWaitTime -= dt;
		}
	}

	private void sortScores() {
		// good ol' selection sort in bad but good enough for 5 elements O(n2)
		for(int i = 0; i < scores.length; ++i) {
			int maxIdx = i;
			
			for(int j = i+1; j < scores.length; ++j) {
				if(scores[j] > scores[maxIdx]) {
					maxIdx = j;
				}
			}
			
			int swapI;
			float swapF;
			String swapS;
			
			swapS = usernames[i];
			usernames[i] = usernames[maxIdx];
			usernames[maxIdx] = swapS;
			
			swapI = scores[i];
			scores[i] = scores[maxIdx];
			scores[maxIdx] = swapI;
			
			swapI = userIDs[i];
			userIDs[i] = userIDs[maxIdx];
			userIDs[maxIdx] = swapI;
			
			swapF = drainTimeouts[i];
			drainTimeouts[i] = drainTimeouts[maxIdx];
			drainTimeouts[maxIdx] = swapF;
		}
	}

	private void addStarsIfMissing() {
		int starCount = 0;
		for(int entOffset = 0; entOffset < (ENTITY_COUNT_MAX*ENTITY_SIZE); entOffset += ENTITY_SIZE) {
			if(entities[entOffset + IN_USE] == 1.0 && entities[entOffset + KIND] == KIND_VAL_STAR) {
				++starCount;
			}
		}
		
		for(; starCount < MINIMUM_STAR_COUNT; ++starCount) {
			spawnStar();
		}
	}

	private void spawnStar() {
		int star = addEntity();
		
		float diameter = (float) (10.0 * Math.min(Math.random() + 0.2, 1.0));
		float x = (float) ((Math.random() - 0.5) * Shell.WIDTH * 2);
		float y = (float) ((Math.random() - 0.5) * Shell.HEIGHT * 2);
		
		set(star, World.DIMENSION_X, diameter);
		set(star, World.DIMENSION_Y, diameter);
		set(star, World.POSITION_X, x);
		set(star, World.POSITION_Y, y);
		set(star, World.VELOCITY_X, 0);
		set(star, World.VELOCITY_Y, 0);
		generateStarColor(star);
		set(star, World.KIND, World.KIND_VAL_STAR);
		set(star, World.COLLISION_ENABLED, 1.0f);
	}

	private void generateStarColor(int starId) {
		float r = 1.0f;
		float g = 1.0f;
		float b = 1.0f;
		
		double rand = 2 * (Math.random() - 0.5);
		if(rand > 0) {
			// red-ish
			g -= rand * 0.25;
			b -= rand * 0.25;
		} else {
			// blue-ish
			r += rand * 0.25;
			g += rand * 0.25;
		}
		
		set(starId, World.COLOR_R, r);
		set(starId, World.COLOR_G, g);
		set(starId, World.COLOR_B, b);
	}

	/**
	 * Performs roughly O(n^2) algorithm to find collisions.
	 * 
	 * Collision circles have a radius equal to the larger of the two dimensions.
	 * Only entities with COLLISION_ENABLED set to 1.0f will be considered.
	 * 
	 * @param dt
	 */
	private void detectAndRespondToCollisions(float dt) {
		// Inter-entity collisions
		for(int ent1Offset = 0; ent1Offset < (ENTITY_COUNT_MAX*ENTITY_SIZE); ent1Offset += ENTITY_SIZE) {
			if(entities[ent1Offset + IN_USE] == 1.0f && entities[ent1Offset + COLLISION_ENABLED] == 1.0f) {
				for(int ent2Offset = ent1Offset + ENTITY_SIZE; ent2Offset < (ENTITY_COUNT_MAX*ENTITY_SIZE); ent2Offset += ENTITY_SIZE) {
					if(entities[ent2Offset + IN_USE] == 1.0f && entities[ent2Offset + COLLISION_ENABLED] == 1.0f) {
						// Diameter is halved to find radius
						float radiusSum = 0.5f * (
								Math.max(entities[ent1Offset + DIMENSION_X], entities[ent1Offset + DIMENSION_Y])
								+
								Math.max(entities[ent2Offset + DIMENSION_X], entities[ent2Offset + DIMENSION_Y])
						);
						float squaredRadiusSum = radiusSum * radiusSum;
						
						float distanceX = entities[ent2Offset + POSITION_X] - entities[ent1Offset + POSITION_X];
						float distanceY = entities[ent2Offset + POSITION_Y] - entities[ent1Offset + POSITION_Y];
						float squaredDistance = distanceX * distanceX + distanceY * distanceY;
						
						if(squaredDistance <= squaredRadiusSum) {
							respondToCollision(ent1Offset, ent2Offset);
						}
					}
				}
			}
		}
		
		// Edge colissions for traps, players and bullets
		for(int entOffset = 0; entOffset < (ENTITY_COUNT_MAX*ENTITY_SIZE); entOffset += ENTITY_SIZE) {
			boolean inUse = entities[entOffset + IN_USE] == 1.0f;
			boolean hasEdgeColissions = entities[entOffset + KIND] == KIND_VAL_TRAP ||
					                    entities[entOffset + KIND] == KIND_VAL_PLAYER ||
					                    entities[entOffset + KIND] == KIND_VAL_BULLET ||
					                    entities[entOffset + KIND] == KIND_VAL_STAR;
			
			if(inUse && hasEdgeColissions) {
				// Reflect off the edges
				float posX = entities[entOffset + POSITION_X];
				float posY = entities[entOffset + POSITION_Y];
				float radiusX = entities[entOffset + DIMENSION_X] / 2;
				float radiusY = entities[entOffset + DIMENSION_X] / 2;
				
				float minExtentX = posX - radiusX;
				float maxExtentX = posX + radiusX;
				float minExtentY = posY - radiusY;
				float maxExtentY = posY + radiusY;
				
				if(minExtentX < MIN_POSITION_X) {
					entities[entOffset + POSITION_X] = MIN_POSITION_X + radiusX;
					entities[entOffset + VELOCITY_X] = -entities[entOffset + VELOCITY_X];
				} else if(maxExtentX > MAX_POSITION_X) {
					entities[entOffset + POSITION_X] = MAX_POSITION_X - radiusX;
					entities[entOffset + VELOCITY_X] = -entities[entOffset + VELOCITY_X];
				}
				
				if(minExtentY < MIN_POSITION_Y) {
					entities[entOffset + POSITION_Y] = MIN_POSITION_Y + radiusY;
					entities[entOffset + VELOCITY_Y] = -entities[entOffset + VELOCITY_Y];
				} else if(maxExtentY > MAX_POSITION_Y) {
					entities[entOffset + POSITION_Y] = MAX_POSITION_Y - radiusY;
					entities[entOffset + VELOCITY_Y] = -entities[entOffset + VELOCITY_Y];
				}
			}
		}
	}

	private void respondToCollision(int offset0, int offset1) {
		float kind0 = entities[offset0 + KIND];
		float kind1 = entities[offset1 + KIND];
		
		if(kind0 == KIND_VAL_BULLET || kind1 == KIND_VAL_BULLET) {
			// Bullet-to-X colissions
			respondToBulletColission(offset0, offset1);
		} else if(kind0 == KIND_VAL_PLAYER || kind1 == KIND_VAL_PLAYER) {
			// Player-to-X colission, except player-to-bullet which is handled before
			respondToPlayerColission(offset0, offset1);
		} else if(kind0 == KIND_VAL_TRAP || kind1 == KIND_VAL_TRAP) {
			respondToTrapColission(offset0, offset1);
		}
	}

	private void respondToBulletColission(int offset0, int offset1) {
		float kind0 = entities[offset0 + KIND];
		float kind1 = entities[offset1 + KIND];
		
		if(kind0 != KIND_VAL_BULLET && kind1 == KIND_VAL_BULLET) {
			// Reverse parameter order if only the last parameter is a bullet
			// this way, in bullet-to-X colissions, bullet will always come first
			respondToBulletColission(offset1, offset0);
			return;
		}
		
		// Bullet-to-X colissions
		if(kind0 == KIND_VAL_BULLET) {
			if(kind1 == KIND_VAL_BULLET) {
				// bullet to bullet colission, delete both
				entities[offset0 + IN_USE] = 0.0f;
				entities[offset1 + IN_USE] = 0.0f;
			} else if(kind1 == KIND_VAL_PLAYER /* || kind1 == KIND_VAL_TRAP */) {
				// bullet to player colission, reverse the players time arrow for 2 seconds and also
				// remove the bullet
				entities[offset0 + IN_USE] = 0.0f;
				entities[offset1 + REVERSED] = 2.0f;
			} else {
				// Ignore colissions with stars
				// Maybe reverse traps too?
			}
		}
	}
	
	private void respondToPlayerColission(int offset0, int offset1) {
		float kind0 = entities[offset0 + KIND];
		float kind1 = entities[offset1 + KIND];
		
		if(kind0 != KIND_VAL_PLAYER && kind1 == KIND_VAL_PLAYER) {
			respondToPlayerColission(offset1, offset0);
			return;
		}
		
		// Player-to-X colissions
		if(kind0 == KIND_VAL_PLAYER) {
			if(kind1 == KIND_VAL_PLAYER) {
				// When player crashes into other player, reverse the time a little bit for both
				entities[offset0 + REVERSED] = 0.3f;
				entities[offset1 + REVERSED] = 0.3f;
			} else if(kind1 == KIND_VAL_STAR) {
				entities[offset1 + IN_USE] = 0.0f;
				float starRadius = entities[offset1 + DIMENSION_X] / 2;
				int starArea = (int) (starRadius * starRadius * Math.PI);

				scores[entityOffsetToScoreIdx(offset0)] += starArea;
			} else if(kind1 == KIND_VAL_TRAP) {
				drainPlayer(offset0, entityOffsetToScoreIdx(offset0));
			}
		}
	}
	
	private void respondToTrapColission(int offset0, int offset1) {
			float kind0 = entities[offset0 + KIND];
			float kind1 = entities[offset1 + KIND];
			
			if(kind0 != KIND_VAL_TRAP && kind1 == KIND_VAL_TRAP) {
				respondToTrapColission(offset1, offset0);
			}
			
			if(kind1 == KIND_VAL_TRAP) {
				float previousPosX0 = entities[1*ENTITY_COUNT_MAX*ENTITY_SIZE + offset0 + POSITION_X];
				float previousPosY0 = entities[1*ENTITY_COUNT_MAX*ENTITY_SIZE + offset0 + POSITION_Y];
				float previousPosX1 = entities[1*ENTITY_COUNT_MAX*ENTITY_SIZE + offset1 + POSITION_X];
				float previousPosY1 = entities[1*ENTITY_COUNT_MAX*ENTITY_SIZE + offset1 + POSITION_Y];
				
				// Reflect velocity on the vector connecting the centers
				float connectX = previousPosX1 - previousPosX0;
				float connectY = previousPosY1 - previousPosY0;
				float connectMag = (float) Math.sqrt(connectX*connectX + connectY*connectY);
				connectX /= connectMag;
				connectY /= connectMag;
				
	//			float normalX = connectY;
	//			float normalY = -connectX;
				float normalX = connectX;
				float normalY = connectY;
				
				float vx0 = entities[offset0 + VELOCITY_X];
				float vy0 = entities[offset0 + VELOCITY_Y];
				if(entities[offset0 + REVERSED] > 0.0f) {
					vx0 = -vx0;
					vy0 = -vy0;
				}
				
				float vx1 = entities[offset1 + VELOCITY_X];
				float vy1 = entities[offset1 + VELOCITY_Y];
				if(entities[offset1 + REVERSED] > 0.0f) {
					vx1 = -vx1;
					vy1 = -vy1;
				}
				
				float dot = vx0 * normalX + vy0 * normalY;
				vx0 = vx0 - 2.0f * dot * normalX;
				vy0 = vy0 - 2.0f * dot * normalY;
				
				normalX = -normalX;
				normalY = -normalY;
				dot = vx1 * normalX + vy1 * normalY;
				vx1 = vx1 - 2.0f * dot * normalX;
				vy1 = vy1 - 2.0f * dot * normalY;
				
				if(entities[offset0 + REVERSED] <= 0.0f) {
					entities[offset0 + POSITION_X] = previousPosX0;
					entities[offset0 + POSITION_Y] = previousPosY0;
					entities[offset0 + VELOCITY_X] = vx0;
					entities[offset0 + VELOCITY_Y] = vy0;
				}
				
				if(entities[offset1 + REVERSED] <= 0.0f) {
					entities[offset1 + POSITION_X] = previousPosX1;
					entities[offset1 + POSITION_Y] = previousPosY1;
					entities[offset1 + VELOCITY_X] = vx1;
					entities[offset1 + VELOCITY_Y] = vy1;
				}
			}
			
	//		// respond by inverting velocity vector
	//		if(kind1 == KIND_VAL_TRAP) {
	//			entities[offset0 + VELOCITY_X] = -entities[offset0 + VELOCITY_X];
	//			entities[offset0 + VELOCITY_Y] = -entities[offset0 + VELOCITY_Y];
	//			
	//			entities[offset1 + VELOCITY_X] = -entities[offset1 + VELOCITY_X];
	//			entities[offset1 + VELOCITY_Y] = -entities[offset1 + VELOCITY_Y];
	//		}
		}

	private void drainPlayer(int entityOffset, int scoreIdx) {
		drainTimeouts[scoreIdx] -= Server.SERVER_UPDATE_INTERVAL;
		if(drainTimeouts[scoreIdx] <= 0) {
			drainTimeouts[scoreIdx] = DRAIN_INTERVAL;
			
			// Drain 5% of points every DRAIN_INTERVAL seconds but at least one point
			int starArea = (int) Math.max(1.0, (scores[scoreIdx] * DRAIN_FACTOR));
			float starRadius = (float) Math.sqrt(starArea / Math.PI);
			float starDim = 2 * starRadius;
			scores[scoreIdx] = (int) Math.max(0.0, scores[scoreIdx] - starArea);
			
			if(starDim > 2) {
				float starDirX = (float) (Math.random() - 0.5);
				float starDirY = (float) (Math.random() - 0.5);
				float starDirInvMagnitude = (float) (1.0 / Math.sqrt(starDirX*starDirX + starDirY*starDirY));
				starDirX *= starDirInvMagnitude;
				starDirY *= starDirInvMagnitude;
				
				float starVx = starDirX * DRAINED_STAR_SPEED;
				float starVy = starDirY * DRAINED_STAR_SPEED;
				
				float starPosX = 10.0f + entities[entityOffset + POSITION_X] + starDirX * 0.5f * (entities[entityOffset + DIMENSION_X] + starDim);
				float starPosY = 10.0f + entities[entityOffset + POSITION_Y] + starDirY * 0.5f * (entities[entityOffset + DIMENSION_Y] + starDim);
				
				int star = addEntity();
				set(star, DIMENSION_X, starDim);
				set(star, DIMENSION_Y, starDim);
				set(star, POSITION_X, starPosX);
				set(star, POSITION_Y, starPosY);
				set(star, VELOCITY_X, starVx);
				set(star, VELOCITY_Y, starVy);
				generateStarColor(star);
				set(star, KIND, KIND_VAL_STAR);
				set(star, COLLISION_ENABLED, 1.0f);
				set(star, LIFETIME, 2.0f);
			}
		}
	}

	private int entityOffsetToScoreIdx(int offset) {
		int playerEntityId = offset / ENTITY_SIZE;
		
		for(int i = 0; i < userIDs.length; ++i) {
			if(playerEntityId == userIDs[i]) {
				return i;
			}
		}
		
		return -1;
	}

	private void timeReverse(float dt) {
		final int LAST_FRAME_OFFSET = ENTITY_SIZE * ENTITY_COUNT_MAX;
		
		for(int offset = 0; offset < LAST_FRAME_OFFSET; offset += ENTITY_SIZE) {
			if(entities[offset + IN_USE] == 1.0f && entities[offset + REVERSED] > 0) {
				float timeLeftToReverse = entities[offset + REVERSED];
				
				if(entities[offset + LAST_FRAME_OFFSET + IN_USE] == 0.0f) {
					// No more frames to reverse, object would not exist anymore, end rewinding early
					timeLeftToReverse = 0;
				} else {
					// Restore state of last frame
					for(int i = 0; i < (PAST_FRAMES_MAX-1); ++i) {
						System.arraycopy(entities, offset + (i+1) * LAST_FRAME_OFFSET, entities, offset + i * LAST_FRAME_OFFSET, ENTITY_SIZE);
					}
					// Do it twice since we archived a frame before and the last frame is the same
					for(int i = 0; i < (PAST_FRAMES_MAX-1); ++i) {
						System.arraycopy(entities, offset + (i+1) * LAST_FRAME_OFFSET, entities, offset + i * LAST_FRAME_OFFSET, ENTITY_SIZE);
					}
					timeLeftToReverse -= Server.SERVER_UPDATE_INTERVAL;
				}
				
				entities[offset + REVERSED] = Math.max(0.0f, timeLeftToReverse);
			}
		}
	}

	private void integratePosition(float dt) {
		for(int offset = 0; offset < (ENTITY_COUNT_MAX*ENTITY_SIZE); offset += ENTITY_SIZE) {
			if(entities[offset + IN_USE] == 1.0f && entities[offset + REVERSED] == 0.0f) {
				entities[offset + POSITION_X] += dt * entities[offset + VELOCITY_X];
				entities[offset + POSITION_Y] += dt * entities[offset + VELOCITY_Y];
				
				entities[offset + POSITION_X] = Math.min(Math.max(entities[offset + POSITION_X], MIN_POSITION_X), MAX_POSITION_X);
				entities[offset + POSITION_Y] = Math.min(Math.max(entities[offset + POSITION_Y], MIN_POSITION_Y), MAX_POSITION_Y);
			}
		}
	}
	
	/**
	 * Gets the X coordinate of the center point of the currently visible portion of the world.
	 * @return
	 */
	public float getCameraPositionX() {
		if(localPlayerID == -1) {
			return 0.0f;
		}
		
		float camPosXMin = MIN_POSITION_X + Shell.WIDTH / 2.0f;
		float camPosXMax = MAX_POSITION_X - Shell.WIDTH / 2.0f;
		float camPosX = Math.min(Math.max(get(localPlayerID, POSITION_X), camPosXMin), camPosXMax);
		
		return camPosX;
	}
	
	/**
	 * Gets the Y coordinate of the center point of the currently visible portion of the world.
	 * @return
	 */
	public float getCameraPositionY() {
		if(localPlayerID == -1) {
			return 0.0f;
		}
		
		float camPosYMin = MIN_POSITION_Y + Shell.HEIGHT / 2.0f;
		float camPosYMax = MAX_POSITION_Y - Shell.HEIGHT / 2.0f;
		float camPosY = Math.min(Math.max(get(localPlayerID, POSITION_Y), camPosYMin), camPosYMax);
		
		return camPosY;
	}
	
	public void draw(float dt, Graphics2D g) {
		AffineTransform oldTrans = g.getTransform();
		Color oldColor = g.getColor();

		updateParticles(dt); // Have to do this here because different threads have different worlds.
		
		// Set transform so that we can draw in y-up normalized device coordinates
		g.translate(Shell.WIDTH / 2.0, Shell.HEIGHT / 2.0);
		g.scale(1, -1);
		
		
		// Set up camera transform, is zero if no local player ID defined
		g.translate(-getCameraPositionX(), -getCameraPositionY());
		
		renderParticles(g);
		renderEntitites(g);
		
		g.setColor(oldColor);
		g.setTransform(oldTrans);
		
		renderHUD(g);
		
		g.setColor(oldColor);
		g.setTransform(oldTrans);
	}

	private void renderEntitites(Graphics2D g) {
		AffineTransform baseTrans = g.getTransform();
		
		angle += 0.005;
		
		for(int offset = 0; offset < (ENTITY_COUNT_MAX*ENTITY_SIZE); offset += ENTITY_SIZE) {
			
			if(entities[offset + IN_USE] == 1.0) {
				if(entities[offset + KIND] == KIND_VAL_TRAP){
					g.rotate(angle + offset, entities[offset + POSITION_X], entities[offset + POSITION_Y]);
				}
				g.translate(entities[offset + POSITION_X], entities[offset + POSITION_Y]);
				g.scale(entities[offset + DIMENSION_X] / 2, entities[offset + DIMENSION_Y] / 2);
				
				int tex_idx = Math.round(entities[offset + TEX_INDEX]);
				
				if(tex_idx == 0) {
					g.setColor(new Color(entities[offset + COLOR_R], entities[offset + COLOR_G], entities[offset + COLOR_B]));
					g.fillOval(-1, -1, 2, 2);
					
				} else {
					g.drawImage(textures[tex_idx], -1, 1, 2, -2, null);
					if(entities[offset + REVERSED] > 0) {
						g.setColor(new Color(1.0f, 0.0f, 0.0f, 0.5f));
						g.fillOval(-1, -1, 2, 2);
					}
					if(entities[offset + KIND] == KIND_VAL_PLAYER){
						int playerIdx = -1;
						for(int i = 0; i < userIDs.length; ++i) {
							if((offset / ENTITY_SIZE) == userIDs[i]) {
								playerIdx = i;
								break;
							}
						}
						
						String name = usernames[playerIdx];
						g.scale(0.05, -0.05);
						g.setFont(new Font(g.getFont().getName(), Font.PLAIN, 10));
						g.translate(-g.getFontMetrics().stringWidth(name)/2, g.getFontMetrics().getHeight() / 4);
						g.setColor(Color.WHITE);
						g.drawString(name, 0, 0);
					}
					
					
				}
				
				g.setTransform(baseTrans);
			}
		}
	}
	
	private void renderParticles(Graphics2D g) {
		AffineTransform baseTrans = g.getTransform();
		
		for(int offset = 0; offset < (PARTICLE_COUNT_MAX * ENTITY_SIZE); offset += ENTITY_SIZE) {
			g.translate(particles[offset + POSITION_X], particles[offset + POSITION_Y]);
			g.scale(particles[offset + DIMENSION_X] / 2, particles[offset + DIMENSION_Y] / 2);
			g.setColor(new Color(particles[offset + COLOR_R], particles[offset + COLOR_G], particles[offset + COLOR_B]));
			g.fillOval(-1, -1, 2, 2);
			g.setTransform(baseTrans);
		}
	}

	private void renderHUD(Graphics2D g) {
		Font oldFont = g.getFont();
		Color oldColor = g.getColor();
		
		if(remainingGameDuration > GAME_DURATION) {
			renderInfoScreen(g, oldFont, new Color(100, 100, 100, 220), "Deuterium", "Preparing game... " + scores.length + " players have joined", "Made with love by: Paul Lonauer, Philipp Stadler, Michael Temper");
		} else if(remainingGameDuration <= 0) {
			renderWinner(g, oldFont);
		}
		
		g.setFont(oldFont);
		g.setColor(oldColor);
		
		renderScores(g, oldFont);
		
		g.setFont(oldFont);
		g.setColor(oldColor);
		
		renderRemainingTime(g, oldFont);
		
		g.setFont(oldFont);
		g.setColor(oldColor);
	}

	private void renderScores(Graphics2D g, Font baseFont) {
		final int MAX_VISIBLE_NAMES = 5;
		// Pixels from the edge of the window top and right
		final int HIGHSCORE_PADDING_TOP = 25;
		final int HIGHSCORE_PADDING_RIGHT = 20;
		final int HIGHSCORE_WIDTH = Shell.WIDTH / 5;
		final int HIGHSCORE_LEFT = Shell.WIDTH - HIGHSCORE_PADDING_RIGHT - HIGHSCORE_WIDTH;
		final int HIGHSCORE_FONT_HEIGHT = 13; 
		final int HIGHSCORE_FONT_BOLD_HEIGHT = 15;
		final int HIGHSCORE_LINE_HEIGHT = (int) (HIGHSCORE_FONT_HEIGHT * 1.7);
		
		Font newFont = new Font(baseFont.getFontName(), Font.PLAIN, HIGHSCORE_FONT_HEIGHT);
		Font boldFont = new Font(baseFont.getFontName(), Font.BOLD, HIGHSCORE_FONT_BOLD_HEIGHT);
		
		g.setFont(newFont);
		g.setColor(Color.WHITE);
		FontMetrics metrics = g.getFontMetrics(newFont);
		
		for(int playerIdx = 0; playerIdx < usernames.length && playerIdx < MAX_VISIBLE_NAMES; ++playerIdx) {
			if(userIDs[playerIdx] == localPlayerID) {
				g.setFont(boldFont);
			}
			
			// y position of the baseline
			final int y = HIGHSCORE_PADDING_TOP + playerIdx * HIGHSCORE_LINE_HEIGHT + HIGHSCORE_FONT_HEIGHT;
			
			g.drawString(usernames[playerIdx].toUpperCase(), HIGHSCORE_LEFT, y);
			
			String scoreString = String.valueOf(scores[playerIdx]);
			
			final int scoreX = Shell.WIDTH - HIGHSCORE_PADDING_RIGHT - metrics.stringWidth(scoreString);
			g.drawString(scoreString, scoreX, y);
			
			if(userIDs[playerIdx] == localPlayerID) {
				g.setFont(newFont);
			}
		}
	}

	private void renderRemainingTime(Graphics2D g, Font baseFont) {
		final int REMAINING_TIME_PADDING = 14;
		final int REMAINING_TIME_FONT_HEIGHT = 28;
		
		float time = (remainingGameDuration > GAME_DURATION) ? (remainingGameDuration - GAME_DURATION) : remainingGameDuration;
		String timeStr = formatDuration(time);
		
		Font timeFont = new Font(baseFont.getFontName(), Font.BOLD, REMAINING_TIME_FONT_HEIGHT);
		g.setColor(Color.WHITE);
		g.setFont(timeFont);
		
		FontMetrics metr = g.getFontMetrics(timeFont);
		
		float x = 0.5f * (Shell.WIDTH - metr.stringWidth(timeStr));
		float y = REMAINING_TIME_PADDING + metr.getHeight();
		g.drawString(timeStr, x, y);
	}

	private String formatDuration(float duration) {
		if(duration > 0) {
			int remainingSecs = (int) Math.ceil(duration);
			int minutes = remainingSecs / 60;
			int seconds = remainingSecs % 60;
			
			StringBuilder timeStr = new StringBuilder(5);
			
			if(minutes < 10) {
				timeStr.append('0');
			}
			
			timeStr.append(minutes);
			timeStr.append(':');
			
			if(seconds < 10) {
				timeStr.append('0');
			}
			timeStr.append(seconds);
			
			return timeStr.toString();
		} else {
			return "00:00";
		}
	}

	private void renderWinner(Graphics2D g, Font baseFont) {
		final int WINNER_PADDING_TOP = (int) (Shell.HEIGHT * 0.4f);
		final int WINNER_FONT_HEIGHT = 22;
		Font winnerMsgFont = new Font(baseFont.getFontName(), Font.BOLD, WINNER_FONT_HEIGHT);
		Font localMsgFont = new Font(baseFont.getFontName(), Font.PLAIN, WINNER_FONT_HEIGHT / 2);
		
		int winnerIdx = findWinnerIdx();
		boolean localWin = winnerIdx == findLocalPlayerScoreIdx();
		String winnerUsername = usernames[winnerIdx];
		
		String winnerMsg = (winnerUsername + " got the job!").toUpperCase();
		String localMsg = (localWin ? "Fantastic, you won! Call yourself star of the solar system!" : "You lost! Maybe next time, little planet.").toUpperCase();
		Color bgColor = localWin ? new Color(20, 140, 20, 200) : new Color(140, 20, 20, 200);
		
		renderInfoScreen(g, baseFont, bgColor, winnerMsg, localMsg, "");
	}
	
	private void renderInfoScreen(Graphics2D g, Font baseFont, Color backgroundColor, String bigWords, String smallWords, String smallWords2) {
		final int WINNER_PADDING_TOP = (int) (Shell.HEIGHT * 0.4f);
		final int WINNER_FONT_HEIGHT = 22;
		Font bigWordsFont = new Font(baseFont.getFontName(), Font.BOLD, WINNER_FONT_HEIGHT);
		Font smallWordsFont = new Font(baseFont.getFontName(), Font.PLAIN, WINNER_FONT_HEIGHT / 2);
		
		bigWords = bigWords.toUpperCase();
		smallWords = smallWords.toUpperCase();
		//smallWords2 = smallWords2.toUpperCase();
		
		g.setColor(backgroundColor);
		g.fillRect(0, 0, Shell.WIDTH, Shell.HEIGHT);
		
		g.setColor(Color.WHITE);
		g.setFont(bigWordsFont);
		FontMetrics metrics = g.getFontMetrics(bigWordsFont);
		
		float x = 0.5f * (Shell.WIDTH - metrics.stringWidth(bigWords));
		float y = WINNER_PADDING_TOP + metrics.getHeight();
		
		g.drawString(bigWords, x, y);
		
		y += metrics.getHeight();
		
		g.setFont(smallWordsFont);
		metrics = g.getFontMetrics(smallWordsFont);
		
		x = 0.5f * (Shell.WIDTH - metrics.stringWidth(smallWords));
		
		g.drawString(smallWords, x, y);
		
		y += metrics.getHeight();
		x = 0.5f * (Shell.WIDTH - metrics.stringWidth(smallWords2));
		
		g.drawString(smallWords2, x, y);
	}

	private int findWinnerIdx() {
		int winnerIdx = -1;
		int winnerScore = Integer.MIN_VALUE;
		
		for(int i = 0; i < scores.length; ++i) {
			if(scores[i] > winnerScore) {
				winnerIdx = i;
				winnerScore = scores[i];
			}
		}
		
		return winnerIdx;
	}
	
	private int findLocalPlayerScoreIdx() {
		for(int i = 0; i < scores.length; ++i) {
			if(userIDs[i] == localPlayerID) {
				return i;
			}
		}
		
		return -1;
	}
}
