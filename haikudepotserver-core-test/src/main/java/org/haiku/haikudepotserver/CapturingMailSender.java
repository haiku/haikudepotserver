/*
 * Copyright 2014-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <p>This mail sender can be called to send mail, but it will actually do nothing.</p>
 */

public class CapturingMailSender implements MailSender {

    protected static Logger LOGGER = LoggerFactory.getLogger(CapturingMailSender.class);

    private final List<SimpleMailMessage> sentMessages = new ArrayList<>();

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
         LOGGER.info("noop; send mail to {}", String.join(",", simpleMessage.getTo()));
        sentMessages.add(simpleMessage);
    }

    @Override
    public void send(SimpleMailMessage[] simpleMessages) throws MailException {
        LOGGER.info("noop; send {} mails", simpleMessages.length);
        sentMessages.addAll(Arrays.asList(simpleMessages));
    }

    public List<SimpleMailMessage> getSentMessages() { return sentMessages; }

    public void clear() {
        sentMessages.clear();
    }

}
