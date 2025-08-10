package play.utils;

import org.apache.commons.codec.digest.DigestUtils;
import play.Logger;
import play.Play;
import play.libs.Crypto;
import play.libs.Json;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * @author Arief Ardiyansah
 * untuk integrity checking source
 */
@Deprecated
public class Checksum {

//    static final List<String> pathsToCheck = Arrays.asList("public", "precompiled/templates", "precompiled/java", "conf/messages", "app/views", "conf/routes", "modules");

    // check integrity sistem
    public static void check() {
        if (!Play.getFile("precompiled").exists() && Play.getResource("precompiled") == null)
            return;
        Logger.info("Integrity checking ...");
        File checksumFile = new File(Play.applicationPath, "/conf/integrity");
        if(!checksumFile.exists()){
            throw new RuntimeException("File Integrity tidak ditemukan");
        }
        try {
            String checksum, checksumHash;
            try (FileInputStream fis = new FileInputStream(checksumFile);
                 BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
                 checksum = br.readLine();
                 checksumHash = br.readLine();
                 if(!DigestUtils.md5Hex(checksum).equals(checksumHash))
                    throw new RuntimeException("Terjadi Perubahan File Integrity");
            }
            checksum = Crypto.decryptAES(checksum);
            Map<String, String> checksumMap = Json.fromJson(checksum, Map.class);

            checksumMap.forEach((key, value) -> {
                File file = new File(Play.applicationPath,  key);
                if(!file.exists())
                    throw new RuntimeException("Terjadi Perubahan Content Aplikasi, file "+key+" tidak ditemukan");
                try(FileInputStream is = new FileInputStream(file)) {
                    String hashFile = DigestUtils.md5Hex(is);
                    if (!value.equals(hashFile))
                        throw new RuntimeException("Terjadi Perubahan Content Aplikasi, file " + key + " tidak sama");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }catch (Exception e) {
            Logger.error(e, "Integrity checking Gagal : %s", e.getMessage());
            throw new RuntimeException("Integrity checking Gagal");
        }
    }
}
