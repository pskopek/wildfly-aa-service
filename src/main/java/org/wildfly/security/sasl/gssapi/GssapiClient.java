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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.MessageProp;
import org.jboss.logging.Logger;
import org.wildfly.common.Assert;
import org.wildfly.security._private.ElytronMessages;
import org.wildfly.security.credential.GSSCredentialCredential;
import org.wildfly.security.sasl.WildFlySasl;
import org.wildfly.security.sasl.util.SaslMechanismInformation;

/**
 * SaslClient for the GSSAPI mechanism as defined by RFC 4752
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class GssapiClient extends AbstractGssapiMechanism implements SaslClient {

    private static final ElytronMessages log = Logger.getMessageLogger(ElytronMessages.class, "org.wildfly.security.sasl.gssapi.client");

    private static final int INITIAL_CHALLENGE_STATE = 1;
    private static final int CHALLENGE_RESPONSE_STATE = 2;
    private static final int SECURITY_LAYER_NEGOTIATION_STATE = 3;

    private final String authorizationId;

    GssapiClient(final String protocol, final String serverName, final Map<String, ?> props,
            final CallbackHandler callbackHandler, final String authorizationId) throws SaslException {
        super(SaslMechanismInformation.Names.GSSAPI, protocol, serverName, props, callbackHandler, log);

        this.authorizationId = authorizationId;

        // Initialise our GSSContext
        GSSManager manager = GSSManager.getInstance();

        String acceptorNameString = protocol + "@" + serverName;
        log.tracef("Acceptor Name '%s'", acceptorNameString);
        GSSName acceptorName;
        try {
            // The client can use other name types but this should be added to the config.
            acceptorName = manager.createName(acceptorNameString, GSSName.NT_HOSTBASED_SERVICE, KERBEROS_V5);
            //acceptorName = acceptorName.canonicalize(KERBEROS_V5);
        } catch (GSSException e) {
            throw log.mechUnableToCreateNameForAcceptor(getMechanismName(), e).toSaslException();
        }

        // Pull the credential if we have it.
        GSSCredential credential = null;

        Object credObj = props.get(Sasl.CREDENTIALS);
        if (credObj instanceof GSSCredential) {
            log.trace("Using GSSCredential supplied in properties.");
            credential = (GSSCredential) credObj;
        } else if (credObj instanceof GSSCredentialCredential) {
            log.trace("Using GSSCredential supplied in properties.");
            credential = ((GSSCredentialCredential) credObj).getGssCredential();
        }

        // Better way to obtain the credential if we don't have one?

        final GSSContext gssContext;
        try {
            gssContext = manager.createContext(acceptorName, KERBEROS_V5, credential, GSSContext.INDEFINITE_LIFETIME);
        } catch (GSSException e) {
            throw log.mechUnableToCreateGssContext(getMechanismName(), e).toSaslException();
        }

        try {
            // JDK only sets this if a credential was supplied, we should support a config override.
            // i.e. we may have a credential because it was delegated to us - doesn't mean we want
            // to delegate it further - at same point we may have a Subject on ACC and still want to delegate.
            boolean delegate = credential != null;
            if (props.containsKey(WildFlySasl.GSSAPI_DELEGATE_CREDENTIAL)) {
                delegate = Boolean.parseBoolean((String) props.get(WildFlySasl.GSSAPI_DELEGATE_CREDENTIAL));
            }
            log.tracef("Delegating credential = %b", delegate);
            if (delegate) {
                gssContext.requestCredDeleg(true);
            }

            // The client must pass the integ_req_flag of true.
            gssContext.requestInteg(true);
            // This was requested so that integrity protection can be used to negotiate the security layer,
            // further integrity protection will be based on the negotiated security layer.

            // requestMutualAuth if: -
            // 1 - The client requests it.
            // 2 - The client will be requesting a security layer. Will interpret as may be requesting as
            // client and server could agree auth only.
            boolean serverAuth = false;
            if (props.containsKey(Sasl.SERVER_AUTH)) {
                serverAuth = Boolean.parseBoolean((String) props.get(Sasl.SERVER_AUTH));
            }

            boolean mayRequireSecurityLayer = mayRequireSecurityLater(orderedQops);
            boolean requestMutualAuth = serverAuth || mayRequireSecurityLayer;
            log.tracef("Setting requering mutual authentication to %b", requestMutualAuth);
            gssContext.requestMutualAuth(requestMutualAuth);

            // Request sequence detection if a security layer could be requested.
            if (mayRequireSecurityLayer) {
                log.trace("Requesting sequence detection.");
                gssContext.requestSequenceDet(true);
            }

            // Need to set this is we may want confidentiality, integrity is always requested.
            for (QOP current : orderedQops) {
                if (current == QOP.AUTH_CONF) {
                    log.trace("Requesting confidentiality");
                    gssContext.requestConf(true);
                    break;
                }
            }

        } catch (GSSException e) {
            throw log.mechUnableToSetGssContextRequestFlags(getMechanismName(), e).toSaslException();
        }

        // Channel Binding Is Not Supported

        this.gssContext = gssContext;
    }

    private boolean mayRequireSecurityLater(final QOP[] preferredQop) {
        for (QOP current : preferredQop) {
            if (current == QOP.AUTH_INT || current == QOP.AUTH_CONF) {
                return true;
            }
        }
        return false;
    }

    private QOP findAgreeableQop(final byte securityLayer) throws SaslException {
        for (QOP current : orderedQops) {
            if (current.includedBy(securityLayer) && isCompatibleWithGssContext(current)) {
                return current;
            }
        }

        throw log.mechInsufficientQopsAvailable(getMechanismName()).toSaslException();
    }

    private boolean isCompatibleWithGssContext(final QOP qop) {
        switch (qop) {
            case AUTH_INT:
                return gssContext.getIntegState();
            case AUTH_CONF:
                return gssContext.getIntegState() && gssContext.getConfState();
            default:
                return true;
        }
    }

    @Override
    public void init() {
        setNegotiationState(INITIAL_CHALLENGE_STATE);
    }

    @Override
    public boolean hasInitialResponse() {
        return true;
    }

    @Override
    public byte[] evaluateChallenge(byte[] challenge) throws SaslException {
        return evaluateMessage(challenge);
    }

    @Override
    protected byte[] evaluateMessage(int state, final byte[] message) throws SaslException {
        switch (state) {
            case INITIAL_CHALLENGE_STATE:
                // GSSAPI is a client first mechanism, this state both verifies that requirement is met and
                // provides the first token from the client
                assert gssContext.isEstablished() == false;
                if (message.length > 0) {
                    throw log.mechInitialChallengeMustBeEmpty(getMechanismName()).toSaslException();
                }

                try {
                    byte[] response = gssContext.initSecContext(NO_BYTES, 0, 0);
                    if (gssContext.isEstablished()) {
                        log.trace("GSSContext established, transitioning to negotiate security layer.");
                        setNegotiationState(SECURITY_LAYER_NEGOTIATION_STATE);
                    } else {
                        log.trace("GSSContext not established, expecting subsequent exchanges.");
                        setNegotiationState(CHALLENGE_RESPONSE_STATE);
                    }

                    return response;
                } catch (GSSException e) {
                    throw log.mechUnableToCreateResponseToken(getMechanismName(), e).toSaslException();
                }
            case CHALLENGE_RESPONSE_STATE:
                // This state is to handle the subsequent exchange of tokens up until the point the
                // GSSContext is established
                assert gssContext.isEstablished() == false;

                try {
                    byte[] response = gssContext.initSecContext(message, 0, message.length);

                    if (gssContext.isEstablished()) {
                        log.trace("GSSContext established, transitioning to negotiate security layer.");
                        setNegotiationState(SECURITY_LAYER_NEGOTIATION_STATE);
                        if (response == null) response = NO_BYTES;
                    } else {
                        log.trace("GSSContext not established, expecting subsequent exchanges.");
                    }
                    return response;
                } catch (GSSException e) {
                    throw log.mechUnableToHandleResponseFromServer(getMechanismName(), e).toSaslException();
                }
            case SECURITY_LAYER_NEGOTIATION_STATE:
                assert gssContext.isEstablished();

                MessageProp msgProp = new MessageProp(0, false);
                try {
                    byte[] unwrapped = gssContext.unwrap(message, 0, message.length, msgProp);
                    if (unwrapped.length != 4) {
                        throw log.mechBadLengthOfMessageForNegotiatingSecurityLayer(getMechanismName()).toSaslException();
                    }

                    byte qopByte = unwrapped[0];
                    selectedQop = findAgreeableQop(qopByte);
                    maxBuffer = networkOrderBytesToInt(unwrapped, 1, 3);
                    log.tracef("Selected QOP=%s, maxBuffer=%d", selectedQop, maxBuffer);
                    if (relaxComplianceChecks == false && maxBuffer > 0 && (qopByte & QOP.AUTH_INT.getValue()) == 0
                            && (qopByte & QOP.AUTH_CONF.getValue()) == 0) {
                        throw log.mechReceivedMaxMessageSizeWhenNoSecurityLayer(getMechanismName(), maxBuffer).toSaslException();
                    }
                    maxBuffer = gssContext.getWrapSizeLimit(0, selectedQop == QOP.AUTH_CONF, maxBuffer);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    baos.write(selectedQop.getValue());
                    if (selectedQop == QOP.AUTH) {
                        // No security layer selected to must set response to 000.
                        baos.write(new byte[] { 0x00, 0x00, 0x00 });
                    } else {
                        actualMaxReceiveBuffer = configuredMaxReceiveBuffer!=0 ? configuredMaxReceiveBuffer : maxBuffer;
                        log.tracef("Out max buffer %d", actualMaxReceiveBuffer);
                        baos.write(intToNetworkOrderBytes(actualMaxReceiveBuffer));
                    }

                    if (authorizationId != null) {
                        baos.write(authorizationId.getBytes(StandardCharsets.UTF_8));
                    }

                    byte[] response = baos.toByteArray();
                    msgProp = new MessageProp(0, false);
                    response = gssContext.wrap(response, 0, response.length, msgProp);

                    if (selectedQop != QOP.AUTH) {
                        log.trace("Setting message wrapper.");
                        setWrapper(new GssapiWrapper(selectedQop == QOP.AUTH_CONF));
                    }

                    log.trace("Negotiation Complete");
                    negotiationComplete();
                    return response;
                } catch (IOException e) {
                    throw log.mechUnableToCreateResponseToken(getMechanismName(), e).toSaslException();
                } catch (GSSException e) {
                    throw log.mechUnableToUnwrapSecurityLayerNegotiationMessage(getMechanismName(), e).toSaslException();
                }
        }
        throw Assert.impossibleSwitchCase(state);
    }

}
