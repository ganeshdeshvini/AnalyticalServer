package org.analyticalserver.ohlc.worker3;

import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class SocketHandler extends TextWebSocketHandler {
    private final JSONParser jsonParser = new JSONParser();

    @Autowired
    private WebSocketSymbolRepository webSocketSymbolRepository;

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) {
        log.info("connection established: " + webSocketSession);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus status) {
        log.info("connection closed: " + webSocketSession);
        webSocketSymbolRepository.removeSubscription(webSocketSession);
    }

    @Override
    public void handleTextMessage(WebSocketSession webSocketSession, TextMessage message) {
        try {
            log.info("websocket: " + webSocketSession.toString());
            log.info("message: " + message.getPayload());
            JSONObject jsonObject = (JSONObject) jsonParser.parse(message.getPayload());
            String symbol = String.valueOf(jsonObject.get("symbol"));
            webSocketSymbolRepository.addSubscription(symbol, webSocketSession);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }
}