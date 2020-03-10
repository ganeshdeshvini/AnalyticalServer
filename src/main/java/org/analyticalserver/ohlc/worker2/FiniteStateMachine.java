package org.analyticalserver.ohlc.worker2;

import lombok.extern.slf4j.Slf4j;
import org.analyticalserver.ohlc.worker3.NotifySubscribedClient;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class FiniteStateMachine {
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS");
    private final JSONParser jsonParser = new JSONParser();

    @Autowired
    private NotifySubscribedClient notifySubscribedClient;
    //
    private AtomicLong cronBarNumber = new AtomicLong(1);
    private AtomicLong barNumberToBeProcessed = new AtomicLong(1);

    //maintains queue
    private ConcurrentHashMap<Long, Queue<String>> mapBarNumberQueue = new ConcurrentHashMap<>();

    //for data
    private ConcurrentHashMap<Long, ConcurrentHashMap<String, List<BarChartData>>> mapBarNumberSymbolDataWrapperList = new ConcurrentHashMap<>();

    //for metadata
    private ConcurrentHashMap<BarNumberSymbol, BarNumberSymbolMetaData> mapBarNumberSymbolMetaDataWrapper = new ConcurrentHashMap<>();

    //for keeping track of volume of previous bar number + symbol
    private ConcurrentHashMap<BarNumberSymbol, BigDecimal> mapBarNumberSymbolVolume = new ConcurrentHashMap<>();

    public void addToQueue(String data) {
        mapBarNumberQueue.compute(cronBarNumber.get(), (key, queue) -> {
            if (queue == null) {
                queue = new ConcurrentLinkedQueue<>();
            }
            queue.add(data);
            return queue;
        });
    }

    public void scheduleCustomIntervalCron(TimeUnit timeUnit, Long period) {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::startProcessingDataPacketFromQueue, period, period, timeUnit);
        log.info(String.format("CRON Set for time unit: %s, period: %d", timeUnit, period));
    }

    public void scheduleDefaultIntervalCron() {
        scheduleCustomIntervalCron(TimeUnit.SECONDS, 15L);
    }

    private synchronized void startProcessingDataPacketFromQueue() {
        log.info("CRON TRIGGERED at: " + dateTimeFormatter.format(LocalDateTime.now()));

        //increment the barNumberInterval so that the next addition to the queue happens on the next barNumber
        cronBarNumber.incrementAndGet();

        //fetch the Queue for the barNumberToBeProcessed
        Queue<String> queueDataPacket = mapBarNumberQueue.get(barNumberToBeProcessed.get());

        log.info(String.format("cronBarNumber: %d, barNumberToBeProcessed: %d", cronBarNumber.get(), barNumberToBeProcessed.get()));
        if (queueDataPacket == null || queueDataPacket.isEmpty()) {
            log.warn("Nothing to process, as Queue is Empty, for bar number: " + barNumberToBeProcessed.getAndIncrement());
            return;
        }

        while (!queueDataPacket.isEmpty()) {
            processDataPacket(queueDataPacket.remove());
        }

        notifySubscribedClient.startNotifyProcess(barNumberToBeProcessed, mapBarNumberSymbolDataWrapperList, mapBarNumberSymbolVolume);

        //increment so that in the next interval we can process another bar
        barNumberToBeProcessed.getAndIncrement();
    }

    private void processDataPacket(String data) {
        try {
            //map json data
            JSONObject jsonObject = (JSONObject) jsonParser.parse(data);

            String currentSymbol = String.valueOf(jsonObject.get("sym"));
            Double currentPrice = Double.valueOf(jsonObject.get("P").toString());
            Double currentQuantity = Double.valueOf(jsonObject.get("Q").toString());

            //compare current value with the meta data(if any) & update high/low price accordingly
            BarNumberSymbolMetaData barNumberSymbolMetaData = compareWithMetaDataAndUpdateIfRequired(currentSymbol, currentPrice);

            //prepare partial bar chart data, excluding the close & volume field
            preparePartialBarCharData(currentSymbol, currentPrice, currentQuantity, barNumberSymbolMetaData);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private void preparePartialBarCharData(String currentSymbol, Double currentPrice, Double currentQuantity, BarNumberSymbolMetaData barNumberSymbolMetaData) {
        mapBarNumberSymbolDataWrapperList.computeIfAbsent(barNumberToBeProcessed.get(), key -> new ConcurrentHashMap<>());

        mapBarNumberSymbolDataWrapperList.get(barNumberToBeProcessed.get()).compute(currentSymbol, (key, dataWrapperList) -> {
            if (dataWrapperList == null) {
                dataWrapperList = new ArrayList<>();
            }
            BarChartData barChartData = BarChartData.builder()
                    .barNumber(barNumberToBeProcessed.get())
                    .symbol(currentSymbol)
                    .o(BigDecimal.valueOf(barNumberSymbolMetaData.getOpen()))
                    .h(BigDecimal.valueOf(barNumberSymbolMetaData.getHigh()))
                    .l(BigDecimal.valueOf(barNumberSymbolMetaData.getLow()))
                    .c(BigDecimal.valueOf(0.0))
                    .currentValue(BigDecimal.valueOf(currentPrice))
                    .currentQuantity(BigDecimal.valueOf(currentQuantity))
                    .build();
            dataWrapperList.add(barChartData);
            return dataWrapperList;
        });
    }

    private BarNumberSymbolMetaData compareWithMetaDataAndUpdateIfRequired(String currentSymbol, Double currentPrice) {
        BarNumberSymbol barNumberSymbol = new BarNumberSymbol(barNumberToBeProcessed.get(), currentSymbol);
        mapBarNumberSymbolMetaDataWrapper.computeIfAbsent(barNumberSymbol, key -> BarNumberSymbolMetaData.builder()
                .open(currentPrice)
                .high(currentPrice)
                .low(currentPrice).build());

        BarNumberSymbolMetaData barNumberSymbolMetaData = mapBarNumberSymbolMetaDataWrapper.get(barNumberSymbol);
        if (currentPrice > barNumberSymbolMetaData.getHigh()) {
            barNumberSymbolMetaData.setHigh(currentPrice);
        }
        if (currentPrice < barNumberSymbolMetaData.getLow()) {
            barNumberSymbolMetaData.setLow(currentPrice);
        }
        return barNumberSymbolMetaData;
    }
}
