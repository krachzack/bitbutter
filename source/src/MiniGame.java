import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferStrategy;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

public class MiniGame {
	
	public static final int WIDTH = 800;
	public static final int HEIGHT = 600;
	private static BufferStrategy bufferStrategy;
	private static World world = new World();
	
	
	public static void main(String[] args) {
		initWindow();
		initWorld();
		
		long lastFrameTime = System.nanoTime();
		while(true) {
			long thisFrameTime = System.nanoTime();
			float dt = (thisFrameTime - lastFrameTime) / 1_000_000_000.0f;
			
			run(dt);
			
			lastFrameTime = thisFrameTime;
		}
	}

	private static void initWindow() {
		Canvas canvas = new Canvas();
		canvas.setSize(WIDTH, HEIGHT);
		
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setIgnoreRepaint(true);
		frame.setResizable(false);
		frame.add(canvas);
		frame.pack();
		frame.setVisible(true);
		
		canvas.createBufferStrategy(2);
		bufferStrategy = canvas.getBufferStrategy();
	}
	
	private static void initWorld() {
		int ent = world.addEntity();
		world.set(ent, World.POSITION_X, 0.0f);
		world.set(ent, World.POSITION_Y, 0.0f);
		
		world.set(ent, World.VELOCITY_X, 3.0f);
		world.set(ent, World.VELOCITY_Y, 3.0f);
		
		world.set(ent, World.COLOR_B, 1.0f);
		
		world.set(ent, World.DIMENSION_X, 10.0f);
		world.set(ent, World.DIMENSION_Y, 10.0f);
		
		ent = world.addEntity();
		world.set(ent, World.POSITION_X, 0.0f);
		world.set(ent, World.POSITION_Y, 0.0f);
		
		world.set(ent, World.VELOCITY_X, -3.0f);
		world.set(ent, World.VELOCITY_Y, 2.0f);
		
		world.set(ent, World.COLOR_R, 1.0f);
		
		world.set(ent, World.DIMENSION_X, 10.0f);
		world.set(ent, World.DIMENSION_Y, 10.0f);
	}

	private static void run(float dt) {
		world.update(dt);
		
		Graphics2D g = (Graphics2D) bufferStrategy.getDrawGraphics();
		
		// Draw the background first
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, WIDTH, HEIGHT);
		
		g.setColor(Color.DARK_GRAY);
		g.drawLine(WIDTH/2, 0, WIDTH/2, HEIGHT);
		g.drawLine(0, HEIGHT/2, WIDTH, HEIGHT/2);
		
		// Then let world render itself
		world.draw(dt, g);

		g.dispose();
		bufferStrategy.show();
	}
	
}
