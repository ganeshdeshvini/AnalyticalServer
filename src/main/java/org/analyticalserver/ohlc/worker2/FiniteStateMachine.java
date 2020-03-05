package org.analyticalserver.ohlc.worker2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Log4j
public class FiniteStateMachine {
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private final long period;
    private final TimeUnit timeUnit;
    private final JSONParser jsonParser = new JSONParser();
    private final ObjectMapper objectMapper = new ObjectMapper();
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

    public FiniteStateMachine(TimeUnit timeUnit, long period) {
        this.timeUnit = timeUnit;
        this.period = period;
    }

    public void addToQueue(String data) {
        mapBarNumberQueue.compute(cronBarNumber.get(), (key, queue) -> {
            if (queue == null) {
                queue = new LinkedBlockingDeque<>();
            }
            queue.add(data);
            return queue;
        });
    }

    public void scheduleIntervalCron() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::startProcessingDataPacketFromQueue, period, period, timeUnit);
        log.info(String.format("CRON Set for time unit: %s, period: %d", timeUnit, period));
    }

    private synchronized void startProcessingDataPacketFromQueue() {
        log.info("CRON TRIGGERED at: " + dateTimeFormatter.format(LocalDateTime.now()));

        //increment the barNumberInterval so that the next addition to the queue happens on the next barNumber
        cronBarNumber.incrementAndGet();

        //fetch the Queue for the barNumberToBeProcessed
        Queue<String> queueDataPacket = mapBarNumberQueue.get(barNumberToBeProcessed.get());

        log.info(String.format("cronBarNumber: %d, barNumberToBeProcessed: %d", cronBarNumber.get(), barNumberToBeProcessed.get()));
        if (queueDataPacket == null || queueDataPacket.isEmpty()) {
            //TODO: need to add logic when empty
            log.warn("Nothing to process, as Queue is Empty, for bar number: " + barNumberToBeProcessed.getAndIncrement());
            return;
        }

        while (!queueDataPacket.isEmpty()) {
            processDataPacket(queueDataPacket.remove());
        }

        notifyClients();

        //increment so that in the next interval we can process another bar
        barNumberToBeProcessed.getAndIncrement();
    }

    private void notifyClients() {
        log.info("Notifying Started for ALL Symbols");
        Long barNumberProcessing = barNumberToBeProcessed.get();

        mapBarNumberSymbolDataWrapperList.get(barNumberProcessing).forEach((symbol, dataWrapperList) -> {
            log.info(String.format("Notifying - BarNumber: %d | Symbol: %s", barNumberProcessing, symbol));

            //TODO: toggle if condition, this is for testing purpose
//            if (!dataWrapperList.isEmpty() && symbol.equals("XXBTZUSD")) {
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
                        log.info(objectMapper.writeValueAsString(barChartData));
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }

                final BigDecimal barNumberSymbolVolume = volume;
                mapBarNumberSymbolVolume.compute(new BarNumberSymbol(barNumberProcessing, symbol), (barNumberSymbol, value) -> barNumberSymbolVolume);
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

    private void processDataPacket(String data) {
        try {
            //map json data
            JSONObject jsonObject = (JSONObject) jsonParser.parse(data);

            String currentSymbol = String.valueOf(jsonObject.get("sym"));
            Double currentPrice = Double.valueOf(jsonObject.get("P").toString());
            Double currentQuantity = Double.valueOf(jsonObject.get("Q").toString());
//            Long currentTimestamp = (Long) jsonObject.get("TS2");

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
