package com.allinweb.ch.facade;

import com.sun.jna.platform.win32.AccCtrl;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.ACCESS_ACEStructure;
import com.sun.jna.platform.win32.WinNT.ACCESS_ALLOWED_ACE;
import com.sun.jna.platform.win32.WinNT.ACE_HEADER;
import com.sun.jna.platform.win32.WinNT.ACL;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.platform.win32.WinNT.PSID;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/** Applies and verifies private operating-system permissions for Page Scanner artifacts. */
@Slf4j
public final class PageScanSnapshotFileSecurity {

    private static final String LOCAL_SYSTEM_SID = "S-1-5-18";
    private static final String BUILTIN_ADMINISTRATORS_SID = "S-1-5-32-544";
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);
    private static final AtomicBoolean UNSUPPORTED_NON_WINDOWS_REPORTED = new AtomicBoolean();
    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .startsWith("windows");

    private PageScanSnapshotFileSecurity() {}

    /**
     * Creates or verifies every directory below one already-owned snapshot root without ever
     * traversing a symbolic link. Each level receives the private policy before the next level is
     * used, closing the legacy-tree gap left by a single createDirectories call.
     */
    static void createPrivateDirectories(Path root, Path directory) throws IOException {
        if (root == null || directory == null) {
            throw new IOException("The Page Scanner artifact directory is unavailable");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        if (!normalizedDirectory.startsWith(normalizedRoot)) {
            throw new IOException("The Page Scanner artifact directory escaped its root");
        }
        secureDirectory(normalizedRoot);
        Path cursor = normalizedRoot;
        for (Path segment : normalizedRoot.relativize(normalizedDirectory)) {
            cursor = cursor.resolve(segment);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
                requireKind(cursor, true);
            } else {
                Files.createDirectory(cursor);
            }
            secureDirectory(cursor);
        }
    }

    /** Verifies and secures an existing descendant chain without following a link or junction. */
    static void requirePrivateDirectoryTree(Path root, Path directory) throws IOException {
        if (root == null || directory == null) {
            throw new IOException("The Page Scanner artifact directory is unavailable");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        if (!normalizedDirectory.startsWith(normalizedRoot)) {
            throw new IOException("The Page Scanner artifact directory escaped its root");
        }
        Path realRoot = normalizedRoot.toRealPath();
        secureDirectory(normalizedRoot);
        Path cursor = normalizedRoot;
        for (Path segment : normalizedRoot.relativize(normalizedDirectory)) {
            cursor = cursor.resolve(segment);
            requireKind(cursor, true);
            secureDirectory(cursor);
        }
        Path realDirectory = normalizedDirectory.toRealPath();
        if (!realDirectory.startsWith(realRoot)) {
            throw new IOException("The Page Scanner artifact directory escaped its real root");
        }
    }

    /** Secures one real directory and makes its private grants inheritable by children. */
    public static void secureDirectory(Path directory) throws IOException {
        requireKind(directory, true);
        if (WINDOWS) {
            try {
                WindowsPrivateAcl.requirePrivate(directory, true);
                return;
            } catch (IOException requiresRepair) {
                // Replace inherited or unexpected grants with the exact private policy below.
            }
            WindowsPrivateAcl.apply(directory, true);
            WindowsPrivateAcl.requirePrivate(directory, true);
            return;
        }
        PosixFileAttributeView view = Files.getFileAttributeView(
                directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            reportUnsupportedNonWindows(directory);
            return;
        }
        if (!Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS)
                .equals(PRIVATE_DIRECTORY_PERMISSIONS)) {
            Files.setPosixFilePermissions(directory, PRIVATE_DIRECTORY_PERMISSIONS);
        }
        requirePrivatePosix(directory, PRIVATE_DIRECTORY_PERMISSIONS);
    }

    /** Secures each immutable capture file and re-verifies its containing directory. */
    public static void secureCaptureDirectory(Path directory) throws IOException {
        secureDirectory(directory);
        try (var entries = Files.list(directory)) {
            for (Path entry : entries.toList()) secureFile(entry);
        }
        requirePrivateDirectory(directory);
    }

    /** Hardens a pre-existing snapshot root before recovery or reads use its contents. */
    public static void secureExistingRoot(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        if (WINDOWS) {
            // SetNamedSecurityInfo propagates the exact inheritable DACL to existing unprotected
            // descendants. Selected legacy captures are additionally protected when read.
            secureDirectory(root);
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                BasicFileAttributes attributes = Files.readAttributes(
                        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isDirectory()) secureDirectory(path);
                else if (attributes.isRegularFile()) secureFile(path);
                else throw new IOException("The Page Scanner artifact tree contains an unsafe entry");
            }
        }
    }

    /** Refuses a capture whose effective directory policy is not private. */
    public static void requirePrivateDirectory(Path directory) throws IOException {
        requireKind(directory, true);
        if (WINDOWS) {
            WindowsPrivateAcl.requirePrivate(directory, true);
            return;
        }
        PosixFileAttributeView view = Files.getFileAttributeView(
                directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            reportUnsupportedNonWindows(directory);
            return;
        }
        requirePrivatePosix(directory, PRIVATE_DIRECTORY_PERMISSIONS);
    }

    static void secureFile(Path file) throws IOException {
        requireKind(file, false);
        if (WINDOWS) {
            try {
                WindowsPrivateAcl.requirePrivate(file, false);
                return;
            } catch (IOException requiresRepair) {
                // Replace inherited or unexpected grants with the exact private policy below.
            }
            WindowsPrivateAcl.apply(file, false);
            WindowsPrivateAcl.requirePrivate(file, false);
            return;
        }
        PosixFileAttributeView view = Files.getFileAttributeView(
                file, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            reportUnsupportedNonWindows(file);
            return;
        }
        if (!Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS)
                .equals(PRIVATE_FILE_PERMISSIONS)) {
            Files.setPosixFilePermissions(file, PRIVATE_FILE_PERMISSIONS);
        }
        requirePrivatePosix(file, PRIVATE_FILE_PERMISSIONS);
    }

    private static void requireKind(Path path, boolean directory) throws IOException {
        if (path == null) throw new IOException("The Page Scanner artifact path is unavailable");
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        boolean expected = directory ? attributes.isDirectory() : attributes.isRegularFile();
        if (!expected || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("The Page Scanner artifact path is unsafe");
        }
    }

    private static void requirePrivatePosix(Path path, Set<PosixFilePermission> expected)
            throws IOException {
        Set<PosixFilePermission> actual = Files.getPosixFilePermissions(
                path, LinkOption.NOFOLLOW_LINKS);
        if (!actual.equals(expected)) {
            throw new IOException("The Page Scanner artifact permissions are not private");
        }
    }

    private static void reportUnsupportedNonWindows(Path path) {
        if (UNSUPPORTED_NON_WINDOWS_REPORTED.compareAndSet(false, true)) {
            log.warn(
                    "Private POSIX permissions are unavailable for Page Scanner artifacts on {}. "
                            + "This compatibility fallback is not permitted on Windows.",
                    path.getFileSystem().provider().getScheme());
        }
    }

    /** Loaded only on Windows, so non-Windows development never initializes native Win32 APIs. */
    private static final class WindowsPrivateAcl {

        private static final int DIRECTORY_INHERITANCE =
                Byte.toUnsignedInt(WinNT.OBJECT_INHERIT_ACE)
                        | Byte.toUnsignedInt(WinNT.CONTAINER_INHERIT_ACE);

        private WindowsPrivateAcl() {}

        private static void apply(Path path, boolean directory) throws IOException {
            List<PrivateSid> principals = principals();
            int aclSize = new ACL().size();
            for (PrivateSid principal : principals) {
                aclSize += Advapi32Util.alignOnDWORD(
                        Advapi32Util.getAceSize(principal.sid().getBytes().length));
            }
            ACL acl = new ACL(aclSize);
            if (!Advapi32.INSTANCE.InitializeAcl(acl, aclSize, WinNT.ACL_REVISION)) {
                throw windowsFailure("initialize the Page Scanner private ACL");
            }
            int inheritance = directory ? DIRECTORY_INHERITANCE : 0;
            for (PrivateSid principal : principals) {
                if (!Advapi32.INSTANCE.AddAccessAllowedAceEx(
                        acl,
                        WinNT.ACL_REVISION,
                        inheritance,
                        WinNT.FILE_ALL_ACCESS,
                        principal.sid())) {
                    throw windowsFailure("build the Page Scanner private ACL");
                }
            }
            int result = Advapi32.INSTANCE.SetNamedSecurityInfo(
                    windowsPath(path),
                    AccCtrl.SE_OBJECT_TYPE.SE_FILE_OBJECT,
                    WinNT.DACL_SECURITY_INFORMATION | WinNT.PROTECTED_DACL_SECURITY_INFORMATION,
                    null,
                    null,
                    acl.getPointer(),
                    null);
            if (result != WinError.ERROR_SUCCESS) {
                throw new IOException(
                        "Windows refused the Page Scanner private ACL (error " + result + ")");
            }
        }

        private static void requirePrivate(Path path, boolean directory) throws IOException {
            try {
                WinNT.SECURITY_DESCRIPTOR_RELATIVE descriptor =
                        Advapi32Util.getFileSecurityDescriptor(path.toFile(), false);
                if ((Short.toUnsignedInt(descriptor.Control) & WinNT.SE_DACL_PROTECTED) == 0) {
                    throw new IOException("The Page Scanner Windows ACL still inherits permissions");
                }

                Map<String, PrivateSid> expected = new LinkedHashMap<>();
                for (PrivateSid principal : principals()) {
                    expected.put(principal.sidString(), principal);
                }
                Set<String> actual = new LinkedHashSet<>();
                ACE_HEADER[] entries = Advapi32Util.getFileSecurity(windowsPath(path), false);
                for (ACE_HEADER entry : entries) {
                    if (!(entry instanceof ACCESS_ALLOWED_ACE allowed)) {
                        throw new IOException("The Page Scanner Windows ACL contains a non-private entry");
                    }
                    ACCESS_ACEStructure access = allowed;
                    String sid = access.getSidString();
                    if (!expected.containsKey(sid)
                            || !actual.add(sid)
                            || (access.Mask & WinNT.FILE_ALL_ACCESS) != WinNT.FILE_ALL_ACCESS) {
                        throw new IOException("The Page Scanner Windows ACL grants an unexpected principal");
                    }
                    int flags = Byte.toUnsignedInt(entry.AceFlags);
                    if ((flags & Byte.toUnsignedInt(WinNT.INHERITED_ACE)) != 0
                            || (directory && (flags & DIRECTORY_INHERITANCE) != DIRECTORY_INHERITANCE)
                            || (!directory && (flags & DIRECTORY_INHERITANCE) != 0)) {
                        throw new IOException("The Page Scanner Windows ACL inheritance is invalid");
                    }
                }
                if (!actual.equals(expected.keySet())) {
                    throw new IOException("The Page Scanner Windows ACL is incomplete");
                }
            } catch (IOException failure) {
                throw failure;
            } catch (RuntimeException nativeFailure) {
                throw new IOException("The Page Scanner Windows ACL could not be verified", nativeFailure);
            }
        }

        private static List<PrivateSid> principals() throws IOException {
            HANDLEByReference token = new HANDLEByReference();
            HANDLE handle = null;
            try {
                if (!Advapi32.INSTANCE.OpenProcessToken(
                        Kernel32.INSTANCE.GetCurrentProcess(), WinNT.TOKEN_QUERY, token)) {
                    throw windowsFailure("read the Page Scanner process identity");
                }
                handle = token.getValue();
                Advapi32Util.Account account = Advapi32Util.getTokenAccount(handle);
                Map<String, PSID> sids = new LinkedHashMap<>();
                addSid(sids, account.sid);
                addSid(sids, Advapi32Util.convertStringSidToSid(LOCAL_SYSTEM_SID));
                addSid(sids, Advapi32Util.convertStringSidToSid(BUILTIN_ADMINISTRATORS_SID));
                List<PrivateSid> result = new ArrayList<>(sids.size());
                for (Map.Entry<String, PSID> entry : sids.entrySet()) {
                    result.add(new PrivateSid(entry.getKey(), entry.getValue()));
                }
                return List.copyOf(result);
            } catch (IOException failure) {
                throw failure;
            } catch (RuntimeException nativeFailure) {
                throw new IOException("The Page Scanner Windows identity is unavailable", nativeFailure);
            } finally {
                if (handle != null && !WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
                    try {
                        Kernel32Util.closeHandle(handle);
                    } catch (RuntimeException closeFailure) {
                        log.warn("Could not close the Page Scanner Windows identity handle", closeFailure);
                    }
                }
            }
        }

        private static void addSid(Map<String, PSID> target, byte[] bytes) {
            PSID sid = new PSID(bytes);
            target.putIfAbsent(Advapi32Util.convertSidToStringSid(sid), sid);
        }

        private static String windowsPath(Path path) {
            return path.toAbsolutePath().normalize().toString().replace('/', '\\');
        }

        private static IOException windowsFailure(String action) {
            return new IOException(
                    "Windows could not " + action + " (error "
                            + Kernel32.INSTANCE.GetLastError() + ")");
        }

        private record PrivateSid(String sidString, PSID sid) {}
    }
}
