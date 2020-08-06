package org.jacoco.core.internal.diff;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import sun.misc.BASE64Encoder;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AST compiles java source files
 */
public class ASTGenerator {
    private String javaText;
    private CompilationUnit compilationUnit;

    public ASTGenerator(String javaText) {
        this.javaText = javaText;
        this.initCompilationUnit();
    }

    /**
     * Get the AST compilation unit, it load slowly first time
     */
    private void initCompilationUnit() {
        //  AST编译
        final ASTParser astParser = ASTParser.newParser(8);
        final Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
        astParser.setCompilerOptions(options);
        astParser.setKind(ASTParser.K_COMPILATION_UNIT);
        astParser.setResolveBindings(true);
        astParser.setBindingsRecovery(true);
        astParser.setStatementsRecovery(true);
        astParser.setSource(javaText.toCharArray());
        compilationUnit = (CompilationUnit) astParser.createAST(null);
    }

    /**
     * Get java class package name
     */
    public String getPackageName() {
        if (compilationUnit == null) {
            return "";
        }
        PackageDeclaration packageDeclaration = compilationUnit.getPackage();
        if (packageDeclaration == null){
            return "";
        }
        String packageName = packageDeclaration.getName().toString();
        return packageName;
    }

    /**
     * Get class unit
     */
    public TypeDeclaration getJavaClass() {
        if (compilationUnit == null) {
            return null;
        }
        TypeDeclaration typeDeclaration = null;
        final List<?> types = compilationUnit.types();
        for (final Object type : types) {
            if (type instanceof TypeDeclaration) {
                typeDeclaration = (TypeDeclaration) type;
                break;
            }
        }
        return typeDeclaration;
    }

    /**
     * Get all methods in java class
     * @return
     */
    public MethodDeclaration[] getMethods() {
        TypeDeclaration typeDec = getJavaClass();
        if (typeDec == null) {
            return new MethodDeclaration[]{};
        }
        MethodDeclaration[] methodDec = typeDec.getMethods();
        return methodDec;
    }

    /**
     * Get all method information in the new class
     */
    public List<MethodInfo> getMethodInfoList() {
        MethodDeclaration[] methodDeclarations = getMethods();
        List<MethodInfo> methodInfoList = new ArrayList<MethodInfo>();
        for (MethodDeclaration method: methodDeclarations) {
            MethodInfo methodInfo = new MethodInfo();
            setMethodInfo(methodInfo, method);
            methodInfoList.add(methodInfo);
        }
        return methodInfoList;
    }

    /**
     * Get the information of the modified type class and all methods in it, excluding the interface class
     */
    public ClassInfo getClassInfo(List<MethodInfo> methodInfos, List<int[]> addLines, List<int[]> delLines) {
        TypeDeclaration typeDec = getJavaClass();
        if (typeDec == null || typeDec.isInterface()) {
            return null;
        }
        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName(getJavaClass().getName().toString());
        classInfo.setPackages(getPackageName());
        classInfo.setMethodInfos(methodInfos);
        classInfo.setAddLines(addLines);
        classInfo.setDelLines(delLines);
        classInfo.setType("REPLACE");
        return classInfo;
    }

    /**
     * Get the information of the new type of class and all the methods in it, excluding the interface class
     */
    public ClassInfo getClassInfo() {
        TypeDeclaration typeDec = getJavaClass();
        if (typeDec == null || typeDec.isInterface()) {
            return null;
        }
        MethodDeclaration[] methodDeclarations = getMethods();
        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName(getJavaClass().getName().toString());
        classInfo.setPackages(getPackageName());
        classInfo.setType("ADD");
        List<MethodInfo> methodInfoList = new ArrayList<MethodInfo>();
        for (MethodDeclaration method: methodDeclarations) {
            MethodInfo methodInfo = new MethodInfo();
            setMethodInfo(methodInfo, method);
            methodInfoList.add(methodInfo);
        }
        classInfo.setMethodInfos(methodInfoList);
        return classInfo;
    }

    /**
     * Get the modified method
     */
    public MethodInfo getMethodInfo(MethodDeclaration methodDeclaration) {
        MethodInfo methodInfo = new MethodInfo();
        setMethodInfo(methodInfo, methodDeclaration);
        return methodInfo;
    }

    private void setMethodInfo(MethodInfo methodInfo,MethodDeclaration methodDeclaration) {
        methodInfo.setMd5(MD5Encode(methodDeclaration.toString()));
        methodInfo.setMethodName(methodDeclaration.getName().toString());
        methodInfo.setParameters(methodDeclaration.parameters().toString());
    }

    /**
     * MD5 value of calculation method
     */
    public static String MD5Encode(String s) {
        String MD5String = "";
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            BASE64Encoder base64en = new BASE64Encoder();
            MD5String = base64en.encode(md5.digest(s.getBytes("utf-8")));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return MD5String;
    }

    /**
     * Determine whether the method exists
     * @param method        the method in new branch
     * @param methodsMap    the method in master branch
     * @return
     */
    public static boolean isMethodExist(final MethodDeclaration method, final Map<String, MethodDeclaration> methodsMap) {
        // 方法名+参数一致才一致
        if (!methodsMap.containsKey(method.getName().toString() + method.parameters().toString())) {
            return false;
        }
        return true;
    }

    /**
     * Determine whether the method is consistent
     */
    public static boolean isMethodTheSame(final MethodDeclaration method1,final MethodDeclaration method2) {
        if (MD5Encode(method1.toString()).equals(MD5Encode(method2.toString()))) {
            return true;
        }
        return false;
    }
}