package org.haiku.haikudepotserver.graphics.monofont;

import org.fest.assertions.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

class BitmapFontFactoryTest {

    @Test
    public void testCharacters() {
        // GIVEN
        BitmapFont font = createFont();
        BitmapCharacter character = font.tryGetCharacter('5').orElseThrow();

        // WHEN
        String characterAsString = character.toString();

        // THEN
        Assertions.assertThat(characterAsString).isEqualTo(
                """
                        *****
                        *....
                        ****.
                        ....*
                        ****."""
        );
    }

    private BitmapFont createFont() {
        try (InputStream inputStream = BitmapFontFactoryTest.class.getResourceAsStream("/bitmapfont/5x5.txt")) {
            return BitmapFontFactory.parseFont(inputStream);
        }
        catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

}
