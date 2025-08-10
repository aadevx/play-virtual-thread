package play.jte;

import gg.jte.compiler.ClassDefinition;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.*;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import play.Play;
import play.classloading.ApplicationClasses;
import play.exceptions.UnexpectedException;
import play.libs.IO;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

public class JteCompiler {

    private static final String JAVA_SOURCE_DEFAULT_VERSION = "21";
    static final Map<String, String> compatibleJavaVersions = Map.of(
            "21", CompilerOptions.VERSION_21
    );

    private final Path classDirectory;
    private final ClassLoader parentClassLoader;
    private final Map<String, Boolean> packagesCache = new HashMap<>();

    private final Map<String, String> settings;

    public JteCompiler(ClassLoader parentClassLoader, Path classDirectory) {
        this.classDirectory = classDirectory;
        this.parentClassLoader = parentClassLoader;
        final String configSourceVersion = Play.configuration.getProperty("java.source", JAVA_SOURCE_DEFAULT_VERSION);
        final String jdtVersion = compatibleJavaVersions.get(configSourceVersion);
        this.settings = Map.ofEntries(
                Map.entry(CompilerOptions.OPTION_ReportMissingSerialVersion, CompilerOptions.IGNORE),
                Map.entry(CompilerOptions.OPTION_LineNumberAttribute, CompilerOptions.GENERATE),
                Map.entry(CompilerOptions.OPTION_SourceFileAttribute, CompilerOptions.GENERATE),
                Map.entry(CompilerOptions.OPTION_ReportDeprecation, CompilerOptions.IGNORE),
                Map.entry(CompilerOptions.OPTION_ReportUnusedImport, CompilerOptions.IGNORE),
                Map.entry(CompilerOptions.OPTION_LocalVariableAttribute, CompilerOptions.GENERATE),
                Map.entry(CompilerOptions.OPTION_PreserveUnusedLocal, CompilerOptions.PRESERVE),
                Map.entry(CompilerOptions.OPTION_MethodParametersAttribute, CompilerOptions.GENERATE),
                Map.entry(CompilerOptions.OPTION_Encoding, "UTF-8"),
                Map.entry(CompilerOptions.OPTION_Source, jdtVersion),
                Map.entry(CompilerOptions.OPTION_TargetPlatform, jdtVersion),
                Map.entry(CompilerOptions.OPTION_Compliance, jdtVersion)
        );
    }

    static final class CompilationUnit implements ICompilationUnit {

        private final String fileName;
        private final char[] typeName;
        private final char[][] packageName;
        private final String source;

        CompilationUnit(String pClazzName, String source) {
            fileName = pClazzName.replace("\\.", "/") + ".java";//bogus
            int dot = pClazzName.lastIndexOf('.');
            if (dot > 0) {
                typeName = pClazzName.substring(dot + 1).toCharArray();
            } else {
                typeName = pClazzName.toCharArray();
            }
            StringTokenizer izer = new StringTokenizer(pClazzName, ".");
            packageName = new char[izer.countTokens() - 1][];
            for (int i = 0; i < packageName.length; i++) {
                packageName[i] = izer.nextToken().toCharArray();
            }

            this.source = source;
        }

        @Override public char[] getFileName() {
            return fileName.toCharArray();
        }

        @Override public char[] getContents() {
            return source.toCharArray();
        }

        @Override public char[] getMainTypeName() {
            return typeName;
        }

        @Override public char[][] getPackageName() {
            return packageName;
        }

        @Override public boolean ignoreOptionalProblems() {
            return false;
        }
    }

    public class MyICompilerRequestor implements ICompilerRequestor {


        @Override public void acceptResult(CompilationResult result) {
            // If error
            if (result.hasErrors()) {
                for (IProblem problem: result.getErrors()) {
                    String className = new String(problem.getOriginatingFileName()).replace("/", ".");
                    className = className.substring(0, className.length() - 5);
                    String message = problem.getMessage();
                    if (problem.getID() == IProblem.CannotImportPackage) {
                        // Non sense !
                        message = problem.getArguments()[0] + " cannot be resolved";
                    }
                    throw new UnexpectedException("Compile error. classname: " + className + ". message: " + message + " ln: " + problem.getSourceLineNumber());
                }
            }
            // Something has been compiled
            ClassFile[] clazzFiles = result.getClassFiles();
            for (ClassFile clazzFile : clazzFiles) {
                char[][] compoundName = clazzFile.getCompoundName();
                StringBuilder clazzName = new StringBuilder();
                for (int j = 0; j < compoundName.length; j++) {
                    if (j != 0) {
                        clazzName.append('.');
                    }
                    clazzName.append(compoundName[j]);
                }
                // write class
                String filename = clazzName.toString().replace('.', '/') + ".class";
                File file = new File(classDirectory.toFile(), filename);
//                file.getParentFile().mkdirs();
                IO.write(clazzFile.getBytes(), file);
            }
        }
    }

