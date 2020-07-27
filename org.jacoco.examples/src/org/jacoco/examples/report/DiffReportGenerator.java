package org.jacoco.examples.report;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** * 用于根据exec文件生成增量覆盖率报告 */
public class DiffReportGenerator {

    private static final String BRANCH = "branch";
    private static final String LOG = "log";
    private static final List<String> diffList = Arrays.asList(BRANCH, LOG);
    public static void main(final String[] args) throws IOException {
        if(args.length <= 0 || !diffList.contains(args[0])){
            System.err.println("arg0 value illegal, it must be one of (branch, log)");
            System.exit(-1);
        }
        getDiffReport(args).create();
    }

    private static DiffReport getDiffReport(String[] args) {
        DiffReport report = null;
        if(BRANCH.equals(args[0])){
            report = BranchDiffReport.newInstance(args);
        } else if(LOG.equals(args[0])){
            report = LogDiffReport.newInstance(args);
        }
        return report;
    }
}