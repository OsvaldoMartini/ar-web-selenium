package com.allinweb.ch.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;

public class ABRLogger {
    private static final String lock = "locked";

    private static volatile ABRLogger instance;
    private static FileHandler handler;

    private Logger _log;

    private ABRLogger() {
        String logPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_LOG);
        if (logPath.isBlank()) {
            JOptionPane.showMessageDialog(
                    null,
                    "The configuration of the log path has not been set. Please set the configuration for"
                            + " the log path",
                    "Log configuration not set",
                    JOptionPane.WARNING_MESSAGE);
        } else {
            File logDirectory = new File(logPath);
            if (!logDirectory.exists()) {
                logDirectory.mkdirs();
            }
            try {
                handler = new FileHandler(logPath + ABRConstants.FILE_NAME_SCANNER_LOG);
                FileOutputStream fileOutputStream =
                        new FileOutputStream(logPath + ABRConstants.FILE_NAME_SCANNER_OUTPUT_LOG);
                PrintStream printStream = new PrintStream(fileOutputStream);
                System.setOut(printStream);
                System.setErr(printStream);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                        null,
                        "An error has occurred during the creation of the logger: Message: " + e.getMessage()
                                + " Cause: " + e.getCause(),
                        "Error in the creation of the logger",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static <T> ABRLogger getInstance(Class<T> forClazz) {
        synchronized (lock) {
            if (instance == null) {
                instance = new ABRLogger();
            }
        }
        instance.setLoggingClass(forClazz);
        return instance;
    }

    private void setLoggingClass(Class<?> clazz) {
        _log = Logger.getLogger(clazz.getName());
        _log.addHandler(handler);
        String logLevel = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.LOG_LEVEL);
        switch (logLevel) {
            case "SEVERE":
                _log.setLevel(Level.SEVERE);
                break;
            case "WARNING":
                _log.setLevel(Level.WARNING);
                break;
            case "INFO":
                _log.setLevel(Level.INFO);
                break;
            case "CONFIG":
                _log.setLevel(Level.CONFIG);
                break;
            case "FINE":
                _log.setLevel(Level.FINE);
                break;
            case "FINER":
                _log.setLevel(Level.FINER);
                break;
            case "FINEST":
                _log.setLevel(Level.FINEST);
                break;
            case "ALL":
                _log.setLevel(Level.ALL);
                break;
            case "OFF":
                _log.setLevel(Level.OFF);
                break;
            default:
                System.out.println("Valore per log_level non previsto: " + logLevel + ". Imposto a ALL per default");
                _log.setLevel(Level.ALL);
        }
    }

    private void logWithExtraction(String msg, Level level) {
        try {
            // Example extraction logic: log a substring if the message is not null
            String extractedMsg = (msg != null) ? msg.substring(0, Math.min(msg.length(), 50)) : null;
            if (extractedMsg == null) {
                _log.log(level, "The object Logged is null");
            } else {
                _log.log(level, extractedMsg);
            }
        } catch (Exception e) {
            _log.log(level, "The object Logged is null");
        }
    }

    public void info(String msg) {
        logWithExtraction(msg, Level.INFO);
    }

    public void warning(String msg) {
        logWithExtraction(msg, Level.WARNING);
    }

    public void severe(String msg) {
        logWithExtraction(msg, Level.SEVERE);
    }

    public void config(String msg) {
        logWithExtraction(msg, Level.CONFIG);
    }

    public void fine(String msg) {
        logWithExtraction(msg, Level.FINE);
    }

    public void finer(String msg) {
        logWithExtraction(msg, Level.FINER);
    }

    public void finest(String msg) {
        logWithExtraction(msg, Level.FINEST);
    }
}
