/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.user.model;

import org.apache.directory.api.ldap.model.name.Dn;

/**
 * <p>Provides an easily used model object to represent a person as stored in the LDAP directory.</p>
 */

public class LdapPerson {

    /**
     * <p>This is the distinguished name that uniquely identifies the user.</p>
     */

    private Dn dn;

    private String cn;
    private String mail;
    private String sn;
    private String uid;
    private String userPassword;

    public Dn getDn() {
        return dn;
    }

    public void setDn(Dn dn) {
        this.dn = dn;
    }

    public String getCn() {
        return cn;
    }

    public void setCn(String cn) {
        this.cn = cn;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    @Override
    public String toString() {
        return "ldap-person; " + (null!=getDn() ? getDn() : "???");
    }

}
