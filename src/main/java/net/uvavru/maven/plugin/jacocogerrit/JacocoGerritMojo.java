package net.uvavru.maven.plugin.jacocogerrit;

import java.io.File;
import java.util.Map;

import net.uvavru.maven.plugin.jacocogerrit.model.PatchCoverageInput;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JacocoGerritMojo.
 */
@Mojo(name = "jacoco-to-gerrit", defaultPhase = LifecyclePhase.INITIALIZE, aggregator = true, requiresDirectInvocation = true,
      requiresOnline = true)
public class JacocoGerritMojo extends AbstractMojo {

    private static final Logger LOGGER = LoggerFactory.getLogger(JacocoGerritMojo.class);

    @Parameter(name = "host", property = "gerrit.host", required = true)
    private String host;

    @Parameter(name = "port", property = "gerrit.port", required = true)
    private Integer port;

    @Parameter(name = "scheme", property = "gerrit.scheme", defaultValue = "http")
    private String scheme;

    @Parameter(name = "username", property = "gerrit.username")
    private String username;

    @Parameter(name = "password", property = "gerrit.password")
    private String password;

    @Parameter(name = "basePath", property = "gerrit.basePath", defaultValue = "/")
    private String basePath;

    @Parameter(name = "projectName", property = "gerrit.projectName", defaultValue = "${env.GERRIT_PROJECT}", required = true)
    private String projectName;

    @Parameter(name = "branchName", property = "gerrit.branchName", defaultValue = "${env.GERRIT_BRANCH}")
    private String branchName = "master";

    @Parameter(name = "changeId", property = "gerrit.changeId", defaultValue = "${env.GERRIT_CHANGE_ID}", required = true)
    private String changeId;

    @Parameter(name = "revisionId", property = "gerrit.revisionId", defaultValue = "${env.GERRIT_PATCHSET_REVISION}", required = true)
    private String revisionId;

    @Parameter(name = "execFile", property = "jacoco.execFile", defaultValue = "${project.basedir}/target/jacoco.exec")
    private File execFile;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "jacoco-gerrit.overrideSourceFileNotFoundError", defaultValue = "false")
    private boolean overrideSourceFileNotFoundError;

    @Override
    public void execute() throws MojoFailureException {

        GerritFacade gerritFacade = new GerritFacade(this);
        SourceFilesMapper sourceFilesMapper = new SourceFilesMapper(this);
        JacocoReader jacocoReader = new JacocoReader(this);

        LOGGER.info("Mapping source files modified in the patchset to compiled classes.");
        Map<String, Map<String, String>> stringMapMap = sourceFilesMapper
                .calculateMapping(gerritFacade.listFiles());

        LOGGER.info("Reading JaCoCo coverage data.");
        PatchCoverageInput patchCoverageInput = jacocoReader.readCoverage(stringMapMap);

        LOGGER.info("Uploading the coverage to Gerrit.");
        gerritFacade.setCoverage(patchCoverageInput);

        LOGGER.info("Test coverage successfully posted to Gerrit.");
    }

    public boolean isAnonymous() {
        return username == null && password == null;
    }

    public String getHost() {
        return host;
    }

    public String getScheme() {
        return scheme;
    }

    public Integer getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getBasePath() {
        return basePath;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getChangeId() {
        return changeId;
    }

    public String getRevisionId() {
        return revisionId;
    }

    public File getExecFile() {
        return execFile;
    }

    public MavenProject getProject() {
        return project;
    }

    public boolean isOverrideSourceFileNotFoundError() {
        return overrideSourceFileNotFoundError;
    }
}
