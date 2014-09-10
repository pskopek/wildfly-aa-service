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

package org.wildfly.security.sasl;

import static org.wildfly.security.sasl.anonymous.AbstractAnonymousFactory.ANONYMOUS;
import static org.wildfly.security.sasl.gssapi.AbstractGssapiFactory.GSSAPI;
import static org.wildfly.security.sasl.localuser.LocalUserSaslFactory.JBOSS_LOCAL_USER;
import static org.wildfly.security.sasl.md5digest.MD5DigestServerFactory.JBOSS_DIGEST_MD5;
import static org.wildfly.security.sasl.plain.PlainServerFactory.PLAIN;

import java.security.Provider;

import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslServerFactory;

import org.wildfly.security.sasl.anonymous.AnonymousClientFactory;
import org.wildfly.security.sasl.anonymous.AnonymousServerFactory;
import org.wildfly.security.sasl.gssapi.GssapiClientFactory;
import org.wildfly.security.sasl.gssapi.GssapiServerFactory;
import org.wildfly.security.sasl.localuser.LocalUserClientFactory;
import org.wildfly.security.sasl.localuser.LocalUserServerFactory;
import org.wildfly.security.sasl.plain.PlainServerFactory;
import org.wildfly.security.sasl.md5digest.MD5DigestClientFactory;
import org.wildfly.security.sasl.md5digest.MD5DigestServerFactory;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class WildFlySaslProvider extends Provider {

    private static final long serialVersionUID = 2819852358608732038L;

    private static final String INFO = "JBoss SASL Provider " + getVersionString();

    private static final String SASL_CLIENT_FACTORY = SaslClientFactory.class.getSimpleName();

    private static final String SASL_SERVER_FACTORY = SaslServerFactory.class.getSimpleName();

    private static final String DOT = ".";

    /**
     * Construct a new instance.
     */
    public WildFlySaslProvider() {
        super("wildfly-sasl", 1.0, INFO);
        // NOTE: make sure that all client and server factories listed here also end up in the META-INF/services files.
        put(SASL_CLIENT_FACTORY + DOT + ANONYMOUS, AnonymousClientFactory.class.getName());
        put(SASL_SERVER_FACTORY + DOT + ANONYMOUS, AnonymousServerFactory.class.getName());
        put(SASL_SERVER_FACTORY + DOT + PLAIN, PlainServerFactory.class.getName());
        put(SASL_SERVER_FACTORY + DOT + JBOSS_LOCAL_USER, LocalUserServerFactory.class.getName());
        put(SASL_CLIENT_FACTORY + DOT + JBOSS_LOCAL_USER, LocalUserClientFactory.class.getName());
        put(SASL_SERVER_FACTORY + DOT + GSSAPI, GssapiServerFactory.class.getName());
        put(SASL_CLIENT_FACTORY + DOT + GSSAPI, GssapiClientFactory.class.getName());
        put(SASL_CLIENT_FACTORY + DOT + JBOSS_DIGEST_MD5, MD5DigestClientFactory.class.getName());
        put(SASL_SERVER_FACTORY + DOT + JBOSS_DIGEST_MD5, MD5DigestServerFactory.class.getName());
    }

    /**
     * Get the version string of the WildFly SASL provider.
     *
     * @return the version string.
     */
    public static String getVersionString() {
        return "NOT SET";
    }

}
