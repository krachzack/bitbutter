import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

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
	
	private static final int POSITION_SIZE = 2;
	private static final int VELOCITY_SIZE = 2;
	private static final int COLOR_SIZE = 3;
	private static final int DIMENSION_SIZE = 2;
	private static final int ENTITY_SIZE = POSITION_SIZE + VELOCITY_SIZE + COLOR_SIZE + DIMENSION_SIZE;
	
	private float[] entities = new float[0];
	private int entityCount = 0;
	
	public int addEntity() {
		int id = entityCount++;
		
		if((id*ENTITY_SIZE) >= entities.length) {
			float[] oldEntities = entities;
			entities = new float[(id + 12) * ENTITY_SIZE];
			System.arraycopy(oldEntities, 0, entities, 0, oldEntities.length);
		}
		
		return id;
	}
	
	public void set(int entityID, int component, float val) {
		entities[entityID * ENTITY_SIZE + component] = val; 
	}
	
	public float get(int entityID, int component) {
		return entities[entityID * ENTITY_SIZE + component]; 
	}
	
	public void update(float dt) {
		move(dt);
	}

	private void move(float dt) {
		for(int offset = 0; offset < (entityCount*ENTITY_SIZE); offset += ENTITY_SIZE) {
			entities[offset + POSITION_X] += dt * entities[offset + VELOCITY_X];
			entities[offset + POSITION_Y] += dt * entities[offset + VELOCITY_Y];
		}
	}
	
	public void draw(float dt, Graphics2D g) {
		AffineTransform oldTrans = g.getTransform();
		Color oldColor = g.getColor();
		
		g.translate(Shell.WIDTH / 2.0, Shell.HEIGHT / 2.0);
		g.scale(1, -1);
		AffineTransform baseTrans = g.getTransform();
		
		for(int offset = 0; offset < (entityCount*ENTITY_SIZE); offset += ENTITY_SIZE) {
			float posX = entities[offset + POSITION_X];
			float posY = entities[offset + POSITION_Y];
			
			g.translate(entities[offset + POSITION_X], entities[offset + POSITION_Y]);
			g.scale(entities[offset + DIMENSION_X] / 2, entities[offset + DIMENSION_Y] / 2);
			
			g.setColor(new Color(entities[offset + COLOR_R], entities[offset + COLOR_G], entities[offset + COLOR_B]));
			g.fillOval(-1, -1, 2, 2);
			
			g.setTransform(baseTrans);
		}
		
		g.setColor(oldColor);
		g.setTransform(oldTrans);
	}
}
