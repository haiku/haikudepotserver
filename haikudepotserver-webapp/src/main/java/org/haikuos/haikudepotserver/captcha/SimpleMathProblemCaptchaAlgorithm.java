/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.captcha;

import org.haikuos.haikudepotserver.captcha.model.Captcha;
import org.haikuos.haikudepotserver.captcha.model.CaptchaAlgorithm;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;

/**
 * <p>Implementation of {@link org.haikuos.haikudepotserver.captcha.model.CaptchaAlgorithm} that presents the user with
 * a fairly trivial maths problem to solve.</p>
 */
public class SimpleMathProblemCaptchaAlgorithm implements CaptchaAlgorithm {

    private static final int WIDTH = 128;
    private static final int HEIGHT = 24;

    private Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 14);

    private BufferedImage bufferedImage;
    private Graphics bufferedImageGraphics;
    private FontMetrics fontMetrics;
    private Random random = new Random(System.currentTimeMillis());

    public SimpleMathProblemCaptchaAlgorithm() {
        super();

        bufferedImage = new BufferedImage(
                WIDTH,
                HEIGHT,
                BufferedImage.TYPE_INT_RGB);

        bufferedImageGraphics = bufferedImage.getGraphics();
        bufferedImageGraphics.setFont(font);
        fontMetrics = bufferedImageGraphics.getFontMetrics(font);
    }

    @Override
    public synchronized Captcha generate() {

        int addend1 = Math.abs(random.nextInt() % 25);
        int addend2 = Math.abs(random.nextInt() % 25);
        String problem = String.format("%d + %d",addend1,addend2);
        String response = Integer.toString(addend1 + addend2);
        byte[] pngImageData;

        synchronized(this) {

            // reset the image.

            bufferedImageGraphics.setColor(Color.DARK_GRAY);
            bufferedImageGraphics.fillRect(0,0,bufferedImage.getWidth(),bufferedImage.getHeight());

            // now render a small image using java 2d with this text in it.

            bufferedImageGraphics.setColor(Color.WHITE);
            bufferedImageGraphics.drawString(
                    problem,
                    (bufferedImage.getWidth() - fontMetrics.stringWidth(problem)) / 2,
                    (bufferedImage.getHeight() + fontMetrics.getAscent()) / 2);

            // now generate a PNG of it.

            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ImageIO.write(bufferedImage, "png", byteArrayOutputStream);
                pngImageData = byteArrayOutputStream.toByteArray();
            }
            catch(IOException ioe) {
                throw new IllegalStateException("unable to write png data in memory",ioe);
            }

        }

        Captcha captcha = new Captcha();
        captcha.setToken(UUID.randomUUID().toString());
        captcha.setResponse(response);
        captcha.setPngImageData(pngImageData);
        return captcha;

    }
}
