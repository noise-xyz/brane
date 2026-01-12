// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto.hd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * BIP-39 English wordlist for mnemonic seed phrase generation.
 *
 * <p>
 * This class provides access to the standard 2048-word English wordlist defined
 * in BIP-39 for generating and validating mnemonic seed phrases.
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP-39</a>
 */
final class EnglishWordlist {

    private static final int WORDLIST_SIZE = 2048;
    private static final String RESOURCE_PATH = "/bip39-english.txt";

    private static final List<String> WORDS;
    private static final Map<String, Integer> WORD_TO_INDEX;

    static {
        try {
            List<String> words = loadWordlist();
            WORDS = Collections.unmodifiableList(words);

            var wordToIndex = new HashMap<String, Integer>(WORDLIST_SIZE);
            for (int i = 0; i < words.size(); i++) {
                wordToIndex.put(words.get(i), i);
            }
            WORD_TO_INDEX = Collections.unmodifiableMap(wordToIndex);
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Failed to load BIP-39 English wordlist: " + e.getMessage());
        }
    }

    private EnglishWordlist() {
        // Utility class
    }

    /**
     * Returns the word at the specified index.
     *
     * @param index the wordlist index (0-2047)
     * @return the word at the given index
     * @throws IndexOutOfBoundsException if index is not in range [0, 2047]
     */
    static String getWord(int index) {
        return WORDS.get(index);
    }

    /**
     * Returns the index of the specified word in the wordlist.
     *
     * @param word the word to look up
     * @return the index of the word (0-2047)
     * @throws IllegalArgumentException if the word is not in the wordlist
     */
    static int getIndex(String word) {
        Objects.requireNonNull(word, "word cannot be null");
        Integer index = WORD_TO_INDEX.get(word);
        if (index == null) {
            throw new IllegalArgumentException("Word not found in BIP-39 wordlist: " + word);
        }
        return index;
    }

    /**
     * Checks if the specified word is in the wordlist.
     *
     * @param word the word to check
     * @return true if the word is in the wordlist, false otherwise
     */
    static boolean contains(String word) {
        return WORD_TO_INDEX.containsKey(word);
    }

    private static List<String> loadWordlist() throws IOException {
        try (InputStream is = EnglishWordlist.class.getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                throw new IOException("Resource not found: " + RESOURCE_PATH);
            }

            var words = new ArrayList<String>(WORDLIST_SIZE);
            try (var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        words.add(trimmed);
                    }
                }
            }

            if (words.size() != WORDLIST_SIZE) {
                throw new IOException(
                        "Invalid wordlist size: expected " + WORDLIST_SIZE + " words, got " + words.size());
            }

            return words;
        }
    }
}
