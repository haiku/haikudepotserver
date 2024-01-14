/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.dataobjects.PkgSupplementModification;
import org.haiku.haikudepotserver.pkg.model.PkgSupplementModificationAgent;
import org.haiku.haikudepotserver.pkg.model.PkgSupplementModificationService;
import org.springframework.stereotype.Service;

@Service
public class PkgSupplementModificationServiceImpl implements PkgSupplementModificationService {

    @Override
    public PkgSupplementModification appendModification(
            ObjectContext context,
            PkgSupplement pkgSupplement,
            PkgSupplementModificationAgent agent,
            String content) {

        Preconditions.checkArgument(StringUtils.isNotBlank(content));
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != agent, "the user must be supplied");
        Preconditions.checkArgument(null != pkgSupplement, "the pkg supplement must be supplied");

        PkgSupplementModification pkgSupplementModification = context.newObject(PkgSupplementModification.class);
        pkgSupplementModification.setPkgSupplement(pkgSupplement);
        pkgSupplementModification.setUser(agent.getUser());
        pkgSupplementModification.setContent(content);
        pkgSupplementModification.setOriginSystemDescription(agent.getOriginSystemDescription());
        pkgSupplementModification.setUserDescription(agent.getUserDescription());

        return pkgSupplementModification;
    }
}
