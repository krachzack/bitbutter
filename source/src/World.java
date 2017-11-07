import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
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
	
	private static final int POSITION_SIZE = 2;
	private static final int VELOCITY_SIZE = 2;
	private static final int COLOR_SIZE = 3;
	private static final int DIMENSION_SIZE = 2;
	private static final int IN_USE_SIZE = 1;
	private static final int REVERSED_SIZE = 1;
	private static final int ENTITY_SIZE = POSITION_SIZE + VELOCITY_SIZE + COLOR_SIZE + DIMENSION_SIZE + IN_USE_SIZE + REVERSED_SIZE;
	
	private static final int ENTITY_COUNT_MAX = 512;
	private static final int PAST_FRAMES_MAX = 1000;
	
	private float[] entities = new float[ENTITY_SIZE * ENTITY_COUNT_MAX * PAST_FRAMES_MAX];
	private int pastFrameCount = 0;
	private boolean timeReverse;
	
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
			move(dt);
			timeReverse(dt);
			
			// Archive the old frame
			System.arraycopy(entities, 0, entities, ENTITY_COUNT_MAX*ENTITY_SIZE, entities.length - (ENTITY_COUNT_MAX*ENTITY_SIZE));
			++pastFrameCount;
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

	private void move(float dt) {
		for(int offset = 0; offset < (ENTITY_COUNT_MAX*ENTITY_SIZE); offset += ENTITY_SIZE) {
			if(entities[offset + IN_USE] == 1.0f && entities[offset + REVERSED] == 0.0f) {
				entities[offset + POSITION_X] += dt * entities[offset + VELOCITY_X];
				entities[offset + POSITION_Y] += dt * entities[offset + VELOCITY_Y];
			}
		}
	}
	
	public void draw(float dt, Graphics2D g) {
		AffineTransform oldTrans = g.getTransform();
		Color oldColor = g.getColor();
		
		g.translate(Shell.WIDTH / 2.0, Shell.HEIGHT / 2.0);
		g.scale(1, -1);
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
