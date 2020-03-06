package org.analyticalserver.ohlc;

import org.analyticalserver.ohlc.worker1.ReadTradeDataJson;
import org.analyticalserver.ohlc.worker2.FiniteStateMachine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ApplicationRunner {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Require filename to proceed, please re-run by passing argument with fully qualified filename");
            System.exit(0);
        }
        String inputFileName = args[0];

        //worker-2 FSM
//        FiniteStateMachine finiteStateMachine = new FiniteStateMachine(TimeUnit.MILLISECONDS, 200);
        FiniteStateMachine finiteStateMachine = new FiniteStateMachine(TimeUnit.SECONDS, 15);
        finiteStateMachine.scheduleIntervalCron();

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        //worker-1 Read trades.json
        ReadTradeDataJson readTradeDataJson = new ReadTradeDataJson(executorService, finiteStateMachine, inputFileName);
        readTradeDataJson.initiateReadingProcess();

        //worker-3 web-sockets
        //TODO: web-sockets thread
    }
}
