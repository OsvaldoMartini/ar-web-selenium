package com.allinweb.ch.facade;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** AWT/Swing-free native file/folder selection for React workflows requiring real paths. */
public final class NativePathChooser {
    private static final String INITIAL_DIRECTORY_ENV = "ARWEB_CHOOSER_INITIAL";

    private NativePathChooser() {}

    public static File chooseReport(File initialDirectory) {
        return choose(initialDirectory, false, System.getProperty("os.name", ""), NativePathChooser::run);
    }

    public static File chooseDirectory(File initialDirectory) {
        return choose(initialDirectory, true, System.getProperty("os.name", ""), NativePathChooser::run);
    }

    /** Opens a generic file selector for configuration paths such as the AR Engine executable/JAR. */
    public static File chooseFile(File initialPath) {
        return chooseGenericFile(
                initialPath, System.getProperty("os.name", ""), NativePathChooser::run);
    }

    static File choose(File initialDirectory, boolean directory, String osName, ProcessPort processPort) {
        return choose(initialDirectory, directory, false, osName, processPort);
    }

    private static File chooseGenericFile(
            File initialPath, String osName, ProcessPort processPort) {
        return choose(initialPath, false, true, osName, processPort);
    }

    private static File choose(
            File initialPath,
            boolean directory,
            boolean genericFile,
            String osName,
            ProcessPort processPort) {
        Command command = command(directory, genericFile, osName);
        Map<String, String> environment = new LinkedHashMap<>();
        File chooserInitialPath = resolveInitialPath(initialPath, directory);
        if (chooserInitialPath != null) {
            environment.put(INITIAL_DIRECTORY_ENV, chooserInitialPath.getAbsolutePath());
        }

        final ProcessResult result;
        try {
            result = processPort.execute(command.arguments(), environment);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to open the native path selector", error);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Native path selection was interrupted", interrupted);
        }

        String selected = result.output() == null ? "" : result.output().trim();
        if (result.exitCode() == 1) return null;
        if (result.exitCode() != 0) {
            throw new IllegalStateException("Native path selector failed with exit code " + result.exitCode());
        }
        if (selected.isEmpty()) return null;
        return new File(selected);
    }

    static Command command(boolean directory, String osName) {
        return command(directory, false, osName);
    }

    private static Command command(boolean directory, boolean genericFile, String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) return windowsCommand(directory, genericFile);
        if (os.contains("mac")) return macCommand(directory, genericFile);
        return linuxCommand(directory, genericFile);
    }

    private static Command windowsCommand(boolean directory, boolean genericFile) {
        String common = "$ErrorActionPreference='Stop';"
                + "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                + "Add-Type -AssemblyName System.Windows.Forms;"
                + "$i=[Environment]::GetEnvironmentVariable('" + INITIAL_DIRECTORY_ENV + "');";
        String script;
        if (directory) {
            script = common
                    + "$d=New-Object System.Windows.Forms.FolderBrowserDialog;"
                    + "$d.Description='Select configuration folder';"
                    + "$d.ShowNewFolderButton=$true;"
                    + "if($i -and [IO.Directory]::Exists($i)){$d.SelectedPath=$i};"
                    + "if($d.ShowDialog() -eq [Windows.Forms.DialogResult]::OK){"
                    + "[Console]::Out.Write($d.SelectedPath)}";
        } else {
            String title = genericFile ? "Select configuration file" : "Select execution report";
            String filter = genericFile
                    ? "AR Engine files (*.jar;*.exe)|*.jar;*.exe|All files (*.*)|*.*"
                    : "Report files (*.xlsx;*.xls;*.csv;*.html;*.pdf;*.txt)|*.xlsx;*.xls;*.csv;*.html;*.htm;*.pdf;*.txt";
            script = common
                    + "$d=New-Object System.Windows.Forms.OpenFileDialog;"
                    + "$d.Title='" + title + "';"
                    + "$d.Filter='" + filter + "';"
                    + "if($i -and [IO.File]::Exists($i)){"
                    + "$d.InitialDirectory=[IO.Path]::GetDirectoryName($i);"
                    + "$d.FileName=[IO.Path]::GetFileName($i)}"
                    + "elseif($i -and [IO.Directory]::Exists($i)){$d.InitialDirectory=$i}"
                    + "elseif($i){$p=Split-Path -Parent $i;"
                    + "if($p -and [IO.Directory]::Exists($p)){"
                    + "$d.InitialDirectory=$p;$d.FileName=Split-Path -Leaf $i}};"
                    + "if($d.ShowDialog() -eq [Windows.Forms.DialogResult]::OK){"
                    + "[Console]::Out.Write($d.FileName)}";
        }
        return new Command(List.of(
                "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-STA", "-Command", script));
    }

    private static Command macCommand(boolean directory, boolean genericFile) {
        String script = directory
                ? "POSIX path of (choose folder with prompt \"Select configuration folder\")"
                : "POSIX path of (choose file with prompt \""
                        + (genericFile ? "Select configuration file" : "Select execution report")
                        + "\")";
        return new Command(List.of("osascript", "-e", script));
    }

    private static Command linuxCommand(boolean directory, boolean genericFile) {
        List<String> arguments = new ArrayList<>(List.of(
                "zenity", "--file-selection", "--title="
                        + (directory
                                ? "Select configuration folder"
                                : genericFile ? "Select configuration file" : "Select execution report")));
        if (directory) {
            arguments.add("--directory");
        } else if (!genericFile) {
            arguments.add("--file-filter=Report files | *.xlsx *.xls *.csv *.html *.htm *.pdf *.txt");
        } else {
            arguments.add("--file-filter=AR Engine files | *.jar *.exe");
            arguments.add("--file-filter=All files | *");
        }
        return new Command(List.copyOf(arguments));
    }

    private static File resolveInitialPath(File requested, boolean directory) {
        if (requested == null) return null;
        File absolute = requested.getAbsoluteFile();
        if (!directory) {
            File parent = absolute.isDirectory() ? absolute : absolute.getParentFile();
            if (absolute.isFile() || (parent != null && parent.isDirectory())) return absolute;
        }

        File candidate = absolute;
        if (candidate.isFile()) candidate = candidate.getParentFile();
        while (candidate != null && !candidate.isDirectory()) {
            candidate = candidate.getParentFile();
        }
        return candidate;
    }

    private static ProcessResult run(List<String> command, Map<String, String> environment)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, new String(output, StandardCharsets.UTF_8));
    }

    record Command(List<String> arguments) {
        Command {
            arguments = List.copyOf(arguments);
        }
    }

    record ProcessResult(int exitCode, String output) {}

    @FunctionalInterface
    interface ProcessPort {
        ProcessResult execute(List<String> command, Map<String, String> environment)
                throws IOException, InterruptedException;
    }
}
