package io.f1r3fly.fs.examples.storage.background;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.f1r3fly.fs.examples.storage.errors.OutOfDeployRetriesError;
import io.f1r3fly.fs.examples.storage.errors.AnotherProposalInProgressError;
import io.f1r3fly.fs.examples.storage.errors.NoNewDeploysError;
import io.f1r3fly.fs.examples.storage.grcp.client.F1r3flyApi;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeployDispatcher {

    private final Logger log;
    private final F1r3flyApi f1R3FlyApi;
    private final String clientAlias;

    // Config
    private final int MAX_EXPRESSION_LENGTH_IN_LOG = 1000;
    private final int MAX_RETRIES = 15;
    private final int POLL_INTERVAL_S = 5;
    private final long RETRY_INTERVAL_S = 15L;

    // BACKGROUND:
    private final Object lock = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean deployInProgress = new AtomicBoolean(false);
    private final AtomicReference<Throwable> lastDeployError = new AtomicReference<>();
    private final AtomicReference<String> lastDeployId = new AtomicReference<>();
    private ScheduledExecutorService executorService;

    private final ConcurrentLinkedDeque<Deployment> deployQueue = new ConcurrentLinkedDeque<>();

    public record Deployment(String rhoOrMettaExpression, boolean useBiggerPhloLimit, String language) {
    }


    public DeployDispatcher(F1r3flyApi f1R3FlyApi, String clientAlias) {
        this.f1R3FlyApi = f1R3FlyApi;
        this.clientAlias = clientAlias;
        log  = LoggerFactory.getLogger(DeployDispatcher.class.getName() + " " + clientAlias);
    }

    private void init() {
        this.deployQueue.clear();

        // single thread pool
        if (executorService != null) {
            executorService.shutdownNow();
        }
        this.executorService = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat("DeployDispatcher-"+ clientAlias + "-%d").build()
        );

        this.lastDeployError.set(null);
        this.lastDeployId.set(null);
        this.running.set(false);
    }


    public void enqueueDeploy(Deployment deployment) {
        synchronized (lock) {
            // dont trim if log level is not enabled
            if (log.isInfoEnabled()) {
                String smaller =
                    deployment.rhoOrMettaExpression.length() > MAX_EXPRESSION_LENGTH_IN_LOG
                        ? deployment.rhoOrMettaExpression.substring(0, MAX_EXPRESSION_LENGTH_IN_LOG) + "..."
                        : deployment.rhoOrMettaExpression;

                log.info("Queue size before {}. Enqueueing deployment: {}", deployQueue.size(), smaller);
            }

            deployQueue.add(deployment);
            lock.notifyAll();
        }
    }


    public void startBackgroundDeploy() {
        if (executorService == null) {
            init();
        }

        running.set(true);

        scheduleNextDeploymentNow();
    }


    public void waitOnEmptyQueue() {
        try {
            synchronized (lock) {
                while (running.get() && (!deployQueue.isEmpty() || deployInProgress.get())) {
                    log.info("Waiting. Queue size: {}. Deploy in progress: {}", deployQueue.size(), deployInProgress.get());
                    lock.wait();
                }
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for queue to be empty", e);
            Thread.currentThread().interrupt(); // Restore interrupted status
        }

        if (lastDeployError.get() != null) {
            Throwable e = lastDeployError.get();
            lastDeployError.set(null);
            throw new RuntimeException("Error during deployment", e);
        }
    }


    // Performs deployment with retries
    private void performDeploymentWithRetry(Deployment deploymentOnRepeat, AtomicInteger existingRetryCount) {
        if (!running.get()) {
            return;
        }
        AtomicInteger retryCount = existingRetryCount != null ? existingRetryCount : new AtomicInteger(0);
        if (retryCount.get() < MAX_RETRIES) {
            Deployment deployment = null;
            try {
                deployInProgress.set(true);

                if (lastDeployId.get() == null) {
                    deployment = deploymentOnRepeat != null ? deploymentOnRepeat
                        : deployQueue.poll();

                    if (deployment == null) {
                        markDeployCompleted();
                        scheduleNextDeploymentWithDelay();
                        return; // exit if no deployment in a queue
                    }

                    String deployId = f1R3FlyApi.deploy(deployment.rhoOrMettaExpression, deployment.useBiggerPhloLimit, deployment.language);
                    log.info("Deployed successfully. Deploy ID: {}", deployId);
                    lastDeployId.set(deployId);
                } else {
                    log.info("Skip deploy. Last deploy ID: {}", lastDeployId.get());
                }

                if (lastDeployId.get() != null) {
                    log.info("Proposing deploy: {}", lastDeployId.get());
                    f1R3FlyApi.propose(lastDeployId.get());
                }

                markDeployCompleted();
                scheduleNextDeploymentNow();
            } catch (NoNewDeploysError e) {
                markDeployCompleted();
                scheduleNextDeploymentNow();
            } catch (AnotherProposalInProgressError e) { // just log and retry
                Deployment d = deployment != null ? deployment : deploymentOnRepeat;
                log.info("Retrying deployment (Attempt {}, queue size {}): AnotherProposalInProgressError. Expression: {}", retryCount.get(), deployQueue.size(), d.rhoOrMettaExpression);
                scheduleRetry(d, retryCount);
            } catch (Throwable e) {
                Deployment d = deployment != null ? deployment : deploymentOnRepeat;
                lastDeployError.set(e);
                log.info("Retrying deployment (Attempt {}, queue size {}). Expression: {}", retryCount.get(), deployQueue.size(), d != null ? d.rhoOrMettaExpression : "null", e);
                scheduleRetry(d, retryCount);
            }
        } else {
            log.error("Failed to deploy after {} retries", MAX_RETRIES);
            lastDeployError.set(new OutOfDeployRetriesError(lastDeployId.get() == null ? "unknown" : lastDeployId.get()));
            markDeployCompleted();
            scheduleNextDeploymentNow();
        }
    }

    private void markDeployCompleted() {
        synchronized (lock) {
            deployInProgress.set(false);
            lastDeployId.set(null); // forget last deploy id
            try {
                lock.notifyAll();
            } catch (Exception e) {
                log.error("Error while notifying", e);
            }
        }
    }

    // Schedules a retry for the deployment
    private void scheduleRetry(Deployment deployment, AtomicInteger retryCount) {
        if (!running.get()) {
            return;
        }
        retryCount.incrementAndGet();

        schedule(RETRY_INTERVAL_S * retryCount.get(), () -> {
            performDeploymentWithRetry(deployment, retryCount);
        }); // retry after 10s, 20s, 30s, ...
    }

    private void scheduleNextDeploymentNow() {
        if (deployInProgress.get()) {
            return;
        }

        log.info("Scheduling next deployment now. Queue size: {}", deployQueue.size());

        schedule(0, () -> {
            performDeploymentWithRetry(null, null); // null means take from queue
        }); // start now
    }

    private void schedule(long delayInSeconds, Runnable runnable) {
        schedule(delayInSeconds, runnable, MAX_RETRIES);
    }

    private void schedule(long delayInSeconds, Runnable runnable, long retryCount) {
        try {
            if (!running.get()) {
                return;
            }

            if (retryCount <= 0) {
                log.error("Failed to schedule after retries");
                throw new RuntimeException("Failed to schedule after retries");
            }

            executorService.schedule(runnable, delayInSeconds, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            log.warn("Error while scheduling", e);

            try {
                log.info("Sleeping for a while before retrying");
                Thread.sleep(1000); // sleep for a while
            } catch (InterruptedException ex) {
                // ignore
            }

            schedule(delayInSeconds, runnable, retryCount - 1); // retry
        }
    }

    private void scheduleNextDeploymentWithDelay() {
        if (deployInProgress.get()) {
            return;
        }

        log.info("Scheduling next deployment with delay. Queue size: {}", deployQueue.size());

        lastDeployId.set(null); // forget last deploy id

        schedule(POLL_INTERVAL_S, () -> {
            performDeploymentWithRetry(null, null); // null means take from queue
        });
    }

    public void hardStop() {

        running.set(false);
        deployQueue.clear();
        lastDeployError.set(null);

        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }

        markDeployCompleted();
    }

}
