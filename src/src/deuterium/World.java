package deuterium;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

public class World {
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
	
	public static final float KIND_VAL_PLAYER = 0.0f;
	public static final float KIND_VAL_TRAP = 1.0f;
	
	private static final int POSITION_SIZE = 2;
	private static final int VELOCITY_SIZE = 2;
	private static final int COLOR_SIZE = 3;
	private static final int DIMENSION_SIZE = 2;
	private static final int IN_USE_SIZE = 1;
	private static final int REVERSED_SIZE = 1;
	private static final int COLLISION_ENABLED_SIZE = 1;
	private static final int KIND_SIZE = 1;
	private static final int ENTITY_SIZE = POSITION_SIZE + VELOCITY_SIZE + COLOR_SIZE + DIMENSION_SIZE + IN_USE_SIZE + REVERSED_SIZE + COLLISION_ENABLED_SIZE + KIND_SIZE;
	
	private static final int ENTITY_COUNT_MAX = 256;
	private static final int PAST_FRAMES_MAX = 500;
	// The world is four time the area of the window, that is a square with double sidelength
	private static final float MAX_POSITION_X = Shell.WIDTH * 0.6f;
	private static final float MIN_POSITION_X = -MAX_POSITION_X;
	private static final float MAX_POSITION_Y = Shell.HEIGHT * 0.6f;
	private static final float MIN_POSITION_Y = -MAX_POSITION_Y;
	
	public float[] entities = new float[ENTITY_SIZE * ENTITY_COUNT_MAX * PAST_FRAMES_MAX];
	public int localPlayerID = -1;
	
	private int pastFrameCount = 0;
	private boolean timeReverse;
	
	public UniversalDTO getFullStateUpdateDTO() {
		return new UniversalDTO(-1, "elohim", "update-full", Arrays.copyOfRange(entities, 0, ENTITY_SIZE * ENTITY_COUNT_MAX));
	}
	
	/**
	 * Called by the client with data from the server to work server updates into the world.
	 * 
	 * @param dto
	 */
	public void handleDTO(UniversalDTO dto) {
		if(dto.getEvent().equals("update-full")) {
			System.arraycopy(dto.getData(), 0, entities, 0, dto.getData().length);
		} else if(dto.getEvent().equals("join-acknowledge")) {
			localPlayerID = (int) dto.getData()[0];
			System.out.println("Server acknowledged this player joining and assigned UID: " + localPlayerID);
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
			
			// Archive the old frame
			System.arraycopy(entities, 0, entities, ENTITY_COUNT_MAX*ENTITY_SIZE, entities.length - (ENTITY_COUNT_MAX*ENTITY_SIZE));
			++pastFrameCount;
		}
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
	}

	private void respondToCollision(int ent1Offset, int ent2Offset) {
		// This assumes ent1Offset is always lower than ent2Offset and that the player has the lowest possible ID
		if(
			entities[ent1Offset + KIND] == KIND_VAL_PLAYER &&
			entities[ent2Offset + KIND] == KIND_VAL_TRAP
		) {
			// Delete the entity with higher ID, which is a trap
			entities[ent2Offset + IN_USE] = 0.0f;
			
			System.out.println((ent2Offset / ENTITY_SIZE) + " was deleted due to collision!");
		}
	}

	private void timeReverse(float dt) {
		for(int offset = 0; offset < (ENTITY_COUNT_MAX*ENTITY_SIZE); offset += ENTITY_SIZE) {
			if(entities[offset + IN_USE] == 1.0f && entities[offset + REVERSED] == 1.0f) {
				for(int i = 0; i < (PAST_FRAMES_MAX-1); ++i) {
					System.arraycopy(entities, offset + (i+1) * (ENTITY_COUNT_MAX*ENTITY_SIZE), entities, offset + i * (ENTITY_COUNT_MAX*ENTITY_SIZE), ENTITY_SIZE);
				}
				// Do it twice since we archived a frame before
				for(int i = 0; i < (PAST_FRAMES_MAX-1); ++i) {
					System.arraycopy(entities, offset + (i+1) * (ENTITY_COUNT_MAX*ENTITY_SIZE), entities, offset + i * (ENTITY_COUNT_MAX*ENTITY_SIZE), ENTITY_SIZE);
				}
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
	
	public void draw(float dt, Graphics2D g) {
		AffineTransform oldTrans = g.getTransform();
		Color oldColor = g.getColor();
		
		// Set transform so that we can draw in y-up normalized device coordinates
		g.translate(Shell.WIDTH / 2.0, Shell.HEIGHT / 2.0);
		g.scale(1, -1);
		
		// Set up camera transform if there is a local player ID defined
		if(localPlayerID != -1) {
			g.translate(-get(localPlayerID, POSITION_X), -get(localPlayerID, POSITION_Y));
		}
		
		AffineTransform baseTrans = g.getTransform();
		
		for(int offset = 0; offset < (ENTITY_COUNT_MAX*ENTITY_SIZE); offset += ENTITY_SIZE) {
			if(entities[offset + IN_USE] == 1.0) {
				g.translate(entities[offset + POSITION_X], entities[offset + POSITION_Y]);
				g.scale(entities[offset + DIMENSION_X] / 2, entities[offset + DIMENSION_Y] / 2);
				
				g.setColor(new Color(entities[offset + COLOR_R], entities[offset + COLOR_G], entities[offset + COLOR_B]));
				g.fillOval(-1, -1, 2, 2);
				
				g.setTransform(baseTrans);
			}
		}
		
		g.setColor(oldColor);
		g.setTransform(oldTrans);
	}
}
