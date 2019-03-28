/*
 * Copyright 2014-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.markup;

import org.haiku.haikudepotserver.multipage.model.Pagination;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.tags.RequestContextAwareTag;
import org.springframework.web.servlet.tags.form.TagWriter;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.RequestDispatcher;
import javax.servlet.jsp.JspException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>This is a JSP tag that is able to render some hyperlinks that allow the user to browse around some linear list
 * of data items.  See {@link Pagination} for the model that produces
 * the series of page numbers.</p>
 */

public class PaginationLinksTag extends RequestContextAwareTag {

    private final static int LINK_COUNT_DEFAULT = 10;

    private Integer linkCount;

    private Pagination pagination;

    public Pagination getPagination() {
        return pagination;
    }

    public void setPagination(Pagination value) {
        this.pagination = value;
    }

    public Integer getLinkCount() {
        return linkCount;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLinkCount(Integer linkCount) {
        this.linkCount = linkCount;
    }

    private String deriveHref(int targetPage) {
        String path = (String) pageContext.getRequest().getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        UriComponentsBuilder builder = ServletUriComponentsBuilder.newInstance().path(path);
        builder.replaceQueryParam("o", Integer.toString(targetPage * getPagination().getMax()));
        return builder.build().toString();
    }

    private void writeArrow(
            TagWriter tagWriter,
            String imageFilename,
            String cssClassName,
            String alt,
            String href) throws JspException {
        tagWriter.startTag("li");
        tagWriter.startTag("a");
        tagWriter.writeAttribute("class", cssClassName);
        tagWriter.writeAttribute("href",href);
        tagWriter.writeAttribute("alt",alt);
        tagWriter.startTag("img");
        tagWriter.writeAttribute("src", "/__img/" + imageFilename);
        tagWriter.endTag(); // img
        tagWriter.endTag(); // a
        tagWriter.endTag(); // li
    }

    @Override
    protected int doStartTagInternal() throws Exception {

        TagWriter tagWriter = new TagWriter(pageContext.getOut());

        Pagination p = getPagination();

        List<String> ulClasses = new ArrayList<>();
        ulClasses.add("pagination-control-container");

        if (0 == p.getPage()) {
            ulClasses.add("pagination-control-on-first");
        }

        if (p.getPage() == p.getPages()-1) {
            ulClasses.add("pagination-control-on-last");
        }

        tagWriter.startTag("ul");
        tagWriter.writeAttribute("class", String.join(" ", ulClasses));

        writeArrow(
                tagWriter,
                "paginationleft.png",
                "pagination-control-left",
                "<--",
                0 == p.getPage() ? "" : deriveHref(p.getPage() - 1));

        int[] pageNumbers = p.generateSuggestedPages(null == getLinkCount() ? LINK_COUNT_DEFAULT : getLinkCount());

        for (int pageNumber : pageNumbers) {
            tagWriter.startTag("li");

            if (pageNumber == p.getPage()) {
                tagWriter.startTag("span");
                tagWriter.writeAttribute("class", "pagination-control-currentpage");
                tagWriter.writeAttribute("href", "");
            } else {
                tagWriter.startTag("a");
                tagWriter.writeAttribute("href", deriveHref(pageNumber));
            }

            tagWriter.appendValue(Integer.toString(pageNumber + 1));
            tagWriter.endTag(); // a
            tagWriter.endTag(); // li
        }

        writeArrow(
                tagWriter,
                "paginationright.png",
                "pagination-control-right",
                "-->",
                p.getPage() == p.getPages()-1 ? "" : deriveHref(p.getPage()+1));

        tagWriter.endTag(); // ul

        return SKIP_BODY;
    }
}
