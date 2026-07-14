/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.jte;

import gg.jte.Content;
import gg.jte.html.HtmlContent;
import gg.jte.html.HtmlTemplateOutput;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

public class TextHelper {

    /**
     * <p>Renders out some supplied text as HTML using paragraph structuring etc...</p>
     */
    public static Content htmlFormattedText(@Nullable final String str) {
        return (HtmlContent) output -> {
            if (StringUtils.isNotBlank(str)) {
                writeHtmlFormatted(output, str, 0, str.length());
            }
        };
    }

    /**
     * <p>Renders the provided text as HTML but also adds highlighting where the expression
     * was found.</p>
     */
    public static Content highlightedHtmlFormattedText(String str, String searchExpression) {

        return (HtmlContent) output -> {
            if (StringUtils.length(str) > 0) {
                int upto = 0;

                do {
                    int next = StringUtils.isBlank(searchExpression) ? -1 : Strings.CI.indexOf(str, searchExpression, upto);

                    if (-1 == next) {
                        next = str.length();
                        writeHtmlFormatted(output, str, upto, next);
                    } else {
                        writeHtmlFormatted(output, str, upto, next);
                        output.writeContent("<span class=\"common-highlight\">");
                        writeHtmlFormatted(output, str, next, next + searchExpression.length());
                        output.writeContent("</span>");
                        next += searchExpression.length();
                    }

                    upto = next;
                } while (upto < str.length());
            }
        };

    }

    private static void writeHtmlFormatted(HtmlTemplateOutput output, String str, int startInclusive, int endExclusive) {
        if (endExclusive != startInclusive) {
            int upto = startInclusive;

            do {
                int next = indexOfNextSpecial(str, upto, endExclusive);

                if (-1 == next) {
                    next = endExclusive;
                    output.writeContent(str, upto, next);
                } else {
                    output.writeContent(str, upto, next);
                    char nextCh = str.charAt(next);

                    switch (nextCh) {
                        case 0x0a:
                            output.writeContent("<br/>");
                            if (next < endExclusive - 1) {
                                if (0x0d == str.charAt(next + 1)) {
                                    next++;
                                }
                            }
                            break;
                        case 0x0d:
                            output.writeContent("<br/>");
                            if (next < endExclusive - 1) {
                                if (0x0a == str.charAt(next + 1)) {
                                    next++;
                                }
                            }
                            break;
                        case 0x09:
                            output.writeContent(" ");
                            break;
                        case '&':
                            output.writeContent("&amp;");
                            break;
                        case '<':
                            output.writeContent("&lt;");
                            break;
                        case '>':
                            output.writeContent("&gt;");
                            break;
                        case '"':
                            output.writeContent("&quot;");
                            break;
                        case '\'':
                            output.writeContent("&apos;");
                            break;
                        default:
                            if (nextCh > 0x7f) {
                                output.writeContent("&#");
                                output.writeContent(Integer.toString(nextCh));
                                output.writeContent(";");
                            }
                            break;
                    }
                    next++;
                }

                upto = next;
            } while (upto < endExclusive);
        }
    }

    private static int indexOfNextSpecial(String str, int startInclusive, int endExclusive) {
        for (int i = startInclusive; i < endExclusive; i++) {
            if (isSpecial(str.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSpecial(char ch) {
        if (ch < 0x20 || ch > 0x7e) {
            return true;
        }
        return switch (ch) {
            case '&', '<', '>', '"', '\'' -> true;
            default -> false;
        };
    }

}
