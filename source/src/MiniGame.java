import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferStrategy;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

public class MiniGame {
	
	private static final int WIDTH = 800;
	private static final int HEIGHT = 600;
	private static BufferStrategy bufferStrategy;

	public static void main(String[] args) {
		initWindow();
		
		while(true) {
			run();
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

	private static void run() {
		Graphics2D g = (Graphics2D) bufferStrategy.getDrawGraphics();
		
		draw(g);

		g.dispose();
		bufferStrategy.show();
	}

	private static void draw(Graphics2D g) {
		int color = (int) Math.abs(255 * Math.sin(System.currentTimeMillis() / 1000.0));
		g.setColor(new Color(color, color, color));
		g.fillRect(0, 0, WIDTH, HEIGHT);
	}
	
}
