/*
 * Copyright 2018-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.singlepage;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * <p>This map will keep track of the frequency at which templates are requested
 * to run the single page.  The most common ones can then be pre-populated into
 * the page.</p>
 */

@Component
public class SinglePageTemplateFrequencyMetrics {

    private Map<String, AtomicLong> templateCounter = new HashMap<>();

    private List<String> sortedTemplates;

    public synchronized void increment(String template) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(template), "a template name must be provided");
        AtomicLong atomicLong = templateCounter.get(template);

        if (null == atomicLong) {
            atomicLong = new AtomicLong();
            templateCounter.put(template, atomicLong);
        }

        atomicLong.incrementAndGet();
        sortedTemplates = null;
    }

    private List<String> getSortedTemplates() {
        if (null == sortedTemplates) {
            sortedTemplates = templateCounter
                    .keySet()
                    .stream()
                    .sorted((t1, t2) -> {
                        int c = -1 * Long.compare(templateCounter.get(t1).get(), templateCounter.get(t2).get());

                        if (0 == c) {
                            c = t1.compareTo(t2);
                        }

                        return c;
                    })
                    .collect(Collectors.toList());
        }

        return sortedTemplates;
    }

    /**
     * <p>Returns the top templates with a limit.  The templates are ordered on the
     * counters.  This method may return an empty list or a list smaller than the
     * limit if there is not too much data available.</p>
     */

    public synchronized List<String> top(int limit) {
        Preconditions.checkArgument(limit > 0, "the limit must be supplied");
        List<String> templates = getSortedTemplates();

        if (templates.size() > limit) {
            return templates.subList(0, limit);
        }

        return templates;
    }


}
