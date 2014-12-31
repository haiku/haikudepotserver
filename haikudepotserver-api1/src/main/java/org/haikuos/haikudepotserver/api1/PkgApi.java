/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haikuos.haikudepotserver.api1.model.pkg.*;
import org.haikuos.haikudepotserver.api1.support.BadPkgIconException;
import org.haikuos.haikudepotserver.api1.support.LimitExceededException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;

/**
 * <p>This API is for access to packages and package versions.</p>
 */

@JsonRpcService("/api/v1/pkg")
public interface PkgApi {

    public final static Integer GETBULKPKG_LIMIT = 50;

    /**
     * <p>This method will ensure that the categories configured on the nominated package are as per the list of
     * packages.</p>
     */

    UpdatePkgCategoriesResult updatePkgCategories(UpdatePkgCategoriesRequest updatePkgCategoriesRequest) throws ObjectNotFoundException;

    /**
     * <p>This method can be invoked to get a list of all of the packages that match some search critera in the
     * request.</p>
     */

    SearchPkgsResult searchPkgs(SearchPkgsRequest request) throws ObjectNotFoundException;

    /**
     * <p>This method will return a package and the specified versions.  It will throw an
     * {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException} if the package was not able to be located.</p>
     */

    GetPkgResult getPkg(GetPkgRequest request) throws ObjectNotFoundException;

    /**
     * <p>Returns a list of meta-data regarding the icon data related to the pkg.  This does not contain the icon
     * data itself; just the meta data.</p>
     */

    GetPkgIconsResult getPkgIcons(GetPkgIconsRequest request) throws ObjectNotFoundException;

    /**
     * <p>This request will configure the icons for the package nominated.  Note that only certain configurations of
     * icon data may be acceptable; for example, it will require a 16x16px and 32x32px bitmap image.</p>
     */

    ConfigurePkgIconResult configurePkgIcon(ConfigurePkgIconRequest request) throws ObjectNotFoundException, BadPkgIconException;

    /**
     * <p>This request will remove any icons from the package.</p>
     */

    RemovePkgIconResult removePkgIcon(RemovePkgIconRequest request) throws ObjectNotFoundException;

    /**
     * <p>This method will get the details of a screenshot image.</p>
     */

    GetPkgScreenshotResult getPkgScreenshot(GetPkgScreenshotRequest request) throws ObjectNotFoundException;

    /**
     * <p>This method will return an ordered list of the screenshots that are available for this package.  It will
     * throw an {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException} in the case where the
     * nominated package is not able to be found.</p>
     */

    GetPkgScreenshotsResult getPkgScreenshots(GetPkgScreenshotsRequest getPkgScreenshotsRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will remove the nominated screenshot from the package.  If the screenshot is not able to be
     * found using the code supplied, the method will throw an instance of
     * {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException}.</p>
     */

    RemovePkgScreenshotResult removePkgScreenshot(RemovePkgScreenshotRequest removePkgScreenshotRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will reorder the screenshots related to the nominated package.  If any of the screenshots are
     * not accounted for, they will be ordered at the end in an indeterminate manner.  If the package is not able to be
     * found given the name supplied, an instance of
     * {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException} will be thrown.</p>
     */

    ReorderPkgScreenshotsResult reorderPkgScreenshots(ReorderPkgScreenshotsRequest reorderPkgScreenshotsRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will set the localized text stored against the <strong>latest</strong> version of the nominated
     * package.  If the package cannot be found then this method will throw an instance of
     * {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException}.  It is not possible to update the
     * localization on an older version and all fields are required.  It is not possible to edit the English
     * localization.</p>
     */

    UpdatePkgVersionLocalizationsResult updatePkgVersionLocalization(UpdatePkgVersionLocalizationsRequest updatePkgVersionLocalizationRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will return the package version localizations for the nominated package.  It will only return
     * the package version localizations for the most recent version on the package.</p>
     */

    GetPkgVersionLocalizationsResult getPkgVersionLocalizations(GetPkgVersionLocalizationsRequest getPkgVersionLocalizationsRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will obtain data about some named packages.  Note that the quantity of packages requested should
     * not exceed {@link #GETBULKPKG_LIMIT}; if it does exceed this limit then an instance of
     * {@link org.haikuos.haikudepotserver.api1.support.LimitExceededException} will be thrown.</p>
     *
     * <p>This limit can be obtained from the
     * {@link org.haikuos.haikudepotserver.api1.MiscellaneousApi#getRuntimeInformation(org.haikuos.haikudepotserver.api1.model.miscellaneous.GetRuntimeInformationRequest)}
     * method.
     * </p>
     *
     * <p>If a package was not able to be found then it will simply not appear in the results.  If reference data
     * objects such as the architecture was unable to be found then this method will throw an instance of
     * {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException}.</p>
     *
     * <p>The definition of architecture on this method is strict; will only return data for which there is
     * a version on that architecture.</p>
     *
     * <p>Various elements of the response can be filtered in or out by using the filter attribute on the request
     * object.</p>
     */

    GetBulkPkgResult getBulkPkg(GetBulkPkgRequest getBulkPkgRequest) throws LimitExceededException, ObjectNotFoundException;

    /**
     * <p>This method will update the prominence of the nominated package.  The prominence is identified by the
     * ordering of the prominence as a natural identifier.</p>
     */

    UpdatePkgProminenceResult updatePkgProminence(UpdatePkgProminenceRequest request) throws ObjectNotFoundException;

    /**
     * <p>Enqueues a request on behalf of the current user to produce a spreadsheet showing the coverage of categories
     * for the packages.  See the {@link org.haikuos.haikudepotserver.api1.JobApi} for details on how to control the
     * job.</p>
     */

    QueuePkgCategoryCoverageExportSpreadsheetJobResult queuePkgCategoryCoverageExportSpreadsheetJob(QueuePkgCategoryCoverageExportSpreadsheetJobRequest request);

    /**
     * <p>Enqueues a request on behalf of the current user to produce a spreadsheet showing which packages have icons
     * associated with them.  See the {@link org.haikuos.haikudepotserver.api1.JobApi} for details on how to control the
     * job.</p>
     */

    QueuePkgIconSpreadsheetJobResult queuePkgIconSpreadsheetJob(QueuePkgIconSpreadsheetJobRequest request);

    /**
     * <p>Enqueues a request on behalf of the current user to produce a spreadsheet showing which packages have what
     * prominence.  See the {@link org.haikuos.haikudepotserver.api1.JobApi} for details on how to control the
     * job.</p>
     */

    QueuePkgProminenceAndUserRatingSpreadsheetJobResult queuePkgProminenceAndUserRatingSpreadsheetJob(QueuePkgProminenceAndUserRatingSpreadsheetJobRequest request);

}
