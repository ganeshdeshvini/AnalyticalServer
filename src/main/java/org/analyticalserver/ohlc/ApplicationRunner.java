package org.analyticalserver.ohlc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

@SpringBootApplication
@Slf4j
public class ApplicationRunner {
    public static void main(String[] args) {
        log.info("Arguments: " + Arrays.toString(args));
        if (args.length < 1) {
            log.error("Require arguments which has filename for trades.json, please re-run by passing argument with fully qualified filename");
            System.exit(0);
        }
        SpringApplication.run(ApplicationRunner.class, args);
    }
}
