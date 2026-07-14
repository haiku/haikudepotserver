/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.jte;

/**
 * <p>A disclosure in this sense is a UI element that is able to show or hide some content
 * depending on the state of a toggle. This avoids unnecessary content being shown to the
 * user, but if they need it, they can opt to disclose it and view it.</p>
 */

public class DisclosureHelper {

    private final static String CLASS_BASE = "common-disclosure";
    private final static String KLASS_DISCLOSURE_SHOWN = "shown";

    public static String disclosureClass(boolean shown) {
        return CLASS_BASE + (shown ? " %s".formatted(KLASS_DISCLOSURE_SHOWN) : "");
    }

}
