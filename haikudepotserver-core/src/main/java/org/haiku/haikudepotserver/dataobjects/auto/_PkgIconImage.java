package org.haiku.haikudepotserver.dataobjects.auto;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.cayenne.exp.property.BaseProperty;
import org.apache.cayenne.exp.property.EntityProperty;
import org.apache.cayenne.exp.property.PropertyFactory;
import org.haiku.haikudepotserver.dataobjects.PkgIcon;
import org.haiku.haikudepotserver.dataobjects.support.AbstractDataObject;

/**
 * Class _PkgIconImage was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _PkgIconImage extends AbstractDataObject {

    private static final long serialVersionUID = 1L;

    public static final String ID_PK_COLUMN = "id";

    public static final BaseProperty<byte[]> DATA = PropertyFactory.createBase("data", byte[].class);
    public static final EntityProperty<PkgIcon> PKG_ICON = PropertyFactory.createEntity("pkgIcon", PkgIcon.class);

    protected byte[] data;

    protected Object pkgIcon;

    public void setData(byte[] data) {
        beforePropertyWrite("data", this.data, data);
        this.data = data;
    }

    public byte[] getData() {
        beforePropertyRead("data");
        return this.data;
    }

    public void setPkgIcon(PkgIcon pkgIcon) {
        setToOneTarget("pkgIcon", pkgIcon, true);
    }

    public PkgIcon getPkgIcon() {
        return (PkgIcon)readProperty("pkgIcon");
    }

    @Override
    public Object readPropertyDirectly(String propName) {
        if(propName == null) {
            throw new IllegalArgumentException();
        }

        switch(propName) {
            case "data":
                return this.data;
            case "pkgIcon":
                return this.pkgIcon;
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
            case "data":
                this.data = (byte[])val;
                break;
            case "pkgIcon":
                this.pkgIcon = val;
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
        out.writeObject(this.data);
        out.writeObject(this.pkgIcon);
    }

    @Override
    protected void readState(ObjectInputStream in) throws IOException, ClassNotFoundException {
        super.readState(in);
        this.data = (byte[])in.readObject();
        this.pkgIcon = in.readObject();
    }

}
