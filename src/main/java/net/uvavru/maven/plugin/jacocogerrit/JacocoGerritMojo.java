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

    // static settings, usually set just once

    /**
     * The jacoco exec file to read the coverage probes from.
     */
    @Parameter(name = "execFile", property = "jacoco.execFile", defaultValue = "${project.basedir}/target/jacoco.exec")
    private File execFile;

    /**
     * The hostname (or IP address) of Gerrit instance.
     */
    @Parameter(name = "host", property = "gerrit.host", required = true)
    private String host;

    /**
     * The port where the REST API of Gerrit is accessible. (e.g., the port where the website runs, such as {@code 8080}).
     */
    @Parameter(name = "port", property = "gerrit.port", required = true)
    private Integer port;

    /**
     * The URI scheme (such as {@code http} or {@code https}).
     */
    @Parameter(name = "scheme", property = "gerrit.scheme", defaultValue = "http")
    private String scheme;

    /**
     * The username to use to access Gerrit REST API. This user needs enabled HTTP access (<i>Settings</i> -> <i>HTTP
     * Password</i>
     * -> <i>Generate HTTP password</i>).
     */
    @Parameter(name = "username", property = "gerrit.username")
    private String username;

    /**
     * The password for HTTP access of user {@link #username}.
     */
    @Parameter(name = "password", property = "gerrit.password")
    private String password;

    /**
     * The basepath (contextroot) where Gerrit is accessible at given {@link #host}, {@link #port}.
     */
    @Parameter(name = "basePath", property = "gerrit.basePath", defaultValue = "/")
    private String basePath;

    // dynamic settings, usually changes per each review request

    /**
     * The project name of the review request.
     */
    @Parameter(name = "projectName", property = "gerrit.projectName", defaultValue = "${env.GERRIT_PROJECT}", required = true)
    private String projectName;

    /**
     * The branch of the review request.
     */
    @Parameter(name = "branchName", property = "gerrit.branchName", defaultValue = "${env.GERRIT_BRANCH}")
    private String branchName = "master";

    /**
     * The change ID of the review request. (This is the unique id assigned to each review request, usually stored in the commit
     * message; such as {@code I1d07ca1b78eb2409006c0e8809844ad940708d47})
     */
    @Parameter(name = "changeId", property = "gerrit.changeId", defaultValue = "${env.GERRIT_CHANGE_ID}", required = true)
    private String changeId;

    /**
     * The Git revision ID (hash) in the full format of the git commit to test. (e.g., {@code
     * 9cb2813f170e5140e4457fe9089cfe678d905fc4})
     */
    @Parameter(name = "revisionId", property = "gerrit.revisionId", defaultValue = "${env.GERRIT_PATCHSET_REVISION}", required
            = true)
    private String revisionId;

    // implicit, set by maven

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    // for troubleshooting

    /**
     * Set this option to {@code true} if there are files in the Gerrit review request that do not exist in the project. This
     * does not apply for file that were deleted in the current patchset. This option is for troubleshooting and should be
     * enabled only if and only if one experiences {@link java.io.FileNotFoundException} with message {@code Files not found, use
     * 'overrideSourceFileNotFoundError' to override this error}.
     */
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
