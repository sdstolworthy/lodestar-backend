package com.redhat.labs.lodestar.socket;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.ws.rs.core.UriBuilder;

import org.eclipse.microprofile.jwt.Claims;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.labs.lodestar.utils.EmbeddedMongoTest;
import com.redhat.labs.lodestar.utils.TokenUtils;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@EmbeddedMongoTest
@QuarkusTest
public class EngagementEventSocketTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngagementEventSocketTest.class);
    private static final LinkedBlockingDeque<String> MESSAGES = new LinkedBlockingDeque<>();

    @TestHTTPResource("/engagements/events")
    URI uri;

    @Inject
    EngagementEventSocket socket;

    /*
     * - No Token - Invalid Token - Valid Token
     */

    @Test
    public void testWebsocketNoToken() {

        try {
            ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri);
        } catch (DeploymentException | IOException e) {

            Throwable[] suppressed = e.getSuppressed();
            if (suppressed.length == 1) {
                Throwable t = suppressed[0].getCause();
                Assertions.assertEquals("Invalid handshake response getStatus: 403 Forbidden",
                        t.getMessage());
            } else {
                fail("failed with exception " + e.getMessage());
            }
        }

    }

    @Test
    public void testWebsocketExpiredToken() throws Exception {

        // create new token
        HashMap<String, Long> timeClaims = new HashMap<>();
        timeClaims.put(Claims.exp.name(), 1l);
        String token = TokenUtils.generateTokenString("/JwtClaimsReader.json", timeClaims);

        // expire token
        TimeUnit.SECONDS.sleep(1);

        // add token to query param
        URI tokenUri = UriBuilder.fromUri(uri).queryParam("access-token", token).build();

        try {
            ContainerProvider.getWebSocketContainer().connectToServer(Client.class, tokenUri);
        } catch (DeploymentException | IOException e) {

            Throwable[] suppressed = e.getSuppressed();
            if (suppressed.length == 1) {
                Throwable t = suppressed[0].getCause();
                Assertions.assertEquals("Invalid handshake response getStatus: 403 Forbidden",
                        t.getMessage());
            } else {
                fail("failed with exception " + e.getMessage());
            }
        }

    }
    
    @Disabled
    @Test
    public void testWebsocketEvents() throws Exception {

        // create new token
        HashMap<String, Long> timeClaims = new HashMap<>();
        String token = TokenUtils.generateTokenString("/JwtClaimsReader.json", timeClaims);

        // add token to query param
        URI tokenUri = UriBuilder.fromUri(uri).queryParam("access-token", token).build();

        // get message from socket
        try (Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, tokenUri)) {
            LOGGER.info("waiting for initial connect message");
            Assertions.assertEquals("CONNECT", MESSAGES.poll(10, TimeUnit.SECONDS));
            LOGGER.info("sending test message to broadcast");
            socket.broadcast("testing");
            LOGGER.info("test message send using socket, now waiting...");
            Assertions.assertEquals("testing", MESSAGES.poll(10, TimeUnit.SECONDS));
            LOGGER.info("got test message");
        }

    }

    @ClientEndpoint
    public static class Client {

        @OnOpen
        public void open(Session session) {
            MESSAGES.add("CONNECT");
        }

        @OnMessage
        void message(String msg) {
            LOGGER.info("client received message '{}' from socket.", msg);
            MESSAGES.add(msg);
        }

    }

}
