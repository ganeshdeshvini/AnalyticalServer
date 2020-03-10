package org.analyticalserver.ohlc.worker3;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSymbolRepository {
    //symbol => {websocketid -> websocketsession}
    private Map<String, Map<String, WebSocketSession>> mapSymbolWebSocketSession = new ConcurrentHashMap<>();

    public void addSubscription(String symbol, WebSocketSession actualWebSocketSession) {
        //        session.sendMessage(new TextMessage("Hello " + value.get("name") + " !"));
        mapSymbolWebSocketSession.compute(symbol, (s, mapIdWebSocketSession) -> {
            if (mapIdWebSocketSession == null) {
                mapIdWebSocketSession = new ConcurrentHashMap<>();
            }
            mapIdWebSocketSession.compute(actualWebSocketSession.getId(), (id, webSocketSession1) -> actualWebSocketSession);
            return mapIdWebSocketSession;
        });
    }

    public void removeSubscription(WebSocketSession actualWebSocketSession) {
        String websocketId = actualWebSocketSession.getId();
        if (!mapSymbolWebSocketSession.isEmpty()) {
            for (Map<String, WebSocketSession> mapSymbolWebSocketSession : mapSymbolWebSocketSession.values()) {
                System.out.println(mapSymbolWebSocketSession);
                mapSymbolWebSocketSession.remove(websocketId);
            }
        }
    }

    public Map<String, Map<String, WebSocketSession>> getMapSymbolWebSocketSession() {
        return mapSymbolWebSocketSession;
    }

    public boolean isEmpty() {
        return mapSymbolWebSocketSession == null || mapSymbolWebSocketSession.isEmpty();
    }

    public boolean isSubscribedSymbol(String symbol) {
        return mapSymbolWebSocketSession.containsKey(symbol);
    }
}
