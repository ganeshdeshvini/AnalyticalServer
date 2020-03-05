package org.analyticalserver.ohlc;

import org.analyticalserver.ohlc.worker1.ReadTradeDataJson;
import org.analyticalserver.ohlc.worker2.FiniteStateMachine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ApplicationRunner {
    public static void main(String[] args) {
        //worker-2 FSM
        FiniteStateMachine finiteStateMachine = new FiniteStateMachine(TimeUnit.MILLISECONDS, 200);
        finiteStateMachine.scheduleIntervalCron();

        //worker-1 Read trades.json
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        ReadTradeDataJson readTradeDataJson = new ReadTradeDataJson(executorService, finiteStateMachine);

        if (args.length > 0 && "DEV".equalsIgnoreCase(args[0])) {
            readTradeDataJson.initiateReadingProcessSimulatePdfDataBar();
        } else {
            readTradeDataJson.initiateReadingProcess();
        }


        //worker-3 web-sockets
        //TODO: web-sockets
    }
}
