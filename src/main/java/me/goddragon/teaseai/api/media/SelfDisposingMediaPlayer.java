package me.goddragon.teaseai.api.media;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

import me.goddragon.teaseai.utils.TeaseLogger;

public class SelfDisposingMediaPlayer {
    public SelfDisposingMediaPlayer(Media media, Runnable asyncCallbackOnPlaybackStarted,
            Runnable asyncCallbackOnPlaybackEnded) {
        this.asyncCallbackOnPlaybackStarted = asyncCallbackOnPlaybackStarted;
        this.asyncCallbackOnPlaybackEnded = asyncCallbackOnPlaybackEnded;
        mediaPlayer = tryCreateSelfDisposingMediaPlayer(media);
    }

    public void start() {
        if (mediaPlayer != null) {
            stop();
            synchronized (playbackStatus) {
                playbackStatus.set(PlaybackStatus.STARTING);
                mediaPlayer.play();
                while (playbackStatus.get() == PlaybackStatus.STARTING) {
                    try {
                        playbackStatus.wait();
                    } catch (InterruptedException ex) {
                        TeaseLogger.getLogger().log(Level.WARNING,
                                "Thread interrupted while waiting for media to start");
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            synchronized (playbackStatus) {
                while (playbackStatus.get() != PlaybackStatus.NOT_PLAYING) {
                    try {
                        playbackStatus.wait();
                    } catch (InterruptedException ex) {
                        TeaseLogger.getLogger().log(Level.WARNING,
                                "Thread interrupted while waiting for media to stop");
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    public boolean isPlaying() {
        return (mediaPlayer != null) && (playbackStatus.get() == PlaybackStatus.PLAYING);
    }

    private MediaPlayer tryCreateSelfDisposingMediaPlayer(Media media) {
        try {
            final MediaPlayer newMediaPlayer = new MediaPlayer(media);
            newMediaPlayer.setOnPlaying(this::asyncOnPlaybackStarted);
            newMediaPlayer.setOnEndOfMedia(this::asyncOnPlaybackEnded);
            newMediaPlayer.setOnError(this::asyncOnPlaybackEnded);
            newMediaPlayer.setOnHalted(this::asyncOnPlaybackEnded);
            newMediaPlayer.setOnStopped(this::asyncOnPlaybackEnded);
            return newMediaPlayer;

        } catch (MediaException ex) {
            TeaseLogger.getLogger().log(
                    Level.SEVERE, "Failed to create MediaPlayer: " + ex.getMessage());
        }

        return null;
    }

    private void asyncOnPlaybackStarted() {
        if (asyncCallbackOnPlaybackStarted != null) {
            asyncCallbackOnPlaybackStarted.run();
        }

        playbackStatus.set(PlaybackStatus.PLAYING);
        synchronized (playbackStatus) {
            playbackStatus.notifyAll();
        }
    }

    private void asyncOnPlaybackEnded() {
        // Release any threads waiting in start()/stop() first, so a slow or stuck
        // native dispose can never leave them blocked forever.
        playbackStatus.set(PlaybackStatus.NOT_PLAYING);
        synchronized (playbackStatus) {
            playbackStatus.notifyAll();
        }

        // Multiple terminal events (onEndOfMedia, onStopped, onError, onHalted) can all
        // fire for the same player. Dispose exactly once, and never synchronously from
        // inside the media event callback - disposing the native gstreamer pipeline while
        // it is dispatching its own event, or disposing it twice, deadlocks in gstDispose.
        if (disposed.compareAndSet(false, true)) {
            Platform.runLater(mediaPlayer::dispose);
        }

        if (asyncCallbackOnPlaybackEnded != null) {
            asyncCallbackOnPlaybackEnded.run();
        }
    }

    public void bindToMediaView(MediaView mediaView) {
        mediaView.setMediaPlayer(mediaPlayer);
    }

    public enum PlaybackStatus { NOT_PLAYING, STARTING, PLAYING }

    private final Runnable asyncCallbackOnPlaybackStarted;
    private final Runnable asyncCallbackOnPlaybackEnded;
    private final MediaPlayer mediaPlayer;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicReference<PlaybackStatus> playbackStatus =
            new AtomicReference<>(PlaybackStatus.NOT_PLAYING);
}
