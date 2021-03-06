package com.credits.wallet.desktop.utils;

import com.credits.wallet.desktop.exception.CompilationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Pattern;

public class SimpleInMemoryCompiler {

    private final static Logger LOGGER = LoggerFactory.getLogger(SimpleInMemoryCompiler.class);
    private final static String SOURCE_FOLDER_PATH = System.getProperty("user.dir") + File.separator + "temp" + File.separator;

    public static byte[] compile(String sourceString, String classname, String token) throws CompilationException {
        File sourceFolder = new File(SOURCE_FOLDER_PATH + token);

        File source = save(sourceFolder, classname, sourceString);

        LOGGER.debug("Compiling class {}", source.getName());
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            loadJdkPathFromEnvironmentVariables();
            compiler = ToolProvider.getSystemJavaCompiler();
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        StandardJavaFileManager stdFileManager = compiler.getStandardFileManager(null, null, null);

        Iterable<? extends JavaFileObject> compilationUnits = stdFileManager.getJavaFileObjectsFromFiles(Collections.singletonList(source));

        CompilationTask task = compiler.getTask(null, null, diagnostics, null, null, compilationUnits);
        Boolean isCompiled = task.call();

        if (!isCompiled) {
            StringBuilder errorMessage = new StringBuilder();
            for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
                LOGGER.error("Error on line {} in {}. Message: {}", diagnostic.getLineNumber(), diagnostic.getSource(),
                    diagnostic.getMessage(null));
                errorMessage.append(String.format("Error on line %d. Message: %s\n", diagnostic.getLineNumber(),
                    diagnostic.getMessage(null)));
            }
            throw new CompilationException("Cannot compile the file: " + source.getName() + "\n" + errorMessage.toString());
        }

        try {
            stdFileManager.close();
        } catch (IOException e) {
            LOGGER.error("Cannot close file manager", e);
        }

        byte[] sourceBytes;
        try {
            File classFile = new File(sourceFolder + File.separator + classname + ".class");
            sourceBytes = Files.readAllBytes(classFile.toPath());
        } catch (IOException e) {
            throw new CompilationException("Cannot read bytes from source file.", e);
        }

        File[] files = sourceFolder.listFiles();
        if(files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        sourceFolder.delete();

        return sourceBytes;
    }

    static void loadJdkPathFromEnvironmentVariables() throws CompilationException {
        Pattern regexpJdkPath = Pattern.compile("jdk[\\d]\\.[\\d]\\.[\\d]([\\d._\\\\])*bin");
        Arrays.stream(System.getenv("Path").split(";"))
            .filter(it -> regexpJdkPath.matcher(it).find())
            .findFirst()
            .orElseThrow(() -> new CompilationException("Cannot compile the file. The java compiler has not been found, Java Development Kit should be installed."));
    }

    private static File save(File sourceFolder, String classname, String sourceString) throws CompilationException {
        byte[] sourceBytes = sourceString.getBytes();
        File sourceFile = new File(sourceFolder + File.separator + classname + ".java");
        try {
            Path parentDir = sourceFile.toPath().getParent();
            if (!Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            Files.write(sourceFile.toPath(), sourceBytes);
        } catch (IOException e) {
            throw new CompilationException("Cannot save source to file " + sourceFile.getPath(), e);
        }

        return sourceFile;
    }
}
