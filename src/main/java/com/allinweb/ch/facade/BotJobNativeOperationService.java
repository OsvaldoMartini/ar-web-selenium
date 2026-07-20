package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobToolbarContext;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.common.base.Strings;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Presentation-neutral file validation and external Engine operations for Bot Job Details. */
public final class BotJobNativeOperationService {
    private static final Set<String> REPORT_EXTENSIONS =
            Set.of(".xlsx", ".xls", ".csv", ".html", ".htm", ".pdf", ".txt");

    private final PropertyPort properties;
    private final FileOpenPort fileOpener;
    private final EnginePort engine;

    BotJobNativeOperationService(PropertyPort properties, FileOpenPort fileOpener, EnginePort engine) {
        this.properties = properties;
        this.fileOpener = fileOpener;
        this.engine = engine;
    }

    public static BotJobNativeOperationService createDefault(
            ARPropertyManager properties, BotJobToolbarConcurrencyGuard guard) {
        return new BotJobNativeOperationService(
                new PropertyPort() {
                    public String get(ARPropertyEnum key) { return properties.getProperty(key); }
                    public String configurationFileName() { return properties.getConfigurationFileName(); }
                },
                BotJobNativeOperationService::launchWithSystemApplication,
                specification -> launchProcess(specification, guard));
    }

    /** Opens only the authoritative workbook for the selected Bot Job. */
    public File openExcel(BotJobToolbarContext context) throws IOException {
        return openFile(requiredExcel(context, "Generate the Excel file before opening it"));
    }

    public File openFile(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IllegalArgumentException("File does not exist");
        File canonical = file.getCanonicalFile();
        fileOpener.open(canonical);
        return canonical;
    }

    public File reportDirectory() {
        File folder = new File(requiredPath(ARPropertyEnum.PATH_REPORT, "Report folder"));
        if (!folder.isDirectory()) {
            throw new IllegalStateException("Report folder does not exist: " + folder.getAbsolutePath());
        }
        return folder;
    }

    /** Resolves an optional React path or native-chooser selection inside PATH_REPORT. */
    public File selectReport(File selected) throws IOException {
        if (selected == null) return null;
        Path reportRoot = reportDirectory().toPath().toRealPath();
        Path candidate = selected.toPath();
        if (!candidate.isAbsolute()) candidate = reportRoot.resolve(candidate);
        candidate = candidate.toAbsolutePath().normalize();
        if (!Files.isRegularFile(candidate)) {
            throw new IllegalArgumentException("Selected report file does not exist");
        }
        Path realCandidate = candidate.toRealPath();
        if (!realCandidate.startsWith(reportRoot)) {
            throw new IllegalArgumentException("Selected report must be inside the configured report folder");
        }
        String reportName = realCandidate.getFileName().toString().toLowerCase(Locale.ROOT);
        if (REPORT_EXTENSIONS.stream().noneMatch(reportName::endsWith)) {
            throw new IllegalArgumentException("Select an Excel, CSV, HTML, PDF, or text report file");
        }
        return realCandidate.toFile();
    }

    /** Safe destination used by Generate and by the current-workbook Excel action. */
    public File excelDestination(BotJobToolbarContext context) {
        if (context == null || Strings.isNullOrEmpty(context.name())) {
            throw new IllegalArgumentException("Bot Job context is required");
        }
        final Path root;
        try {
            root = Path.of(requiredPath(ARPropertyEnum.PATH_EXCEL, "Excel folder"))
                    .toAbsolutePath()
                    .normalize();
        } catch (InvalidPathException invalidPath) {
            throw new IllegalStateException("Excel folder is invalid", invalidPath);
        }
        Path destination = root.resolve(context.name().trim() + ARConstants.FILE_FORMAT_EXCEL).normalize();
        if (!destination.startsWith(root) || destination.getParent() == null || !destination.getParent().equals(root)) {
            throw new IllegalArgumentException("Bot Job name cannot be used as an Excel filename");
        }
        return destination.toFile();
    }

