/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.jte;

import com.google.common.base.Preconditions;
import gg.jte.Content;
import gg.jte.html.HtmlContent;
import jakarta.annotation.Nullable;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.web.util.UriComponentsBuilder;

public class RatingHelper {

    /**
     * <p>Renders some HTML with a series of star-icons that describe the rating from 1 to 5 stars.
     * Partial stars are also able to be shown.</p>
     */

    public static Content stars(@Nullable Number rating, int size) {
        Preconditions.checkArgument(size > 0);
        Preconditions.checkArgument(size <= 64);

        return (HtmlContent) output -> {
            if (rating != null) {
                float ratingFloat = rating.floatValue();

                for (int i = 0; i < 5; i++) {
                    output.writeContent("<img size=\"");
                    output.writeContent(Integer.toString(size));
                    output.writeContent("\" src=\"");
                    output.writeContent(starUrl(ratingFloat));
                    output.writeContent("\">");
                    ratingFloat -= 1.0f;
                }
            }
        };
    }

    private static String starUrl(float value) {
        return UriComponentsBuilder.newInstance()
                .pathSegment(WebConstants.SEGMENT_IMG, "star%s.svg".formatted(starLeafnameSuffix(value)))
                .build()
                .toString();
    }

    private static String starLeafnameSuffix(float value) {
        if (value > 0.5f) {
            return "on";
        }
        if (value > 0f) {
            return "half";
        }
        return "off";
    }


}
