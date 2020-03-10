package org.analyticalserver.ohlc.worker3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.analyticalserver.ohlc.worker2.BarChartData;
import org.analyticalserver.ohlc.worker2.BarNumberSymbol;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class NotifySubscribedClient {
    @Autowired
    private WebSocketSymbolRepository webSocketSymbolRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void startNotifyProcess(AtomicLong barNumberToBeProcessed, ConcurrentHashMap<Long, ConcurrentHashMap<String, List<BarChartData>>> mapBarNumberSymbolDataWrapperList, ConcurrentHashMap<BarNumberSymbol, BigDecimal> mapBarNumberSymbolVolume) {
        log.info("Notification started");
        Long barNumberProcessing = barNumberToBeProcessed.get();
        mapBarNumberSymbolDataWrapperList.get(barNumberProcessing).forEach((symbol, dataWrapperList) -> {
            if (!webSocketSymbolRepository.isSubscribedSymbol(symbol)) {
                log.info(String.format("Symbol: %s is not subscribed, will skip", symbol));
                return;
            }

            log.info(String.format("Notifying - BarNumber: %d | Symbol: %s", barNumberProcessing, symbol));

            if (!dataWrapperList.isEmpty()) {

                //Add the close field for the last data in the list
                BarChartData lastBarChartData = dataWrapperList.get(dataWrapperList.size() - 1);
                lastBarChartData.setC(lastBarChartData.getCurrentValue());

                BigDecimal volume = BigDecimal.valueOf(0.0);
                if (barNumberProcessing >= 2) {
                    //get last known bar volume for the symbol
                    volume = getLastBarNumberSymbolKnownVolume(barNumberProcessing, mapBarNumberSymbolVolume, symbol);
                }

                for (BarChartData barChartData : dataWrapperList) {
                    volume = volume.add(barChartData.getCurrentQuantity());
                    barChartData.setVolume(volume);
                    try {
                        notifyAllSubscribedClients(symbol, objectMapper.writeValueAsString(barChartData));
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }

                final BigDecimal barNumberSymbolVolume = volume;
                mapBarNumberSymbolVolume.compute(new BarNumberSymbol(barNumberProcessing, symbol), (barNumberSymbol, value) -> barNumberSymbolVolume);
            }
        });
    }

    private void notifyAllSubscribedClients(String symbol, String jsondata) {
        log.info(jsondata);
        Map<String, WebSocketSession> mapSymbolWebSocketSession = webSocketSymbolRepository.getMapSymbolWebSocketSession().get(symbol);
        mapSymbolWebSocketSession.forEach((websocketId, webSocketSession) -> {
            try {
                webSocketSession.sendMessage(new TextMessage(jsondata));
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        });
    }

    private BigDecimal getLastBarNumberSymbolKnownVolume(Long barNumberProcessing, ConcurrentHashMap<BarNumberSymbol, BigDecimal> mapBarNumberSymbolVolume, String symbol) {
        BigDecimal lastKnownVolume;
        while (barNumberProcessing >= 1) {
            lastKnownVolume = mapBarNumberSymbolVolume.get(new BarNumberSymbol(barNumberProcessing - 1, symbol));
            if (lastKnownVolume != null) {
                return lastKnownVolume;
            }
            barNumberProcessing--;
        }
        return BigDecimal.valueOf(0.0);
    }
}
