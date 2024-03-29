package org.haiku.haikudepotserver.dataobjects.auto;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.cayenne.exp.property.EntityProperty;
import org.apache.cayenne.exp.property.PropertyFactory;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.dataobjects.support.AbstractDataObject;

/**
 * Class _PkgPkgCategory was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _PkgPkgCategory extends AbstractDataObject {

    private static final long serialVersionUID = 1L;

    public static final String ID_PK_COLUMN = "id";

    public static final EntityProperty<PkgCategory> PKG_CATEGORY = PropertyFactory.createEntity("pkgCategory", PkgCategory.class);
    public static final EntityProperty<PkgSupplement> PKG_SUPPLEMENT = PropertyFactory.createEntity("pkgSupplement", PkgSupplement.class);


    protected Object pkgCategory;
    protected Object pkgSupplement;

    public void setPkgCategory(PkgCategory pkgCategory) {
        setToOneTarget("pkgCategory", pkgCategory, true);
    }

    public PkgCategory getPkgCategory() {
        return (PkgCategory)readProperty("pkgCategory");
    }

    public void setPkgSupplement(PkgSupplement pkgSupplement) {
        setToOneTarget("pkgSupplement", pkgSupplement, true);
    }

    public PkgSupplement getPkgSupplement() {
        return (PkgSupplement)readProperty("pkgSupplement");
    }

    @Override
    public Object readPropertyDirectly(String propName) {
        if(propName == null) {
            throw new IllegalArgumentException();
        }

        switch(propName) {
            case "pkgCategory":
                return this.pkgCategory;
            case "pkgSupplement":
                return this.pkgSupplement;
            default:
                return super.readPropertyDirectly(propName);
        }
    }

    @Override
    public void writePropertyDirectly(String propName, Object val) {
        if(propName == null) {
            throw new IllegalArgumentException();
        }

        switch (propName) {
            case "pkgCategory":
                this.pkgCategory = val;
                break;
            case "pkgSupplement":
                this.pkgSupplement = val;
                break;
            default:
                super.writePropertyDirectly(propName, val);
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        writeSerialized(out);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        readSerialized(in);
    }

    @Override
    protected void writeState(ObjectOutputStream out) throws IOException {
        super.writeState(out);
        out.writeObject(this.pkgCategory);
        out.writeObject(this.pkgSupplement);
    }

    @Override
    protected void readState(ObjectInputStream in) throws IOException, ClassNotFoundException {
        super.readState(in);
        this.pkgCategory = in.readObject();
        this.pkgSupplement = in.readObject();
    }

}
