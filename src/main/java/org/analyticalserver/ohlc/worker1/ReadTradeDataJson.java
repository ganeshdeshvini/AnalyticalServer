package org.analyticalserver.ohlc.worker1;

import lombok.extern.log4j.Log4j;
import org.analyticalserver.ohlc.worker2.FiniteStateMachine;

import java.io.File;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Log4j
public class ReadTradeDataJson {
    private static final String INPUT_FILE_NAME = "trades.json";
    private final ExecutorService executorService;
    private final FiniteStateMachine finiteStateMachine;

    public ReadTradeDataJson(ExecutorService executorService, FiniteStateMachine finiteStateMachine) {
        this.executorService = executorService;
        this.finiteStateMachine = finiteStateMachine;
    }

    public void initiateReadingProcess() {
        executorService.submit(this::startReadingDataFromFileAndSubmitToQueue);
    }

    private void startReadingDataFromFileAndSubmitToQueue() {
        log.info("Opening File for reading");
        try (Scanner scanner = new Scanner(new File(getResourceFileName()))) {
            log.info("Reading Started...");
            while (scanner.hasNextLine()) {
                finiteStateMachine.addToQueue(scanner.nextLine());
            }
            log.info("Reading Finished...");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getResourceFileName() {
        return ClassLoader.getSystemClassLoader().getResource(INPUT_FILE_NAME).getFile();
    }

    /**
     * Below code is for simulating/testing with PDFData
     */
    public void initiateReadingProcessSimulatePdfDataBar() {
        executorService.submit(this::startReadingDataFromFileAndSubmitToQueueSimulatePdfDataBar);
    }

    private void startReadingDataFromFileAndSubmitToQueueSimulatePdfDataBar() {
        try (Scanner scanner = new Scanner(new File(getResourceFileName()))) {
            int cnt = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                finiteStateMachine.addToQueue(line);

                //This is timestamp of the last json in bar1 as shown in sample pdf
                if (line.contains("1538409768683832846")) {
                    TimeUnit.MILLISECONDS.sleep(200);
                }

                cnt++;
                if (cnt > 40) {
                    log.error("Stopped reading");
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
