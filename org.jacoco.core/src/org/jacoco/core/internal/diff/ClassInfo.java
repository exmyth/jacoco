package org.jacoco.core.internal.diff;

import java.util.List;

public class ClassInfo {
    /**
     * java file
     */
    private String classFile;
    /**
     * class name
     */
    private String className;
    /**
     * package name
     */
    private String packages;

    /**
     * the method in class
     */
    private List<MethodInfo> methodInfos;

    /**
     * Number of rows added
     */
    private List<int[]> addLines;

    /**
     * Number of rows deleted
     */
    private List<int[]> delLines;

    /**
     * Modify type
     */
    private String type;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<int[]> getAddLines() {
        return addLines;
    }

    public void setAddLines(List<int[]> addLines) {
        this.addLines = addLines;
    }

    public List<int[]> getDelLines() {
        return delLines;
    }

    public void setDelLines(List<int[]> delLines) {
        this.delLines = delLines;
    }

    public String getClassFile() {
        return classFile;
    }

    public void setClassFile(String classFile) {
        this.classFile = classFile;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getPackages() {
        return packages;
    }

    public void setPackages(String packages) {
        this.packages = packages;
    }

    public List<MethodInfo> getMethodInfos() {
        return methodInfos;
    }

    public void setMethodInfos(List<MethodInfo> methodInfos) {
        this.methodInfos = methodInfos;
    }
}