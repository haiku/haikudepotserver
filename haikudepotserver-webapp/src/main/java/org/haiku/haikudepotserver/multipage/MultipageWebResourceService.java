package org.haiku.haikudepotserver.multipage;

import com.google.common.base.Suppliers;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * <p>This service helps with locating web resources such as images or CSS files etc...</p>
 */

@Service
public class MultipageWebResourceService {

    private final RuntimeInformationService runtimeInformationService;
    private final Supplier<WebResourcePathPrefixes> pathPrefixesSupplier;

    public MultipageWebResourceService(RuntimeInformationService runtimeInformationService) {
        this.runtimeInformationService = runtimeInformationService;
        this.pathPrefixesSupplier = Suppliers.memoize(this::createPathPrefixes);
    }

    public WebResourcePathPrefixes getPathPrefixes() {
        return pathPrefixesSupplier.get();
    }

    private WebResourcePathPrefixes createPathPrefixes() {
        String uniqingSegment = getUniquingSegment();
        return new WebResourcePathPrefixes(
                "/%s/%s/".formatted(WebConstants.SEGMENT_JS, uniqingSegment),
                "/%s/%s/".formatted(WebConstants.SEGMENT_CSS, uniqingSegment),
                "/%s/%s/".formatted(WebConstants.SEGMENT_IMG, uniqingSegment)
        );
    }

    private String getUniquingSegment() {
        HashFunction fn = Hashing.sha256();
        String version = runtimeInformationService.getProjectVersion();

        if (version.endsWith("-SNAPSHOT")) {
            return fn.hashLong(runtimeInformationService.getStartTimestamp()).toString().substring(0, 4);
        }

        return fn.hashString(version, StandardCharsets.US_ASCII).toString().substring(0, 4);
    }

}
