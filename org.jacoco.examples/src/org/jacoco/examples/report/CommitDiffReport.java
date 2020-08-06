package org.jacoco.examples.report;

import org.jacoco.core.analysis.CoverageBuilder;

import java.io.File;

public class CommitDiffReport extends DiffReport{

    private final String gitPath;
    private final String branchName;
    private final String newCommitId;
    private final String oldCommitId;

    public CommitDiffReport(String gitPath, String branchName, String newCommitId, String oldCommitId, String dataFiles, String classFiles, String sourceFiles, String html) {
        super(new File(gitPath, dataFiles), new File(gitPath, classFiles), new File(gitPath, sourceFiles), new File(gitPath, html));
        this.gitPath = gitPath;
        this.branchName = branchName;
        this.newCommitId = newCommitId;
        this.oldCommitId = oldCommitId;
    }

    public static DiffReport newInstance(String[] args) {
        if(args.length < 9){
            System.err.println("java -jar org_jacoco_examples_jar/org.jacoco.examples.jar" +
                    " commit <gitPath> <branchName> <newCommitId> <oldCommitId> <execName> <classPath>  <sourcePath> <reportPath> [exclusion]");
            System.out.println();
            System.out.println("eg: java -jar org_jacoco_examples_jar/org.jacoco.examples.jar log ./ feature/1.0.1 master middleware-checker.exec target/classes  src/main/java report");
            System.exit(-1);
        }
        CommitDiffReport report = new CommitDiffReport(args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8]);
        if(args.length > 9){
            report.setExclusionList(args[9]);
        }
        return report;
    }

    @Override
    protected CoverageBuilder buildCoverageBuilder() {
        return CoverageBuilder.buildDiffCommitToCommit(gitPath, branchName, newCommitId, oldCommitId);
    }
}