    public File createBat(BotJobToolbarContext context) throws IOException {
        File excel = requiredExcel(context, "Generate the Excel file before creating the BAT file");
        String enginePath = requiredPath(ARPropertyEnum.PATH_ENGINE, "Engine JAR");
        File baseDirectory = new File(requiredPath(ARPropertyEnum.PATH_DB, "Database folder"));
        if (!baseDirectory.isDirectory()) throw new IllegalStateException("Database folder does not exist");
        String configPath = System.getProperty("ARWebConfig");
        if (Strings.isNullOrEmpty(configPath)) configPath = properties.configurationFileName();
        String projectType = safe(context.projectType());
        String fileName = "execute_" + projectType.replace(' ', '_').toLowerCase(Locale.ROOT)
                + '_' + context.homeBankingId() + "_Botjob_" + context.botJobId() + ".bat";
        File bat = new File(baseDirectory, fileName);
        String command = "java.exe -jar \"" + enginePath + "\" execute/j "
                + context.homeBankingId() + ' ' + context.botJobId() + " 1 \""
                + excel.getAbsolutePath() + "\" -c \"" + safe(configPath) + "\"";
        try (FileWriter writer = new FileWriter(bat)) {
            writer.write(command);
        }
        return bat;
    }

    public void launchExternalEngine(BotJobToolbarContext context) throws IOException {
        File excel = requiredExcel(context, "Generate the Excel file before launching the Bot Job");
        if (!supportsDesktopBrowserTools(context.projectType())) {
            throw new IllegalStateException("Mobile Bot Jobs can only be executed from AR Mobile");
        }
        File webDriver = new File(requiredPath(ARPropertyEnum.PATH_WEBDRIVER, "WebDriver file"));
        if (!webDriver.exists()) throw new IllegalStateException("The configured WebDriver file is missing");
        File engineJar = new File(requiredPath(ARPropertyEnum.PATH_ENGINE, "Engine JAR"));
        if (!engineJar.isFile()) throw new IllegalStateException("The configured Engine JAR is missing");
        File logDirectory = new File(requiredPath(ARPropertyEnum.PATH_LOG, "Engine log folder"));
        if (!logDirectory.isDirectory() && !logDirectory.mkdirs()) {
            throw new IOException("Unable to create Engine log folder: " + logDirectory.getAbsolutePath());
        }
        List<String> command = List.of(
                "java.exe", "-jar", engineJar.getAbsolutePath(), "execute/j",
                String.valueOf(context.homeBankingId()), String.valueOf(context.botJobId()), "1",
                excel.getAbsolutePath(), "-c", safe(properties.configurationFileName()));
        engine.launch(new LaunchSpecification(
                command,
                new File(ARConstants.USER_PATH),
                new File(logDirectory, "engine_debug_log_output.log"),
                new File(logDirectory, "engine_debug_log_error.log")));
    }

    private File requiredExcel(BotJobToolbarContext context, String missingMessage) {
        File excel = excelDestination(context);
        if (!excel.isFile()) throw new IllegalStateException(missingMessage);
        return excel;
    }

    private String requiredPath(ARPropertyEnum key, String label) {
        String value = properties.get(key);
        if (Strings.isNullOrEmpty(value)) throw new IllegalStateException(label + " is not configured");
        return value.trim();
    }

    private static boolean supportsDesktopBrowserTools(String priority) {
        String value = safe(priority).trim();
        return value.isEmpty()
                || ARPropertyEnum.WEB_APP.getValue().equalsIgnoreCase(value)
                || "Rest Api".equalsIgnoreCase(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void launchProcess(
            LaunchSpecification specification, BotJobToolbarConcurrencyGuard guard) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(specification.command());
        builder.directory(specification.workingDirectory());
        builder.redirectOutput(specification.standardOutput());
        builder.redirectError(specification.standardError());
        Process process = builder.start();
        if (!guard.trackExternalEngine(process)) {
            process.destroy();
            throw new IllegalStateException("An external Engine execution is already active");
        }
        process.onExit().whenComplete((finished, failure) -> guard.externalEngineFinished(process));
    }

    private static void launchWithSystemApplication(File file) throws IOException {
        new ProcessBuilder(systemOpenCommand(file, System.getProperty("os.name", ""))).start();
    }

    static List<String> systemOpenCommand(File file, String osName) {
        if (file == null) throw new IllegalArgumentException("File is required");
        String os = safe(osName).toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return List.of("rundll32.exe", "url.dll,FileProtocolHandler", file.getAbsolutePath());
        } else if (os.contains("mac")) {
            return List.of("open", file.getAbsolutePath());
        }
        return List.of("xdg-open", file.getAbsolutePath());
    }

    record LaunchSpecification(
            List<String> command, File workingDirectory, File standardOutput, File standardError) {}

    interface PropertyPort {
        String get(ARPropertyEnum key);
        String configurationFileName();
    }

    @FunctionalInterface
    interface FileOpenPort {
        void open(File file) throws IOException;
    }

    @FunctionalInterface
    interface EnginePort {
        void launch(LaunchSpecification specification) throws IOException;
    }
}
