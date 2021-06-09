package pacovfor$jbc.config;

import java.io.File;

public class Config {

    public static String cfgsDir;
    public static String logDir;

    static {
        updateCfgsDir();
        updateLogDir();
    }

    public static void updateCfgsDir() {
        cfgsDir = System.getProperty("cfgsDir", "cfgs");
    }

    public static void updateLogDir() {
        logDir = System.getProperty("logDir", "logs");
    }

    public static void tryCreatingFilePathOrElseThrowExc(String filePath) {
        File newFile = new File(filePath);
        if (!newFile.mkdirs()) {
            throw new IllegalArgumentException("filepath: " + filePath + " could not be created");
        }
    }

    public static void checkIfFileExistsElseCreateFile(String filePath) {
        File newFile = new File(filePath);
        if (newFile.exists()) {
            return;
        }

        tryCreatingFilePathOrElseThrowExc(filePath);
    }
}
