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
package org.wildfly.security.sasl.gssapi;

import org.jboss.logging.Logger;

/**
 * Test suite to run all GSSAPI tests to allow various permutations of mechanism interaction to be verified.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class KDCServer {

    private static Logger log = Logger.getLogger(KDCServer.class);

    static TestKDC testKdc;
    static String serverKeyTab;
    static String clientKeyTab;

    public static void main(String[] args) {
        log.debug("Start");

        TestKDC testKdc = new TestKDC();
        testKdc.startDirectoryService();
        testKdc.startKDC();
        KDCServer.testKdc = testKdc;
        serverKeyTab = testKdc.generateKeyTab("/opt/serverKeyTab", "sasl/test_server_1@WILDFLY.ORG", "servicepwd");
        clientKeyTab = testKdc.generateKeyTab("/opt/clientKeyTab", "jduke@WILDFLY.ORG", "theduke");
        //serverKeyTab = "/opt/keytab/serverKeyTab";
        log.debug("keytab written to:" + serverKeyTab);
    }

    public static void stopServers() {
        if (testKdc != null) {
            testKdc.stopAll();
            testKdc = null;
        }
    }

}
