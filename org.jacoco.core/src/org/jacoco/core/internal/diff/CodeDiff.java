package org.jacoco.core.internal.diff;

import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Code version comparison
 */
public class CodeDiff {
    public final static String REF_HEADS = "refs/heads/";
    public final static  String MASTER = "master";

    /**
     * Comparison between branch and branch
     * @param gitPath
     * @param newBranchName
     * @param oldBranchName
     * @return
     */
    public static List<ClassInfo> diffBranchToBranch(String gitPath, String newBranchName, String oldBranchName) {
        List<ClassInfo> classInfos = diffMethods(gitPath, newBranchName, oldBranchName);
        return classInfos;
    }
    private static List<ClassInfo> diffMethods(String gitPath, String newBranchName, String oldBranchName) {
        try {
            //  Get local branch
            GitAdapter gitAdapter = new GitAdapter(gitPath);
            Git git = gitAdapter.getGit();
            Ref localBranchRef = gitAdapter.getRepository().exactRef(REF_HEADS + newBranchName);
            Ref localMasterRef = gitAdapter.getRepository().exactRef(REF_HEADS + oldBranchName);
            //  checkout local branch
            gitAdapter.checkOutAndPull(localMasterRef, oldBranchName);
            gitAdapter.checkOutAndPull(localBranchRef, newBranchName);
            //  Get branch information
            AbstractTreeIterator newTreeParser = gitAdapter.prepareTreeParser(localBranchRef);
            AbstractTreeIterator oldTreeParser = gitAdapter.prepareTreeParser(localMasterRef);
            //  Contrast difference
            List<DiffEntry> diffs = git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).setShowNameAndStatusOnly(true).call();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DiffFormatter df = new DiffFormatter(out);
            //Set the comparator to ignore blank character comparison（Ignores all whitespace）
            df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
            df.setRepository(git.getRepository());
            List<ClassInfo> allClassInfos = batchPrepareDiffMethod(gitAdapter, newBranchName, oldBranchName, df, diffs);
            return allClassInfos;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return  new ArrayList<ClassInfo>();
    }

    /**
     * Comparison between single branch Tag versions
     * @param gitPath
     * @param newTag
     * @param oldTag
     * @return
     */
    public static List<ClassInfo> diffTagToTag(String gitPath, String branchName, String newTag, String oldTag) {
        if(StringUtils.isEmptyOrNull(gitPath) || StringUtils.isEmptyOrNull(branchName)  || StringUtils.isEmptyOrNull(newTag)  || StringUtils.isEmptyOrNull(oldTag) ){
            throw new IllegalArgumentException("Parameter(local gitPath,develop branchName,new Tag,old Tag) can't be empty or null !");
        }else if(newTag.equals(oldTag)){
            throw new IllegalArgumentException("Parameter new Tag and old Tag can't be the same");
        }
        File gitPathDir = new File(gitPath);
        if(!gitPathDir.exists()){
            throw new IllegalArgumentException("Parameter local gitPath is not exit !");
        }

        List<ClassInfo> classInfos = diffTagMethods(gitPath,branchName, newTag, oldTag);
        return classInfos;
    }
    private static List<ClassInfo> diffTagMethods(String gitPath,String branchName, String newTag, String oldTag) {
        try {
            //  init local repository
            GitAdapter gitAdapter = new GitAdapter(gitPath);
            Git git = gitAdapter.getGit();
            Repository repo =  gitAdapter.getRepository();
            Ref localBranchRef = repo.exactRef(REF_HEADS + branchName);

            //  update local repository
            gitAdapter.checkOutAndPull(localBranchRef, branchName);

            ObjectId head = repo.resolve(newTag+"^{tree}");
            ObjectId previousHead = repo.resolve(oldTag+"^{tree}");

            // Instanciate a reader to read the data from the Git database
            ObjectReader reader = repo.newObjectReader();
            // Create the tree iterator for each commit
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, previousHead);
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, head);

