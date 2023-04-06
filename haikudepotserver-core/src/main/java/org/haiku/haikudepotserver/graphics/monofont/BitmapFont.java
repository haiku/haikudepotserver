/*
 * Copyright 2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics.monofont;

import com.google.common.base.Preconditions;

import java.util.Map;
import java.util.Optional;

/**
 * <p>This class provides a set of characters as bitmap images that can
 * be rendered on an image.</p>
 */

public class BitmapFont {

    private final Map<Character, BitmapCharacter> characters;

    public BitmapFont(Map<Character, BitmapCharacter> characters) {
        this.characters = Preconditions.checkNotNull(characters);
    }

    public Optional<BitmapCharacter> tryGetCharacter(char c) {
        return Optional.ofNullable(characters.get(c));
    }

}
