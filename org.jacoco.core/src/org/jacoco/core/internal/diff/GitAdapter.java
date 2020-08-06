package org.jacoco.core.internal.diff;

import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.*;
import java.util.*;

public class GitAdapter {
    private Git git;
    private Repository repository;
    private String gitFilePath;

    //  Git authorization
    private static UsernamePasswordCredentialsProvider usernamePasswordCredentialsProvider;

    public GitAdapter(String gitFilePath) {
        this.gitFilePath = gitFilePath;
        this.initGit(gitFilePath);
    }
    private void initGit(String gitFilePath) {
        try {
            git = Git.open(new File(gitFilePath));
            repository = git.getRepository();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getGitFilePath() {
        return gitFilePath;
    }

    public Git getGit() {
        return git;
    }

    public Repository getRepository() {
        return repository;
    }

    /**
     * git authorization. Need to set up users with all permissions
     * @param username
     * @param password
     */
    public static void setCredentialsProvider(String username, String password) {
        if(usernamePasswordCredentialsProvider == null || !usernamePasswordCredentialsProvider.isInteractive()){
            usernamePasswordCredentialsProvider = new UsernamePasswordCredentialsProvider(username,password);
        }
    }

    /**
     * Get the specified file content of the specified branch
     * @param branchName
     * @param javaPath
     * @return
     * @throws IOException
     */
    public String getBranchSpecificFileContent(String branchName, String javaPath) throws IOException {
        Ref branch = repository.exactRef("refs/heads/" + branchName);
        ObjectId objId = branch.getObjectId();
        RevWalk walk = new RevWalk(repository);
        RevTree tree = walk.parseTree(objId);
        return  getFileContent(javaPath,tree,walk);
    }

    /**
     * Get the specified file content of the specified tag version of the specified branch
     * @param tagRevision
     * @param javaPath
     * @return  java类
     * @throws IOException
     */
    public String getTagRevisionSpecificFileContent(String tagRevision, String javaPath) throws IOException {
        ObjectId objId = repository.resolve(tagRevision);
        RevWalk walk = new RevWalk(repository);
        RevCommit revCommit = walk.parseCommit(objId);
        RevTree tree = revCommit.getTree();
        return  getFileContent(javaPath,tree,walk);
    }

    public String getCommitLogSpecificFileContent(String gitLog, String javaPath) throws IOException {
        ObjectId objId = repository.resolve(gitLog);
        RevWalk walk = new RevWalk(repository);
        RevCommit revCommit = walk.parseCommit(objId);
        RevTree tree = revCommit.getTree();
        return  getFileContent(javaPath,tree,walk);
    }
    
    /**
     * Get the contents of the specified file specified by the specified branch
     * @param javaPath
     * @param tree          git RevTree
     * @param walk          git RevWalk
     * @return  java类
     * @throws IOException
     */
    private String getFileContent(String javaPath,RevTree tree,RevWalk walk) throws IOException {
        TreeWalk treeWalk = TreeWalk.forPath(repository, javaPath, tree);
        ObjectId blobId = treeWalk.getObjectId(0);
        ObjectLoader loader = repository.open(blobId);
        byte[] bytes = loader.getBytes();
        walk.dispose();
        return new String(bytes);
    }

    /**
     * Analyze branch tree structure information
     * @param localRef
     * @return
     * @throws IOException
     */
    public AbstractTreeIterator prepareTreeParser(Ref localRef) throws IOException {
        RevWalk walk = new RevWalk(repository);
        RevCommit commit = walk.parseCommit(localRef.getObjectId());
        RevTree tree = walk.parseTree(commit.getTree().getId());
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        ObjectReader reader = repository.newObjectReader();
        treeParser.reset(reader, tree.getId());
        walk.dispose();
        return treeParser;
    }
    /**
     *
     * @param branchName
     * @throws GitAPIException GitAPIException
     */
    public void checkOut(String branchName) throws GitAPIException {
        //  切换分支
        git.checkout().setCreateBranch(false).setName(branchName).call();
    }

    /**
     * check out branch code
     * @param localRef
     * @param branchName
     * @throws GitAPIException GitAPIException
     */
    public void checkOutAndPull(Ref localRef, String branchName) throws GitAPIException {
        boolean isCreateBranch = localRef == null;
        if (!isCreateBranch && checkBranchNewVersion(localRef)) {
            return;
        }
        //  check out
        git.checkout().setCreateBranch(isCreateBranch).setName(branchName).setStartPoint("origin/" + branchName).setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM).call();
        //  pull last code
        git.pull().setCredentialsProvider(usernamePasswordCredentialsProvider).call();
    }

    /**
     * Determine whether the local branch is the latest version. Currently, it is not considered that the branch does not exist in the remote warehouse but exists locally
     * @param localRef
     * @return  boolean
     * @throws GitAPIException GitAPIException
     */
    private boolean checkBranchNewVersion(Ref localRef) throws GitAPIException {
        String localRefName = localRef.getName();
        String localRefObjectId = localRef.getObjectId().getName();
        //  Get all remote branches
        Collection<Ref> remoteRefs = git.lsRemote().setCredentialsProvider(usernamePasswordCredentialsProvider).setHeads(true).call();
        for (Ref remoteRef : remoteRefs) {
            String remoteRefName = remoteRef.getName();
            String remoteRefObjectId = remoteRef.getObjectId().getName();
            if (remoteRefName.equals(localRefName)) {
                if (remoteRefObjectId.equals(localRefObjectId)) {
                    return true;
                }
                return false;
            }
        }
        return false;
    }
}