            //  Contrast difference
            List<DiffEntry> diffs = git.diff().setOldTree(oldTreeIter).setNewTree(newTreeIter).setShowNameAndStatusOnly(true).call();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DiffFormatter df = new DiffFormatter(out);
            //Set the comparator to ignore blank character comparison（Ignores all whitespace）
            df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
            df.setRepository(repo);
            List<ClassInfo> allClassInfos = batchPrepareDiffMethodForTag(gitAdapter, newTag,oldTag, df, diffs);
            return allClassInfos;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return  new ArrayList<ClassInfo>();
    }
    /**
     * Multi-threaded execute comparison
     */
    private static List<ClassInfo> batchPrepareDiffMethodForTag(final GitAdapter gitAdapter, final String newTag, final String oldTag, final DiffFormatter df, List<DiffEntry> diffs) {
        int threadSize = 100;
        int dataSize = diffs.size();
        int threadNum = dataSize / threadSize + 1;
        boolean special = dataSize % threadSize == 0;
        ExecutorService executorService = Executors.newFixedThreadPool(threadNum);

        List<Callable<List<ClassInfo>>> tasks = new ArrayList<Callable<List<ClassInfo>>>();
        Callable<List<ClassInfo>> task = null;
        List<DiffEntry> cutList = null;
        //  Break down the data of each thread
        for (int i = 0; i < threadNum; i++) {
            if (i == threadNum - 1) {
                if (special) {
                    break;
                }
                cutList = diffs.subList(threadSize * i, dataSize);
            } else {
                cutList = diffs.subList(threadSize * i, threadSize * (i + 1));
            }
            final List<DiffEntry> diffEntryList = cutList;
            task = new Callable<List<ClassInfo>>() {
                public List<ClassInfo> call() throws Exception {
                    List<ClassInfo> allList = new ArrayList<ClassInfo>();
                    for (DiffEntry diffEntry : diffEntryList) {
                        ClassInfo classInfo = prepareDiffMethodForTag(gitAdapter, newTag, oldTag, df, diffEntry);
                        if (classInfo != null) {
                            allList.add(classInfo);
                        }
                    }
                    return allList;
                }
            };
            // There is a corresponding relationship between the task container list submitted here and the returned Future list
            tasks.add(task);
        }
        List<ClassInfo> allClassInfoList = new ArrayList<ClassInfo>();
        try {
            List<Future<List<ClassInfo>>> results = executorService.invokeAll(tasks);
            //Summary of results
            for (Future<List<ClassInfo>> future : results ) {
                allClassInfoList.addAll(future.get());
            }
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            // Close thread pool
            executorService.shutdown();
        }
        return allClassInfoList;
    }

    /**
     * Multi-threaded execution comparison
     */
    private static List<ClassInfo> batchPrepareDiffMethodForCommit(final GitAdapter gitAdapter, final String newCommitId, final String oldCommitId, final DiffFormatter df, List<DiffEntry> diffs) {
        int threadSize = 100;
        int dataSize = diffs.size();
        int threadNum = dataSize / threadSize + 1;
        boolean special = dataSize % threadSize == 0;
        ExecutorService executorService = Executors.newFixedThreadPool(threadNum);

        List<Callable<List<ClassInfo>>> tasks = new ArrayList<Callable<List<ClassInfo>>>();
        Callable<List<ClassInfo>> task = null;
        List<DiffEntry> cutList = null;
        //  Break down the data of each thread
        for (int i = 0; i < threadNum; i++) {
            if (i == threadNum - 1) {
                if (special) {
                    break;
                }
                cutList = diffs.subList(threadSize * i, dataSize);
            } else {
                cutList = diffs.subList(threadSize * i, threadSize * (i + 1));
            }
            final List<DiffEntry> diffEntryList = cutList;
            task = new Callable<List<ClassInfo>>() {
                public List<ClassInfo> call() throws Exception {
                    List<ClassInfo> allList = new ArrayList<ClassInfo>();
                    for (DiffEntry diffEntry : diffEntryList) {
                        ClassInfo classInfo = prepareDiffMethodForCommit(gitAdapter, newCommitId, oldCommitId, df, diffEntry);
                        if (classInfo != null) {
                            allList.add(classInfo);
                        }
                    }
                    return allList;
                }
            };
            // There is a corresponding relationship between the task container list submitted here and the returned Future list
            tasks.add(task);
        }
        List<ClassInfo> allClassInfoList = new ArrayList<ClassInfo>();
        try {
            List<Future<List<ClassInfo>>> results = executorService.invokeAll(tasks);
            //Summary of results
            for (Future<List<ClassInfo>> future : results ) {
                allClassInfoList.addAll(future.get());
            }
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            // Close thread pool
            executorService.shutdown();
        }
        return allClassInfoList;
    }

    /**
     * Single difference file comparison
     */
    private synchronized static ClassInfo prepareDiffMethodForTag(GitAdapter gitAdapter, String newTag, String oldTag, DiffFormatter df, DiffEntry diffEntry) {
        List<MethodInfo> methodInfoList = new ArrayList<MethodInfo>();
        try {
            String newJavaPath = diffEntry.getNewPath();
            //  Exclude test class
            if (newJavaPath.contains("/src/test/java/")) {
                return null;
            }
            //  Not java files and delete types are not recorded
            if (!newJavaPath.endsWith(".java") || diffEntry.getChangeType() == DiffEntry.ChangeType.DELETE){
                return null;
            }
            String newClassContent = gitAdapter.getTagRevisionSpecificFileContent(newTag,newJavaPath);
            ASTGenerator newAstGenerator = new ASTGenerator(newClassContent);
            /*  ADD type   */
            if (diffEntry.getChangeType() == DiffEntry.ChangeType.ADD) {
                return newAstGenerator.getClassInfo();
            }
            /*  Modify type  */
            //  Get the file difference position, so as to count the number of different lines, such as increasing the number of lines, reducing the number of lines
            FileHeader fileHeader = df.toFileHeader(diffEntry);
            List<int[]> addLines = new ArrayList<int[]>();
            List<int[]> delLines = new ArrayList<int[]>();
            EditList editList = fileHeader.toEditList();
            for(Edit edit : editList){
                if (edit.getLengthA() > 0) {
                    delLines.add(new int[]{edit.getBeginA(), edit.getEndA()});
                }
                if (edit.getLengthB() > 0 ) {
                    addLines.add(new int[]{edit.getBeginB(), edit.getEndB()});
                }
            }
            String oldJavaPath = diffEntry.getOldPath();
            String oldClassContent = gitAdapter.getTagRevisionSpecificFileContent(oldTag,oldJavaPath);
            ASTGenerator oldAstGenerator = new ASTGenerator(oldClassContent);
            MethodDeclaration[] newMethods = newAstGenerator.getMethods();
            MethodDeclaration[] oldMethods = oldAstGenerator.getMethods();
            Map<String, MethodDeclaration> methodsMap = new HashMap<String, MethodDeclaration>();
            for (int i = 0; i < oldMethods.length; i++) {
                methodsMap.put(oldMethods[i].getName().toString()+ oldMethods[i].parameters().toString(), oldMethods[i]);
            }
            for (final MethodDeclaration method : newMethods) {
                // If the method name is new, add the method directly to the List
                if (!ASTGenerator.isMethodExist(method, methodsMap)) {
                    MethodInfo methodInfo = newAstGenerator.getMethodInfo(method);
                    methodInfoList.add(methodInfo);
                    continue;
                }
                // If both versions have this method, judge whether the method is consistent according to MD5
                if (!ASTGenerator.isMethodTheSame(method, methodsMap.get(method.getName().toString()+ method.parameters().toString()))) {
                    MethodInfo methodInfo =  newAstGenerator.getMethodInfo(method);
                    methodInfoList.add(methodInfo);
                }
            }
            return newAstGenerator.getClassInfo(methodInfoList, addLines, delLines);
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Single difference file comparison
     */
    private synchronized static ClassInfo prepareDiffMethodForCommit(GitAdapter gitAdapter, String log1, String log2, DiffFormatter df, DiffEntry diffEntry) {
        List<MethodInfo> methodInfoList = new ArrayList<MethodInfo>();
        try {
            String newJavaPath = diffEntry.getNewPath();
            if (newJavaPath.contains("/src/test/java/")) {
                return null;
            }
            if (!newJavaPath.endsWith(".java") || diffEntry.getChangeType() == DiffEntry.ChangeType.DELETE){
                return null;
            }
            String newClassContent = gitAdapter.getCommitLogSpecificFileContent(log1,newJavaPath);
            ASTGenerator newAstGenerator = new ASTGenerator(newClassContent);
            if (diffEntry.getChangeType() == DiffEntry.ChangeType.ADD) {
                return newAstGenerator.getClassInfo();
            }
            FileHeader fileHeader = df.toFileHeader(diffEntry);
            List<int[]> addLines = new ArrayList<int[]>();
            List<int[]> delLines = new ArrayList<int[]>();
            EditList editList = fileHeader.toEditList();
            for(Edit edit : editList){
                if (edit.getLengthA() > 0) {
                    delLines.add(new int[]{edit.getBeginA(), edit.getEndA()});
                }
                if (edit.getLengthB() > 0 ) {
                    addLines.add(new int[]{edit.getBeginB(), edit.getEndB()});
                }
            }
            String oldJavaPath = diffEntry.getOldPath();
            String oldClassContent = gitAdapter.getCommitLogSpecificFileContent(log2,oldJavaPath);
            ASTGenerator oldAstGenerator = new ASTGenerator(oldClassContent);
            MethodDeclaration[] newMethods = newAstGenerator.getMethods();
            MethodDeclaration[] oldMethods = oldAstGenerator.getMethods();
            Map<String, MethodDeclaration> methodsMap = new HashMap<String, MethodDeclaration>();
            for (int i = 0; i < oldMethods.length; i++) {
                methodsMap.put(oldMethods[i].getName().toString()+ oldMethods[i].parameters().toString(), oldMethods[i]);
            }
            for (final MethodDeclaration method : newMethods) {
                if (!ASTGenerator.isMethodExist(method, methodsMap)) {
                    MethodInfo methodInfo = newAstGenerator.getMethodInfo(method);
                    methodInfoList.add(methodInfo);
                    continue;
                }
                if (!ASTGenerator.isMethodTheSame(method, methodsMap.get(method.getName().toString()+ method.parameters().toString()))) {
                    MethodInfo methodInfo =  newAstGenerator.getMethodInfo(method);
                    methodInfoList.add(methodInfo);
                }
            }
            return newAstGenerator.getClassInfo(methodInfoList, addLines, delLines);
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Multi-threaded execution comparison
     */
    private static List<ClassInfo> batchPrepareDiffMethod(final GitAdapter gitAdapter, final String branchName, final String oldBranchName, final DiffFormatter df, List<DiffEntry> diffs) {
        int threadSize = 100;
        int dataSize = diffs.size();
        int threadNum = dataSize / threadSize + 1;
        boolean special = dataSize % threadSize == 0;
        ExecutorService executorService = Executors.newFixedThreadPool(threadNum);

        List<Callable<List<ClassInfo>>> tasks = new ArrayList<Callable<List<ClassInfo>>>();
        Callable<List<ClassInfo>> task = null;
        List<DiffEntry> cutList = null;
        for (int i = 0; i < threadNum; i++) {
            if (i == threadNum - 1) {
                if (special) {
                    break;
                }
                cutList = diffs.subList(threadSize * i, dataSize);
            } else {
                cutList = diffs.subList(threadSize * i, threadSize * (i + 1));
            }
            final List<DiffEntry> diffEntryList = cutList;
            task = new Callable<List<ClassInfo>>() {
                public List<ClassInfo> call() throws Exception {
                    List<ClassInfo> allList = new ArrayList<ClassInfo>();
                    for (DiffEntry diffEntry : diffEntryList) {
                        ClassInfo classInfo = prepareDiffMethod(gitAdapter, branchName, oldBranchName, df, diffEntry);
                        if (classInfo != null) {
                            allList.add(classInfo);
                        }
                    }
                    return allList;
                }
            };
            tasks.add(task);
        }
        List<ClassInfo> allClassInfoList = new ArrayList<ClassInfo>();
        try {
            List<Future<List<ClassInfo>>> results = executorService.invokeAll(tasks);
            for (Future<List<ClassInfo>> future : results ) {
                allClassInfoList.addAll(future.get());
            }
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            executorService.shutdown();
        }
        return allClassInfoList;
    }

    /**
     * 单个差异文件对比
     */
    private synchronized static ClassInfo prepareDiffMethod(GitAdapter gitAdapter, String branchName, String oldBranchName, DiffFormatter df, DiffEntry diffEntry) {
        List<MethodInfo> methodInfoList = new ArrayList<MethodInfo>();
        try {
            String newJavaPath = diffEntry.getNewPath();
            if (newJavaPath.contains("/src/test/java/")) {
                return null;
            }
            if (!newJavaPath.endsWith(".java") || diffEntry.getChangeType() == DiffEntry.ChangeType.DELETE){
                return null;
            }
            String newClassContent = gitAdapter.getBranchSpecificFileContent(branchName,newJavaPath);
            ASTGenerator newAstGenerator = new ASTGenerator(newClassContent);
            if (diffEntry.getChangeType() == DiffEntry.ChangeType.ADD) {
                return newAstGenerator.getClassInfo();
            }
            FileHeader fileHeader = df.toFileHeader(diffEntry);
            List<int[]> addLines = new ArrayList<int[]>();
            List<int[]> delLines = new ArrayList<int[]>();
            EditList editList = fileHeader.toEditList();
            for(Edit edit : editList){
                if (edit.getLengthA() > 0) {
                    delLines.add(new int[]{edit.getBeginA(), edit.getEndA()});
                }
                if (edit.getLengthB() > 0 ) {
                    addLines.add(new int[]{edit.getBeginB(), edit.getEndB()});
                }
            }
            String oldJavaPath = diffEntry.getOldPath();
            String oldClassContent = gitAdapter.getBranchSpecificFileContent(oldBranchName,oldJavaPath);
            ASTGenerator oldAstGenerator = new ASTGenerator(oldClassContent);
            MethodDeclaration[] newMethods = newAstGenerator.getMethods();
            MethodDeclaration[] oldMethods = oldAstGenerator.getMethods();
            Map<String, MethodDeclaration> methodsMap = new HashMap<String, MethodDeclaration>();
            for (int i = 0; i < oldMethods.length; i++) {
                methodsMap.put(oldMethods[i].getName().toString()+ oldMethods[i].parameters().toString(), oldMethods[i]);
            }
            for (final MethodDeclaration method : newMethods) {
                if (!ASTGenerator.isMethodExist(method, methodsMap)) {
                    MethodInfo methodInfo = newAstGenerator.getMethodInfo(method);
                    methodInfoList.add(methodInfo);
                    continue;
                }
                if (!ASTGenerator.isMethodTheSame(method, methodsMap.get(method.getName().toString()+ method.parameters().toString()))) {
                    MethodInfo methodInfo =  newAstGenerator.getMethodInfo(method);
                    methodInfoList.add(methodInfo);
                }
            }
            return newAstGenerator.getClassInfo(methodInfoList, addLines, delLines);
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<ClassInfo> diffCommitToCommit(String gitPath, String branchName, String newCommitId, String oldCommitId) {
        try {
            Git git = Git.open(new File(gitPath));
            List<DiffEntry> diffs = getBranchDiffCommit(git, branchName, newCommitId, oldCommitId);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DiffFormatter df = new DiffFormatter(out);
            df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
            df.setRepository(git.getRepository());
            List<ClassInfo> allClassInfos = batchPrepareDiffMethodForCommit(new GitAdapter(gitPath), newCommitId, oldCommitId, df, diffs);
            return allClassInfos;
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return new ArrayList<ClassInfo>();
        }
    }

    public static List<DiffEntry> getBranchDiffCommit(Git git, String branchName, String newCommitId, String oldCommitId) {
        try {
            ObjectId objId = git.getRepository().resolve(branchName);
            Iterable<RevCommit> allCommitsLater = git.log().add(objId).call();
            Iterator<RevCommit> iter = allCommitsLater.iterator();
            TreeWalk tw = new TreeWalk(git.getRepository());

            RevCommit newCommit = null;
            RevCommit oldCommit = null;
            while (iter.hasNext()){
                RevCommit commit = iter.next();
                if(newCommit == null){
                    if(org.jacoco.core.util.StringUtils.startsWith(commit.getName(), newCommitId)){
                        newCommit = commit;
                    }
                } else if(org.jacoco.core.util.StringUtils.startsWith(commit.getName(), oldCommitId)){
                    oldCommit = commit;
                    break;
                }
            }
            if(newCommit == null || oldCommit == null){
                return new ArrayList<DiffEntry>();
            }
            tw.addTree(oldCommit.getTree());
            tw.addTree(newCommit.getTree());
            tw.setRecursive(true);
            RenameDetector rd = new RenameDetector(git.getRepository());
            rd.addAll(DiffEntry.scan(tw));
            return rd.compute();
        } catch (Exception e) {
            System.err.println(e.getLocalizedMessage());
            return new ArrayList<DiffEntry>();
        }
    }
}