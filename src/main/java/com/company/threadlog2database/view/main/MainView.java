package com.company.threadlog2database.view.main;

import com.company.threadlog2database.log.AppLogService;
import com.company.threadlog2database.log.AsyncDbLogger;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.app.main.StandardMainView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

import java.time.Instant;

@Route("")
@ViewController(id = "log_MainView")
@ViewDescriptor(path = "main-view.xml")
public class MainView extends StandardMainView {
    private final AppLogService logger ;

    public MainView(AppLogService logger) {
        this.logger = logger;
    }


    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {

        long start = System.nanoTime();
        for (int i = 0; i < 9999999; i++) {
            logger.log(new AsyncDbLogger.LogRecord(Instant.now(), "DEBUG", "demo", "hello #" + i, "{\"iteration\":" + i + "}"));
        }
        logger.info("demo", "done!");
        long end = System.nanoTime();
        long elapsedNanos = end - start;
        long elapsedMillis = elapsedNanos / 1_000_000;

        System.out.println("Enqueued 9999999 log records in " + elapsedMillis + " ms");
    }
}
