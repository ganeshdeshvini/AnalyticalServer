package org.analyticalserver.ohlc.worker3;

import lombok.extern.slf4j.Slf4j;
import org.analyticalserver.ohlc.worker1.ReadTradeDataJson;
import org.analyticalserver.ohlc.worker2.FiniteStateMachine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WorkerService {
    @Autowired
    private ApplicationArguments applicationArguments;
    @Autowired
    private FiniteStateMachine finiteStateMachine;
    @Autowired
    private ReadTradeDataJson readTradeDataJson;
    private boolean isStarted = false;

    public String startProcess() {
        if (isStarted) {
            return "Worker threads already started!!!";
        }

        String[] args = applicationArguments.getSourceArgs();
        log.info("Source args: " + Arrays.toString(args));

        String inputFileName = args[0];
        Long milliseconds = null;
        if (args.length >= 2) {
            milliseconds = Long.parseLong(args[1]);
        }

        //worker-2 FSM
        if (milliseconds == null) {
            finiteStateMachine.scheduleDefaultIntervalCron();
        } else {
            finiteStateMachine.scheduleCustomIntervalCron(TimeUnit.MILLISECONDS, milliseconds);
        }

        //worker-1 Read trades.json
        readTradeDataJson.initiateReadingProcess(inputFileName);

        isStarted = true;
        return "Worker threads started!!!";
    }
}
