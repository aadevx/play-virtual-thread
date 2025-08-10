package controllers;

import play.jte.AbstractPage;

public class Page extends AbstractPage {

    public Page(String templatename) {
        super(templatename);
    }

    public Long getTime() {
        return System.currentTimeMillis();
    }

    public String getLabel() {
        return "renderer";
    }
}
