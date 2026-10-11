/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.security.model.Permission;

public class PermissionMapper {

    public static Permission mapFromDataObjectPermission(org.haiku.haikudepotserver.dataobjects.Permission permission) {
        Preconditions.checkArgument(permission != null);
        return Permission.valueOf(permission.getCode().toUpperCase());
    }

    public static org.haiku.haikudepotserver.api2.model.Permission mapToApiPermission(Permission permission) {
        Preconditions.checkArgument(permission != null);
        return switch (permission) {
            case Permission.REPOSITORY_VIEW -> org.haiku.haikudepotserver.api2.model.Permission.REPOSITORY_VIEW;
            case Permission.REPOSITORY_EDIT -> org.haiku.haikudepotserver.api2.model.Permission.REPOSITORY_EDIT;
            case Permission.REPOSITORY_IMPORT -> org.haiku.haikudepotserver.api2.model.Permission.REPOSITORY_IMPORT;
            case Permission.REPOSITORY_LIST -> org.haiku.haikudepotserver.api2.model.Permission.REPOSITORY_LIST;
            case Permission.REPOSITORY_LIST_INACTIVE -> org.haiku.haikudepotserver.api2.model.Permission.REPOSITORY_LIST_INACTIVE;
            case Permission.REPOSITORY_ADD -> org.haiku.haikudepotserver.api2.model.Permission.REPOSITORY_ADD;
            case Permission.USER_VIEWJOBS -> org.haiku.haikudepotserver.api2.model.Permission.USER_VIEWJOBS;
            case Permission.USER_VIEW -> org.haiku.haikudepotserver.api2.model.Permission.USER_VIEW;
            case Permission.USER_EDIT -> org.haiku.haikudepotserver.api2.model.Permission.USER_EDIT;
            case Permission.USER_CHANGEPASSWORD -> org.haiku.haikudepotserver.api2.model.Permission.USER_CHANGEPASSWORD;
            case Permission.USER_LIST -> org.haiku.haikudepotserver.api2.model.Permission.USER_LIST;
            case Permission.USER_AGREE_USAGE_CONDITIONS -> org.haiku.haikudepotserver.api2.model.Permission.USER_AGREE_USAGE_CONDITIONS;
            case Permission.AUTHORIZATION_CONFIGURE -> org.haiku.haikudepotserver.api2.model.Permission.AUTHORIZATION_CONFIGURE;
            case Permission.USERRATING_EDIT -> org.haiku.haikudepotserver.api2.model.Permission.USERRATING_EDIT;
            case Permission.USERRATING_REMOVE -> org.haiku.haikudepotserver.api2.model.Permission.USERRATING_REMOVE;
            case Permission.USERRATING_DERIVEANDSTOREFORPKG -> org.haiku.haikudepotserver.api2.model.Permission.USERRATING_DERIVEANDSTOREFORPKG;
            case Permission.PKG_CREATEUSERRATING -> org.haiku.haikudepotserver.api2.model.Permission.PKG_CREATEUSERRATING;
            case Permission.PKG_EDITICON -> org.haiku.haikudepotserver.api2.model.Permission.PKG_EDITICON;
            case Permission.PKG_EDITSCREENSHOT -> org.haiku.haikudepotserver.api2.model.Permission.PKG_EDITSCREENSHOT;
            case Permission.PKG_EDITCATEGORIES -> org.haiku.haikudepotserver.api2.model.Permission.PKG_EDITCATEGORIES;
            case Permission.PKG_EDITLOCALIZATION -> org.haiku.haikudepotserver.api2.model.Permission.PKG_EDITLOCALIZATION;
            case Permission.PKG_EDITPROMINENCE -> org.haiku.haikudepotserver.api2.model.Permission.PKG_EDITPROMINENCE;
            case Permission.PKG_EDITCHANGELOG -> org.haiku.haikudepotserver.api2.model.Permission.PKG_EDITCHANGELOG;
            case Permission.PKG_EDITVERSION -> org.haiku.haikudepotserver.api2.model.Permission.PKG_EDITVERSION;
            case Permission.PKG_EDITNATIVEDESKTOP -> org.haiku.haikudepotserver.api2.model.Permission.PKG_EDITNATIVEDESKTOP;
            case Permission.BULK_PKGCATEGORYCOVERAGEEXPORTSPREADSHEET -> org.haiku.haikudepotserver.api2.model.Permission.BULK_PKGCATEGORYCOVERAGEEXPORTSPREADSHEET;
            case Permission.BULK_PKGCATEGORYCOVERAGEIMPORTSPREADSHEET -> org.haiku.haikudepotserver.api2.model.Permission.BULK_PKGCATEGORYCOVERAGEIMPORTSPREADSHEET;
            case Permission.BULK_PKGPROMINENCEANDUSERRATINGSPREADSHEETREPORT ->
                    org.haiku.haikudepotserver.api2.model.Permission.BULK_PKGPROMINENCEANDUSERRATINGSPREADSHEETREPORT;
            case Permission.BULK_PKGVERSIONLOCALIZATIONCOVERAGEEXPORTSPREADSHEET ->
                    org.haiku.haikudepotserver.api2.model.Permission.BULK_PKGVERSIONLOCALIZATIONCOVERAGEEXPORTSPREADSHEET;
            case Permission.BULK_PKGLOCALIZATIONCOVERAGEEXPORTSPREADSHEET ->
                    org.haiku.haikudepotserver.api2.model.Permission.BULK_PKGLOCALIZATIONCOVERAGEEXPORTSPREADSHEET;
            case Permission.BULK_PKGICONSPREADSHEETREPORT -> org.haiku.haikudepotserver.api2.model.Permission.BULK_PKGICONSPREADSHEETREPORT;
            case Permission.BULK_PKGSCREENSHOTSPREADSHEETREPORT -> org.haiku.haikudepotserver.api2.model.Permission.BULK_PKGSCREENSHOTSPREADSHEETREPORT;
            case Permission.BULK_PKGSCREENSHOTEXPORTARCHIVE -> org.haiku.haikudepotserver.api2.model.Permission.BULK_PKGSCREENSHOTEXPORTARCHIVE;
            case Permission.BULK_PKGSCREENSHOTIMPORTARCHIVE -> org.haiku.haikudepotserver.api2.model.Permission.BULK_PKGSCREENSHOTIMPORTARCHIVE;
            case Permission.BULK_PKGICONEXPORTARCHIVE -> org.haiku.haikudepotserver.api2.model.Permission.BULK_PKGICONEXPORTARCHIVE;
            case Permission.BULK_PKGICONIMPORTARCHIVE -> org.haiku.haikudepotserver.api2.model.Permission.BULK_PKGICONIMPORTARCHIVE;
            case Permission.BULK_USERRATINGSPREADSHEETREPORT_PKG -> org.haiku.haikudepotserver.api2.model.Permission.BULK_USERRATINGSPREADSHEETREPORT_PKG;
            case Permission.BULK_USERRATINGSPREADSHEETREPORT_ALL -> org.haiku.haikudepotserver.api2.model.Permission.BULK_USERRATINGSPREADSHEETREPORT_ALL;
            case Permission.BULK_USERRATINGSPREADSHEETREPORT_USER -> org.haiku.haikudepotserver.api2.model.Permission.BULK_USERRATINGSPREADSHEETREPORT_USER;
            case Permission.BULK_PKGVERSIONPAYLOADDATAPOPULATION -> org.haiku.haikudepotserver.api2.model.Permission.BULK_PKGVERSIONPAYLOADDATAPOPULATION;
            case Permission.JOBS_VIEW -> org.haiku.haikudepotserver.api2.model.Permission.JOBS_VIEW;
            case Permission.SHUTDOWN_ALL_INSTANCES -> org.haiku.haikudepotserver.api2.model.Permission.SHUTDOWN_ALL_INSTANCES;
        };
    }

