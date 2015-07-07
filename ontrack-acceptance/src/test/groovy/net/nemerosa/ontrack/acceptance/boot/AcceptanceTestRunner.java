package net.nemerosa.ontrack.acceptance.boot;

import net.nemerosa.ontrack.acceptance.support.AcceptanceTest;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AcceptanceTestRunner extends BlockJUnit4ClassRunner {

    private final Logger logger = LoggerFactory.getLogger(JUnitAcceptanceRunner.class);
    private AcceptanceConfig config;

    public AcceptanceTestRunner(Class<?> klass, AcceptanceConfig config) throws InitializationError {
        super(klass);
        this.config = config;
    }

    @Override
    public void run(RunNotifier notifier) {
        logger.info("*** [TESTSUITE] " + getDescription());
        super.run(notifier);
    }

    @Override
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
        AcceptanceTest acceptanceTest = method.getAnnotation(AcceptanceTest.class);
        if (acceptanceTest != null && !config.acceptTest(acceptanceTest)) {
            notifier.fireTestIgnored(describeChild(method));
            return;
        }
        // Default
        logger.info("[TEST] " + describeChild(method));
        super.runChild(method, notifier);
    }


}
