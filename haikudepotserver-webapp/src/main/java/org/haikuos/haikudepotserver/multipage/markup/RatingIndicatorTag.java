/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.multipage.markup;

import org.springframework.web.servlet.tags.RequestContextAwareTag;
import org.springframework.web.servlet.tags.form.TagWriter;

/**
 * <p>This tag renders an HTML structure that represents a number of stars that represents a user rating value.
 * This value has to be within 0 to 5.</p>
 */

public class RatingIndicatorTag extends RequestContextAwareTag {

    private Float value;

    public Float getValue() {
        return value;
    }

    public void setValue(Float value) {
        this.value = value;
    }

    @Override
    protected int doStartTagInternal() throws Exception {

        TagWriter tagWriter = new TagWriter(pageContext.getOut());

        if(null!=getValue()) {

            if(getValue() < 0f || getValue() > 5f) {
                throw new IllegalStateException("the value for the rating indicator must be [0..5]");
            }

            tagWriter.startTag("span");
            tagWriter.writeAttribute("class","rating-indicator");

            int value = (int) (getValue() * 2f);

            for(int i=0;i<5;i++) {
                tagWriter.startTag("img");

                tagWriter.writeAttribute("height", "16");
                tagWriter.writeAttribute("width", "16");

                switch(value) {

                    case 1:
                        tagWriter.writeAttribute("src","/img/starhalf.png");
                        tagWriter.writeAttribute("alt","o");
                        break;

                    case 0:
                        tagWriter.writeAttribute("src","/img/staroff.png");
                        tagWriter.writeAttribute("alt",".");
                        break;

                    default:
                        tagWriter.writeAttribute("src","/img/staron.png");
                        tagWriter.writeAttribute("alt","*");
                        break;

                }

                tagWriter.endTag();

                value = Math.max(0,value-2);
            }

            tagWriter.endTag();
        }

        return SKIP_BODY;
    }

}
