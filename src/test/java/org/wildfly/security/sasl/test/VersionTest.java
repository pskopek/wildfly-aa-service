/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.security.sasl.test;

import static org.junit.Assert.*;

import org.junit.Test;
import org.wildfly.security.sasl.WildFlySaslProvider;

/**
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class VersionTest {

    @Test
    public void testVersionSet() {
        assertNotNull(WildFlySaslProvider.getVersionString());
        assertNotEquals("", WildFlySaslProvider.getVersionString());
        assertNotEquals("NOT SET", WildFlySaslProvider.getVersionString());
    }

}
