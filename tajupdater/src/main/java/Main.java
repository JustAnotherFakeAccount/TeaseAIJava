import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public class Main {
    public static void main(String[] args) {
        TeaseLogger.getLogger().log(Level.INFO, "Launching TAJ Updater...");
        File updateFolder = new File("Updates");
        File update = null;
        if (updateFolder.exists() && updateFolder.isDirectory() && updateFolder.listFiles().length > 0) {
            update = getLatestFileFromDir(updateFolder);
            if (update != null) {
                TeaseLogger.getLogger().log(Level.INFO, "Copying newest update...");
                try {
                    Thread.sleep(500L);
                    Files.copy(update.toPath(), new File(update.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Thread.sleep(500L);
                    Files.delete(update.toPath());
                } catch (FileSystemException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            TeaseLogger.getLogger().log(Level.SEVERE, "Missing TAJ Updates folder! Exiting.");
        }

        if (update == null) {
            File[] files = new File("").getParentFile().listFiles();
            if (files == null || files.length == 0) {
                System.exit(0);
                return;
            }
            for (int i = 1; i < files.length; ++i) {
                if (!files[i].getName().endsWith(".jar") || files[i].getName().toLowerCase().contains("update")) continue;
                update = files[i];
            }
        }

        try {
            TeaseLogger.getLogger().log(Level.INFO, "Launching TAJ...");
            Runtime.getRuntime().exec("java -jar " + update.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    private static File getLatestFileFromDir(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }
        File lastModifiedFile = files[0];
        for (int i = 1; i < files.length; ++i) {
            if (lastModifiedFile.lastModified() >= files[i].lastModified()) continue;
            lastModifiedFile = files[i];
        }
        return lastModifiedFile;
    }
}
