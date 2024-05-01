/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.passwordreset;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import freemarker.ext.beans.BeansWrapper;
import freemarker.ext.beans.BeansWrapperBuilder;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserPasswordResetToken;
import org.haiku.haikudepotserver.passwordreset.model.PasswordResetMail;
import org.haiku.haikudepotserver.passwordreset.model.PasswordResetService;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
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
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    protected final static Logger LOGGER = LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    private final static String MAIL_SUBJECT = "passwordreset-subject";
    private final static String MAIL_PLAINTEXT = "passwordreset-plaintext";

    private final MailSender mailSender;
    private final ServerRuntime serverRuntime;
    private final UserAuthenticationService userAuthenticationService;
    private final Configuration freemarkerConfiguration;
    private final Integer timeToLiveHours;
    private final String baseUrl;
    private final String from;

    public PasswordResetServiceImpl(
            ServerRuntime serverRuntime,
            MailSender mailSender,
            UserAuthenticationService userAuthenticationService,
            @Qualifier("emailFreemarkerConfiguration") Configuration freemarkerConfiguration,
            @Value("${hds.passwordreset.ttlhours:1}") Integer timeToLiveHours,
            @Value("${hds.base-url}") String baseUrl,
            @Value("${hds.email.from}") String from
    ) {
        this.mailSender = Preconditions.checkNotNull(mailSender);
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.userAuthenticationService = Preconditions.checkNotNull(userAuthenticationService);
        this.freemarkerConfiguration = Preconditions.checkNotNull(freemarkerConfiguration);
        this.timeToLiveHours = Preconditions.checkNotNull(timeToLiveHours);
        this.baseUrl = Preconditions.checkNotNull(baseUrl);
        this.from = Preconditions.checkNotNull(from);
    }

    private String fillFreemarkerTemplate(
            PasswordResetMail mailModel,
            String templateLeafName,
            NaturalLanguage naturalLanguage) throws PasswordResetException {

        BeansWrapper wrapper = new BeansWrapperBuilder(Configuration.VERSION_2_3_21).build();

        try {
            StringWriter writer = new StringWriter();
            Template template = freemarkerConfiguration.getTemplate(templateLeafName + "_" + naturalLanguage.getCode());
            template.process(wrapper.wrap(mailModel), writer);
            return writer.toString();
        } catch (TemplateException te) {
            throw new PasswordResetException("unable to process the freemarker template for sending out mail for the password reset token", te);
        } catch (IOException ioe) {
            throw new PasswordResetException("unable to obtain the freemarker templates for sending out mail for the password reset token",ioe);
        }
    }

    /**
     * <p>This method will create a user password reset token and will also email the user
     * about it so that they are able to click on a link in the email, visit the application
     * server and get their password changed.</p>
     */

    private void createTokenAndInvite(User user) throws PasswordResetException {
        Preconditions.checkArgument(null != user, "the user must be provided");
        Preconditions.checkState(!Strings.isNullOrEmpty(user.getEmail()), "the user must have an email configured");

        ObjectContext contextLocal = serverRuntime.newContext();
        User userLocal = User.getByObjectId(contextLocal, user.getObjectId());

        UserPasswordResetToken userPasswordResetToken = contextLocal.newObject(UserPasswordResetToken.class);
        userPasswordResetToken.setUser(userLocal);
        userPasswordResetToken.setCode(UUID.randomUUID().toString());
        userPasswordResetToken.setCreateTimestamp(new java.sql.Timestamp(Clock.systemUTC().millis()));

        PasswordResetMail mailModel = new PasswordResetMail();
        mailModel.setPasswordResetBaseUrl(baseUrl + "/" + URL_SEGMENT_PASSWORDRESET + "/");
        mailModel.setUserNickname(user.getNickname());
        mailModel.setUserPasswordResetTokenCode(userPasswordResetToken.getCode());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject(fillFreemarkerTemplate(mailModel, MAIL_SUBJECT, user.getNaturalLanguage()));
        message.setText(fillFreemarkerTemplate(mailModel, MAIL_PLAINTEXT, user.getNaturalLanguage()));

        contextLocal.commitChanges();

        try {
            this.mailSender.send(message);
        } catch (MailException me) {
            throw new PasswordResetException("the password reset email to "+user.toString()+" was not able to be sent",me);
        }
    }

    @Override
    public void initiate(String email) throws PasswordResetException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(email), "the email must be provided");
        Preconditions.checkArgument(-1 != email.indexOf('@'), "the email is malformed"); // very basic sanity check

        ObjectContext context = serverRuntime.newContext();

        List<User> users = User.findByEmail(context, email);

        if (users.isEmpty()) {
            LOGGER.warn("attempt to send password reset token to {}, but there are no users associated with this email address", email);
        } else {

            int count = 0;

            LOGGER.info("will create tokens and invite; {}", email);

            for(User user : users) {
                if(!user.getActive()) {
                    LOGGER.warn("it is not possible to send a password reset to an inactive user; {}", user.toString());
                } else {
                    if (user.getIsRoot()) {
                        LOGGER.warn("it is not possible to send a password reset to a root user; {}", user.toString());
                    } else {
                        createTokenAndInvite(user);
                        count++;
                    }
                }
            }

            LOGGER.info("did create tokens and invite; {} - sent {}", email, count);
        }
    }

    @Override
    public void complete(String tokenCode, String passwordClear) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(tokenCode), "the token code must be provided");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(passwordClear), "the pssword clear must be provided");

        Instant now = Instant.now();

        try {
            if (!Strings.isNullOrEmpty(tokenCode)) {

                ObjectContext context = serverRuntime.newContext();
                Optional<UserPasswordResetToken> tokenOptional = UserPasswordResetToken.getByCode(context, tokenCode);

                if (tokenOptional.isPresent()) {

                    try {
                        UserPasswordResetToken token = tokenOptional.get();

                        if(token.getCreateTimestamp().getTime() > now.minus(timeToLiveHours, ChronoUnit.HOURS).toEpochMilli()) {

                            User user = token.getUser();

                            if (user.getActive()) {

                                if (!Strings.isNullOrEmpty(passwordClear) && userAuthenticationService.validatePassword(passwordClear)) {
                                    userAuthenticationService.setPassword(user, passwordClear);
                                    context.deleteObjects(token);
                                    context.commitChanges();

                                    LOGGER.info("did reset the password for; {}", user.toString());
                                } else {
                                    LOGGER.warn("the password has been supplied as invalid; will ignore");
                                }

                            } else {
                                LOGGER.warn("the user having their password reset is inactive; will ignore");
                            }

                        } else {
                            LOGGER.warn("the token used to reset the password is expired; will ignore");
                        }
                    } finally {

                        // open a new context so that just in case something goes wrong / invalid in the other context,
                        // that the deletion of the token can still proceed.

                        ObjectContext deleteContext = serverRuntime.newContext();
                        Optional<UserPasswordResetToken> deleteTokenOptional = UserPasswordResetToken.getByCode(deleteContext, tokenCode);

                        if (deleteTokenOptional.isPresent()) {
                            deleteContext.deleteObjects(deleteTokenOptional.get());
                            deleteContext.commitChanges();
                            LOGGER.info("did delete user password reset token {} after having processed it", tokenCode);
                        }

                    }

                } else {
                    LOGGER.warn("unable to find the user password reset token {}; will ignore", tokenCode);
                }

            } else {
                LOGGER.warn("the code has been supplied as null when attempting to reset a password; will ignore");
            }
        }
        catch(Throwable th) {
            LOGGER.error("unable to reset the password from a token", th);
        }
    }

    @Override
    public void deleteExpiredPasswordResetTokens() {
        ObjectContext context = serverRuntime.newContext();
        Instant now = Instant.now();

        List<UserPasswordResetToken> tokens = ObjectSelect.query(UserPasswordResetToken.class)
                .where(UserPasswordResetToken.CREATE_TIMESTAMP
                        .lt(new java.sql.Timestamp(now.minus(timeToLiveHours, ChronoUnit.HOURS).toEpochMilli())))
                .select(context);

        if(tokens.isEmpty()) {
            LOGGER.debug("no expired tokens to delete");
        } else {
            context.deleteObjects(tokens.toArray(new UserPasswordResetToken[0]));
            context.commitChanges();
            LOGGER.info("did delete {} expired tokens", tokens.size());
        }

    }

}
