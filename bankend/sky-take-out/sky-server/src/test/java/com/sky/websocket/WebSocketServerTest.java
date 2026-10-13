package com.sky.websocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketServerTest {

    private final WebSocketServer server = new WebSocketServer();

    @AfterEach
    void clearSessions() {
        ((Map<?, ?>) ReflectionTestUtils.getField(WebSocketServer.class, "sessionMap")).clear();
    }

    @Test
    void broadcastsToAllOpenConnections() throws IOException {
        Session first = openSession("first");
        Session second = openSession("second");

        server.sendToAllClient("reminder");

        verify(first.getBasicRemote()).sendText("reminder");
        verify(second.getBasicRemote()).sendText("reminder");
    }

    @Test
    void skipsAndRemovesClosedConnections() throws IOException {
        Session closed = openSession("closed");
        when(closed.isOpen()).thenReturn(false);
        Session active = openSession("active");

        server.sendToAllClient("reminder");

        verify(closed.getBasicRemote(), never()).sendText(anyString());
        verify(active.getBasicRemote()).sendText("reminder");
        Map<?, ?> sessions = (Map<?, ?>) ReflectionTestUtils.getField(WebSocketServer.class, "sessionMap");
        assertFalse(sessions.containsKey("closed"));
    }

    @ParameterizedTest
    @MethodSource("sendFailures")
    void failedConnectionDoesNotInterruptBroadcast(Exception failure) throws IOException {
        Session broken = openSession("broken");
        RemoteEndpoint.Basic remote = broken.getBasicRemote();
        doThrow(failure).when(remote).sendText(anyString());
        Session active = openSession("active");

        assertDoesNotThrow(() -> server.sendToAllClient("reminder"));

        verify(active.getBasicRemote()).sendText("reminder");
    }

    static Stream<Exception> sendFailures() {
        return Stream.of(new IOException("connection lost"), new IllegalStateException("connection closed"));
    }

    @Test
    void closingOldConnectionKeepsReconnectedSessionWithSameSid() throws IOException {
        Session old = openSession("admin");
        Session current = openSession("admin");

        server.onClose(old, "admin");
        server.sendToAllClient("reminder");

        verify(old.getBasicRemote(), never()).sendText(anyString());
        verify(current.getBasicRemote()).sendText("reminder");
    }

    private Session openSession(String sid) {
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getBasicRemote()).thenReturn(mock(RemoteEndpoint.Basic.class));
        server.onOpen(session, sid);
        return session;
    }
}
