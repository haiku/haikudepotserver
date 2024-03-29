package org.haiku.haikudepotserver.dataobjects.auto;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Timestamp;
import java.util.List;

import org.apache.cayenne.exp.property.DateProperty;
import org.apache.cayenne.exp.property.EntityProperty;
import org.apache.cayenne.exp.property.ListProperty;
import org.apache.cayenne.exp.property.NumericProperty;
import org.apache.cayenne.exp.property.PropertyFactory;
import org.apache.cayenne.exp.property.StringProperty;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshotImage;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.dataobjects.support.AbstractDataObject;

/**
 * Class _PkgScreenshot was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _PkgScreenshot extends AbstractDataObject {

    private static final long serialVersionUID = 1L;

    public static final String ID_PK_COLUMN = "id";

    public static final StringProperty<String> CODE = PropertyFactory.createString("code", String.class);
    public static final DateProperty<Timestamp> CREATE_TIMESTAMP = PropertyFactory.createDate("createTimestamp", Timestamp.class);
    public static final StringProperty<String> HASH_SHA256 = PropertyFactory.createString("hashSha256", String.class);
    public static final NumericProperty<Integer> HEIGHT = PropertyFactory.createNumeric("height", Integer.class);
    public static final NumericProperty<Integer> LENGTH = PropertyFactory.createNumeric("length", Integer.class);
    public static final DateProperty<Timestamp> MODIFY_TIMESTAMP = PropertyFactory.createDate("modifyTimestamp", Timestamp.class);
    public static final NumericProperty<Integer> ORDERING = PropertyFactory.createNumeric("ordering", Integer.class);
    public static final NumericProperty<Integer> WIDTH = PropertyFactory.createNumeric("width", Integer.class);
    public static final ListProperty<PkgScreenshotImage> PKG_SCREENSHOT_IMAGES = PropertyFactory.createList("pkgScreenshotImages", PkgScreenshotImage.class);
    public static final EntityProperty<PkgSupplement> PKG_SUPPLEMENT = PropertyFactory.createEntity("pkgSupplement", PkgSupplement.class);

    protected String code;
    protected Timestamp createTimestamp;
    protected String hashSha256;
    protected Integer height;
    protected Integer length;
    protected Timestamp modifyTimestamp;
    protected Integer ordering;
    protected Integer width;

    protected Object pkgScreenshotImages;
    protected Object pkgSupplement;

    public void setCode(String code) {
        beforePropertyWrite("code", this.code, code);
        this.code = code;
    }

    public String getCode() {
        beforePropertyRead("code");
        return this.code;
    }

    public void setCreateTimestamp(Timestamp createTimestamp) {
        beforePropertyWrite("createTimestamp", this.createTimestamp, createTimestamp);
        this.createTimestamp = createTimestamp;
    }

    public Timestamp getCreateTimestamp() {
        beforePropertyRead("createTimestamp");
        return this.createTimestamp;
    }

    public void setHashSha256(String hashSha256) {
        beforePropertyWrite("hashSha256", this.hashSha256, hashSha256);
        this.hashSha256 = hashSha256;
    }

    public String getHashSha256() {
        beforePropertyRead("hashSha256");
        return this.hashSha256;
    }

    public void setHeight(Integer height) {
        beforePropertyWrite("height", this.height, height);
        this.height = height;
    }

    public Integer getHeight() {
        beforePropertyRead("height");
        return this.height;
    }

    public void setLength(Integer length) {
        beforePropertyWrite("length", this.length, length);
        this.length = length;
    }

    public Integer getLength() {
        beforePropertyRead("length");
        return this.length;
    }

    public void setModifyTimestamp(Timestamp modifyTimestamp) {
        beforePropertyWrite("modifyTimestamp", this.modifyTimestamp, modifyTimestamp);
        this.modifyTimestamp = modifyTimestamp;
    }

    public Timestamp getModifyTimestamp() {
        beforePropertyRead("modifyTimestamp");
        return this.modifyTimestamp;
    }

    public void setOrdering(Integer ordering) {
        beforePropertyWrite("ordering", this.ordering, ordering);
        this.ordering = ordering;
    }

    public Integer getOrdering() {
        beforePropertyRead("ordering");
        return this.ordering;
    }

    public void setWidth(Integer width) {
        beforePropertyWrite("width", this.width, width);
        this.width = width;
    }

    public Integer getWidth() {
        beforePropertyRead("width");
        return this.width;
    }

    public void addToPkgScreenshotImages(PkgScreenshotImage obj) {
        addToManyTarget("pkgScreenshotImages", obj, true);
    }

    public void removeFromPkgScreenshotImages(PkgScreenshotImage obj) {
        removeToManyTarget("pkgScreenshotImages", obj, true);
    }

    @SuppressWarnings("unchecked")
    public List<PkgScreenshotImage> getPkgScreenshotImages() {
        return (List<PkgScreenshotImage>)readProperty("pkgScreenshotImages");
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
            case "code":
                return this.code;
            case "createTimestamp":
                return this.createTimestamp;
            case "hashSha256":
                return this.hashSha256;
            case "height":
                return this.height;
            case "length":
                return this.length;
            case "modifyTimestamp":
                return this.modifyTimestamp;
            case "ordering":
                return this.ordering;
            case "width":
                return this.width;
            case "pkgScreenshotImages":
                return this.pkgScreenshotImages;
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
            case "code":
                this.code = (String)val;
                break;
            case "createTimestamp":
                this.createTimestamp = (Timestamp)val;
                break;
            case "hashSha256":
                this.hashSha256 = (String)val;
                break;
            case "height":
                this.height = (Integer)val;
                break;
            case "length":
                this.length = (Integer)val;
                break;
            case "modifyTimestamp":
                this.modifyTimestamp = (Timestamp)val;
                break;
            case "ordering":
                this.ordering = (Integer)val;
                break;
            case "width":
                this.width = (Integer)val;
                break;
            case "pkgScreenshotImages":
                this.pkgScreenshotImages = val;
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
        out.writeObject(this.code);
        out.writeObject(this.createTimestamp);
        out.writeObject(this.hashSha256);
        out.writeObject(this.height);
        out.writeObject(this.length);
        out.writeObject(this.modifyTimestamp);
        out.writeObject(this.ordering);
        out.writeObject(this.width);
        out.writeObject(this.pkgScreenshotImages);
        out.writeObject(this.pkgSupplement);
    }

    @Override
    protected void readState(ObjectInputStream in) throws IOException, ClassNotFoundException {
        super.readState(in);
        this.code = (String)in.readObject();
        this.createTimestamp = (Timestamp)in.readObject();
        this.hashSha256 = (String)in.readObject();
        this.height = (Integer)in.readObject();
        this.length = (Integer)in.readObject();
        this.modifyTimestamp = (Timestamp)in.readObject();
        this.ordering = (Integer)in.readObject();
        this.width = (Integer)in.readObject();
        this.pkgScreenshotImages = in.readObject();
        this.pkgSupplement = in.readObject();
    }

}
