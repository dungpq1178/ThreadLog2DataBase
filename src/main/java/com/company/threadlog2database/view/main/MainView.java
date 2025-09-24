package com.company.threadlog2database.view.main;

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
    final AsyncDbLogger logger = AsyncDbLogger.createDefault();
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        for (int i = 0; i < 10000; i++) {
            logger.log(new AsyncDbLogger.LogRecord(Instant.now(), "DEBUG", "demo", "hello #" + i, "{\"iteration\":" + i + "}"));
        }
        logger.info("demo", "done!");
    }
}
