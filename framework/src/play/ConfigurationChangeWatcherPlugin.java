package play;

import java.io.File;

/**
 * Plugin used for tracking for application.conf changes
 */
public class ConfigurationChangeWatcherPlugin extends PlayPlugin {
    protected static long configLastModified = System.currentTimeMillis();

    @Override
    public void onApplicationStart() {
        configLastModified = System.currentTimeMillis();
    }

    @Override
    public void onConfigurationRead() {
        if (Play.mode.isProd()) {
            Play.pluginCollection.disablePlugin(this);
        }
    }

    @Override
    public void detectChange() {
        for (File conf : Play.confs) {
            if (conf.lastModified() > configLastModified) {
                configLastModified = conf.lastModified();
                onConfigurationFileChanged(conf);
            }
        }
    }

    protected void onConfigurationFileChanged(File conf) {
        throw new RuntimeException("Need to restart Play because " + conf.getAbsolutePath() + " has been changed");
    }
}
