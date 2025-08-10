package play.libs.ws;

import play.libs.MimeTypes;

import java.io.File;

public final class FileParam {
    final File file;
    final String paramName;
    final String contentType;

    public FileParam(File file, String name) {
        this.file = file;
        this.paramName = name;
        this.contentType = MimeTypes.getMimeType(file.getName());
    }

    public File file() {
        return file;
    }

    public String paramName() {
        return paramName;
    }

    public String contentType() {
        return contentType;
    }

    public static FileParam[] getFileParams(File... files) {
        FileParam[] filesp = new FileParam[files.length];
        for (int i = 0; i < files.length; i++) {
            filesp[i] = new FileParam(files[i], files[i].getName());
        }
        return filesp;
    }
}
