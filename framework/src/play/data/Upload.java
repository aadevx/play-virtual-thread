package play.data;

import java.io.File;
import java.io.InputStream;

public interface Upload {

    byte[] asBytes();
    InputStream asStream();
    String getContentType();
    String getFileName();
    String getFieldName();
    Long getSize();
    boolean isInMemory();
    File asFile();
}
