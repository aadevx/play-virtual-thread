package play.metrics;

import play.Logger;
import play.Play;
import play.libs.IO;

import java.io.InputStream;

/***
 * interface application information
 * app harus implement interface ini jika ingin memunculkan informasi app di metrics promotheus
 */
public interface ApplicationInfo {

    default String name() {
        return Play.configuration.getProperty("application.name");
    }

    default String version() {
        return "1.0";
    }

    default void restart() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        try {
            processBuilder.command(Play.applicationPath+name(),"restart");
            Process proc = processBuilder.start();
            InputStream is = proc.getInputStream();
            InputStream err = proc.getErrorStream();
            String str = IO.readContentAsString(is) + IO.readContent(err);
            Logger.info(str);
        }catch (Exception e) {
            Logger.error(e.getMessage());
        }
    }

}
