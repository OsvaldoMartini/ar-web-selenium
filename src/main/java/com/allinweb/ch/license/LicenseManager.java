package com.allinweb.ch.license;

import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.common.base.Strings;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.filechooser.FileSystemView;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LicenseManager {
    private static final String KEY = "0123456789abcdef"; // 16-byte key for AES-128
    private static final PerformMessage performMessage;
    private static final ARPropertyManager arPropertyManager;

    static {
        performMessage = PerformMessage.getInstance();
        arPropertyManager = ARPropertyManager.getInstance();
    }

    public static void generateRequestFile(String fileFolder, String ownerLicence) throws Exception {
        String encryptedRequest = encrypt(ownerLicence + "|" + SystemDetails.getSystemDetails(), KEY);
        String fileName = "ARWeb 1.1.0.request";
        File newFile = new File(fileFolder, fileName);

        // Write the encrypted data to the file
        try (FileWriter writer = new FileWriter(newFile)) {
            writer.write(encryptedRequest);
            log.info("File saved to: " + newFile.getAbsolutePath());
        } catch (IOException error) {
            log.warn("Error reading/writing to the file: " + error.getMessage());
            // You already handle errors from the caller UI
        }
    }

    public static boolean importResponseFile(String filePath) throws Exception {
        File responseFile = new File(filePath);

        if (responseFile.exists()) {
            String content = Files.readString(responseFile.toPath());
            String decryptedResponse = decrypt(content, KEY);
            generateLicFile(decryptedResponse);
            return true;
        }

        return false;
    }

    private static void generateLicFile(String data) throws Exception {
        String licensePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);
        if (Strings.isNullOrEmpty(licensePath)) {
            licensePath = System.getProperty("user.dir");
        }

        // Cross-platform path
        Path licPath = Paths.get(licensePath, "ARWeb.lic");
        Files.writeString(licPath, encrypt(data, KEY));
        log.info("License file written to: {}", licPath.toAbsolutePath());
    }

    public static String encrypt(String data, String key) throws Exception {
        Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);
        byte[] encrypted = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decrypt(String encryptedData, String key) throws Exception {
        try {
            Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, aesKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            return new String(decrypted);
        } catch (Exception error) {
            log.error("An error occurred while decrypting the license file.", error);
            performMessage.errorMessage(
                    "An error occurred while decrypting the license file.",
                    "File Name:",
                    "ARWeb.lic",
                    "Please verify if the file is corrupted or tampered.",
                    null,
                    0);
        }
        return null;
    }

    public static LicenceVal checkLicenseFile(String licensePath) throws Exception {
        // licensePath is the folder; file is ARWeb.lic inside it
        Path licPath = Paths.get(licensePath, "ARWeb.lic");
        if (!Files.exists(licPath)) {
            return LicenceVal.MISSING; // File is absent
        } else {
            String licContent = new String(Files.readAllBytes(licPath));
            String decryptedContent = decrypt(licContent, KEY);
            return validateLicense(decryptedContent);
        }
    }

    public static String getDecryptedResponseFile(String requestFile) throws Exception {
        Path licPath = Paths.get(requestFile);

        String fileName = licPath.getFileName().toString();

        if (!fileName.endsWith(".request")) {
            performMessage.errorMessage(
                    "Invalid file selected!",
                    "Must have a '.request' extension.",
                    "File selected:",
                    fileName,
                    null,
                    0);
            return "Invalid file selected";
        }

        if (!Files.exists(licPath)) {
            return "noFileFound"; // File is absent
        } else {
            String licContent = new String(Files.readAllBytes(licPath));
            String decryptedContent = decrypt(licContent, KEY);
            return decryptedContent;
        }
    }

    private static LicenceVal validateLicense(String decryptedContent) {
        if (decryptedContent == null) {
            return LicenceVal.MISSING;
        }

        // Suppose the decrypted content is formatted as "PCID|domain|userName|expiryDate"
        String[] parts = decryptedContent.split("\\|");
        if (parts.length != 4) return LicenceVal.MISSING; // Invalid data format

        String pcID = parts[0];
        String domainName = parts[1];
        String userName = parts[2];
        LocalDate expiryDate = LocalDate.parse(parts[3], DateTimeFormatter.ISO_LOCAL_DATE);
        String formatted = expiryDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        arPropertyManager.setProperty(ARPropertyEnum.EXPIRATION.getValue(), formatted);

        if (LocalDate.now().isAfter(expiryDate)) return LicenceVal.EXPIRED;        // date has expired
        if (!SystemDetails.getSystemComputerName().equals(pcID)) return LicenceVal.PCNOTMATCH;
        if (!SystemDetails.getSystemDomainName().equals(domainName)) return LicenceVal.DOMAINNOTMATCH;
        if (!SystemDetails.getSystemUserName().equals(userName)) return LicenceVal.USRNOTMATCH;

        return LicenceVal.VALID; // License is valid
    }

    /**
     * Cross-platform Desktop directory resolution.
     * Tries <user.home>/Desktop, then OS home dir, finally current working dir.
     */
    private static String getDesktopDir() {
        // 1) Try <user.home>/Desktop
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            File desktop = new File(userHome, "Desktop");
            if (desktop.exists() && desktop.isDirectory()) {
                return desktop.getAbsolutePath();
            }
        }

        // 2) Fallback: OS "home" / user root via FileSystemView
        File home = FileSystemView.getFileSystemView().getHomeDirectory();
        if (home != null && home.exists()) {
            return home.getAbsolutePath();
        }

        // 3) Final fallback: current working directory
        return System.getProperty("user.dir");
    }

    public String genereteResponseFile(String decryptedContent, int numDays) {
        try {
            String[] parts = decryptedContent.split("\\|");

            String pcID = parts[1];
            String domainName = parts[2];
            String userName = parts[3];

            LocalDate expiryDate = LocalDate.parse(parts[4], DateTimeFormatter.ISO_LOCAL_DATE);
            expiryDate = expiryDate.plusDays(numDays);

            String encryptedResponse = encrypt(pcID + "|" + domainName + "|" + userName + "|" + expiryDate, KEY);

            // Cross-platform: write response file to Desktop / home / working dir
            String desktopPath = getDesktopDir();
            File newFile = new File(desktopPath, "ARWeb 1.1.0.response");

            try (FileWriter writer = new FileWriter(newFile)) {
                writer.write(encryptedResponse);
                log.info("File saved to: " + newFile.getAbsolutePath());
                return "File creation success";
            } catch (IOException e) {
                log.error("Error writing to file: " + e.getMessage());
                performMessage.errorMessage(
                        "Error writing to the file!",
                        "File Name:",
                        "ARWeb 1.1.0.response",
                        "Please verify that you have permission to read/write to the Desktop or selected folder.",
                        null,
                        0);
                return "Denied permission to read/write";
            }

        } catch (Exception error) {
            log.error("Error generating response file", error);
            performMessage.errorMessage(
                    "Error generating Response  file!",
                    "Please verify that you have permission to read/write to the Desktop or selected folder.",
                    "Please verify if the file is corrupted or tampered.",
                    null,
                    null,
                    0);

            return "file is corrupted or tampered.";
        }
    }
}
