/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haiku.haikudepotserver.api1.model.miscellaneous.*;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;

@JsonRpcService("/__api/v1/miscellaneous")
public interface MiscellaneousApi {

    /**
     * <p>Returns a list of all of the categories.  If a natural language code is supplied in the reuqest, then
     * the results' names will be localized; otherwise a database-based default will be returned.</p>
     */

    GetAllPkgCategoriesResult getAllPkgCategories(GetAllPkgCategoriesRequest getAllPkgCategoriesRequest);

    /**
     * <p>Returns a list of all of the natural languages.  If a natural language code is supplied in the request
     * then the results' names will be localized; otherwise a database-based default will be returned.</p>
     */

    GetAllNaturalLanguagesResult getAllNaturalLanguages(GetAllNaturalLanguagesRequest getAllNaturalLanguagesRequest);

    /**
     * <p>This method will raise a runtime exception to test the behaviour of the server and client in this
     * situation.</p>
     */

    RaiseExceptionResult raiseException(RaiseExceptionRequest raiseExceptionRequest);

    /**
     * <p>This method will return information about the running application server.</p>
     */

    GetRuntimeInformationResult getRuntimeInformation(GetRuntimeInformationRequest getRuntimeInformationRequest);

    /**
     * <p>This method will return all of the localization messages that might be able to be displayed
     * to the user from the result of validation problems and so on.  This method will throw an instance of
     * {@link ObjectNotFoundException} if the natural language
     * specified in the request does not exist.</p>
     */

    GetAllMessagesResult getAllMessages(GetAllMessagesRequest getAllMessagesRequest) throws ObjectNotFoundException;

    /**
     * <P>This method will return a list of all of the possible architectures in the system such as x86 or arm.
     * Note that this will explicitly exclude the pseudo-architectures of "source" and "any".</p>
     */

    GetAllArchitecturesResult getAllArchitectures(GetAllArchitecturesRequest getAllArchitecturesRequest);

    /**
     * <p>This method will return all of the possible user rating stabilities that can be used when the user
     * rates a package version.  If a natural language code is supplied in the request then the results' names
     * will be localized; otherwise a database-based default will be used.</p>
     */

    GetAllUserRatingStabilitiesResult getAllUserRatingStabilities(GetAllUserRatingStabilitiesRequest getAllUserRatingStabilitiesRequest);

    /**
     * <p>This method will return all of the possible prominences.</p>
     */

    GetAllProminencesResult getAllProminences(GetAllProminencesRequest request);

    /**
     * <p>This method will return a feed URL based on the supplied information in the request.  If
     * any of the elements supplied in the request do not exist then this method will throw an
     * instance of {@link ObjectNotFoundException}.</p>
     */

    GenerateFeedUrlResult generateFeedUrl(GenerateFeedUrlRequest request) throws ObjectNotFoundException;

}
