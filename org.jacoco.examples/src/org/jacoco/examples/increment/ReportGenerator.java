package org.jacoco.examples.increment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.internal.diff.GitAdapter;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.core.util.StringUtils;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.html.HTMLFormatter;

/** * 用于根据exec文件生成增量覆盖率报告 */
public class ReportGenerator {

    private final String title;
    private final String gitPath;
    private final String newBranch;
    private final String oldBranch;
    private final File executionDataFile;
    private final File classesDirectory;
    private final File sourceDirectory;
    private final File reportDirectory;
    private ExecFileLoader execFileLoader;
    private List<String> packageExclusionList = new ArrayList<String>();
    private List<String> nameExclusionList = new ArrayList<String>();

    public ReportGenerator(String gitPath, String newBranch, String oldBranch, String dataFiles, String classFiles, String sourceFiles, String html) {
        this.gitPath = gitPath;
        this.newBranch = newBranch;
        this.oldBranch = oldBranch;

        this.title = dataFiles;
        this.executionDataFile = new File(gitPath, dataFiles);//第一步生成的exec的文件
        this.classesDirectory = new File(gitPath, classFiles);//目录下必须包含源码编译过的class文件,用来统计覆盖率。所以这里用server打出的jar包地址即可,运行的jar或者Class目录
        this.sourceDirectory = new File(gitPath, sourceFiles);//源码目录
        this.reportDirectory = new File(gitPath, html);//要保存报告的地址
    }

    public void setPackageExclusionList(List<String> packageExclusionList) {
        this.packageExclusionList = packageExclusionList;
    }

    public void setNameExclusionList(List<String> nameExclusionList) {
        this.nameExclusionList = nameExclusionList;
    }

    public void create() throws IOException {
        loadExecutionData();
        final IBundleCoverage bundleCoverage = analyzeStructure();
        createReport(bundleCoverage);
    }

    private void createReport(final IBundleCoverage bundleCoverage)
            throws IOException {

        final HTMLFormatter htmlFormatter = new HTMLFormatter();
        final IReportVisitor visitor = htmlFormatter.createVisitor(new FileMultiReportOutput(reportDirectory));

        visitor.visitInfo(execFileLoader.getSessionInfoStore().getInfos(),execFileLoader.getExecutionDataStore().getContents());

        visitor.visitBundle(bundleCoverage, new DirectorySourceFileLocator(sourceDirectory, "utf-8", 4));
        
//        //多源码路径
//        MultiSourceFileLocator sourceLocator = new MultiSourceFileLocator(4);
//        sourceLocator.add( new DirectorySourceFileLocator(sourceDir1, "utf-8", 4));
//        sourceLocator.add( new DirectorySourceFileLocator(sourceDir2, "utf-8", 4));
//        sourceLocator.add( new DirectorySourceFileLocator(sourceDir3, "utf-8", 4));
//        visitor.visitBundle(bundleCoverage,sourceLocator);

        visitor.visitEnd();
    }

    private void loadExecutionData() throws IOException {
        execFileLoader = new ExecFileLoader();
        execFileLoader.load(executionDataFile);
    }

    private IBundleCoverage analyzeStructure() throws IOException {
        // git登录授权
        GitAdapter.setCredentialsProvider("zhongmaming", "qPmz12480.");
        // 全量覆盖
        // final CoverageBuilder coverageBuilder = new CoverageBuilder();
        
        // 基于分支比较覆盖，参数1：本地仓库，参数2：开发分支（预发分支），参数3：基线分支(不传时默认为master)
        // 本地Git路径，新分支 第三个参数不传时默认比较maser，传参数为待比较的基线分支
        final CoverageBuilder coverageBuilder = new CoverageBuilder(gitPath,newBranch, oldBranch, packageExclusionList, nameExclusionList);
        
        // 基于Tag比较的覆盖 参数1：本地仓库，参数2：代码分支，参数3：新Tag(预发版本)，参数4：基线Tag（变更前的版本）
        //final CoverageBuilder coverageBuilder = new CoverageBuilder("E:\\Git-pro\\JacocoTest","daily","v004","v003");

        if(CoverageBuilder.classInfos != null && !CoverageBuilder.classInfos.isEmpty()){
            Analyzer analyzer = new Analyzer(execFileLoader.getExecutionDataStore(), coverageBuilder);
            analyzer.analyzeAll(classesDirectory);
        }
        return coverageBuilder.getBundle(title);
    }

    public static void main(final String[] args) throws IOException {

        if(args.length < 7){
            System.err.println("参数不正确，正确用法举例：\njava -jar D:/IdeaProjects/middleware-checker feature/1.0.1 master middleware-checker.exec target/classes  src/main/java report");
            System.exit(-1);
        }
        ReportGenerator generator = new ReportGenerator(args[0], args[1], args[2], args[3], args[4], args[5],args[6]);

        if(args.length > 7){
            String[] split = StringUtils.split(args[7], "/");
            if(split.length>0){
                generator.setPackageExclusionList(Arrays.asList(StringUtils.split(StringUtils.deleteWhitespace(split[0]), ",")));
            }
            if(split.length>1){
                generator.setNameExclusionList(Arrays.asList(StringUtils.split(StringUtils.deleteWhitespace(split[1]), ",")));
            }
        }
        generator.create();
    }
}