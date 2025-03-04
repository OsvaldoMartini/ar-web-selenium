package com.allinweb.ch.licence;

import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.ShlObj;

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
import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.ShlObj;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class LicenseManager {
    private static final String KEY = "0123456789abcdef"; // 16-byte key for AES-128

    public static void generateRequestFile(String ownerLicence) throws Exception {
        String encryptedRequest = encrypt(ownerLicence + "|" + SystemDetails.getSystemDetails(), KEY);

        // Retrieve Desktop path using Windows API (JNA)
        char[] path = new char[ShlObj.CSIDL_DESKTOPDIRECTORY];
        if (Shell32.INSTANCE.SHGetFolderPathW(null, ShlObj.CSIDL_DESKTOPDIRECTORY, null, ShlObj.SHGFP_TYPE_CURRENT, path) != 0) {
            throw new IOException("Failed to get desktop directory.");
        }

        String desktopPath = Native.toString(path).trim(); // Trim to remove extra null characters

        // Create the file in the desktop directory
        File newFile = new File(desktopPath, "ARWeb 1.1.0.request");

        // Write the encrypted data to the file
        try (FileWriter writer = new FileWriter(newFile)) {
            writer.write(encryptedRequest);
            System.out.println("File saved to: " + newFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean importResponseFile() throws Exception {
        Path responsePath = Paths.get(System.getProperty("user.home") + "/Desktop/ARWeb 1.1.0.response");
        if (Files.exists(responsePath)) {
            String content = Files.readString(responsePath);
            String decryptedResponse = decrypt(content, KEY);
            generateLicFile(decryptedResponse);
            return true;
        }
        return false;
    }

    private static void generateLicFile(String data) throws Exception {
        Files.writeString(Paths.get("ARWeb.lic"), encrypt(data, KEY));
    }

    public static String encrypt(String data, String key) throws Exception {
        Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);
        byte[] encrypted = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decrypt(String encryptedData, String key) throws Exception {
        Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, aesKey);
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decrypted);
    }

    public static LicenceVal checkLicenseFile() throws Exception {
        Path licPath = Paths.get("ARWeb.lic");
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
        if (!Files.exists(licPath)) {
            return "noFileFound"; // File is absent
        } else {
            String licContent = new String(Files.readAllBytes(licPath));
            String decryptedContent = decrypt(licContent, KEY);
            return decryptedContent;
        }
    }

    private static LicenceVal validateLicense(String decryptedContent) {
        // Suppose the decrypted content is formatted as "PCID|expiryDate" (e.g., "PC12345|2025-12-31")
        String[] parts = decryptedContent.split("\\|");
        if (parts.length != 4) return LicenceVal.MISSING; // Invalid data format

        System.out.println(parts);

        String pcID = parts[0];
        String domainName = parts[1];
        String userName = parts[2];
        LocalDate expiryDate = LocalDate.parse(parts[3], DateTimeFormatter.ISO_LOCAL_DATE);
        // System.out.println(" expiryDate is " + expiryDate);
        // Check if the PC ID matches and the current date is before the expiry date
        if (LocalDate.now().isAfter(expiryDate)) return LicenceVal.EXPIRED; // date has expired

        if (!SystemDetails.getSystemComputerName().equals(pcID)) return LicenceVal.PCNOTMATCH;
        if (!SystemDetails.getSystemDomainName().equals(domainName)) return LicenceVal.DOMAINNOTMATCH;
        if (!SystemDetails.getSystemUserName().equals(userName)) return LicenceVal.USRNOTMATCH; // PC ID does not match
        return LicenceVal.VALID; // License is valid
    }

    public void genereteResponseFile(String decryptedContent, int numDays) throws Exception {
        // Suppose the decrypted content is formatted as "PCID|expiryDate" (e.g., "PC12345|2025-12-31")
        try {
            String[] parts = decryptedContent.split("\\|");

            String pcID = parts[1];
            String domainName = parts[2];
            String userName = parts[3];
            // LocalDate expiryDate = LocalDate.parse(parts[4], DateTimeFormatter.ISO_LOCAL_DATE);

            LocalDate expiryDate = LocalDate.parse(parts[4], DateTimeFormatter.ISO_LOCAL_DATE);
            expiryDate = expiryDate.plusDays(numDays);

            String encryptedResponse = encrypt(pcID + "|" + domainName + "|" + userName + "|" + expiryDate, KEY);
            Files.writeString(
                    Paths.get(System.getProperty("user.home") + "/Desktop/ARWeb 1.1.0.response"), encryptedResponse);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }
}
