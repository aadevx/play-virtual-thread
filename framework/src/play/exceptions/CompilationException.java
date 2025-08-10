package play.exceptions;

import play.libs.IO;
import play.utils.FileUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * A java compilation error
 */
public class CompilationException extends PlayException implements SourceAttachment {

    private final String problem;
    private File source;
    private Integer line;
    private Integer start;
    private Integer end;

    public CompilationException(String problem) {
        super(problem);
        this.problem = problem;
    }

    public CompilationException(File source, String problem, int line, int start, int end) {
        super(problem);
        this.problem = problem;
        this.line = line;
        this.source = source;
        this.start = start;
        this.end = end;
    }

    @Override
    public String getErrorTitle() {
        return "Compilation error";
    }

    @Override
    public String getErrorDescription() {
        return String.format("The file <strong>%s</strong> could not be compiled.\nError raised is : <strong>%s</strong>", isSourceAvailable() ? FileUtils.relativePath(source) : "", problem.replace("<", "&lt;"));
    }
    
    @Override
    public String getMessage() {
        return problem;
    }

    @Override
    public List<String> getSource() {
        String sourceCode = IO.readContentAsString(source);
        if (start != -1 && end != -1) {
            if (start.equals(end)) {
                sourceCode = sourceCode.substring(0, start + 1) + "↓" + sourceCode.substring(end + 1);
            } else {
                sourceCode = sourceCode.substring(0, start) + "\000" + sourceCode.substring(start, end + 1) + "\001" + sourceCode.substring(end + 1);
            }
        }
        return Arrays.asList(sourceCode.split("\n"));
    }

    @Override
    public Integer getLineNumber() {
        return line;
    }

    @Override
    public String getSourceFile() {
        return FileUtils.relativePath(source);
    }

    public Integer getSourceStart() {
        return start;
    }

    public Integer getSourceEnd() {
        return end;
    }

    @Override
    public boolean isSourceAvailable() {
        return source != null && line != null;
    }
}
