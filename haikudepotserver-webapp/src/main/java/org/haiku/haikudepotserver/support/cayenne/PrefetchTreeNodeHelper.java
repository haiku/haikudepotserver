/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.cayenne;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.CayenneRuntimeException;
import org.apache.cayenne.query.PrefetchTreeNode;

import java.io.IOException;

/**
 * <p>This material is generally not working in Cayenne 3.1, but looks like it is working in 3.2 (not released yet).</p>
 */

public class PrefetchTreeNodeHelper {

    public static void appendAsEJBQL(
            PrefetchTreeNode prefetchTreeNode,
            Appendable out,
            String rootId) throws IOException {

        Preconditions.checkArgument(null!=out,"appendable must be supplied to append the EJBQL to");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(rootId), "the root id must be supplied");

        if(null != prefetchTreeNode.getParent()) {
            out.append(" LEFT JOIN FETCH ");
            out.append(rootId);
            out.append(".");
            out.append(prefetchTreeNode.getName());
        }

        for(PrefetchTreeNode child : prefetchTreeNode.getChildren()) {
            appendAsEJBQL(child, out, rootId);
        }
    }

   public static String toEJBQL(PrefetchTreeNode prefetchTreeNode, String rootId) {

       Preconditions.checkArgument(null!=prefetchTreeNode, "the prefetch tree node must be supplied");
       Preconditions.checkArgument(!Strings.isNullOrEmpty(rootId), "the root id must be supplied");

       StringBuilder builder = new StringBuilder();

       try {
           appendAsEJBQL(prefetchTreeNode, builder, rootId);
       }
       catch(IOException ioe) {
           throw new CayenneRuntimeException("unexpected error appending to a " + StringBuilder.class.getSimpleName(), ioe);
       }

       return builder.toString();
   }

}
