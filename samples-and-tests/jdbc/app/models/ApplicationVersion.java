package models;

import play.metrics.ApplicationInfo;

public class ApplicationVersion implements ApplicationInfo {
    @Override
    public String name() {
        return "JDBC";
    }

    @Override
    public String version() {
        return "2.0";
    }
}
