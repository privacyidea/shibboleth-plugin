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

import java.util.Locale;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link TokenListEntry#formatLastUsed}: privacyIDEA reports {@code last_used} in two
 * inconsistent shapes, output is locale-aware (month names, AM/PM vs 24h), and unparseable / empty input
 * must degrade gracefully rather than blank out. Exact localized wording is CLDR-dependent, so the parsing
 * tests assert on stable properties (year present, 12h vs 24h) rather than a brittle full string.
 */
public class TokenListEntryTest
{
    @Test
    public void isoFormatWithColonOffsetEnglishIs12Hour()
    {
        String out = TokenListEntry.formatLastUsed("2026-10-12T12:18:30+00:00", Locale.US);
        assertTrue(out.contains("2026"), out);
        assertTrue(out.contains("12:18"), out);
        assertTrue(out.contains("PM"), out);
    }

    @Test
    public void spaceSeparatorMicrosecondsNoColonOffsetGermanIs24Hour()
    {
        String out = TokenListEntry.formatLastUsed("2026-10-12 14:24:33.305586+0200", Locale.GERMANY);
        assertTrue(out.contains("2026"), out);
        assertTrue(out.contains("14:24"), out);
        assertFalse(out.contains("PM"), out);
    }

    @Test
    public void noOffsetParses()
    {
        String out = TokenListEntry.formatLastUsed("2026-10-12 15:24:33", Locale.US);
        assertTrue(out.contains("2026"), out);
        assertTrue(out.contains("3:24"), out);
    }

    @Test
    public void emptyStaysEmpty()
    {
        assertEquals(TokenListEntry.formatLastUsed("", Locale.US), "");
        assertEquals(TokenListEntry.formatLastUsed("   ", Locale.US), "");
        assertEquals(TokenListEntry.formatLastUsed(null, Locale.US), "");
    }

    @Test
    public void unparseableFallsBackToTrimmedRaw()
    {
        assertEquals(TokenListEntry.formatLastUsed("  whenever  ", Locale.US), "whenever");
    }
}