    public static Permission mapFromApiPermission(org.haiku.haikudepotserver.api2.model.Permission permission) {
        Preconditions.checkArgument(permission != null);
        return switch (permission) {
            case REPOSITORY_VIEW -> Permission.REPOSITORY_VIEW;
            case REPOSITORY_EDIT -> Permission.REPOSITORY_EDIT;
            case REPOSITORY_IMPORT -> Permission.REPOSITORY_IMPORT;
            case REPOSITORY_LIST -> Permission.REPOSITORY_LIST;
            case REPOSITORY_LIST_INACTIVE -> Permission.REPOSITORY_LIST_INACTIVE;
            case REPOSITORY_ADD -> Permission.REPOSITORY_ADD;
            case USER_VIEWJOBS -> Permission.USER_VIEWJOBS;
            case USER_VIEW -> Permission.USER_VIEW;
            case USER_EDIT -> Permission.USER_EDIT;
            case USER_CHANGEPASSWORD -> Permission.USER_CHANGEPASSWORD;
            case USER_LIST -> Permission.USER_LIST;
            case USER_AGREE_USAGE_CONDITIONS -> Permission.USER_AGREE_USAGE_CONDITIONS;
            case AUTHORIZATION_CONFIGURE -> Permission.AUTHORIZATION_CONFIGURE;
            case USERRATING_EDIT -> Permission.USERRATING_EDIT;
            case USERRATING_REMOVE -> Permission.USERRATING_REMOVE;
            case USERRATING_DERIVEANDSTOREFORPKG -> Permission.USERRATING_DERIVEANDSTOREFORPKG;
            case PKG_CREATEUSERRATING -> Permission.PKG_CREATEUSERRATING;
            case PKG_EDITICON -> Permission.PKG_EDITICON;
            case PKG_EDITSCREENSHOT -> Permission.PKG_EDITSCREENSHOT;
            case PKG_EDITCATEGORIES -> Permission.PKG_EDITCATEGORIES;
            case PKG_EDITLOCALIZATION -> Permission.PKG_EDITLOCALIZATION;
            case PKG_EDITPROMINENCE -> Permission.PKG_EDITPROMINENCE;
            case PKG_EDITCHANGELOG -> Permission.PKG_EDITCHANGELOG;
            case PKG_EDITVERSION -> Permission.PKG_EDITVERSION;
            case PKG_EDITNATIVEDESKTOP -> Permission.PKG_EDITNATIVEDESKTOP;
            case BULK_PKGCATEGORYCOVERAGEEXPORTSPREADSHEET -> Permission.BULK_PKGCATEGORYCOVERAGEEXPORTSPREADSHEET;
            case BULK_PKGCATEGORYCOVERAGEIMPORTSPREADSHEET -> Permission.BULK_PKGCATEGORYCOVERAGEIMPORTSPREADSHEET;
            case BULK_PKGPROMINENCEANDUSERRATINGSPREADSHEETREPORT ->
                    Permission.BULK_PKGPROMINENCEANDUSERRATINGSPREADSHEETREPORT;
            case BULK_PKGVERSIONLOCALIZATIONCOVERAGEEXPORTSPREADSHEET ->
                    Permission.BULK_PKGVERSIONLOCALIZATIONCOVERAGEEXPORTSPREADSHEET;
            case BULK_PKGLOCALIZATIONCOVERAGEEXPORTSPREADSHEET ->
                    Permission.BULK_PKGLOCALIZATIONCOVERAGEEXPORTSPREADSHEET;
            case BULK_PKGICONSPREADSHEETREPORT -> Permission.BULK_PKGICONSPREADSHEETREPORT;
            case BULK_PKGSCREENSHOTSPREADSHEETREPORT -> Permission.BULK_PKGSCREENSHOTSPREADSHEETREPORT;
            case BULK_PKGSCREENSHOTEXPORTARCHIVE -> Permission.BULK_PKGSCREENSHOTEXPORTARCHIVE;
            case BULK_PKGSCREENSHOTIMPORTARCHIVE -> Permission.BULK_PKGSCREENSHOTIMPORTARCHIVE;
            case BULK_PKGICONEXPORTARCHIVE -> Permission.BULK_PKGICONEXPORTARCHIVE;
            case BULK_PKGICONIMPORTARCHIVE -> Permission.BULK_PKGICONIMPORTARCHIVE;
            case BULK_USERRATINGSPREADSHEETREPORT_PKG -> Permission.BULK_USERRATINGSPREADSHEETREPORT_PKG;
            case BULK_USERRATINGSPREADSHEETREPORT_ALL -> Permission.BULK_USERRATINGSPREADSHEETREPORT_ALL;
            case BULK_USERRATINGSPREADSHEETREPORT_USER -> Permission.BULK_USERRATINGSPREADSHEETREPORT_USER;
            case BULK_PKGVERSIONPAYLOADDATAPOPULATION -> Permission.BULK_PKGVERSIONPAYLOADDATAPOPULATION;
            case JOBS_VIEW -> Permission.JOBS_VIEW;
            case SHUTDOWN_ALL_INSTANCES -> Permission.SHUTDOWN_ALL_INSTANCES;
        };
    }

}
