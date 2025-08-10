package play.exceptions;

import org.yaml.snakeyaml.scanner.ScannerException;
import play.libs.IO;
import play.utils.FileUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class YAMLException extends PlayException implements SourceAttachment {

	final ScannerException e;
	final File yaml;

    public YAMLException(ScannerException e, File yaml) {
        super(e.getMessage() + " (in file " + FileUtils.relativePath(yaml) + " line " + (e.getProblemMark().getLine() + 1) + ", column " + (e.getProblemMark().getColumn() + 1) + ")", e);
        this.e = e;
        this.yaml = yaml;
    }

    @Override
    public String getErrorTitle() {
        return "Malformed YAML";
    }

    @Override
    public String getErrorDescription() {
        if (yaml == null) {
            return "Cannot parse the yaml file: " + e.getProblem();
        }
        return "Cannot parse the <strong>" + FileUtils.relativePath(yaml) + "</strong> file: " + e.getProblem();
    }

    @Override
    public Integer getLineNumber() {
        return e.getProblemMark().getLine() + 1;
    }

    @Override
    public List<String> getSource() {
        return Arrays.asList(IO.readContentAsString(yaml).split("\n"));
    }

    @Override
    public String getSourceFile() {
        return FileUtils.relativePath(yaml);
    }

    @Override
    public boolean isSourceAvailable() {
        return yaml != null && e.getProblemMark() != null;
    }
}
