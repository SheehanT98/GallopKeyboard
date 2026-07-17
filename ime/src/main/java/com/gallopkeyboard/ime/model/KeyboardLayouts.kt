package com.gallopkeyboard.ime.model

/**
 * Complete keyboard layout data for all layers.
 *
 * Bottom rows: layer switch · emoji · clipboard · space · return.
 * Voice entry is the toolbar **Voice panel** control (not a bottom mic key).
 */
object KeyboardLayouts {

    private val bottomLettersSwitchRow: List<KeyDefinition> = listOf(
        KeyDefinition("ABC", type = KeyType.LAYER_SWITCH, widthMultiplier = 1.25f),
        KeyDefinition("\uD83D\uDE0A", type = KeyType.EMOJI, widthMultiplier = 1.15f),
        KeyDefinition("\uD83D\uDCCB", type = KeyType.CLIPBOARD, widthMultiplier = 1.15f),
        KeyDefinition("space", output = " ", type = KeyType.SPACE, widthMultiplier = 4.0f),
        KeyDefinition("return", type = KeyType.RETURN, widthMultiplier = 1.75f),
    )

    /** Symbols bottom row — clipboard stays for quick clip access. */
    private val symbolsBottomRow: List<KeyDefinition> = listOf(
        KeyDefinition("ABC", type = KeyType.LAYER_SWITCH, widthMultiplier = 1.25f),
        KeyDefinition("\uD83D\uDE0A", type = KeyType.EMOJI, widthMultiplier = 1.15f),
        KeyDefinition("\uD83D\uDCCB", type = KeyType.CLIPBOARD, widthMultiplier = 1.15f),
        KeyDefinition("space", output = " ", type = KeyType.SPACE, widthMultiplier = 4.0f),
        KeyDefinition("return", type = KeyType.RETURN, widthMultiplier = 1.75f),
    )

    val azertyLetters: List<List<KeyDefinition>> = listOf(
        listOf("A", "Z", "E", "R", "T", "Y", "U", "I", "O", "P")
            .map { KeyDefinition(label = it, output = it.lowercase()) },
        listOf("Q", "S", "D", "F", "G", "H", "J", "K", "L", "M")
            .map { KeyDefinition(label = it, output = it.lowercase()) },
        listOf(
            KeyDefinition("\u21E7", type = KeyType.SHIFT, widthMultiplier = 1.5f),
            KeyDefinition("W", output = "w"),
            KeyDefinition("X", output = "x"),
            KeyDefinition("C", output = "c"),
            KeyDefinition("V", output = "v"),
            KeyDefinition("B", output = "b"),
            KeyDefinition("N", output = "n"),
            KeyDefinition("'", type = KeyType.ACCENT_ADAPTIVE),
            KeyDefinition("\u232B", type = KeyType.DELETE, widthMultiplier = 1.5f),
        ),
        listOf(
            KeyDefinition("?123", type = KeyType.LAYER_SWITCH, widthMultiplier = 1.25f),
            KeyDefinition("\uD83D\uDE0A", type = KeyType.EMOJI, widthMultiplier = 1.15f),
            KeyDefinition("\uD83D\uDCCB", type = KeyType.CLIPBOARD, widthMultiplier = 1.15f),
            KeyDefinition("space", output = " ", type = KeyType.SPACE, widthMultiplier = 4.0f),
            KeyDefinition("return", type = KeyType.RETURN, widthMultiplier = 1.75f),
        ),
    )

    val qwertyLetters: List<List<KeyDefinition>> = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
            .map { KeyDefinition(label = it, output = it.lowercase()) },
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
            .map { KeyDefinition(label = it, output = it.lowercase()) },
        listOf(
            KeyDefinition("\u21E7", type = KeyType.SHIFT, widthMultiplier = 1.5f),
            KeyDefinition("Z", output = "z"),
            KeyDefinition("X", output = "x"),
            KeyDefinition("C", output = "c"),
            KeyDefinition("V", output = "v"),
            KeyDefinition("B", output = "b"),
            KeyDefinition("N", output = "n"),
            KeyDefinition("M", output = "m"),
            KeyDefinition("\u232B", type = KeyType.DELETE, widthMultiplier = 1.5f),
        ),
        listOf(
            KeyDefinition("123", type = KeyType.LAYER_SWITCH, widthMultiplier = 1.25f),
            KeyDefinition("\uD83D\uDE0A", type = KeyType.EMOJI, widthMultiplier = 1.15f),
            KeyDefinition("\uD83D\uDCCB", type = KeyType.CLIPBOARD, widthMultiplier = 1.15f),
            KeyDefinition("space", output = " ", type = KeyType.SPACE, widthMultiplier = 4.0f),
            KeyDefinition("return", type = KeyType.RETURN, widthMultiplier = 1.75f),
        ),
    )

    val numbersRows: List<List<KeyDefinition>> = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            .map { KeyDefinition(label = it, output = it) },
        listOf("-", "/", ":", ";", "(", ")", "&", "@")
            .map { KeyDefinition(label = it, output = it) },
        listOf(
            KeyDefinition("#+=", type = KeyType.LAYER_SWITCH, widthMultiplier = 1.5f),
            KeyDefinition(".", output = "."),
            KeyDefinition(",", output = ","),
            KeyDefinition("?", output = "?"),
            KeyDefinition("!", output = "!"),
            KeyDefinition("'", output = "'"),
            KeyDefinition("\u232B", type = KeyType.DELETE, widthMultiplier = 1.5f),
        ),
        bottomLettersSwitchRow,
    )

    val symbolsRows: List<List<KeyDefinition>> = listOf(
        listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
            .map { KeyDefinition(label = it, output = it) },
        listOf("_", "\\", "|", "~", "<", ">", "$", "&", "@")
            .map { KeyDefinition(label = it, output = it) },
        listOf(
            KeyDefinition("123", type = KeyType.LAYER_SWITCH, widthMultiplier = 1.5f),
            KeyDefinition(".", output = "."),
            KeyDefinition(",", output = ","),
            KeyDefinition("?", output = "?"),
            KeyDefinition("!", output = "!"),
            KeyDefinition("'", output = "'"),
            KeyDefinition("\u232B", type = KeyType.DELETE, widthMultiplier = 1.5f),
        ),
        symbolsBottomRow,
    )

    /**
     * Returns the letter layout for the given layout name.
     * Defaults to QWERTY if the layout name is not recognized.
     */
    fun lettersForLayout(layout: String): List<List<KeyDefinition>> = when (layout) {
        "azerty" -> azertyLetters
        "qwerty" -> qwertyLetters
        else -> qwertyLetters
    }
}
