package org.jacoco.examples.report;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;

import java.io.File;
import java.io.IOException;

/** * 用于根据exec文件生成增量覆盖率报告 */
public class BranchDiffReport extends DiffReport{

    private final String gitPath;
    private final String newBranch;
    private final String oldBranch;

    public BranchDiffReport(String gitPath, String newBranch, String oldBranch, String dataFiles, String classFiles, String sourceFiles, String html) {
        super(new File(gitPath, dataFiles), new File(gitPath, classFiles), new File(gitPath, sourceFiles), new File(gitPath, html));
        this.gitPath = gitPath;
        this.newBranch = newBranch;
        this.oldBranch = oldBranch;
    }

    public static DiffReport newInstance(String[] args) {
        if(args.length < 8){
            System.err.println("java -jar org_jacoco_examples_jar/org.jacoco.examples.jar" +
                    " branch <gitPath> <newBranchName> <oldBranchName> <execName> <classPath>  <sourcePath> <reportPath> [exclusion]");
            System.out.println();
            System.out.println("eg: java -jar org_jacoco_examples_jar/org.jacoco.examples.jar branch ./ feature/1.0.1 master middleware-checker.exec target/classes  src/main/java report");
            System.exit(-1);
        }
        BranchDiffReport report = new BranchDiffReport(args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
        if(args.length > 8){
            report.setExclusionList(args[8]);
        }
        return report;
    }

    @Override
    protected CoverageBuilder buildCoverageBuilder() {
        return CoverageBuilder.buildDiffBranchToBranch(gitPath,newBranch, oldBranch);
    }
}