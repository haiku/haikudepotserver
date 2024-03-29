package org.haiku.haikudepotserver.dataobjects.auto;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.apache.cayenne.exp.property.EntityProperty;
import org.apache.cayenne.exp.property.ListProperty;
import org.apache.cayenne.exp.property.NumericProperty;
import org.apache.cayenne.exp.property.PropertyFactory;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.haiku.haikudepotserver.dataobjects.PkgIconImage;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.dataobjects.support.AbstractDataObject;

/**
 * Class _PkgIcon was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _PkgIcon extends AbstractDataObject {

    private static final long serialVersionUID = 1L;

    public static final String ID_PK_COLUMN = "id";

    public static final NumericProperty<Integer> SIZE = PropertyFactory.createNumeric("size", Integer.class);
    public static final EntityProperty<MediaType> MEDIA_TYPE = PropertyFactory.createEntity("mediaType", MediaType.class);
    public static final ListProperty<PkgIconImage> PKG_ICON_IMAGES = PropertyFactory.createList("pkgIconImages", PkgIconImage.class);
    public static final EntityProperty<PkgSupplement> PKG_SUPPLEMENT = PropertyFactory.createEntity("pkgSupplement", PkgSupplement.class);

    protected Integer size;

    protected Object mediaType;
    protected Object pkgIconImages;
    protected Object pkgSupplement;

    public void setSize(Integer size) {
        beforePropertyWrite("size", this.size, size);
        this.size = size;
    }

    public Integer getSize() {
        beforePropertyRead("size");
        return this.size;
    }

    public void setMediaType(MediaType mediaType) {
        setToOneTarget("mediaType", mediaType, true);
    }

    public MediaType getMediaType() {
        return (MediaType)readProperty("mediaType");
    }

    public void addToPkgIconImages(PkgIconImage obj) {
        addToManyTarget("pkgIconImages", obj, true);
    }

    public void removeFromPkgIconImages(PkgIconImage obj) {
        removeToManyTarget("pkgIconImages", obj, true);
    }

    @SuppressWarnings("unchecked")
    public List<PkgIconImage> getPkgIconImages() {
        return (List<PkgIconImage>)readProperty("pkgIconImages");
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
            case "size":
                return this.size;
            case "mediaType":
                return this.mediaType;
            case "pkgIconImages":
                return this.pkgIconImages;
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
            case "size":
                this.size = (Integer)val;
                break;
            case "mediaType":
                this.mediaType = val;
                break;
            case "pkgIconImages":
                this.pkgIconImages = val;
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
        out.writeObject(this.size);
        out.writeObject(this.mediaType);
        out.writeObject(this.pkgIconImages);
        out.writeObject(this.pkgSupplement);
    }

    @Override
    protected void readState(ObjectInputStream in) throws IOException, ClassNotFoundException {
        super.readState(in);
        this.size = (Integer)in.readObject();
        this.mediaType = in.readObject();
        this.pkgIconImages = in.readObject();
        this.pkgSupplement = in.readObject();
    }

}
