package org.analyticalserver.ohlc.worker3;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkerController {
    @Autowired
    private WorkerService workerService;

    @GetMapping("/workers/start")
    public String startWorkerThreads() {
        return workerService.startProcess();
    }
}
