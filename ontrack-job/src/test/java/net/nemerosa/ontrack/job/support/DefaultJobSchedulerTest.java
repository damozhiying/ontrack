package net.nemerosa.ontrack.job.support;

import com.google.common.collect.ImmutableSet;
import net.nemerosa.ontrack.job.*;
import net.nemerosa.ontrack.job.orchestrator.JobOrchestrator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;

public class DefaultJobSchedulerTest {

    private SynchronousScheduledExecutorService schedulerPool;
    private SynchronousScheduledExecutorService jobPool;

    private final Supplier<RuntimeException> noFutureException = () -> new IllegalStateException("No future being returned.");

    @Before
    public void before() {
        schedulerPool = new SynchronousScheduledExecutorService();
        jobPool = new SynchronousScheduledExecutorService();
    }

    @After
    public void after() {
        schedulerPool.shutdownNow();
        jobPool.shutdownNow();
    }

    protected JobScheduler createJobScheduler() {
        return createJobScheduler(false);
    }

    protected JobScheduler createJobScheduler(boolean initiallyPaused) {
        return new DefaultJobScheduler(
                NOPJobDecorator.INSTANCE,
                schedulerPool,
                NOPJobListener.INSTANCE,
                initiallyPaused,
                (pool, job) -> jobPool,
                false,
                1.0
        );
    }

