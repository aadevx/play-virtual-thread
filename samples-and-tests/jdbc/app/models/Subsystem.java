package models;

import javax.persistence.EnumType;
import play.db.jdbc.Enumerated;

@Enumerated(EnumType.STRING)
public enum Subsystem {
    CA("Cert Authority", "CAT", 0, "Certificate Authority"),
    EPNS("E-Proc nasional", "EPN", 0, "EProc Nasional"),
    JLIB("Java Library", "JLB", 0, "Java Library"),
    ROOT("Root System", "R", -1, "Root System");

    public final String name;
    public final String tlc;
    public final Integer builtin;
    public final String desc;

    private Subsystem(String name, String tlc, int builtin, String desc) {
        this.name = name;
        this.tlc = tlc;
        this.builtin = Integer.valueOf(builtin);
        this.desc = desc;
    }

}
