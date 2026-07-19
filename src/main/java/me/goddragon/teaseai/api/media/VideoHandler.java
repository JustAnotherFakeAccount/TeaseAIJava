package me.goddragon.teaseai.api.media;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaView;

import me.goddragon.teaseai.TeaseAI;
import me.goddragon.teaseai.utils.TeaseLogger;

public class VideoHandler {
    public VideoHandler(Runnable asyncOnVideoPlaybackStarted, Runnable asyncOnVideoPlaybackEnded) {
        this.asyncOnVideoPlaybackStarted = asyncOnVideoPlaybackStarted;
        this.asyncOnVideoPlaybackEnded = asyncOnVideoPlaybackEnded;
    }

    public void play(Media media, boolean waitUntilPlaybackFinished) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }

        mediaPlayer = new SelfDisposingMediaPlayer(media, this::asyncOnPlaybackStarted, this::asyncOnPlaybackEnded);
        switchToVideoView(mediaPlayer);
        isWaitingForPlaybackToFinish = waitUntilPlaybackFinished;
        mediaPlayer.start();

        if (waitUntilPlaybackFinished) {
            while (mediaPlayer.isPlaying()) {
                TeaseAI.application.waitPossibleScripThread(0);
                TeaseAI.application.checkForNewResponses();
            }

            isWaitingForPlaybackToFinish = false;
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    public boolean isPlaying() {
        return (mediaPlayer != null) && (mediaPlayer.isPlaying());
    }

    private static void switchToVideoView(SelfDisposingMediaPlayer mediaPlayer) {
        final AtomicBoolean readyFlag = new AtomicBoolean();

        TeaseAI.application.runOnUIThread(() -> {
            final MediaView mediaView = TeaseAI.application.getController().getMediaView();
            final ImageView imageView = TeaseAI.application.getController().getImageView();
            final StackPane mediaViewBox = TeaseAI.application.getController().getMediaViewBox();

            mediaPlayer.bindToMediaView(mediaView);

            mediaView.setOpacity(1);
            imageView.setOpacity(0);

            mediaView.setPreserveRatio(true);
            mediaView.fitWidthProperty().bind(mediaViewBox.widthProperty());
            mediaView.fitHeightProperty().bind(mediaViewBox.heightProperty());

            synchronized (readyFlag) {
                readyFlag.set(true);
                readyFlag.notifyAll();
            }
        });

        // Re-check the guard inside the monitor to avoid a missed wakeup if the UI thread signals
        // before we start waiting, and bound the wait so a stuck UI thread can never freeze the
        // script thread forever.
        final long deadline = System.currentTimeMillis() + UI_SYNC_TIMEOUT_MILLIS;
        synchronized (readyFlag) {
            while (!readyFlag.get()) {
                final long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    TeaseLogger.getLogger().log(Level.WARNING,
                            "Timed out waiting for video user interface to update");
                    return;
                }
                try {
                    readyFlag.wait(remaining);
                } catch (InterruptedException ex) {
                    TeaseLogger.getLogger().log(Level.WARNING,
                            "Thread interrupted while initialising video user interface");
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void asyncOnPlaybackStarted() {
        if (asyncOnVideoPlaybackStarted != null) {
            asyncOnVideoPlaybackStarted.run();
        }
    }

    private void asyncOnPlaybackEnded() {
        if (asyncOnVideoPlaybackEnded != null) {
            asyncOnVideoPlaybackEnded.run();
        }
        
        if (isWaitingForPlaybackToFinish) {
            final Thread scriptThread = TeaseAI.getApplication().getScriptThread();
            synchronized (scriptThread) {
                scriptThread.notifyAll();
            }
        }
    }

    private SelfDisposingMediaPlayer mediaPlayer;
    private boolean isWaitingForPlaybackToFinish;
    private final Runnable asyncOnVideoPlaybackStarted;
    private final Runnable asyncOnVideoPlaybackEnded;

    private static final long UI_SYNC_TIMEOUT_MILLIS = 30_000L;
}
