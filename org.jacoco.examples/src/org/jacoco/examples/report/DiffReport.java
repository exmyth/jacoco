package org.jacoco.examples.report;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.internal.diff.ClassInfo;
import org.jacoco.core.internal.diff.GitAdapter;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.core.util.StringUtils;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.html.HTMLFormatter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author zhongmaming
 * @version 1.1.0
 * @date 2020/7/24 0024
 */
public abstract class DiffReport {
    protected ExecFileLoader execFileLoader;
    protected List<String> packageExclusionList = new ArrayList<String>();
    protected List<String> nameExclusionList = new ArrayList<String>();
    private final File executionDataFile;
    private final File sourceDirectory;
    private final File classDirectory;
    private final File reportDirectory;

    public DiffReport(File executionDataFile, File classDirectory, File sourceDirectory, File reportDirectory) {
        this.executionDataFile = executionDataFile;
        this.classDirectory = classDirectory;
        this.sourceDirectory = sourceDirectory;
        this.reportDirectory = reportDirectory;
    }

    private void setPackageExclusionList(List<String> packageExclusionList) {
        this.packageExclusionList = packageExclusionList;
    }

    private void setNameExclusionList(List<String> nameExclusionList) {
        this.nameExclusionList = nameExclusionList;
    }

    public void create() throws IOException {
        loadExecutionData();
        // git登录授权
        GitAdapter.setCredentialsProvider("zhongmaming", "qPmz12480.");
        CoverageBuilder coverageBuilder = buildCoverageBuilder();
        excludeClass(CoverageBuilder.classInfos, packageExclusionList, nameExclusionList);
        if(CoverageBuilder.classInfos != null && !CoverageBuilder.classInfos.isEmpty()){
            Analyzer analyzer = new Analyzer(execFileLoader.getExecutionDataStore(), coverageBuilder);
            analyzer.analyzeAll(classDirectory);
        }
        IBundleCoverage bundleCoverage = coverageBuilder.getBundle(executionDataFile.getName());
        createReport(bundleCoverage);
    }

    protected abstract CoverageBuilder buildCoverageBuilder();

    private void loadExecutionData() throws IOException {
        execFileLoader = new ExecFileLoader();
        execFileLoader.load(executionDataFile);
    }

    private void createReport(final IBundleCoverage bundleCoverage)
            throws IOException {

        final HTMLFormatter htmlFormatter = new HTMLFormatter();
        final IReportVisitor visitor = htmlFormatter.createVisitor(new FileMultiReportOutput(reportDirectory));

        visitor.visitInfo(execFileLoader.getSessionInfoStore().getInfos(),execFileLoader.getExecutionDataStore().getContents());

        visitor.visitBundle(bundleCoverage, new DirectorySourceFileLocator(sourceDirectory, "utf-8", 4));

//        //Multiple source path
//        MultiSourceFileLocator sourceLocator = new MultiSourceFileLocator(4);
//        sourceLocator.add( new DirectorySourceFileLocator(sourceDir1, "utf-8", 4));
//        sourceLocator.add( new DirectorySourceFileLocator(sourceDir2, "utf-8", 4));
//        sourceLocator.add( new DirectorySourceFileLocator(sourceDir3, "utf-8", 4));
//        visitor.visitBundle(bundleCoverage,sourceLocator);
        visitor.visitEnd();
    }

    protected DiffReport setExclusionList(String args) {
        String[] split = StringUtils.split(args, "/");
        if (split.length > 0) {
            setPackageExclusionList(Arrays.asList(StringUtils.split(StringUtils.deleteWhitespace(split[0]), ",")));
        }
        if (split.length > 1) {
            setNameExclusionList(Arrays.asList(StringUtils.split(StringUtils.deleteWhitespace(split[1]), ",")));
        }
        return this;
    }

    private List<ClassInfo> excludeClass(List<ClassInfo> classInfoList, List<String> packageExclusionList, List<String> nameExclusionList) {
        List<Pattern> packageList = new ArrayList<Pattern>();
        List<Pattern> nameList = new ArrayList<Pattern>();
        for (String item : packageExclusionList) {
            packageList.add(Pattern.compile(item));
        }
        for (String item : nameExclusionList) {
            nameList.add(Pattern.compile(item));
        }
        for (int i = classInfoList.size() - 1; i >= 0; i--) {
            if (isInExclusionList(classInfoList.get(i), packageList, nameList)) {
                classInfoList.remove(i);
            }
        }
        return classInfoList;
    }

    private boolean isInExclusionList(ClassInfo classInfo, List<Pattern> packageList, List<Pattern> nameList) {
        for (Pattern p : packageList){
            if(p.matcher(classInfo.getPackages()).matches()){
                return true;
            }
        }
        for (Pattern p : nameList){
            if(p.matcher(classInfo.getClassName()).matches()){
                return true;
            }
        }
        return false;
    }
}
