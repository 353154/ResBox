package com.resbox.player.data.remote.crypto

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Complete TVBox configuration decryptor & sanitizer.
 * Automatically detects and decrypts:
 * 1. PNG / BMP / GIF image steganography (`**` marker with Base64 payload appended at the tail).
 * 2. Hex-encoded `2423` (`$#`) AES-128-CBC encrypted TVBox configs.
 * 3. Standard AES-128-ECB Hex-encoded configs.
 * 4. Multi-layer Base64 with random prefix/suffix padding before `{`.
 * 5. Non-standard JSON with `//` and `/* */` comments and trailing commas.
 */
object ConfigDecryptor {

    private const val STEGANOGRAPHY_MARKER = "**"
    private const val AES_CBC_HEX_HEADER = "2423" // Hex for "$#"

    fun decryptAndCleanConfig(rawBytes: ByteArray): String {
        val rawText = String(rawBytes, StandardCharsets.UTF_8).trim()
        if (rawText.isEmpty()) return "{}"

        // Step 1: Check for image steganography or prefix padding with "**"
        val extractedPayload = extractAfterSteganographyMarker(rawText)

        // Step 2: Check for "2423" ($#) AES-CBC encrypted hex format
        val decryptedText = when {
            extractedPayload.startsWith(AES_CBC_HEX_HEADER, ignoreCase = true) -> {
                decryptAesCbc2423(extractedPayload)
            }
            isLikelyBase64(extractedPayload) -> {
                decodeBase64Payload(extractedPayload)
            }
            isHexString(extractedPayload) && extractedPayload.length > 64 -> {
                decryptAesEcbHex(extractedPayload)
            }
            else -> extractedPayload
        }

        // Step 3: Strip C-style comments, garbage prefix before '{', and trailing commas
        return sanitizeJsonCommentsAndTrailingCommas(decryptedText)
    }

    private fun extractAfterSteganographyMarker(input: String): String {
        val markerIndex = input.lastIndexOf(STEGANOGRAPHY_MARKER)
        if (markerIndex >= 0 && markerIndex + STEGANOGRAPHY_MARKER.length < input.length) {
            val candidate = input.substring(markerIndex + STEGANOGRAPHY_MARKER.length).trim()
            if (candidate.length > 16) {
                return candidate
            }
        }
        return input
    }

    /**
     * Standard TVBox `2423` AES-128-CBC decryption:
     * Format: `2423` + hex(key) + `2324` + hex(cipherData) + hex(iv)
     */
    private fun decryptAesCbc2423(hexPayload: String): String {
        return try {
            val delimiter = "2324" // "#$"
            val delimIndex = hexPayload.indexOf(delimiter, ignoreCase = true)
            if (delimIndex < 0) return hexPayload

            val keyHex = hexPayload.substring(4, delimIndex)
            val keyStr = String(hexToBytes(keyHex), StandardCharsets.UTF_8)
                .padEnd(16, '0')
                .take(16)

            val tailHex = hexPayload.substring(delimIndex + delimiter.length)
            val ivHexLength = 26 // 13 bytes in standard TVBox offset or 32 hex chars
            val actualIvHex = if (tailHex.length > 32) tailHex.takeLast(26) else ""
            val ivStr = String(hexToBytes(actualIvHex), StandardCharsets.UTF_8)
                .padEnd(16, '0')
                .take(16)

            val cipherHex = if (tailHex.length > 26) {
                tailHex.substring(0, tailHex.length - 26)
            } else {
                tailHex
            }

            val cipherBytes = hexToBytes(cipherHex)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(keyStr.toByteArray(StandardCharsets.UTF_8), "AES")
            val ivSpec = IvParameterSpec(ivStr.toByteArray(StandardCharsets.UTF_8))
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            hexPayload
        }
    }

    /**
     * Fallback AES-128-ECB decryption for common TVBox public keys.
     */
    private fun decryptAesEcbHex(hexPayload: String): String {
        val commonKeys = listOf(
            "1234567890123456",
            "tvbox12345678901",
            "catvod1234567890"
        )
        val cipherBytes = try {
            hexToBytes(hexPayload)
        } catch (_: Exception) {
            return hexPayload
        }

        for (keyCandidate in commonKeys) {
            try {
                val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
                val keySpec = SecretKeySpec(keyCandidate.toByteArray(StandardCharsets.UTF_8), "AES")
                cipher.init(Cipher.DECRYPT_MODE, keySpec)
                val plain = String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
                if (plain.contains("sites") || plain.contains("urls")) {
                    return plain
                }
            } catch (_: Exception) {
                // Continue trying next key
            }
        }
        return hexPayload
    }

    private fun decodeBase64Payload(input: String): String {
        var current = input.trim()
        repeat(2) {
            if (!current.trimStart().startsWith("{")) {
                try {
                    val decoded = String(
                        Base64.decode(current, Base64.DEFAULT),
                        StandardCharsets.UTF_8
                    )
                    if (decoded.contains("{") && decoded.contains("}")) {
                        current = decoded
                    }
                } catch (_: Exception) {
                    // Not straight base64; try stripping prefix characters (often 6-8 random chars)
                    for (offset in 1..12) {
                        if (current.length > offset + 16) {
                            try {
                                val sub = current.substring(offset)
                                val decodedSub = String(
                                    Base64.decode(sub, Base64.DEFAULT),
                                    StandardCharsets.UTF_8
                                )
                                if (decodedSub.contains("sites") || decodedSub.contains("urls")) {
                                    current = decodedSub
                                    break
                                }
                            } catch (_: Exception) {
                                // Ignore
                            }
                        }
                    }
                }
            }
        }
        return current
    }

    /**
     * Strips single-line (`//`) and multi-line (`/* */`) comments outside of JSON strings, removes leading garbage
     * before the first `{`, and cleans trailing commas before `}` or `]`.
     */
    fun sanitizeJsonCommentsAndTrailingCommas(raw: String): String {
        val firstBrace = raw.indexOf('{')
        val lastBrace = raw.lastIndexOf('}')
        if (firstBrace < 0 || lastBrace <= firstBrace) return raw

        val sliced = raw.substring(firstBrace, lastBrace + 1)
        val sb = StringBuilder(sliced.length)
        var inString = false
        var isEscaped = false
        var i = 0

        while (i < sliced.length) {
            val c = sliced[i]
            val next = if (i + 1 < sliced.length) sliced[i + 1] else '\u0000'

            if (inString) {
                sb.append(c)
                if (isEscaped) {
                    isEscaped = false
                } else if (c == '\\') {
                    isEscaped = true
                } else if (c == '"') {
                    inString = false
                }
                i++
            } else {
                if (c == '"') {
                    inString = true
                    sb.append(c)
                    i++
                } else if (c == '/' && next == '/') {
                    // Skip single-line comment until newline
                    i += 2
                    while (i < sliced.length && sliced[i] != '\r' && sliced[i] != '\n') {
                        i++
                    }
                } else if (c == '/' && next == '*') {
                    // Skip block comment until */
                    i += 2
                    while (i + 1 < sliced.length && !(sliced[i] == '*' && sliced[i + 1] == '/')) {
                        i++
                    }
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
        }

        // Remove trailing commas like `, }` or `, ]`
        return sb.toString()
            .replace(Regex(",\s*([}\]])"), "$1")
            .trim()
    }

    private fun isLikelyBase64(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return false
        return trimmed.length >= 24 && !trimmed.contains("\n{")
    }

    private fun isHexString(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.length % 2 == 0 && trimmed.all {
            it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
