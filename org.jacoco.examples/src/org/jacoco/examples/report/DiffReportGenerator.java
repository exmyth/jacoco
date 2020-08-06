package org.jacoco.examples.report;

import org.eclipse.jgit.errors.NotSupportedException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** * Used to generate incremental coverage report based on exec file */
public class DiffReportGenerator {

    private static final String BRANCH = "branch";
    private static final String COMMIT = "commit";
    private static final List<String> diffList = Arrays.asList(BRANCH, COMMIT);
    public static void main(final String[] args) throws Exception {
        if(args.length <= 0 || !diffList.contains(args[0])){
            System.err.println("arg0 value illegal, it must be one of (branch, commit)");
            System.exit(-1);
        }
        getDiffReport(args).create();
    }

    private static DiffReport getDiffReport(String[] args) throws NotSupportedException {
        DiffReport report;
        if(BRANCH.equals(args[0])){
            report = BranchDiffReport.newInstance(args);
        } else if(COMMIT.equals(args[0])){
            report = CommitDiffReport.newInstance(args);
        } else {
            throw new NotSupportedException("not supported operation");
        }
        return report;
    }
}