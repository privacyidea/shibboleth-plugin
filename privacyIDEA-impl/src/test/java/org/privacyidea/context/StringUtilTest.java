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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Unit tests for {@link StringUtil#sanitizeForLog(String)}.
 */
public class StringUtilTest
{
    @Test
    public void plainValueUnchanged()
    {
        assertEquals(StringUtil.sanitizeForLog("alice@example.org"), "alice@example.org");
    }

    @Test
    public void nullBecomesLiteral()
    {
        assertEquals(StringUtil.sanitizeForLog(null), "null");
    }

    @Test
    public void controlCharactersReplaced()
    {
        // A CRLF pair is one line break, so it becomes a single underscore.
        assertEquals(StringUtil.sanitizeForLog("alice\r\nINFO next\tline\u0000"), "alice_INFO next_line_");
    }

    @Test
    public void unicodeLineSeparatorsReplaced()
    {
        assertEquals(StringUtil.sanitizeForLog("a\u2028b\u2029c\u0085d"), "a_b_c_d");
    }

    @Test
    public void c1ControlCharactersReplaced()
    {
        // C1 controls (U+0080..U+009F) are outside the ASCII-only \p{Cntrl}, e.g. U+009B (CSI).
        assertEquals(StringUtil.sanitizeForLog("a\u009B31mb\u0080c"), "a_31mb_c");
    }
}
