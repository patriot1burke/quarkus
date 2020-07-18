package io.quarkus.cli.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;

import io.quarkus.devtools.project.BuildTool;
import io.quarkus.devtools.project.QuarkusProject;

public class ExecuteUtil {

    public static void executeGradle(File projectDirectory, String buildTarget)
            throws InterruptedException {
        String gradleExecutable = findExecutable("gradle");
        if (gradleExecutable == null) {
            System.out.println("unable to find the gradle executable, is it in your path?");
        } else {
            gradleExecutable += File.separator + "bin" + File.separator + "gradle";

            try {
                Process process = new ProcessBuilder()
                        .command(gradleExecutable, buildTarget)
                        .directory(projectDirectory)
                        .start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }

                int exit = process.waitFor();
                if (exit != 0)
                    System.out.println("Build failed.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void executeMaven(File projectDirectory, String buildTarget) {
        String mvnPath = findExecutable("mvn");
        System.setProperty("maven.home", mvnPath);

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(new File(projectDirectory.getAbsolutePath() + File.separatorChar + "pom.xml"));
        request.setGoals(Collections.singletonList(buildTarget));

        Invoker invoker = new DefaultInvoker();

        InvocationResult result = null;
        try {
            result = invoker.execute(request);
        } catch (MavenInvocationException e) {
            System.out.println("Failed during invocation: " + e.getMessage());
        }

        if (result.getExitCode() != 0) {
            System.out.println("Build failed.");
        }
    }

    public static String findExecutable(String exec) {
        Optional<Path> mvnPath = Stream.of(System.getenv("PATH").split(Pattern.quote(File.pathSeparator)))
                .map(Paths::get)
                .filter(path -> Files.exists(path.resolve(exec))).findFirst();

        return mvnPath.map(value -> value.getParent().toString()).orElse(null);
    }

    public static void executeWrapper(File wrapper, String target) throws InterruptedException {
        try {
            Process process = new ProcessBuilder()
                    .command("./" + wrapper.getName(), target)
                    .directory(wrapper.getParentFile())
                    .start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            int exit = process.waitFor();
            if (exit != 0)
                System.out.println("Build failed.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static File getGradleWrapper(String projectPath) {
        if (System.getProperty("os.name").startsWith("Windows")) {
            File wrapper = new File(projectPath + File.separator + "gradlew.bat");
            if (wrapper.isFile())
                return wrapper;
        } else {
            File wrapper = new File(projectPath + File.separator + "gradlew");
            if (wrapper.isFile())
                return wrapper;
        }

        return null;
    }

    public static File getMavenWrapper(String projectPath) {
        if (System.getProperty("os.name").startsWith("Windows")) {
            File wrapper = new File(projectPath + File.separator + "mvnw.bat");
            if (wrapper.isFile())
                return wrapper;
        } else {
            File wrapper = new File(projectPath + File.separator + "mvnw");
            if (wrapper.isFile())
                return wrapper;
        }

        return null;
    }

    public static int executeBuildsystem(File path, String mavenTarget, String gradleTarget) {
        File projectPath = path != null ? new File(path.getAbsolutePath()) : new File(System.getProperty("user.dir"));

        BuildTool buildTool = QuarkusProject.resolveExistingProjectBuildTool(projectPath.toPath());

        if (buildTool.getBuildFiles() != null && buildTool.getBuildFiles().length > 0) {
            File buildFile = new File(buildTool.getBuildFiles()[0]);

            if (!buildFile.isFile()) {
                System.out.println("Was not able to find a build file in: " + projectPath);
                return ExitCodes.FAILURE;
            }

            try {
                if (buildTool.equals(BuildTool.MAVEN)) {
                    File wrapper = getMavenWrapper(projectPath.getAbsolutePath());
                    if (wrapper != null) {
                        executeWrapper(wrapper, mavenTarget);
                    } else {
                        executeMaven(projectPath, mavenTarget);
                    }

                }
                //do gradle
                else {
                    File wrapper = getGradleWrapper(projectPath.getAbsolutePath());
                    if (wrapper != null) {
                        executeWrapper(wrapper, gradleTarget);
                    } else {
                        executeGradle(projectPath, gradleTarget);
                    }
                }
            } catch (InterruptedException i) {
                System.out.println("Build was interrupted.");
                return ExitCodes.FAILURE;
            }

            return ExitCodes.SUCCESS;
        } else {
            System.out.println("Was not able to find a build file in: " + projectPath);
            return ExitCodes.FAILURE;
        }
    }
}
