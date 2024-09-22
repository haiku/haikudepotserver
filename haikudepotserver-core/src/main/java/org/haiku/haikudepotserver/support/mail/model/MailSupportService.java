/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.mail.model;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoded;
import org.springframework.mail.MailException;

import java.util.Collection;
import java.util.Set;

/**
 * <p>Service for sending internet emails based on a template for the subject
 * and the body.</p>
 */

public interface MailSupportService {

    default void sendMail(
            User user,
            Object mailModel,
            String templatePrefix
    ) throws MailException {
        Preconditions.checkArgument(StringUtils.isNotBlank(user.getEmail()), "the user must have an email address");
        sendMail(
                Set.of(user.getEmail()),
                mailModel,
                templatePrefix,
                user.getNaturalLanguage());
    }

    /**
     * <p>Sends an email to the recipients from the system's specified `from` address. The email subject and text body
     * are formed from Apache Freemarker templates. The prefix is given and then specific suffixes are added to get the
     * subject template and the email body templates.</p>
     */

    void sendMail(
            Collection<String> recipientAddresses,
            Object mailModel,
            String templatePrefix,
            NaturalLanguageCoded naturalLanguage
    ) throws MailException;

}
