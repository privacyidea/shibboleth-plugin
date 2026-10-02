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
package org.privacyidea;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * The Shibboleth plugin installer only copies the resources listed in {@code module.properties}. A file that is
 * in the jar but not listed there never reaches an installed IdP (deploy.sh copies whole directories and would
 * not notice), so every installable resource must be listed, and every listed source must exist.
 */
public class ModulePropertiesTest
{
    private static final String BASE = "/org/privacyidea";
    private static final List<String> INSTALLED_DIRS = List.of("edit-webapp", "views", "messages", "doc");

    private Path baseDir;
    private Set<String> listedSources;

    @BeforeClass
    public void loadModuleProperties() throws IOException, URISyntaxException
    {
        Properties properties = new Properties();
        try (InputStream in = ModulePropertiesTest.class.getResourceAsStream(BASE + "/module.properties"))
        {
            assertNotNull(in, "module.properties not found on the classpath");
            properties.load(in);
        }
        listedSources = properties.stringPropertyNames().stream()
                                  .filter(key -> key.startsWith("idp.authn.privacyIDEA.") && key.endsWith(".src"))
                                  .map(properties::getProperty)
                                  .map(String::trim)
                                  .collect(Collectors.toSet());
        // Resolve via module.properties: the package directory itself also exists under test-classes.
        baseDir = Paths.get(Objects.requireNonNull(ModulePropertiesTest.class.getResource(BASE + "/module.properties")).toURI()).getParent();
    }

    @Test
    public void everyInstallableResourceIsListed() throws IOException
    {
        for (String dir : INSTALLED_DIRS)
        {
            try (Stream<Path> files = Files.walk(baseDir.resolve(dir)))
            {
                List<String> missing = files.filter(Files::isRegularFile)
                                            .map(file -> BASE + "/" + baseDir.relativize(file).toString().replace('\\', '/'))
                                            .filter(resource -> !listedSources.contains(resource))
                                            .sorted()
                                            .collect(Collectors.toList());
                assertTrue(missing.isEmpty(), "Not listed in module.properties, so never installed: " + missing);
            }
        }
    }

    @Test
    public void everyListedSourceExists()
    {
        List<String> missing = listedSources.stream()
                                            .filter(resource -> ModulePropertiesTest.class.getResource(resource) == null)
                                            .sorted()
                                            .collect(Collectors.toList());
        assertTrue(missing.isEmpty(), "Listed in module.properties but not in the jar: " + missing);
    }
}
