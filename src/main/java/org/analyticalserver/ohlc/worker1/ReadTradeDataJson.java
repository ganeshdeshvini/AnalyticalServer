package org.analyticalserver.ohlc.worker1;

import lombok.extern.log4j.Log4j;
import org.analyticalserver.ohlc.worker2.FiniteStateMachine;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;

@Log4j
public class ReadTradeDataJson {
    private final String fileNameWithPath;
    private final ExecutorService executorService;
    private final FiniteStateMachine finiteStateMachine;

    public ReadTradeDataJson(ExecutorService executorService, FiniteStateMachine finiteStateMachine, String fileNameWithPath) {
        this.executorService = executorService;
        this.finiteStateMachine = finiteStateMachine;
        this.fileNameWithPath = fileNameWithPath;
    }

    public void initiateReadingProcess() {
        executorService.submit(this::startReadingDataFromFileAndSubmitToQueue);
    }

    private void startReadingDataFromFileAndSubmitToQueue() {
        log.info("Opening File for reading");
        try (Scanner scanner = new Scanner(new FileInputStream(fileNameWithPath))) {
            log.info("Reading Started...");
            while (scanner.hasNextLine()) {
                finiteStateMachine.addToQueue(scanner.nextLine());
            }
            log.info("Reading Finished...");

        } catch (FileNotFoundException e) {
            System.err.println("File does not exist, please specify valid fully qualified filename");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
