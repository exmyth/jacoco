/*******************************************************************************
 * Copyright (c) 2009, 2020 Mountainminds GmbH & Co. KG and Contributors
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jacoco.core.internal.analysis.BundleCoverageImpl;
import org.jacoco.core.internal.analysis.SourceFileCoverageImpl;
import org.jacoco.core.internal.diff.ClassInfo;
import org.jacoco.core.internal.diff.CodeDiff;

/**
 * Builder for hierarchical {@link ICoverageNode} structures from single
 * {@link IClassCoverage} nodes. The nodes are feed into the builder through its
 * {@link ICoverageVisitor} interface. Afterwards the aggregated data can be
 * obtained with {@link #getClasses()}, {@link #getSourceFiles()} or
 * {@link #getBundle(String)} in the following hierarchy:
 *
 * <pre>
 * {@link IBundleCoverage}
 * +-- {@link IPackageCoverage}*
 *     +-- {@link IClassCoverage}*
 *     +-- {@link ISourceFileCoverage}*
 * </pre>
 */
public class CoverageBuilder implements ICoverageVisitor {

	private final Map<String, IClassCoverage> classes;

	private final Map<String, ISourceFileCoverage> sourcefiles;

	public static List<ClassInfo> classInfos;    // 新增的成员变量

	/**
	 * Create a new builder.
	 *
	 */
	public CoverageBuilder() {
		this.classes = new HashMap<String, IClassCoverage>();
		this.sourcefiles = new HashMap<String, ISourceFileCoverage>();
	}

	/**
	 * Returns all class nodes currently contained in this builder.
	 *
	 * @return all class nodes
	 */
	public Collection<IClassCoverage> getClasses() {
		return Collections.unmodifiableCollection(classes.values());
	}

	/**
	 * Returns all source file nodes currently contained in this builder.
	 *
	 * @return all source file nodes
	 */
	public Collection<ISourceFileCoverage> getSourceFiles() {
		return Collections.unmodifiableCollection(sourcefiles.values());
	}

	/**
	 * Creates a bundle from all nodes currently contained in this bundle.
	 *
	 * @param name
	 *            Name of the bundle
	 * @return bundle containing all classes and source files
	 */
	public IBundleCoverage getBundle(final String name) {
		return new BundleCoverageImpl(name, classes.values(),
				sourcefiles.values());
	}

	/**
	 * Returns all classes for which execution data does not match.
	 *
	 * @see IClassCoverage#isNoMatch()
	 * @return collection of classes with non-matching execution data
	 */
	public Collection<IClassCoverage> getNoMatchClasses() {
		final Collection<IClassCoverage> result = new ArrayList<IClassCoverage>();
		for (final IClassCoverage c : classes.values()) {
			if (c.isNoMatch()) {
				result.add(c);
			}
		}
		return result;
	}

	// === ICoverageVisitor ===

	public void visitCoverage(final IClassCoverage coverage) {
		final String name = coverage.getName();
		final IClassCoverage dup = classes.put(name, coverage);
		if (dup != null) {
			if (dup.getId() != coverage.getId()) {
				System.err.println("Can't add different class with same name: " + name);
			}
		} else {
			final String source = coverage.getSourceFileName();
			if (source != null) {
				final SourceFileCoverageImpl sourceFile = getSourceFile(source,
						coverage.getPackageName());
				sourceFile.increment(coverage);
			}
		}
	}

	private SourceFileCoverageImpl getSourceFile(final String filename,
			final String packagename) {
		final String key = packagename + '/' + filename;
		SourceFileCoverageImpl sourcefile = (SourceFileCoverageImpl) sourcefiles
				.get(key);
		if (sourcefile == null) {
			sourcefile = new SourceFileCoverageImpl(filename, packagename);
			sourcefiles.put(key, sourcefile);
		}
		return sourcefile;
	}

	/**
	 * 分支与master对比
	 * @param gitPath local gitPath
	 * @param branchName new test branch name
	 */
	public CoverageBuilder(String gitPath, String branchName) {
		this.classes = new HashMap<String, IClassCoverage>();
		this.sourcefiles = new HashMap<String, ISourceFileCoverage>();
		classInfos = CodeDiff.diffBranchToBranch(gitPath, branchName,CodeDiff.MASTER);
	}

	/**
	 * 分支与分支之间对比
	 * @param gitPath local gitPath
	 * @param newBranchName newBranchName
	 * @param oldBranchName oldBranchName
	 */
	public CoverageBuilder(String gitPath, String newBranchName, String oldBranchName) {
		this.classes = new HashMap<String, IClassCoverage>();
		this.sourcefiles = new HashMap<String, ISourceFileCoverage>();
		classInfos = CodeDiff.diffBranchToBranch(gitPath, newBranchName, oldBranchName);
	}

	public CoverageBuilder(String gitPath, String newBranchName, String oldBranchName, List<String> packageExclusionList, List<String> nameExclusionList) {
		this.classes = new HashMap<String, IClassCoverage>();
		this.sourcefiles = new HashMap<String, ISourceFileCoverage>();
		List<ClassInfo> classInfoList = CodeDiff.diffBranchToBranch(gitPath, newBranchName, oldBranchName);
		this.classInfos = excludeClass(classInfoList, packageExclusionList, nameExclusionList);
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

	public CoverageBuilder(String gitPath, String branchName, String log1, String log2, List<String> packageExclusionList, List<String> nameExclusionList) {
		this.classes = new HashMap<String, IClassCoverage>();
		this.sourcefiles = new HashMap<String, ISourceFileCoverage>();
		List<ClassInfo> classInfoList = CodeDiff.diffLogToBranch(gitPath, branchName, log1, log2);
		this.classInfos = excludeClass(classInfoList, packageExclusionList, nameExclusionList);
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

	/**
	 * tag与tag之间对比
	 * @param gitPath local gitPath
	 * @param branchName develop branchName
	 * @param newTag new Tag
	 * @param oldTag old Tag
	 */
	public CoverageBuilder(String gitPath, String branchName, String newTag, String oldTag) {
		this.classes = new HashMap<String, IClassCoverage>();
		this.sourcefiles = new HashMap<String, ISourceFileCoverage>();
		classInfos = CodeDiff.diffTagToTag(gitPath,branchName, newTag, oldTag);
	}

}
