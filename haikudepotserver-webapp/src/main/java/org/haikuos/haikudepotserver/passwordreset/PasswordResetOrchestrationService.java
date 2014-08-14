/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.passwordreset;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.dataobjects.UserPasswordResetToken;
import org.haikuos.haikudepotserver.passwordreset.model.PasswordResetMail;
import org.haikuos.haikudepotserver.security.AuthenticationService;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class PasswordResetOrchestrationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(PasswordResetOrchestrationService.class);

    private final static String MAIL_SUBJECT = "passwordreset-subject";
    private final static String MAIL_PLAINTEXT = "passwordreset-plaintext";

    @Resource
    MailSender mailSender;

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    AuthenticationService authenticationService;

    @Resource
    Configuration freemarkerConfiguration;

    @Value("${passwordreset.ttlhours:1}")
    Integer timeToLiveHours;

    @Value("${baseurl}")
    String baseUrl;

    @Value("${email.from}")
    String from;

    private String fillFreemarkerTemplate(
            PasswordResetMail mailModel,
            String templateLeafName,
            NaturalLanguage naturalLanguage) throws PasswordResetException {

        BeansWrapper wrapper = BeansWrapper.getDefaultInstance();

        try {
            StringWriter writer = new StringWriter();
            Template template = freemarkerConfiguration.getTemplate(templateLeafName + "_" + naturalLanguage.getCode());
            template.process(wrapper.wrap(mailModel), writer);
            return writer.toString();
        }
        catch(TemplateException te) {
            throw new PasswordResetException("unable to process the freemarker template for sending out mail for the password reset token", te);
        }
        catch(IOException ioe) {
            throw new PasswordResetException("unable to obtain the freemarker templates for sending out mail for the password reset token",ioe);
        }
    }

    /**
     * <p>This method will create a user password reset token and will also email the user
     * about it so that they are able to click on a link in the email, visit the application
     * server and get their password changed.</p>
     */

    private void createTokenAndInvite(User user) throws PasswordResetException {
        Preconditions.checkNotNull(user);
        Preconditions.checkState(!Strings.isNullOrEmpty(user.getEmail()));

        ObjectContext contextLocal = serverRuntime.getContext();
        User userLocal = User.getByObjectId(contextLocal, user.getObjectId());

        UserPasswordResetToken userPasswordResetToken = contextLocal.newObject(UserPasswordResetToken.class);
        userPasswordResetToken.setUser(userLocal);
        userPasswordResetToken.setCode(UUID.randomUUID().toString());
        userPasswordResetToken.setCreateTimestamp(new Date());

        PasswordResetMail mailModel = new PasswordResetMail();
        mailModel.setPasswordResetBaseUrl(baseUrl + "/passwordreset/");
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
        }
        catch(MailException me) {
            throw new PasswordResetException("the password reset email to "+user.toString()+" was not able to be sent",me);
        }
    }

    /**
     * <p>This method will create the necessary tokens to reset a password and will dispatch those tokens
     * out to the users using their email address.  It is assumed that the email address is validated
     * by this point.</p>
     */

    public void initiate(String email) throws PasswordResetException {
        Preconditions.checkNotNull(email);
        Preconditions.checkState(-1 != email.indexOf('@')); // very basic sanity check

        ObjectContext context = serverRuntime.getContext();

        List<User> users = User.findByEmail(context, email);

        if (users.isEmpty()) {
            LOGGER.warn("attempt to send password reset token to {}, but there are no users associated with this email address");
        } else {

            int count = 0;

            LOGGER.info("will create tokens and invite; {}", email);

            for(User user : users) {
                if(!user.getActive()) {
                    LOGGER.warn("it is not possible to send a password reset to an inactive user; {}", user.toString());
                }
                else {
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

    /**
     * <p>This method will action the password reset token that the user would have supplied in a URL together with
     * a clear-text password.  This method will either perform the action or will not perform the action.  It will
     * not return if it has done anything or not, but it will log what it has done.</p>
     */

    public void complete(String tokenCode, String passwordClear) {
        DateTime now = new DateTime();

        try {
            if (!Strings.isNullOrEmpty(tokenCode)) {

                ObjectContext context = serverRuntime.getContext();
                Optional<UserPasswordResetToken> tokenOptional = UserPasswordResetToken.getByCode(context, tokenCode);

                if (tokenOptional.isPresent()) {

                    try {
                        UserPasswordResetToken token = tokenOptional.get();

                        if(token.getCreateTimestamp().getTime() > now.minusHours(timeToLiveHours).getMillis()) {

                            User user = token.getUser();

                            if (user.getActive()) {

                                if (!Strings.isNullOrEmpty(passwordClear) && authenticationService.validatePassword(passwordClear)) {
                                    user.setPasswordSalt();
                                    user.setPasswordHash(authenticationService.hashPassword(user, passwordClear));
                                    context.deleteObjects(token);
                                    context.commitChanges();

                                    LOGGER.info("did reset the password for; {}", user.toString());
                                } else {
                                    LOGGER.warn("the password has been supplied as invalid; will ignore");
                                }

                            } else {
                                LOGGER.warn("the user having their password reset is inactive; will ignore");
                            }

                        }
                        else {
                            LOGGER.warn("the token used to reset the password is expired; will ignore");
                        }
                    } finally {

                        // open a new context so that just in case something goes wrong / invalid in the other context,
                        // that the deletion of the token can stil proceed.

                        ObjectContext deleteContext = serverRuntime.getContext();
                        Optional<UserPasswordResetToken> deleteTokenOptional = UserPasswordResetToken.getByCode(context, tokenCode);

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

    /**
     * <p>This method will delete any tokens that have expired.</p>
     */

    public void deleteExpiredPasswordResetTokens() {
        ObjectContext context = serverRuntime.getContext();
        DateTime now = new DateTime();

        SelectQuery query = new SelectQuery(
                UserPasswordResetToken.class,
                ExpressionFactory.lessExp(
                        UserPasswordResetToken.CREATE_TIMESTAMP_PROPERTY,
                        now.minusHours(timeToLiveHours).toDate()));

        List<UserPasswordResetToken> tokens = (List<UserPasswordResetToken>) context.performQuery(query);

        if(tokens.isEmpty()) {
            LOGGER.debug("no expired tokens to delete");
        }
        else {
            context.deleteObjects(tokens.toArray(new UserPasswordResetToken[tokens.size()]));
            context.commitChanges();
            LOGGER.info("did delete {} expired tokens", tokens.size());
        }

    }

}