    @Test
    public void schedule() throws InterruptedException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        jobScheduler.schedule(job, Schedule.EVERY_SECOND);
        tick_seconds(3);
        assertEquals(4, job.getCount());
    }

    @Test
    public void schedule_wait_at_startup() throws InterruptedException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        jobScheduler.schedule(job, Schedule.EVERY_SECOND.after(1));
        tick_seconds(3);
        assertEquals(3, job.getCount());
    }

    @Test
    public void scheduler_paused_at_startup() throws InterruptedException {
        JobScheduler jobScheduler = createJobScheduler(true);
        TestJob job = TestJob.of();
        jobScheduler.schedule(job, Schedule.EVERY_SECOND);
        tick_seconds(3);
        assertEquals(0, job.getCount()); // Job did not run
        // Resumes the execution and waits
        jobScheduler.resume();
        tick_seconds(2);
        // The job has run
        assertEquals(2, job.getCount());
    }

    @Test
    public void scheduler_paused_at_startup_with_orchestration() throws InterruptedException {
        // Creates a job scheduler
        JobScheduler jobScheduler = createJobScheduler(true);
        // Job orchestration
        TestJob job = TestJob.of();
        JobOrchestrator jobOrchestrator = new JobOrchestrator(
                jobScheduler,
                "Orchestrator",
                Collections.singletonList(
                        () -> Stream.of(
                                JobRegistration.of(job).withSchedule(Schedule.EVERY_SECOND)
                        )
                )
        );
        // Registers the orchestrator
        jobScheduler.schedule(jobOrchestrator, Schedule.EVERY_SECOND);
        // Waits some time...
        tick_seconds(3);
        // ... and the job should not have run
        assertEquals(0, job.getCount());
        // ... and the orchestrator must not have run
        Optional<JobStatus> orchestratorStatus = jobScheduler.getJobStatus(jobOrchestrator.getKey());
        assertTrue(
                orchestratorStatus.isPresent() &&
                        orchestratorStatus.get().getRunCount() == 0 &&
                        !orchestratorStatus.get().isRunning()
        );
        // Resumes the job scheduler
        jobScheduler.resume();
        // Resumes all jobs
        schedulerPool.runUntilIdle();
        // Waits for one second for the orchestrator to kick off
        tick_seconds(1);
        // Forces the registration of pending jobs
        schedulerPool.runUntilIdle();
        jobPool.runUntilIdle();
        // The job managed by the orchestrator must have run
        assertEquals(1, job.getCount());
    }

    @Test
    public void reschedule() throws InterruptedException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        // Initially every second
        jobScheduler.schedule(job, Schedule.EVERY_SECOND);
        // Checks it has run
        tick_seconds(2);
        int count = job.getCount();
        assertEquals(3, count);
        // Then every minute
        jobScheduler.schedule(job, new Schedule(1, 1, TimeUnit.MINUTES));
        // Checks after three more seconds than the count has not moved
        tick_seconds(3);
        assertEquals(count, job.getCount());
    }

    @Test
    public void fire_immediately() throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        // Fires far in the future
        jobScheduler.schedule(job, Schedule.EVERY_MINUTE.after(10));
        // Not fired, even after 10 seconds
        tick_seconds(10);
        assertEquals(0, job.getCount());
        // Fires immediately and waits for the result
        jobScheduler.fireImmediately(job.getKey()).orElseThrow(noFutureException);
        jobPool.runUntilIdle();
        assertEquals(1, job.getCount());
    }

    @Test
    public void fire_immediately_in_concurrency() throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        // Fires now
        jobScheduler.schedule(job, Schedule.EVERY_SECOND);
        // Job is started and scheduled
        schedulerPool.runUntilIdle();
        // ... but nothing has happened yet
        assertEquals(0, job.getCount());
        // Checks its status
        Optional<JobStatus> jobStatus = jobScheduler.getJobStatus(job.getKey());
        assertTrue(jobStatus.isPresent() && jobStatus.get().isRunning());
        // Fires immediately and waits for the result
        Future<?> future = jobScheduler.fireImmediately(job.getKey()).orElse(null);
        assertNull("Job is not fired because already running", future);
        // The job is already running, count is still 0
        assertEquals(0, job.getCount());
        // Waits until completion
        jobPool.runUntilIdle();
        assertEquals(1, job.getCount());
    }

    @Test
    public void statuses() throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();

        TestJob longJob = TestJob.of("long").withWait(5_000);
        jobScheduler.schedule(longJob, Schedule.EVERY_MINUTE);

        TestJob shortJob = TestJob.of("short");
        jobScheduler.schedule(shortJob, Schedule.EVERY_SECOND.after(60));

        // The long job is already running, not the short one
        schedulerPool.runNextPendingCommand();
        Map<JobKey, JobStatus> statuses = jobScheduler.getJobStatuses().stream().collect(Collectors.toMap(
                JobStatus::getKey,
                status -> status
        ));

        JobStatus longStatus = statuses.get(longJob.getKey());
        assertTrue(longStatus.isRunning());

        JobStatus shortStatus = statuses.get(shortJob.getKey());
        assertFalse(shortStatus.isRunning());
    }

    @Test
    public void removing_a_running_job() throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        // Fires now
        jobScheduler.schedule(job, Schedule.EVERY_SECOND);
        // After some seconds, the job keeps running
        tick_seconds(3);
        assertEquals(4, job.getCount());
        // Now, removes the job
        jobScheduler.unschedule(job.getKey());
        // Waits a bit, and checks the job has stopped running
        tick_seconds(3);
        assertEquals(4, job.getCount());
    }

    @Test
    public void removing_a_long_running_job() throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        // Fires now
        jobScheduler.schedule(job, Schedule.EVERY_SECOND);
        // The job is now running
        schedulerPool.runNextPendingCommand();
        Optional<JobStatus> status = jobScheduler.getJobStatus(job.getKey());
        assertTrue(status.isPresent() && status.get().isRunning());
        // Now, removes the job
        jobScheduler.unschedule(job.getKey());
        // Waits a bit, and checks the job has stopped running
        jobPool.runUntilIdle();
        assertEquals(0, job.getCount());
    }

    @Test
    public void job_failures() throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of().withFail(true);
        // Fires now
        jobScheduler.schedule(job, Schedule.EVERY_SECOND);
        // After some seconds, the job keeps running and has only failed
        tick_seconds(3);
        JobStatus status = jobScheduler.getJobStatus(job.getKey()).orElse(null);
        assertNotNull(status);
        long lastErrorCount = status.getLastErrorCount();
        assertEquals(4, lastErrorCount);
        assertEquals("Task failure", status.getLastError());
        // Now, fixes the job
        job.setFail(false);
        // Waits a bit, and checks the job is now OK
        tick_seconds(2);
        status = jobScheduler.getJobStatus(job.getKey()).orElse(null);
        assertNotNull(status);
        assertEquals(2, job.getCount());
        assertEquals(0, status.getLastErrorCount());
        assertNull(status.getLastError());
    }

    protected void test_with_pause(BiConsumer<JobScheduler, TestJob> pause, BiConsumer<JobScheduler, TestJob> resume) throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        // Fires now
        jobScheduler.schedule(job, Schedule.EVERY_SECOND);
        // Runs a few times
        times(2, () -> {
            schedulerPool.tick(1, SECONDS);
            jobPool.tick(1, SECONDS);
        });
        int count = job.getCount();
        assertEquals(2, count);
        // Pauses
        pause.accept(jobScheduler, job);
        // After some seconds, the job has not run
        times(2, () -> {
            schedulerPool.tick(1, SECONDS);
            jobPool.tick(1, SECONDS);
        });
        assertEquals(count, job.getCount());
        // Resumes the job
        resume.accept(jobScheduler, job);
        // After some seconds, the job has started again
        times(2, () -> {
            schedulerPool.tick(1, SECONDS);
            jobPool.tick(1, SECONDS);
        });
        assertEquals(4, job.getCount());
    }

    @Test
    public void job_pause() throws InterruptedException, ExecutionException, TimeoutException {
        test_with_pause(
                (jobScheduler, job) -> job.pause(),
                (jobScheduler, job) -> job.resume()
        );
    }

    @Test
    public void job_schedule_pause() throws InterruptedException, ExecutionException, TimeoutException {
        test_with_pause(
                (jobScheduler, job) -> jobScheduler.pause(job.getKey()),
                (jobScheduler, job) -> jobScheduler.resume(job.getKey())
        );
    }

    @Test
    public void scheduler_pause() throws InterruptedException, ExecutionException, TimeoutException {
        test_with_pause(
                (jobScheduler, job) -> jobScheduler.pause(),
                (jobScheduler, job) -> jobScheduler.resume()
        );
    }

    @Test
    public void stop() throws InterruptedException {
        JobScheduler jobScheduler = createJobScheduler();

        TestJob job = TestJob.of();
        jobScheduler.schedule(job, Schedule.EVERY_SECOND);

        // Kicks the scheduling of the job
        schedulerPool.runNextPendingCommand();

        // Checks it's running
        JobStatus jobStatus = jobScheduler.getJobStatus(job.getKey()).orElse(null);
        assertNotNull(jobStatus);
        assertTrue("Job is running", jobStatus.isRunning());

        // Stops the job
        System.out.println("Stopping the job.");
        assertTrue("Job has been stopped", jobScheduler.stop(job.getKey()));

        // Checks it has actually been stopped
        jobStatus = jobScheduler.getJobStatus(job.getKey()).orElse(null);
        assertNotNull("Job is still scheduled", jobStatus);
        assertFalse("Job is actually stopped", jobStatus.isRunning());
    }

    @Test
    public void keys() throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();

        TestJob longCountJob = TestJob.of("long");
        TestJob countJob = TestJob.of("short");
        TestJob otherTypeJob = TestJob.of("other").withCategory(Fixtures.TEST_OTHER_CATEGORY);

        jobScheduler.schedule(longCountJob, Schedule.EVERY_SECOND);
        jobScheduler.schedule(countJob, Schedule.EVERY_SECOND);
        jobScheduler.schedule(otherTypeJob, Schedule.EVERY_SECOND);

        assertEquals(
                ImmutableSet.of(longCountJob.getKey(), countJob.getKey(), otherTypeJob.getKey()),
                jobScheduler.getAllJobKeys()
        );

        assertEquals(
                ImmutableSet.of(longCountJob.getKey(), countJob.getKey()),
                jobScheduler.getJobKeysOfCategory(Fixtures.TEST_CATEGORY)
        );

        assertEquals(
                ImmutableSet.of(otherTypeJob.getKey()),
                jobScheduler.getJobKeysOfCategory(Fixtures.TEST_OTHER_CATEGORY)
        );
    }

    @Test(expected = JobNotScheduledException.class)
    public void pause_for_not_schedule_job() {
        JobScheduler jobScheduler = createJobScheduler();
        jobScheduler.pause(JobCategory.of("test").getType("test").getKey("x"));
    }

    @Test(expected = JobNotScheduledException.class)
    public void resume_for_not_schedule_job() {
        JobScheduler jobScheduler = createJobScheduler();
        jobScheduler.resume(JobCategory.of("test").getType("test").getKey("x"));
    }

    @Test(expected = JobNotScheduledException.class)
    public void fire_immediately_for_not_schedule_job() {
        JobScheduler jobScheduler = createJobScheduler();
        jobScheduler.fireImmediately(JobCategory.of("test").getType("test").getKey("x"));
    }

    @Test
    public void job_status_for_not_schedule_job() {
        JobScheduler jobScheduler = createJobScheduler();
        assertFalse(jobScheduler.getJobStatus(JobCategory.of("test").getType("test").getKey("x")).isPresent());
    }

    @Test
    public void invalid_job() throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        // Fires now
        jobScheduler.schedule(job, Schedule.EVERY_SECOND);
        // After some seconds, the job keeps running
        tick_seconds(3);
        int count = job.getCount();
        assertEquals("Job ran four times", 4, count);
        // Invalidates the job
        job.invalidate();
        // The status indicates the job is no longer valid, but is still there
        JobStatus status = jobScheduler.getJobStatus(job.getKey()).orElse(null);
        assertNotNull(status);
        assertFalse(status.isValid());
        assertNull(status.getNextRunDate());
        // After some seconds, the job has not run
        tick_seconds(1);
        jobPool.runUntilIdle();
        assertEquals(count, job.getCount());
        // ... and it's gone
        assertFalse(jobScheduler.getJobStatus(job.getKey()).isPresent());
    }

    @Test
    public void paused_job_can_be_fired() throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        // Initially every second
        jobScheduler.schedule(job, Schedule.EVERY_SECOND);
        tick_seconds(2);
        // After a few seconds, the count has moved
        int count = job.getCount();
        assertEquals(3, count);
        // Pauses the job now
        jobScheduler.pause(job.getKey());
        // Not running
        tick_seconds(2);
        assertEquals(count, job.getCount());
        // Forcing the run
        jobScheduler.fireImmediately(job.getKey()).orElseThrow(noFutureException);
        jobPool.runUntilIdle();
        assertTrue(job.getCount() > count);
    }

    @Test
    public void not_scheduled_job_can_be_fired() throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        // No schedule
        jobScheduler.schedule(job, Schedule.NONE);
        tick_seconds(2);
        // After a few seconds, the count has NOT moved
        assertEquals(0, job.getCount());
        // Forcing the run
        jobScheduler.fireImmediately(job.getKey()).orElseThrow(noFutureException);
        jobPool.runUntilIdle();
        assertEquals(1, job.getCount());
    }

    @Test
    public void not_scheduled_job_cannot_be_paused() throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        // No schedule
        jobScheduler.schedule(job, Schedule.NONE);
        tick_seconds(2);
        // After a few seconds, the count has NOT moved
        assertEquals(0, job.getCount());
        // Pausing the job
        jobScheduler.pause(job.getKey());
        // Not paused
        JobStatus status = jobScheduler.getJobStatus(job.getKey()).orElse(null);
        assertNotNull(status);
        assertFalse(status.isPaused());
    }

    @Test
    public void disabled_job_cannot_be_fired() throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        job.pause();
        // Initially every second
        jobScheduler.schedule(job, Schedule.EVERY_SECOND);
        tick_seconds(2);
        // After a few seconds, the count has NOT moved
        assertEquals(0, job.getCount());
        // Forcing the run
        Optional<Future<?>> future = jobScheduler.fireImmediately(job.getKey());
        assertFalse("Job not fired", future.isPresent());
        // ... to not avail
        assertEquals(0, job.getCount());
    }

    @Test
    public void invalid_job_cannot_be_fired() throws InterruptedException, ExecutionException, TimeoutException {
        JobScheduler jobScheduler = createJobScheduler();
        TestJob job = TestJob.of();
        job.invalidate();
        // Schedules, but not now
        jobScheduler.schedule(job, Schedule.EVERY_MINUTE.after(1));
        // Forcing the run
        Optional<Future<?>> future = jobScheduler.fireImmediately(job.getKey());
        assertFalse("Job not fired", future.isPresent());
        // ... to not avail
        assertEquals(0, job.getCount());
        // ... and it's now gone
        assertFalse(jobScheduler.getJobStatus(job.getKey()).isPresent());
    }

    /**
     * Runs a piece of codes a given number of times
     *
     * @param count Number of times to run
     * @param code  Code to run
     */
    public static void times(int count, Runnable code) {
        for (int i = 0; i < count; i++) {
            code.run();
        }
    }

    /**
     * Runs the scheduler for x seconds with intervals of 1/2 seconds.
     */
    protected void tick_seconds(int count) {
        times(count * 2, () -> {
            schedulerPool.tick(500, MILLISECONDS);
            jobPool.runUntilIdle();
        });
    }

}
