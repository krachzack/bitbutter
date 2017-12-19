package deuterium;

import java.awt.AWTEvent;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.concurrent.locks.ReentrantLock;

public class KeyInput implements AWTEventListener {
	private static final boolean[] WASD = new boolean[4];
	private static final ReentrantLock lock = new ReentrantLock();

	public static boolean[] getWASD() {
		lock.lock();
		try {
			return WASD.clone();
		} finally {
			lock.unlock();
		}
	}
	
	public void keyPressed(KeyEvent e) {
		System.out.println(e.getKeyChar());
		lock.lock();
		try {
			switch (e.getKeyCode()) {
			case KeyEvent.VK_UP: WASD[0] = true; break;
			case KeyEvent.VK_LEFT: WASD[1] = true; break;
			case KeyEvent.VK_DOWN: WASD[2] = true; break;
			case KeyEvent.VK_RIGHT: WASD[3] = true; break;
			case KeyEvent.VK_W: WASD[0] = true; break;
			case KeyEvent.VK_A: WASD[1] = true; break;
			case KeyEvent.VK_S: WASD[2] = true; break;
			case KeyEvent.VK_D: WASD[3] = true; break;
			default: break;
			}
		} finally {
			lock.unlock();
		}
	}

	public void keyReleased(KeyEvent e) {
		lock.lock();
		try {
			switch (e.getKeyCode()) {
			case KeyEvent.VK_UP: WASD[0] = false; break;
			case KeyEvent.VK_LEFT: WASD[1] = false; break;
			case KeyEvent.VK_DOWN: WASD[2] = false; break;
			case KeyEvent.VK_RIGHT: WASD[3] = false; break;
			case KeyEvent.VK_W: WASD[0] = false; break;
			case KeyEvent.VK_A: WASD[1] = false; break;
			case KeyEvent.VK_S: WASD[2] = false; break;
			case KeyEvent.VK_D: WASD[3] = false; break;
			default: break;
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void eventDispatched(AWTEvent event) {
		KeyEvent evt = (KeyEvent) event;
		if(evt.getID() == KeyEvent.KEY_PRESSED) {
			keyPressed(evt);
		} else if(evt.getID() == KeyEvent.KEY_RELEASED) {
			keyReleased(evt);
		}
		evt.consume();
	}
}
