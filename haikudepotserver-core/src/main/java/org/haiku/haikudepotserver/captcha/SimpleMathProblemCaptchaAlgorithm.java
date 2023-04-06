/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.captcha;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.ImageLineInt;
import ar.com.hjg.pngj.PngWriter;
import ar.com.hjg.pngj.chunks.PngChunkPLTE;
import org.haiku.haikudepotserver.captcha.model.Captcha;
import org.haiku.haikudepotserver.captcha.model.CaptchaAlgorithm;
import org.haiku.haikudepotserver.graphics.bitmap.IndexBitmap;
import org.haiku.haikudepotserver.graphics.monofont.BitmapCharacter;
import org.haiku.haikudepotserver.graphics.monofont.BitmapFont;
import org.haiku.haikudepotserver.graphics.monofont.BitmapFontFactory;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Random;
import java.util.UUID;

/**
 * <p>Implementation of {@link CaptchaAlgorithm} that presents the user with
 * a fairly trivial maths problem to solve.</p>
 */
public class SimpleMathProblemCaptchaAlgorithm implements CaptchaAlgorithm {

    private static final String RSRC_FONT = "/bitmapfont/5x5.txt";

    private static final int WIDTH = 128;
    private static final int HEIGHT = 24;
    private static final int PADDING = 4;

    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);

    private final BitmapFont font;
    private final Random random = new Random(System.currentTimeMillis());

    public SimpleMathProblemCaptchaAlgorithm() {
        super();
        font = createFont();
    }

    @Override
    public synchronized Captcha generate() {
        int addend1 = Math.abs(random.nextInt() % 25) + 5;
        int addend2 = Math.abs(random.nextInt() % 25) + 5;
        String problem = String.format("%d + %d",addend1,addend2);
        String response = Integer.toString(addend1 + addend2);

        int width = derivePixelWidthOfText(problem);
        int height = HEIGHT;

        width = width + (width % 2);

        IndexBitmap indexBitmap = new IndexBitmap(width, height);
        setupBackground(indexBitmap);
        renderTextWithRandomY(indexBitmap, problem);

        Captcha captcha = new Captcha();
        captcha.setToken(UUID.randomUUID().toString());
        captcha.setResponse(response);
        captcha.setPngImageData(renderPng(indexBitmap));
        return captcha;
    }

    private byte[] renderPng(IndexBitmap indexBitmap) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // https://github.com/leonbloy/pngj/wiki
            ImageInfo imi = new ImageInfo(
                    indexBitmap.getWidth(), indexBitmap.getHeight(),
                    4, false, false, true);
            PngWriter png = new PngWriter(baos, imi);
            png.getMetadata().setDpi(72);
            png.getMetadata().setTimeNow();

            // TODO: some sort of palette should be used here with dark and light
            // colours.
            PngChunkPLTE palette = png.getMetadata().createPLTEChunk();
            palette.setNentries(2);
            palette.setEntry(0, 0, 0, 0);
            palette.setEntry(1, 255,255,255);

            ImageLineInt line = new ImageLineInt(imi);

            for (int y = 0; y < indexBitmap.getHeight(); y++) {
                for (int x = 0; x < indexBitmap.getWidth(); x++) {
                    line.getScanline()[x] = indexBitmap.get(x, y);
                }
                png.writeRow(line);
            }

            png.end();

            return baos.toByteArray();
        }
        catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private static BitmapFont createFont() {
        try (InputStream inputStream = SimpleMathProblemCaptchaAlgorithm.class.getResourceAsStream(RSRC_FONT)) {
            return BitmapFontFactory.parseFont(inputStream);
        }
        catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private int derivePixelWidthOfText(String text) {
        int result = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            BitmapCharacter character = getBitmapCharacter(c);
            result += character.getWidth() + (2 * PADDING);
        }

        return result;
    }

    private BitmapCharacter getBitmapCharacter(char c) {
        return font.tryGetCharacter(c)
                .orElseGet(() -> font.tryGetCharacter((char) 0x1A)
                        .orElseThrow());
    }

    private void setupBackground(IndexBitmap indexBitmap) {
        indexBitmap.fill((short) 0);
    }

    private void renderTextWithRandomY(IndexBitmap indexBitmap, String text) {
        int xOffset = PADDING;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            BitmapCharacter character = getBitmapCharacter(c);
            renderCharacterWithRandomY(indexBitmap, character, xOffset);
            xOffset += character.getWidth() + (2 * PADDING);
        }
    }

    private void renderCharacterWithRandomY(IndexBitmap indexBitmap, BitmapCharacter character, int xOffset) {
        int yOffset = PADDING + Math.abs(
                random.nextInt()
                        % ((indexBitmap.getHeight() - 2 * PADDING) - character.getHeight()));
        renderCharacter(indexBitmap, character, xOffset, yOffset);
    }

    private void renderCharacter(IndexBitmap indexBitmap, BitmapCharacter character, int xOffset, int yOffset) {
        for (int x = 0; x < character.getWidth(); x++) {
            for (int y = 0; y < character.getHeight(); y++) {
                if (character.get(x, y)) {
                    indexBitmap.set(xOffset + x, yOffset + y, (short) 1);
                }
            }
        }
    }

}
