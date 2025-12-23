package GUI;

import javafx.scene.media.MediaPlayer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AudioManager {

    private static final Set<MediaPlayer> players = Collections.synchronizedSet(new LinkedHashSet<>());

    private AudioManager() {}

    public static void register(MediaPlayer mp) {
        if (mp == null) return;
        players.add(mp);
    }

    public static void unregister(MediaPlayer mp) {
        if (mp == null) return;
        players.remove(mp);
    }

    public static void pauseAll() {
        synchronized (players) {
            for (MediaPlayer mp : players) {
                try {
                    mp.pause();
                } catch (Throwable ignored) {}
            }
        }
    }

    public static void resumeAll() {
        synchronized (players) {
            for (MediaPlayer mp : players) {
                try {
                    mp.play();
                } catch (Throwable ignored) {}
            }
        }
    }

    public static void stopAll() {
        synchronized (players) {
            for (MediaPlayer mp : players) {
                try {
                    mp.stop();
                    mp.dispose();
                } catch (Throwable ignored) {}
            }
            players.clear();
        }
    }
}
