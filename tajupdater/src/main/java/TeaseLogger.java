import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

public class TeaseLogger {
    private static TeaseLogger logger = new TeaseLogger();
    private PrintStream outStream;
    private boolean fileLog = true;

    public TeaseLogger() {
        if (!fileLog) {
            return;
        }
        try {
            if (!new File("Logs").exists()) {
                new File("Logs").mkdir();
            }
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy H-mm-ss");
            Date date = new Date();
            outStream = new PrintStream(new FileOutputStream("Logs" + File.separator + "updater-log-" + dateFormat.format(date) + ".txt"));
            System.setOut(outStream);
            System.setErr(outStream);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String formatMessage(String message, Level level) {
        Date dat = new Date();
        dat.setTime(System.currentTimeMillis());
        if (message.trim().isEmpty()) {
            return "";
        }
        String levelString = level.toString();
        if (level == Level.FINE) {
            levelString = "CHAT";
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm:ss a");
        return dateFormat.format(dat) + " " + levelString + ": " + message.replaceAll("[\\x00]", "");
    }

    public void log(Level level, String message) {
        log(level, message, level == Level.SEVERE);
    }

    public void log(Level level, String message, Exception e) {
        String logMessage = formatMessage(message, level);
        if (!logMessage.isEmpty()) {
            System.out.println(logMessage);
        }
    }

    public void log(Level level, String message, boolean stacktrace) {
        String logMessage = formatMessage(message, level);
        if (!logMessage.isEmpty()) {
            System.out.println(logMessage);
        }
    }

    public static TeaseLogger getLogger() {
        return logger;
    }

    public static void setLogger(TeaseLogger logger) {
        TeaseLogger.logger = logger;
    }
}
