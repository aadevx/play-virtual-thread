package play.db.jdbc;

import java.io.Serializable;

public abstract class GenericModel implements Serializable {


    // set anything before save
    protected void prePersist() {

    }
    // set load after created;
    protected void postLoad() {

    }

    // do something before delete;
    protected void preDelete() {

    }

    // refresh set object model , setup postLoad
    public void refresh() {
        postLoad();
    }

    // set anything after save
    protected void postPersist() {

    }
}
