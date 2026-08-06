/*
 * Copyright 2026 NetKnights GmbH - nils.behlen@netknights.it
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.privacyidea.context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Set;

/**
 * A single row in the {@code tokenSelection} token list shown to the user: the display fields plus whether
 * this token can be used ({@link #isUsable()}) and, if so, whether it authenticates by triggering a
 * challenge ({@link #isTriggerable()} → a "Use" button) or by typing a code in the always-present OTP field.
 * <p>
 * Unusable tokens (revoked / locked / inactive / mid-rollout) are still listed — sorted to the end — with a
 * {@link #getStatus()} label, so a user sees the token exists rather than wondering where it went.
 * <p>
 * Kept separate from the java-client's {@code TokenInfo} so the view depends only on what it renders and the
 * classification logic lives in one place.
 */
public final class TokenListEntry
{
    /**
     * Token types that authenticate by triggering a challenge the user then responds to, rather than by
     * typing a code. Everything else (hotp/totp/spass/tan/…) is entered in the always-present OTP field.
     * NOTE: hotp/totp can be put in challenge-response mode server-side, which {@code GET /token} does not
     * report — those are treated as direct-entry here for now (a known limitation, see issue #84).
     */
    private static final Set<String> TRIGGERABLE_TYPES = Set.of("push", "webauthn", "passkey");

    /**
     * Parser for the {@code last_used} timestamp. privacyIDEA is not consistent about the format — it can be
     * either {@code 2026-08-03T13:48:30+00:00} (ISO, 'T' separator, colon in offset) or
     * {@code 2026-08-03 15:24:33.305586+0200} (space separator, microseconds, no colon in offset). We normalise
     * the space to 'T' and accept optional fractional seconds and either offset style (or none).
     */
    private static final DateTimeFormatter LAST_USED_PARSER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4).appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
            .optionalStart().appendOffset("+HHMM", "Z").optionalEnd()
            .toFormatter(Locale.ROOT);

    private final String serial;
    private final String type;
    private final String description;
    private final String lastUsed;
    private final boolean usable;
    private final String status;
    private final boolean triggerable;

    /**
     * @param serial       the token serial
     * @param type         the token type (privacyIDEA {@code tokentype}, e.g. push / totp / passkey)
     * @param description  the token description (may be empty)
     * @param lastUsed     the last-authentication timestamp as privacyIDEA reports it, or empty if never used
     * @param active       privacyIDEA {@code active} flag
     * @param revoked      privacyIDEA {@code revoked} flag
     * @param locked       privacyIDEA {@code locked} flag
     * @param rolloutState privacyIDEA {@code rollout_state} (usable only when {@code enrolled})
     * @param locale       the request locale, used to format {@link #getLastUsed()} (month names, AM/PM vs 24h)
     */
    public TokenListEntry(String serial, String type, String description, String lastUsed,
                          boolean active, boolean revoked, boolean locked, String rolloutState, Locale locale)
    {
        this.serial = serial == null ? "" : serial;
        this.type = type == null ? "" : type;
        this.description = description == null ? "" : description;
        this.lastUsed = formatLastUsed(lastUsed, locale);

        // Determine usability and, when not usable, a short reason to show the user.
        if (revoked)
        {
            this.usable = false;
            this.status = "revoked";
        }
        else if (locked)
        {
            this.usable = false;
            this.status = "locked";
        }
        else if (!active)
        {
            this.usable = false;
            this.status = "inactive";
        }
        else if (rolloutState != null && !rolloutState.isEmpty() && !"enrolled".equals(rolloutState))
        {
            this.usable = false;
            this.status = rolloutState;
        }
        else
        {
            this.usable = true;
            this.status = "";
        }
        // Only a usable, challenge-type token gets a "Use" button.
        this.triggerable = this.usable && TRIGGERABLE_TYPES.contains(this.type.toLowerCase());
    }

    /**
     * Turn privacyIDEA's raw {@code last_used} string into a compact, locale-appropriate timestamp — e.g.
     * {@code Oct 12, 2026, 12:18 PM} (en) or {@code 12.10.2026, 12:18} (de). The wall-clock the server sent is
     * shown as-is (no zone conversion). Falls back to the trimmed raw value if the timestamp matches neither
     * known format, so a parser surprise degrades to "ugly but shown" rather than a blank field.
     */
    static String formatLastUsed(String raw, Locale locale)
    {
        if (raw == null || raw.trim().isEmpty())
        {
            return "";
        }
        String trimmed = raw.trim();
        try
        {
            LocalDateTime parsed = LocalDateTime.from(LAST_USED_PARSER.parse(trimmed.replaceFirst(" ", "T")));
            return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                                    .withLocale(locale == null ? Locale.getDefault() : locale)
                                    .format(parsed);
        }
        catch (RuntimeException e)
        {
            return trimmed;
        }
    }

    public String getSerial() {return serial;}

    public String getType() {return type;}

    public String getDescription() {return description;}

    /** @return the last-used timestamp, locale-formatted for display, or empty if the token was never used. */
    public String getLastUsed() {return lastUsed;}

    /** @return whether the token can be used to authenticate (active, enrolled, not revoked/locked). */
    public boolean isUsable() {return usable;}

    /** @return why the token is unusable (e.g. {@code revoked} / {@code locked}), or empty when usable. */
    public String getStatus() {return status;}

    /** @return whether this token is used by triggering a challenge (Use button) vs. typed directly. */
    public boolean isTriggerable() {return triggerable;}
}
