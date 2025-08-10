package play.i18n;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.exceptions.UnexpectedException;
import play.libs.IO;
import play.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * Messages plugin
 */
public class MessagesPlugin extends PlayPlugin {

    static Long lastLoading = 0L;

    private static final List<String> includeMessageFilenames = new ArrayList<>();

    @Override
    public void onApplicationStart() {
        includeMessageFilenames.clear();
        Messages.defaults = new Properties();
        try {
            File message = new File(Play.frameworkPath, "resources/messages");
            Messages.defaults.putAll(read(message));
        } catch (Exception e) {
            Logger.warn("Default messages file missing: %s", e);
        }
        for (File module : Play.modules.values()) {
            File messages = new File(module, "conf/messages");
            URL resource = FileUtils.toURl(messages);
            if(Play.usePrecompiled && resource != null) {
                Messages.defaults.putAll(read(resource, "modules/"+module.getName()+"/conf"));
            } else if (messages != null && messages.exists() && !messages.isDirectory()) {
                Messages.defaults.putAll(read(messages));
            }
        }
        File appDM = Play.getVirtualFile( "conf/messages");
        URL resourceDM = Play.getResource("conf/messages");
        if(Play.usePrecompiled && resourceDM != null) {
            Messages.defaults.putAll(read(resourceDM, "conf"));
        } else if (appDM != null && appDM.exists() && !appDM.isDirectory()) {
            Messages.defaults.putAll(read(appDM));
        }
        for (String locale : Play.langs) {
            Properties properties = new Properties();
            for (File module : Play.modules.values()) {
                File messages = new File(module, "conf/messages." + locale);
                URL resource = FileUtils.toURl(messages);
                if(Play.usePrecompiled && resource != null) {
                    properties.putAll(read(resource, "modules/"+module.getName()+"/conf"));
                } else if (messages != null && messages.exists() && !messages.isDirectory()) {
                    properties.putAll(read(messages));
                }
            }
            File appM = Play.getVirtualFile( "conf/messages."+ locale);
            URL resourceM = Play.getResource("conf/messages."+locale);
            if( Play.usePrecompiled && resourceM != null) {
                properties.putAll(read(resourceM, "conf"));
            } else if (appM != null && appM.exists() && !appM.isDirectory()) {
                properties.putAll(read(appM));
            } else {
                Logger.warn("Messages file missing for locale %s", locale);
            }
            Messages.locales.put(locale, properties);
        }
        lastLoading = System.currentTimeMillis();
    }

    static Properties read(File file) {
        Properties propsFromFile = null;
        if (file != null && !file.isDirectory()) {
            InputStream inStream = null;
            try {
                inStream = new FileInputStream(file);
            } catch (Exception e) {
                throw new UnexpectedException(e);
            }
            propsFromFile = IO.readUtf8Properties(inStream);
            // Include
            Map<Object, Object> toInclude = new HashMap<>(16);
            for (Object key : propsFromFile.keySet()) {
                if (key.toString().startsWith("@include.")) {
                    try {
                        String filenameToInclude = propsFromFile.getProperty(key.toString());
                        File fileToInclude = getIncludeFile(file, filenameToInclude);
                        if (fileToInclude != null && fileToInclude.exists() && !fileToInclude.isDirectory()) {
                            // Check if the file was not previously read
                            if (!includeMessageFilenames.contains(fileToInclude.getAbsolutePath())) {
                                toInclude.putAll(read(fileToInclude));
                                includeMessageFilenames.add(fileToInclude.getAbsolutePath());
                            }
                        } else {
                            Logger.warn("Missing include: %s from file %s", filenameToInclude, file.getPath());
                        }
                    } catch (Exception ex) {
                        Logger.warn("Missing include: %s, caused by: %s", key, ex);
                    }
                }
            }
            propsFromFile.putAll(toInclude);
        }
        return propsFromFile;
    }

    static Properties read(URL file, String parent) {
        Properties propsFromFile = null;
        if (file != null) {
            try {
                propsFromFile = IO.readUtf8Properties(file.openStream());
                // Include
                Map<Object, Object> toInclude = new HashMap<>(16);
                for (Object key : propsFromFile.keySet()) {
                    if (key.toString().startsWith("@include.")) {
                        try {
                            String filenameToInclude = propsFromFile.getProperty(key.toString());
                            if(filenameToInclude.startsWith("./"))
                                filenameToInclude = filenameToInclude.substring(1);
                            if(!filenameToInclude.startsWith("/"))
                                filenameToInclude = "/"+filenameToInclude;
                            URL fileToInclude = Play.getResource(parent+filenameToInclude);
                            if (fileToInclude != null) {
                                // Check if the file was not previously read
                                if (!includeMessageFilenames.contains(parent+"/"+filenameToInclude)) {
                                    toInclude.putAll(read(fileToInclude, parent));
                                    includeMessageFilenames.add(parent+"/"+filenameToInclude);
                                }
                            } else {
                                Logger.warn("Missing include resource : %s from file %s", filenameToInclude, file.getPath());
                            }
                        } catch (Exception ex) {
                            Logger.warn("Missing include resource : %s, caused by: %s", key, ex);
                        }
                    }
                }
                propsFromFile.putAll(toInclude);
            } catch (IOException e) {

            }
        }
        Logger.debug("Message putAll : %s", propsFromFile);
        return propsFromFile;
    }

    private static File getIncludeFile(File file, String filenameToInclude) {
        if (file != null && filenameToInclude != null && !filenameToInclude.isEmpty()) {
            // Test absolute path
            File fileToInclude = new File(filenameToInclude);
            if (fileToInclude.isAbsolute()) {
                return fileToInclude;
            } else {
                return new File(file.getParent(), filenameToInclude); 
            }
        }
        return null;
    }

    @Override
    public void detectChange() {
        File vf = Play.getVirtualFile("conf/messages");
        if (vf != null && vf.exists() && !vf.isDirectory()
                && vf.lastModified() > lastLoading) {
            onApplicationStart();
            return;
        }
        for (File module : Play.modules.values()) {
            vf = new File(module, "conf/messages");
            if (vf != null && vf.exists() && !vf.isDirectory()
                    && vf.lastModified() > lastLoading) {
                onApplicationStart();
                return;
            }
        }
        for (String locale : Play.langs) {
            vf = Play.getVirtualFile("conf/messages." + locale);
            if (vf != null && vf.exists() && !vf.isDirectory()
                    && vf.lastModified() > lastLoading) {
                onApplicationStart();
                return;
            }
            for (File module : Play.modules.values()) {
                vf = new File(module, "conf/messages." + locale);
                if (vf != null && vf.exists() && !vf.isDirectory()
                        && vf.lastModified() > lastLoading) {
                    onApplicationStart();
                    return;
                }
            }
        }

        for (String includeFilename : includeMessageFilenames) {
            File fileToInclude = new File(includeFilename);
            if (fileToInclude != null && fileToInclude.exists()
                    && !fileToInclude.isDirectory()
                    && fileToInclude.lastModified() > lastLoading) {
                onApplicationStart();
                return;
            }
        }
    }
}