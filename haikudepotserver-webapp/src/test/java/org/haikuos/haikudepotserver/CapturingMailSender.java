/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.util.List;

/**
 * <p>This mail sender can be called to send mail, but it will actually do nothing.</p>
 */

public class CapturingMailSender implements MailSender {

    protected static Logger logger = LoggerFactory.getLogger(CapturingMailSender.class);

    private List<SimpleMailMessage> sentMessages = Lists.newArrayList();

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
         logger.info("noop; send mail to {}",simpleMessage.getTo());
        sentMessages.add(simpleMessage);
    }

    @Override
    public void send(SimpleMailMessage[] simpleMessages) throws MailException {
        logger.info("noop; send {} mails",simpleMessages.length);
        sentMessages.addAll(Lists.newArrayList(simpleMessages));
    }

    public List<SimpleMailMessage> getSentMessages() { return sentMessages; }

    public void clear() {
        sentMessages.clear();
    }

}
