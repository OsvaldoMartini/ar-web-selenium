package com.allinweb.ch.readersAndWriters;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;

@Slf4j
public class FileManager {

    private final File managedFile;

    public FileManager(String filePath) {
        managedFile = new File(filePath);
    }

    public FileManager deleteFileOnDisk() {
        if (managedFile.exists()) {
            managedFile.delete();
        }
        return this;
    }

    public FileManager createFileOnDisk() {
        if (!managedFile.exists()) {
            managedFile.mkdirs();
            deleteFileOnDisk();
            try {
                managedFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return this;
    }

    File getFile() {
        return managedFile;
    }
}
