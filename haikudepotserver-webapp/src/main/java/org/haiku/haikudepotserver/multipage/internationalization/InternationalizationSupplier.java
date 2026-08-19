/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.internationalization;

import com.google.common.base.Preconditions;
import gg.jte.Content;
import gg.jte.html.HtmlContent;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.multipage.PassiveContentRepository;
import org.springframework.context.MessageSource;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * <p>An instance of this object is used in the data supplied to page. It can be
 * invoked from the page logic to get messages and passive content that pertains
 * to the locale it is configured with.</p>
 */

public class InternationalizationSupplier {

    private final static Pattern KEY_PATTERN = Pattern.compile("^mp(\\.[a-z0-9_]+)+$");

    private final Locale locale;
    private final MessageSource messageSource;
    private final PassiveContentRepository passiveContentRepository;

    public InternationalizationSupplier(
            MessageSource messageSource,
            PassiveContentRepository passiveContentRepository,
            Locale locale) {
        this.locale = locale;
        this.messageSource = messageSource;
        this.passiveContentRepository = passiveContentRepository;
    }

    /**
     * <p>Returns {@link Content} that will render out some passive (not formatted) content identified by the supplied
     * leaf. The leaf might be something like {@code instructions.html}. The system will try to find first
     * {@code instructions_de.html} for example and if that's not available it will fall back to
     * {@code instructions_en.html}.</p>
     */
    public Content getPassiveContext(String leaf) {
        return (HtmlContent) output -> {
            if (StringUtils.isNotBlank(leaf)) {
                output.writeUnsafeContent(passiveContentRepository.get(leaf, locale));
            }
        };
    }

    /**
     * <p>Get a localized message for a key. The key must be in a suitable format to be allowed for the lookup.
     * An example of a suitable key is {@code mp.pkg_view.title}. The parameters after the message key are substituted
     * into the message. For example {@code {0}} represent the first positional parameter.</p>
     * @see java.text.MessageFormat
     */

    public String getMessage(String key, Object... params) {
        Preconditions.checkArgument(StringUtils.isNotBlank(key), "missing localization key");

        if (!key.startsWith("naturalLanguage") // TODO; temporary exemption - those keys need to be normalized
                && !KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("invalid localization key [%s]".formatted(key));
        }

        return messageSource.getMessage(key, params, locale);
    }

    /**
     * <p>This will format some number with a certain number of decimal places.</p>
     */

    public String formatDecimal(Number value, int decimalPlaces) {
        if (null == value) {
            return "";
        }
        NumberFormat decimalFormat = DecimalFormat.getNumberInstance(locale);
        decimalFormat.setMaximumFractionDigits(decimalPlaces);
        return decimalFormat.format(value);
    }

}
