package de.flashheart.rlg.commander.websockets;

import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
@Log4j2
public class ServerWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.debug("Server connection opened");
        sessions.add(session);
        
        TextMessage message = new TextMessage("one-time message from server");
        log.debug("Server sends: {}", message);
        session.sendMessage(message);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.debug("Server connection closed: {}", status);
        sessions.remove(session);
    }

    @Scheduled(fixedRate = 10000)
    void sendPeriodicMessages() throws IOException {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                String broadcast = "server periodic message " + LocalTime.now();
                log.debug("Server sends: {}", broadcast);
                session.sendMessage(new TextMessage(broadcast));
            }
        }
    }
    
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String request = message.getPayload();
        log.debug("Server received: {}", request);
        
        String response = String.format("response from server to '%s'", HtmlUtils.htmlEscape(request));
        log.debug("Server sends: {}", response);
        session.sendMessage(new TextMessage(response));
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Server transport error: {}", exception.getMessage());
    }
    
    @Override
    public List<String> getSubProtocols() {
        return Collections.singletonList("subprotocol.demo.websocket");
    }
}
