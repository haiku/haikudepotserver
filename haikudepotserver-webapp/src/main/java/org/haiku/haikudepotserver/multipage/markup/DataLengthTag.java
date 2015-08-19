/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.markup;

import org.springframework.web.servlet.tags.RequestContextAwareTag;

import javax.servlet.jsp.JspWriter;
import java.text.DecimalFormat;

/**
 * <p>Given a quantity of bytes, this will render the quantity as some sensible units.  For example, KB, MB etc...</p>
 */

public class DataLengthTag extends RequestContextAwareTag {

    private Integer length;

    private DecimalFormat numberFormat = new DecimalFormat("#,##0.#");

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    private void outputValueAndUnit(JspWriter writer, double value, String unit) throws Exception {
        writer.write(numberFormat.format(value));
        writer.write(' ');
        writer.write(unit);
    }

    @Override
    protected int doStartTagInternal() throws Exception {

        if(null!=length) {

            JspWriter writer = pageContext.getOut();

            if(length < 0) {
                writer.write("0");
            }
            else {
                if(length < 1024) {
                    outputValueAndUnit(writer, length, "bytes");
                }
                else {
                    if (length < 1024 * 1024) {
                        outputValueAndUnit(writer, length / 1024.0, "KB");
                    }
                    else {
                        if (length < 1024 * 1024 * 1024) {
                            outputValueAndUnit(writer, length / (1024 * 1024), "MB");
                        } else {
                            outputValueAndUnit(writer, length / (1024 * 1024 * 1024), "GB");
                        }
                    }
                }
            }
        }

        return SKIP_BODY;
    }

}
