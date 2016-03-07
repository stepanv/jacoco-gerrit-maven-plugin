package net.uvavru.maven.plugin.jacocogerrit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The SourceFilesMapper.
 */
public class SourceFilesMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(SourceFilesMapper.class);
    private static final String JAVA_SUFFIX = ".java";

    private final JacocoGerritMojo mojo;

    public SourceFilesMapper(JacocoGerritMojo mojo) {
        this.mojo = mojo;
    }

    public Map<String, Map<String, String>> calculateMapping(List<String> relativeSourceFilePaths) throws MojoFailureException {
        //Map<DirToScan, Map<SourcePathRelativeToProject, SourcePathRelativeToProjectRoot>>
        Map<String, Map<String, String>> scanMap = new TreeMap<>();
        List<String> filesNotFound = new ArrayList<>();
        MavenProject mavenProjectParent = mojo.getProject();

        for (String relativeSourceFilePath : relativeSourceFilePaths) {

            AtomicBoolean foundClassFile = new AtomicBoolean(false);

            Path basedir = mavenProjectParent.getBasedir().toPath();
            Path sourcePath = basedir.resolve(relativeSourceFilePath);

            Stream.concat(Stream.of(mavenProjectParent),
                          mavenProjectParent.getCollectedProjects().stream()).forEach(mavenProject -> {

                LOGGER.debug("Inspecting source file '{}' whether it's located in source root: {}", sourcePath,
                             mavenProject.getCompileSourceRoots());

                for (String source : mavenProject.getCompileSourceRoots()) {
                    Path compileSourceRootPath = Paths.get(source);
                    if (sourcePath.startsWith(compileSourceRootPath)) {

                        // the 'relativeSourceFilePath' belongs to 'mavenProject'
                        int relativizedSourceFilePathCount = sourcePath.relativize(compileSourceRootPath).getNameCount();
                        Path subPath = sourcePath
                                .subpath(sourcePath.getNameCount() - relativizedSourceFilePathCount, sourcePath.getNameCount());

                        String subPathString = subPath.toString();

                        // strip '.java'
                        if (subPathString.endsWith(JAVA_SUFFIX)) {
                            String classWithPackageAsPath = subPathString
                                    .substring(0, subPathString.length() - JAVA_SUFFIX.length());

                            String classDir = mavenProject.getBuild().getOutputDirectory();
                            scanMap.computeIfAbsent(classDir, path -> new HashMap<String, String>())
                                    .put(classWithPackageAsPath, relativeSourceFilePath);

                            foundClassFile.set(true);
                        }
                    }
                }
            });
            if (!foundClassFile.get()) {
                LOGGER.info("File '{}' not identified as a source file in {}.",
                            relativeSourceFilePath, mavenProjectParent);
                if (!Files.exists(sourcePath)) {
                    filesNotFound.add(relativeSourceFilePath);
                    logErrorOrWarning(
                            "File '{}' is not relative to the basedir '{}' of maven project '{}'). Filepath '{}' does not exist!",
                            relativeSourceFilePath,
                            mavenProjectParent.getBasedir(),
                            mavenProjectParent.getName(),
                            sourcePath);
                }
            }
        }

        // fail in case that source files were not found
        if (!filesNotFound.isEmpty() && !mojo.isOverrideSourceFileNotFoundError()) {
            Utils.logErrorAndThrow(LOGGER, MojoFailureException::new,
                                   "Some files where not found in the project! Files: " + filesNotFound.stream().collect(
                                           Collectors.joining(", ")));
        }

        return scanMap;
    }

    private void logErrorOrWarning(String message, Object... argArray) {
        if (mojo.isOverrideSourceFileNotFoundError()) {
            LOGGER.warn(message, argArray);
        } else {
            LOGGER.error(message, argArray);
        }
    }

}
