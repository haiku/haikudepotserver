/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.mail;

import com.google.common.base.Preconditions;
import freemarker.ext.beans.BeansWrapper;
import freemarker.ext.beans.BeansWrapperBuilder;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.logging.log4j.util.Strings;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoded;
import org.haiku.haikudepotserver.passwordreset.PasswordResetException;
import org.haiku.haikudepotserver.support.mail.model.MailSupportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;

@Service
public class MailSupportServiceImpl implements MailSupportService {

    protected final static Logger LOGGER = LoggerFactory.getLogger(MailSupportServiceImpl.class);

    private final static String MAIL_TEMPLATE_SUBJECT_SUFFIX = "-subject";
    private final static String MAIL_TEMPLATE_PLAINTEXT_SUFFIX = "-plaintext";

    private final MailSender mailSender;

    private final Configuration freemarkerConfiguration;

    private final String from;

    public MailSupportServiceImpl(
            MailSender mailSender,
            @Qualifier("emailFreemarkerConfiguration") Configuration freemarkerConfiguration,
            @Value("${hds.email.from}") String from) {
        this.mailSender = Preconditions.checkNotNull(mailSender);
        this.freemarkerConfiguration = Preconditions.checkNotNull(freemarkerConfiguration);
        this.from = Preconditions.checkNotNull(from);
    }

    /**
     * <p>Sends an email to the recipients from the system's specified `from` address. The email subject and text body
     * are formed from Apache Freemarker templates. The prefix is given and then specific suffixes are added to get the
     * subject template and the email body templates.</p>
     */

    @Override
    public void sendMail(
            Collection<String> recipientAddresses,
            Object mailModel,
            String templatePrefix,
            NaturalLanguageCoded naturalLanguage
    ) throws MailException {

        if (recipientAddresses.isEmpty()) {
            LOGGER.info("no recipients provided; will not send email [{}]", templatePrefix);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipientAddresses.toArray(new String[]{}));
        message.setSubject(fillFreemarkerTemplate(mailModel, templatePrefix + MAIL_TEMPLATE_SUBJECT_SUFFIX, naturalLanguage));
        message.setText(fillFreemarkerTemplate(mailModel, templatePrefix + MAIL_TEMPLATE_PLAINTEXT_SUFFIX, naturalLanguage));

        mailSender.send(message);

        LOGGER.info("did send email [{}] to [{}]", templatePrefix, Strings.join(recipientAddresses, ','));
    }

    private String fillFreemarkerTemplate(
            Object mailModel,
            String templateLeafName,
            NaturalLanguageCoded naturalLanguage) throws PasswordResetException {

        BeansWrapper wrapper = new BeansWrapperBuilder(Configuration.VERSION_2_3_21).build();

        try {
            StringWriter writer = new StringWriter();
            Template template = freemarkerConfiguration.getTemplate(templateLeafName + "_" + naturalLanguage.getLanguageCode());
            template.process(wrapper.wrap(mailModel), writer);
            return writer.toString();
        } catch (TemplateException te) {
            throw new PasswordResetException("unable to process the freemarker template for sending out mail for the password reset token", te);
        } catch (IOException ioe) {
            throw new PasswordResetException("unable to obtain the freemarker templates for sending out mail for the password reset token",ioe);
        }
    }

}
