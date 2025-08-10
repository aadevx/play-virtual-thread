package play.data.parsing;

import org.apache.commons.lang3.StringUtils;
import play.Play;
import play.PlayPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;

/**
 *  Creates temporary folders for file parsing, and deletes
 *  it after request completion.
 */
public class TempFilePlugin extends PlayPlugin {

    private static final AtomicLong count = new AtomicLong();

    public static final ThreadLocal<File> tempFolder = new ThreadLocal<>();

    public static File createTempFolder() {
        if (Play.tmpDir == null) {
            return null;
        }
        if (tempFolder.get() == null) {
            File file = new File(Play.tmpDir +
                    File.separator + "uploads" + File.separator +
                    System.currentTimeMillis() + "_" + StringUtils.leftPad(String.valueOf(count.getAndIncrement()), 10, '0'));
            file.mkdirs();
            tempFolder.set(file);
        }
        return tempFolder.get();
    }

    @Override
    public void onInvocationSuccess() {
        File file = tempFolder.get();
        if (file != null) {
            tempFolder.remove();
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
            }
        }
    }
}
