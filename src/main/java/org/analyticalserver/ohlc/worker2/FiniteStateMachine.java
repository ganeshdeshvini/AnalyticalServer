package org.analyticalserver.ohlc.worker2;

import lombok.extern.log4j.Log4j;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

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
    private final long period;
    private final TimeUnit timeUnit;
    private JSONParser jsonParser = new JSONParser();
    private AtomicLong cronBarNumber = new AtomicLong(1);
    private AtomicLong barNumberToBeProcessed = new AtomicLong(1);
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    //maintains queue
    private ConcurrentHashMap<Long, Queue<String>> mapBarNumberQueue = new ConcurrentHashMap<>();

    //for data
    private ConcurrentHashMap<Long, ConcurrentHashMap<String, List<BarChartData>>> mapBarNumberSymbolDataWrapperList = new ConcurrentHashMap<>();

    //for metadata
    private ConcurrentHashMap<BarNumberSymbol, BarNumberSymbolMetaData> mapBarNumberSymbolMetaDataWrapper = new ConcurrentHashMap<>();

    //for keeping track of previous volume of previous bar number + symbol
    private ConcurrentHashMap<BarNumberSymbol, Double> mapBarNumberSymbolVolume = new ConcurrentHashMap<>();

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

    private void startProcessingDataPacketFromQueue() {
        log.info("CRON TRIGGERED at: " + dateTimeFormatter.format(LocalDateTime.now()));

        //increment the barNumberInterval so that the next addition to the queue happens on the next barNumber
        cronBarNumber.incrementAndGet();

        //fetch the Queue for the barNumberProcessed
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
            log.info(String.format("BarNumber: %d | Symbol: %s", barNumberProcessing, symbol));

            //TODO: toggle if condition, this is for testing purpose
//            if (!dataWrapperList.isEmpty() && symbol.equals("XXBTZUSD")) {
            if (!dataWrapperList.isEmpty()) {

                //Add the close field for the last data in the list
                BarChartData lastBarChartData = dataWrapperList.get(dataWrapperList.size() - 1);
                lastBarChartData.setC(lastBarChartData.getCurrentValue());

                Double volume = 0.0;
                if (barNumberProcessing > 1) {
                    //get last known bar volume for the symbol
                    volume = getLastBarNumberSymbolKnownVolume(barNumberProcessing, mapBarNumberSymbolVolume, symbol);
                }

                for (BarChartData barChartData : dataWrapperList) {
                    volume += barChartData.getCurrentQuantity();
                    barChartData.setVolume(volume);
                    log.info(barChartData);
                }

                final Double barNumberSymbolVolume = volume;
                mapBarNumberSymbolVolume.compute(new BarNumberSymbol(barNumberProcessing, symbol), (barNumberSymbol, value) -> barNumberSymbolVolume);
            }
        });
    }

    private Double getLastBarNumberSymbolKnownVolume(Long barNumberProcessing, ConcurrentHashMap<BarNumberSymbol, Double> mapBarNumberSymbolVolume, String symbol) {
        Double lastKnownVolume;
        while (barNumberProcessing >= 1) {
            lastKnownVolume = mapBarNumberSymbolVolume.get(new BarNumberSymbol(barNumberProcessing - 1, symbol));
            if (lastKnownVolume != null) {
                return lastKnownVolume;
            }
            barNumberProcessing--;
        }
        return 0.0;
    }

    private void processDataPacket(String data) {
        try {
            //map json data
            JSONObject jsonObject = (JSONObject) jsonParser.parse(data);

            String currentSymbol = String.valueOf(jsonObject.get("sym"));
            Double currentPrice = Double.valueOf(jsonObject.get("P").toString());
            Double currentQuantity = Double.valueOf(jsonObject.get("Q").toString());
            Long currentTimestamp = (Long) jsonObject.get("TS2");

            //compare current data with the meta data(if any) & update high/low price accordingly
            BarNumberSymbolMetaData barNumberSymbolMetaData = compareWithMetaDataAndUpdateIfRequired(currentSymbol, currentPrice);

            //prepare bar chart data, leaving the close & volume field
            preparePartialBarCharData(currentSymbol, currentPrice, currentQuantity, barNumberSymbolMetaData);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private void preparePartialBarCharData(String currentSymbol, Double currentPrice, Double currentQuantity, BarNumberSymbolMetaData barNumberSymbolMetaData) {
        mapBarNumberSymbolDataWrapperList.computeIfAbsent(barNumberToBeProcessed.get(), bnp -> new ConcurrentHashMap<>());
        ConcurrentHashMap<String, List<BarChartData>> mapSymbolDataWrapperList = mapBarNumberSymbolDataWrapperList.get(barNumberToBeProcessed.get());

        mapSymbolDataWrapperList.compute(currentSymbol, (key, dataWrapperList) -> {
            if (dataWrapperList == null) {
                dataWrapperList = new ArrayList<>();
            }
            BarChartData barChartData = BarChartData.builder()
                    .barNumber(barNumberToBeProcessed.get())
                    .symbol(currentSymbol)
                    .o(barNumberSymbolMetaData.getOpen())
                    .h(barNumberSymbolMetaData.getHigh())
                    .l(barNumberSymbolMetaData.getLow())
                    .c(0.0)
                    .currentValue(currentPrice)
                    .currentQuantity(currentQuantity)
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
