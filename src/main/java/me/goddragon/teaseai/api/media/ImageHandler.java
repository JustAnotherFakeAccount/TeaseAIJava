package me.goddragon.teaseai.api.media;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javafx.scene.image.ImageView;
import javafx.scene.media.MediaView;

import me.goddragon.teaseai.TeaseAI;
import me.goddragon.teaseai.utils.FileUtils;
import me.goddragon.teaseai.utils.TeaseLogger;
import me.goddragon.teaseai.utils.media.AnimatedGif;
import me.goddragon.teaseai.utils.media.Animation;
import me.goddragon.teaseai.utils.media.ImageUtils;

public class ImageHandler {
    public void showPicture(File file, int durationSeconds) {
        if (file == null) {
            TeaseAI.application.runOnUIThread(this::removePicture);
            return;
        }

        if (!file.exists()) {
            TeaseLogger.getLogger().log(
                    Level.SEVERE, "Picture " + file.getPath() + " does not exist.");
            return;
        }

        currentImageURL = file.getAbsolutePath();
        switchToImageView(file);
        if (durationSeconds > 0) {
            TeaseAI.application.sleepPossibleScripThread(durationSeconds * 1000L);
        }
    }

    public String getCurrentImageURL() {
        return currentImageURL;
    }

    public File tryGetImageFromURL(String url) {
        currentImageURL = url;

        final File downloadPath = getDownloadImagePathFromUrl(url);
        if (downloadPath.exists()) {
            return downloadPath;
        } else {
            if (tryDownloadImageFromURL(url, downloadPath)) {
                return downloadPath;
            }
        }

        return null;
    }

    private void switchToImageView(File file) {
        final AtomicBoolean readyFlag = new AtomicBoolean();

        TeaseAI.application.runOnUIThread(() -> {
            MediaView mediaView = TeaseAI.application.getController().getMediaView();

            if (mediaView.getMediaPlayer() != null) {
                mediaView.getMediaPlayer().stop();
            }

            ImageView imageView = TeaseAI.application.getController().getImageView();

            // Handle visibilities
            mediaView.setOpacity(0);
            imageView.setOpacity(1);

            // Stop any current image animation that might be running before displaying a new
            // picture
            stopCurrentAnimation();

            if (FileUtils.getExtension(file).equalsIgnoreCase("gif")) {
                currentAnimation = new AnimatedGif(file.toURI().toString());
                currentAnimation.setCycleCount(Integer.MAX_VALUE);
                currentAnimation.play(imageView);
            } else {
                ImageUtils.setImageInView(file, imageView);
            }
            
            synchronized (readyFlag) {
                readyFlag.set(true);
                readyFlag.notifyAll();
            }
        });

        awaitUiUpdate(readyFlag, "image");
    }

    /**
     * Blocks the calling (script) thread until the posted UI-thread work signals {@code readyFlag},
     * or until {@link #UI_SYNC_TIMEOUT_MILLIS} elapses. The guard is re-checked inside the monitor
     * to avoid a missed wakeup if the UI thread signals before we start waiting, and the wait is
     * bounded so a stuck UI thread can never freeze the script thread forever.
     */
    private static void awaitUiUpdate(AtomicBoolean readyFlag, String what) {
        final long deadline = System.currentTimeMillis() + UI_SYNC_TIMEOUT_MILLIS;
        synchronized (readyFlag) {
            while (!readyFlag.get()) {
                final long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    TeaseLogger.getLogger().log(Level.WARNING,
                            "Timed out waiting for " + what + " user interface to update");
                    return;
                }
                try {
                    readyFlag.wait(remaining);
                } catch (InterruptedException ex) {
                    TeaseLogger.getLogger().log(Level.WARNING,
                            "Thread interrupted while initialising " + what + " user interface");
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private File getDownloadImagePathFromUrl(String url) {
        try {
            URL parsed = new URL(url);
            String path = parsed.getPath();
            path = path.substring(path.lastIndexOf('/') + 1);
            return new File(MediaURL.IMAGE_DOWNLOAD_PATH + File.separator + path);
        } catch (java.net.MalformedURLException e) {
            // Fallback: strip at '?' manually
            String path = url.substring(url.lastIndexOf('/') + 1);
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            return new File(MediaURL.IMAGE_DOWNLOAD_PATH + File.separator + path);
        }
    }

    private boolean tryDownloadImageFromURL(String url, File downloadPath) {
        boolean wasSuccessful = false;

        TeaseLogger.getLogger().log(Level.FINER, String.format("Fetching url '%s'", url));

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.addRequestProperty("Referer", url);
            connection.addRequestProperty("Accept", "*/*");
            connection.addRequestProperty("User-Agent",
                    "Mozilla/5.0 (X11; Linux x86_64; rv:12.0) Gecko/20100101 Firefox/12.0");
            connection.connect();

            final int responseCode = connection.getResponseCode();
            final String responseMessage = connection.getResponseMessage();
            TeaseLogger.getLogger().log(Level.FINER,
                    String.format("Response code received %d '%s'", responseCode, responseMessage));

            if (responseCode == HttpURLConnection.HTTP_OK) {
                TeaseLogger.getLogger().log(Level.FINER,
                        String.format("Fetched %,d bytes of type '%s'",
                                connection.getContentLength(), connection.getContentType()));

                saveDownloadedImage(connection.getInputStream(), downloadPath);
                wasSuccessful = true;
            } else {
                TeaseLogger.getLogger().log(
                        Level.WARNING, "Unsupported response code, ignoring conent");
            }
        } catch (IOException ex) {
            TeaseLogger.getLogger().log(
                    Level.WARNING, "Unable to find image on url " + url + ": " + ex.getMessage());
        } catch (ClassCastException ex) {
            TeaseLogger.getLogger().log(
                    Level.SEVERE, "Url " + url + " does not appear to be an http connection");
        }

        return wasSuccessful;
    }

    private void saveDownloadedImage(InputStream inputStream, File downloadPath)
            throws IOException {
        final byte[] buffer = new byte[1024];
        try (InputStream in = new BufferedInputStream(inputStream)) {
            try (OutputStream out = new FileOutputStream(downloadPath)) {
                int bytesRead;
                while (-1 != (bytesRead = in.read(buffer))) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    private void removePicture() {
        ImageView imageView = TeaseAI.application.getController().getImageView();
        stopCurrentAnimation();
        imageView.setImage(null);
    }

    private void stopCurrentAnimation() {
        if (currentAnimation != null) {
            currentAnimation.stop();
            currentAnimation = null;
        }
    }

    private Animation currentAnimation = null;
    private String currentImageURL;

    private static final long UI_SYNC_TIMEOUT_MILLIS = 30_000L;
}
