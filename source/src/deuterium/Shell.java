package deuterium;
import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferStrategy;
import java.util.concurrent.BlockingQueue;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Shell {
	
	public static float mouseX;
	public static float mouseY;
	
	public static boolean mousePressed;
	public static boolean mouseReleased;
	public static boolean rightMousePressed;
	public static boolean rightMouseReleased;
	
	private static boolean mousePressedNextFrame;
	private static boolean rightMousePressedNextFrame;
	private static boolean mouseReleasedNextFrame;
	private static boolean rightMouseReleasedNextFrame;
	
	public static final int WIDTH = 800;
	public static final int HEIGHT = 600;
	
	private static BufferStrategy bufferStrategy;
	private static Canvas canvas;
	private static World world;
	
	private static final float SHOOT_COOLDOWN = 1.0f;
	private static float remainingShootCooldown;
	
	public static void run(BlockingQueue<UniversalDTO> fromServer, BlockingQueue<UniversalDTO> toServer) {
		initWindow();
		world = new World();
		
		initMouseClicks();
		
		System.out.println("running client shell");
		
		long lastFrameTime = System.nanoTime();
		while(true) {
			long thisFrameTime = System.nanoTime();
			float dt = (thisFrameTime - lastFrameTime) / 1_000_000_000.0f;
			
			if(dt > 0.0166666f) {
				System.err.println("Delta time was long: " + dt + "s");
			}
			
			UniversalDTO nextDTO;
			while((nextDTO = fromServer.poll()) != null) {
				world.handleDTO(nextDTO);
			}
			
			locateMouse();
			try {
				remainingShootCooldown = Math.max(0, remainingShootCooldown-dt);
				
				boolean[] wasd = KeyboardLord.getWASD();
				//float directionX = (float) (mouseX / Math.sqrt(mouseX*mouseX + mouseY*mouseY));
				//float directionY = (float) (mouseY / Math.sqrt(mouseX*mouseX + mouseY*mouseY));
				float directionX = ((wasd[3] ? 1f : 0f) - (wasd[1] ? 1f : 0f)) * (wasd[0] != wasd[2] ? 0.7f : 1f);
				float directionY = ((wasd[0] ? 1f : 0f) - (wasd[2] ? 1f : 0f)) * (wasd[1] != wasd[3] ? 0.7f : 1f);
				
				if(Float.isFinite(directionX)) {
					// If the mouse is exactly above the player, ignore the steer request
					toServer.put(new UniversalDTO(-1, "client", "request-steer", new float[] { directionX, directionY }));
					
					if(mousePressed && remainingShootCooldown == 0.0f) {
						remainingShootCooldown = SHOOT_COOLDOWN;
						toServer.put(new UniversalDTO(-1, "client", "request-shoot", new float[] { directionX, directionY }));
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			run(dt);
			
			lastFrameTime = thisFrameTime;
		}
	}

	private static void initMouseClicks() {
		Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {	
			@Override
			public void eventDispatched(AWTEvent awtEvent) {
				MouseEvent event = (MouseEvent) awtEvent;
				
				synchronized (Shell.class) {
					if(event.getID() == MouseEvent.MOUSE_PRESSED) {
						if(event.getButton() == MouseEvent.BUTTON1) {
							mousePressedNextFrame = true;
						} else {
							rightMousePressedNextFrame = true;
						}
					} else if(event.getID() == MouseEvent.MOUSE_RELEASED) {
						if(event.getButton() == MouseEvent.BUTTON1) {
							mousePressedNextFrame = false;
							mouseReleasedNextFrame = true;
						} else {
							rightMousePressedNextFrame = false;
							rightMouseReleasedNextFrame = true;
						}
					} else {
						if(event.getButton() == MouseEvent.BUTTON1) {
							mousePressedNextFrame = false;
							mouseReleasedNextFrame = false;
						} else {
							rightMousePressedNextFrame = false;
							rightMouseReleasedNextFrame = false;
						}
					}
				}
			}
		}, AWTEvent.MOUSE_EVENT_MASK);
	}

	private static void locateMouse() {
		Point mousePos = MouseInfo.getPointerInfo().getLocation();
		SwingUtilities.convertPointFromScreen(mousePos, canvas);
		mouseX = mousePos.x - WIDTH / 2.0f;
		mouseY = -(mousePos.y - HEIGHT / 2.0f);
		
		synchronized (Shell.class) {
			mousePressed = mousePressedNextFrame;
			mouseReleased = mouseReleasedNextFrame;
			
			rightMousePressed = rightMousePressedNextFrame;
			rightMouseReleased = rightMouseReleasedNextFrame;
		}
	}

	private static void initWindow() {
		canvas = new Canvas();
		canvas.setSize(WIDTH, HEIGHT);
		
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setIgnoreRepaint(true);
		frame.setResizable(false);
		frame.add(canvas);
		frame.pack();
		frame.setVisible(true);
		
		canvas.createBufferStrategy(2);
		canvas.addKeyListener(new KeyboardLord());
		bufferStrategy = canvas.getBufferStrategy();
	}

	private static void run(float dt) {
		Graphics2D g = (Graphics2D) bufferStrategy.getDrawGraphics();
		
		// Draw the background first
		g.setColor(new Color(0.8f, 0.8f, 0.8f));
		g.fillRect(0, 0, WIDTH, HEIGHT);
		
//		g.setColor(Color.DARK_GRAY);
//		g.drawLine(WIDTH/2, 0, WIDTH/2, HEIGHT);
//		g.drawLine(0, HEIGHT/2, WIDTH, HEIGHT/2);
		
		// Then let world render itself
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		world.draw(dt, g);

		g.dispose();
		bufferStrategy.show();
	}
	
}
