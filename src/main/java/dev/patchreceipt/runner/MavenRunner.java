package dev.patchreceipt.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class MavenRunner {

    private final BoundedProcessRunner processRunner;
    private final Path applicationRoot;
    private final Path mavenUserHome;
    private final boolean offline;

    public MavenRunner(
            BoundedProcessRunner processRunner,
            @Value("${patchreceipt.application-root:${user.dir}}") String applicationRoot,
            @Value("${patchreceipt.maven-user-home:${user.dir}/.cache/maven}") String mavenUserHome,
            @Value("${patchreceipt.runner.offline:false}") boolean offline) {
        this.processRunner = processRunner;
        this.applicationRoot = Path.of(applicationRoot).toAbsolutePath().normalize();
        this.mavenUserHome = Path.of(mavenUserHome).toAbsolutePath().normalize();
        this.offline = offline;
    }

    public ProcessResult run(
            Path project,
            List<String> goalsAndOptions,
            Duration timeout,
            int maximumCharacters) throws IOException, InterruptedException {
        Files.createDirectories(mavenUserHome);
        Files.createDirectories(mavenUserHome.resolve("repository"));

        List<String> command = new ArrayList<>(resolveCommand(project));
        command.add("-B");
        command.add("-ntp");
        command.add("-Dstyle.color=never");
        command.add("-Dmaven.repo.local=" + mavenUserHome.resolve("repository"));
        if (offline) {
            command.add("-o");
        }
        command.addAll(goalsAndOptions);

        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("MAVEN_USER_HOME", mavenUserHome.toString());
        environment.put("MAVEN_OPTS",
                "-Xmx256m -XX:MaxMetaspaceSize=160m -Djava.awt.headless=true");
        environment.put("NO_COLOR", "1");
        environment.put("TERM", "dumb");

        return processRunner.run(command, project, environment, timeout, maximumCharacters);
    }

    private List<String> resolveCommand(Path project) {
        String explicit = System.getenv("PATCHRECEIPT_MAVEN_COMMAND");
        if (explicit != null && !explicit.isBlank()) {
            return List.of(explicit);
        }

        boolean windows = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT)
                .contains("win");
        Path projectWrapper = project.resolve(windows ? "mvnw.cmd" : "mvnw");
        Path rootWrapper = applicationRoot.resolve(windows ? "mvnw.cmd" : "mvnw");
        Path wrapper = Files.isRegularFile(projectWrapper) ? projectWrapper : rootWrapper;
        if (windows) {
            return windowsLauncher(project);
        }
        if (Files.isRegularFile(wrapper)) {
            return List.of("sh", wrapper.toString());
        }
        return List.of("mvn");
    }

    private List<String> windowsLauncher(Path project) {
        Path distributionRoot = mavenUserHome.resolve("wrapper/dists/apache-maven-3.9.16");
        if (!Files.isDirectory(distributionRoot)) {
            throw new IllegalStateException(
                    "Pinned Maven runtime is missing. Run mvnw.cmd -v before PatchReceipt.");
        }
        try (Stream<Path> paths = Files.walk(distributionRoot, 4)) {
            Path classworlds = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getParent() != null
                            && "boot".equals(path.getParent().getFileName().toString()))
                    .filter(path -> path.getFileName().toString().startsWith("plexus-classworlds-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Pinned Maven launcher JAR is missing under " + distributionRoot));
            Path mavenHome = classworlds.getParent().getParent();
            Path java = Path.of(
                    System.getProperty("java.home"), "bin", "java.exe").toAbsolutePath();
            return List.of(
                    java.toString(),
                    "-classpath",
                    classworlds.toString(),
                    "-Dclassworlds.conf=" + mavenHome.resolve("bin/m2.conf"),
                    "-Dmaven.home=" + mavenHome,
                    "-Dmaven.multiModuleProjectDirectory=" + project.toAbsolutePath(),
                    "org.codehaus.plexus.classworlds.launcher.Launcher");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot resolve pinned Maven runtime", exception);
        }
    }
}
