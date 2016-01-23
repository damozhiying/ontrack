package net.nemerosa.ontrack.job.support;

import net.nemerosa.ontrack.job.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultJobScheduler implements JobScheduler {

    private final Logger logger = LoggerFactory.getLogger(JobScheduler.class);

    private final JobDecorator jobDecorator;
    private final ScheduledExecutorService scheduledExecutorService;

    private final Map<JobKey, JobScheduledService> services = new ConcurrentHashMap<>(new TreeMap<>());

    public DefaultJobScheduler(JobDecorator jobDecorator, ScheduledExecutorService scheduledExecutorService) {
        this.jobDecorator = jobDecorator;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public void schedule(Job job, Schedule schedule) {
        logger.info("[job] Scheduling {} with schedule {}", job.getKey(), schedule);
        // Manages existing schedule
        JobScheduledService existingService = services.remove(job.getKey());
        if (existingService != null) {
            logger.info("[job] Stopping existing service for {}", job.getKey());
            existingService.cancel();
        }
        // Gets the job task
        Runnable jobTask = job.getTask();
        // Decorates the task
        Runnable decoratedTask = jobDecorator.decorate(job, jobTask);
        // Creates and starts the scheduled service
        logger.info("[job] Starting service {}", job.getKey());
        JobScheduledService jobScheduledService = new JobScheduledService(decoratedTask, schedule, scheduledExecutorService);
        // Registration
        services.put(job.getKey(), jobScheduledService);
    }

    @Override
    public Future<?> fireImmediately(JobKey jobKey) {
        // Gets the existing scheduled service
        JobScheduledService jobScheduledService = services.get(jobKey);
        if (jobScheduledService == null) {
            throw new JobNotScheduledException(jobKey);
        }
        // Fires the job immediately
        return jobScheduledService.fireImmediately();
    }

    private class JobScheduledService implements Runnable {

        private final Runnable decoratedTask;
        private final ScheduledFuture<?> scheduledFuture;

        private AtomicReference<CompletableFuture<?>> completableFuture = new AtomicReference<>();

        private JobScheduledService(Runnable decoratedTask, Schedule schedule, ScheduledExecutorService scheduledExecutorService) {
            this.decoratedTask = decoratedTask;
            scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                    this,
                    schedule.getInitialPeriod(),
                    schedule.getPeriod(),
                    schedule.getUnit()
            );
        }

        @Override
        public void run() {
            fireImmediately();
        }

        public void cancel() {
            scheduledFuture.cancel(false);
            // The decorated task might still run
        }

        public Future<?> fireImmediately() {
            return completableFuture.updateAndGet(this::optionallyFireTask);
        }

        protected CompletableFuture<?> optionallyFireTask(CompletableFuture<?> runningCompletableFuture) {
            if (runningCompletableFuture != null) {
                return runningCompletableFuture;
            } else {
                return fireTask();
            }
        }

        protected CompletableFuture<Void> fireTask() {
            return CompletableFuture
                    .runAsync(decoratedTask, scheduledExecutorService)
                    .whenComplete((ignored, ex) -> {
                        completableFuture.set(null);
                        // TODO Stores the exception
                    });
        }
    }
}