    /**
     * Please compile this className
     */
    @SuppressWarnings("deprecation")
    public void compile(LinkedHashSet<ClassDefinition> classDefinitions) {
        ICompilationUnit[] compilationUnits = new ICompilationUnit[classDefinitions.size()];
        int i=0;
        for (ClassDefinition cls : classDefinitions) {
            compilationUnits[i] = new CompilationUnit(cls.getName(), cls.getCode());
            i++;
        }
        IErrorHandlingPolicy policy = DefaultErrorHandlingPolicies.exitOnFirstError();
        IProblemFactory problemFactory = new DefaultProblemFactory(Locale.ENGLISH);

        /*
         * To find types ...
         */
        INameEnvironment nameEnvironment = new INameEnvironment() {

            @Override public NameEnvironmentAnswer findType(final char[][] compoundTypeName) {
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < compoundTypeName.length; i++) {
                    if (i != 0) {
                        result.append('.');
                    }
                    result.append(compoundTypeName[i]);
                }
                return findType(result.toString());
            }

            @Override public NameEnvironmentAnswer findType(final char[] typeName, final char[][] packageName) {
                StringBuilder result = new StringBuilder();
                for (char[] aPackageName : packageName) {
                    result.append(aPackageName);
                    result.append('.');
                }
                result.append(typeName);
                return findType(result.toString());
            }

            private NameEnvironmentAnswer findType(final String name) {
                try {
                    // first let the framework try to resolve
                    ApplicationClasses.ApplicationClass applicationClass = Play.classes.getApplicationClass(name);
                    if (applicationClass != null && applicationClass.javaByteCode != null) {
                        ClassFileReader classFileReader = new ClassFileReader(applicationClass.javaByteCode, name.toCharArray(), true);
                        return new NameEnvironmentAnswer(classFileReader, null);
                    }
                    String resourceName = name.replace(".", "/") + ".class";
                    InputStream is = parentClassLoader.getResourceAsStream(resourceName);
                    if(is != null) {
                        // Now look for our own classes
                        ClassFileReader classFileReader = new ClassFileReader(IO.readContent(is), name.toCharArray(), true);
                        return new NameEnvironmentAnswer(classFileReader, null);
                    }
                    File vf = new File(classDirectory.toFile(), resourceName);
                    if (vf.exists()) {
                        ClassFileReader classFileReader = new ClassFileReader(IO.readContent(vf), name.toCharArray(), true);
                        return new NameEnvironmentAnswer(classFileReader, null);
                    }
                    return null;
                } catch (Exception e) {
                    // Something very bad
                    throw new RuntimeException(e);
                }
            }

            public boolean isPackage(char[][] parentPackageName, char[] packageName) {
                // Rebuild something usable
                StringBuilder sb = new StringBuilder();
                if (parentPackageName != null) {
                    for (char[] p : parentPackageName) {
                        sb.append(new String(p));
                        sb.append(".");
                    }
                }

                String child = new String(packageName);
                sb.append(".");
                sb.append(child);
                String name = sb.toString();

                // Currently there is no complete package dictionary so a couple of simple
                // checks hopefully suffices.
                if (Character.isUpperCase(child.charAt(0))) {
                    // Typically only a class begins with a capital letter.
                    return false;
                }
                else if (packagesCache.containsKey(name)) {
                    // Check the cache if this was a class identified earlier.
                    return packagesCache.get(name);
                }
                // Check if there are .java or .class for this resource
                else if (parentClassLoader.getResource(name.replace('.', '/') + ".class") != null) {
                    packagesCache.put(name, false);
                    return false;
                }
                else if (Play.classes.getApplicationClass(name) != null) {
                    packagesCache.put(name, false);
                    return false;
                }
               else {
                    // Does there exist a class with this name?
                    try {
                        parentClassLoader.loadClass(name);
                        return false;
                    }
                    catch (Exception e) {
                        // nop
                    }
                }
                packagesCache.put(name, true);
                return true;
            }

            @Override public void cleanup() {
            }
        };


        MyICompilerRequestor compilerRequestor = new MyICompilerRequestor();

        /*
         * The JDT compiler
         */
        Compiler jdtCompiler = new Compiler(nameEnvironment, policy, settings, compilerRequestor, problemFactory) {

            @Override
            protected void handleInternalException(Throwable e, CompilationUnitDeclaration ud, CompilationResult result) {
            }
        };

        // Go !
        jdtCompiler.compile(compilationUnits);

    }
}
