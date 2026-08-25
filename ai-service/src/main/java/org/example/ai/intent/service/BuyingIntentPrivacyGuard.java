package org.example.ai.intent.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Best-effort prevention of accidental contact publication; no submitted value is logged or
 * embedded. This is deliberately described as screening, not anonymization: user-authored text can
 * still contain indirect identifiers and is disclosed as seller-visible before publication.
 */
@Component
public class BuyingIntentPrivacyGuard {

    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern URL = Pattern.compile("(?i)(?:https?://|www\\.)\\S+");
    private static final Pattern BARE_DOMAIN = Pattern.compile(
            "(?i)(?<![@\\p{L}\\p{N}-])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+"
                    + "(?:com|net|org|io|uz|ru|biz|info|me|co|ai)(?:/[A-Z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*)?"
                    + "(?![\\p{L}\\p{N}-])");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\s().-]*){7,}(?!\\d)");
    private static final Pattern HANDLE = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}._%+-])@[a-z0-9_][a-z0-9_.-]{2,31}(?![\\p{L}\\p{N}_.-])");
    private static final Pattern ADDRESS_LABEL = Pattern.compile(
            "(?iu)\\b(?:delivery\\s+address|address|manzil|adres|адрес|манзил)\\s*[:=#-]\\s*\\S+");
    private static final Pattern STREET_AND_NUMBER = Pattern.compile(
            "(?iu)\\b(?:street|st\\.?|road|rd\\.?|avenue|ave\\.?|lane|building|house|"
                    + "ko['‘’`]?cha(?:si)?|кўча|куча|улица|ул\\.?|дом)\\b"
                    + "[\\p{L}\\p{M}\\s.'’`-]{0,48}\\d{1,5}(?:[-/]\\d{1,5})?\\b");

    public void requireNoContactDetails(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (EMAIL.matcher(value).find()
                || URL.matcher(value).find()
                || BARE_DOMAIN.matcher(value).find()
                || PHONE.matcher(value).find()
                || HANDLE.matcher(value).find()
                || ADDRESS_LABEL.matcher(value).find()
                || STREET_AND_NUMBER.matcher(value).find()) {
            throw new IllegalArgumentException(fieldName
                    + " must not contain contact handles, phone numbers, email/domain details, or precise addresses. "
                    + "Published text is seller-visible, so contact exchange is privacy-restricted.");
        }
    }
}
