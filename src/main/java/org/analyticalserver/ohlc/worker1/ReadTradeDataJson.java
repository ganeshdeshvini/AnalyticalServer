package org.analyticalserver.ohlc.worker1;

import lombok.extern.slf4j.Slf4j;
import org.analyticalserver.ohlc.worker2.FiniteStateMachine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class ReadTradeDataJson {
    @Autowired
    private FiniteStateMachine finiteStateMachine;

    public void initiateReadingProcess(String inputFileName) {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.submit(() -> startReadingDataFromFileAndSubmitToQueue(inputFileName));
    }

    private void startReadingDataFromFileAndSubmitToQueue(String inputFileName) {
        log.info("Opening File for reading");
        try (Scanner scanner = new Scanner(new FileInputStream(inputFileName))) {
            log.info("Reading Started...");
            while (scanner.hasNextLine()) {
                finiteStateMachine.addToQueue(scanner.nextLine());
            }
            log.info("Reading Finished...");

        } catch (FileNotFoundException e) {
            log.error("File does not exist, please specify valid fully qualified filename");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
