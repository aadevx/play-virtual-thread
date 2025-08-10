package play.mvc.results;

import play.Play;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.utils.FileUtils;

import java.io.File;

public class RenderStatic extends Result {

    public final String file;
    public final File resolvedFile;

    public RenderStatic(String file) {
        String relativePath = file;
        this.resolvedFile = Play.getVirtualFile(file);
        if (resolvedFile != null && resolvedFile.exists() && resolvedFile.isDirectory()) {
            File vf = new File(resolvedFile, "index.html");
            if (vf != null) {
                relativePath = FileUtils.relativePath(vf);
            }
        }
        this.file = relativePath;
    }

    @Override
    public void apply(Request request, Response response) {
    }

}
