package net.uvavru.maven.plugin.jacocogerrit;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import net.uvavru.maven.plugin.jacocogerrit.model.PatchCoverageInput;

import org.apache.maven.plugin.MojoFailureException;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.tools.ExecFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JacocoReader.
 */
public class JacocoReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(JacocoReader.class);

    private final JacocoGerritMojo mojo;

    public JacocoReader(JacocoGerritMojo mojo) {
        this.mojo = mojo;
    }

    public PatchCoverageInput readCoverage(Map<String, Map<String, String>> scanMap) throws MojoFailureException {

        PatchCoverageInput result = new PatchCoverageInput();
        ExecFileLoader execFileLoader = new ExecFileLoader();
        try {
            execFileLoader.load(mojo.getExecFile());
        } catch (IOException e) {
            Utils.logErrorAndThrow(LOGGER, MojoFailureException::new, "Cannot read JaCoCo exec file: " + mojo.getExecFile(), e);
        }

        scanMap.forEach((dirToScan, stringMap) -> {

            try {
                new Analyzer(execFileLoader.getExecutionDataStore(), coverage -> {

                    // InnerClasses are mapped to their wrapping classes
                    String key = coverage.getName().contains("$")
                            ? coverage.getName().substring(0, coverage.getName().indexOf('$'))
                            : coverage.getName();

                    if (!stringMap.containsKey(key)) {
                        LOGGER.debug("Skipping class '{}' because it wasn't found among classes to analyze.",
                                     coverage.getName());
                        return;
                    }

                    String filePath = stringMap.get(key);

                    LOGGER.info("Setting coverage for class: '{}', source: '{}'", coverage.getName(), filePath);

                    if (coverage.isNoMatch()) {
                        // testing against different bytecode
                        Utils.logErrorAndThrow(LOGGER, IllegalStateException::new, "The class file identified as '" + coverage
                                .getName() + "' found in directory '" + dirToScan + "', which is mapped to source file '" +
                                filePath +
                                "', has different checksum."
                                + " It looks like "
                                + "the class was recompiled after the JaCoCo analysis was "
                                + "run!");
                    }

                    for (int line = coverage.getFirstLine(); line <= coverage.getLastLine(); ++line) {
                        ILine coverageLine = coverage.getLine(line);
                        ICounter branchCounter = coverageLine.getBranchCounter();
                        ICounter instructionCounter = coverageLine.getInstructionCounter();

                        report(coverage, line, coverageLine);

                        if (coverageLine.getStatus() == ICounter.EMPTY) {
                            // line was not analyzed
                            continue;
                        }

                        result.setLineCoverage(filePath, line,
                                               instructionCounter.getCoveredCount() == 0 ? 0 : 1,
                                               branchCounter.getTotalCount(),
                                               branchCounter.getCoveredCount()
                        );
                    }
                }).analyzeAll(new File(dirToScan));
            } catch (IOException e) {
                throw new IllegalStateException("An error occurred during analysis of directory: " + dirToScan, e);
            }
        });
        return result;
    }

    private void report(IClassCoverage coverage, int line, ILine coverageLine) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        String result = null;
        switch (coverageLine.getStatus()) {
        case ICounter.EMPTY:
            result = "not analyzed";
            break;
        case ICounter.FULLY_COVERED:
            result = "covered";
            break;
        case ICounter.PARTLY_COVERED:
            ICounter branchCounter = coverageLine.getBranchCounter();
            result = "partial: " + branchCounter.getCoveredCount() + " out of " + branchCounter
                    .getTotalCount() + " were covered";
            break;
        case ICounter.NOT_COVERED:
            result = "missed";
            break;
        default:
            Utils.logErrorAndThrow(LOGGER, IllegalStateException::new,
                                   "Coverage line status not expected: " + coverageLine.getStatus());
        }
        LOGGER.debug("Class: {}:{} .. result: {}", coverage.getSourceFileName(), line, result);
    }

}
