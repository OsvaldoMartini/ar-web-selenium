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

    static File choose(File initialDirectory, boolean directory, String osName, ProcessPort processPort) {
        Command command = command(directory, osName);
        Map<String, String> environment = new LinkedHashMap<>();
        if (initialDirectory != null && initialDirectory.isDirectory()) {
            environment.put(INITIAL_DIRECTORY_ENV, initialDirectory.getAbsolutePath());
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
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) return windowsCommand(directory);
        if (os.contains("mac")) return macCommand(directory);
        return linuxCommand(directory);
    }

    private static Command windowsCommand(boolean directory) {
        String common = "$ErrorActionPreference='Stop';"
                + "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                + "Add-Type -AssemblyName System.Windows.Forms;"
                + "$i=[Environment]::GetEnvironmentVariable('" + INITIAL_DIRECTORY_ENV + "');";
        String script;
        if (directory) {
            script = common
                    + "$d=New-Object System.Windows.Forms.FolderBrowserDialog;"
                    + "$d.Description='Select Excel export destination folder';"
                    + "$d.ShowNewFolderButton=$true;"
                    + "if($i -and [IO.Directory]::Exists($i)){$d.SelectedPath=$i};"
                    + "if($d.ShowDialog() -eq [Windows.Forms.DialogResult]::OK){"
                    + "[Console]::Out.Write($d.SelectedPath)}";
        } else {
            script = common
                    + "$d=New-Object System.Windows.Forms.OpenFileDialog;"
                    + "$d.Title='Select execution report';"
                    + "$d.Filter='Report files (*.xlsx;*.xls;*.csv;*.html;*.pdf;*.txt)|*.xlsx;*.xls;*.csv;*.html;*.htm;*.pdf;*.txt';"
                    + "if($i -and [IO.Directory]::Exists($i)){$d.InitialDirectory=$i};"
                    + "if($d.ShowDialog() -eq [Windows.Forms.DialogResult]::OK){"
                    + "[Console]::Out.Write($d.FileName)}";
        }
        return new Command(List.of(
                "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-STA", "-Command", script));
    }

    private static Command macCommand(boolean directory) {
        String script = directory
                ? "POSIX path of (choose folder with prompt \"Select Excel export destination folder\")"
                : "POSIX path of (choose file with prompt \"Select execution report\")";
        return new Command(List.of("osascript", "-e", script));
    }

    private static Command linuxCommand(boolean directory) {
        List<String> arguments = new ArrayList<>(List.of(
                "zenity", "--file-selection", "--title="
                        + (directory ? "Select Excel export destination folder" : "Select execution report")));
        if (directory) {
            arguments.add("--directory");
        } else {
            arguments.add("--file-filter=Report files | *.xlsx *.xls *.csv *.html *.htm *.pdf *.txt");
        }
        return new Command(List.copyOf(arguments));
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
