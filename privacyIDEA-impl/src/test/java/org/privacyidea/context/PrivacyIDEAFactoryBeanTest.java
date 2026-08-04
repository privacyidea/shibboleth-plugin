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

import org.privacyidea.PrivacyIDEA;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link PrivacyIDEAFactoryBean}: the fail-fast behaviour of {@code getObject()} before the
 * client is built (PR-review hardening) and the basic FactoryBean contract.
 */
public class PrivacyIDEAFactoryBeanTest
{
    @Test(expectedExceptions = IllegalStateException.class)
    public void getObjectBeforeInitializeThrows()
    {
        // Fail fast rather than injecting null and NPE-ing at request time.
        new PrivacyIDEAFactoryBean().getObject();
    }

    @Test
    public void factoryBeanContract()
    {
        PrivacyIDEAFactoryBean factory = new PrivacyIDEAFactoryBean();
        assertEquals(factory.getObjectType(), PrivacyIDEA.class);
        assertTrue(factory.isSingleton());
    }

    @Test
    public void initializeBuildsClientAndGetObjectReturnsIt()
    {
        PrivacyIDEAFactoryBean factory = new PrivacyIDEAFactoryBean();
        factory.setServerURL("https://localhost");
        factory.setVerifySSL(false);
        factory.initialize();
        try
        {
            assertNotNull(factory.getObject());
        }
        finally
        {
            factory.destroy();
        }
    }

    @Test
    public void invalidHttpTimeoutIsIgnoredNotFatal()
    {
        // Defensive parse (like remember_me_days): a typo must not fail flow startup.
        PrivacyIDEAFactoryBean factory = new PrivacyIDEAFactoryBean();
        factory.setHttpTimeoutMs("not-a-number");
        factory.setHttpTimeoutMs("0");
        factory.setHttpTimeoutMs("");
        factory.setServerURL("https://localhost");
        factory.initialize();
        try
        {
            assertNotNull(factory.getObject());
        }
        finally
        {
            factory.destroy();
        }
    }
}
