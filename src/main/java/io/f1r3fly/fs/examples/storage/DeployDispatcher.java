package io.f1r3fly.fs.examples.storage;

import io.f1r3fly.fs.examples.storage.errors.F1r3flyDeployError;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeployDispatcher {

    private final Logger logger = LoggerFactory.getLogger(DeployDispatcher.class.getName());
    private F1r3flyApi f1R3FlyApi;

    // Config
    private final int MAX_DEPLOYMENT_STRING_LENGTH = 1000;
    private final int POLL_INTERVAL_MS = 5000;
    private final int WAITING_STEP_MS = 5000;
    private final int MAX_RETRIES = 10;
    private final int RETRY_INTERVAL_MS = 15000;

    // BACKGROUND:
    private volatile boolean isDeploying = false;
    private final AtomicReference<Throwable> lastDeployError = new AtomicReference<>();
    private Thread backgroundThread;
    private ExecutorService executorService;
    private int retryCount = 0;

    private final ConcurrentLinkedQueue<Deployment> queue;

    public record Deployment(String rhoOrMettaExpression, boolean useBiggerPhloLimit, String language) {
    }

    private class BackgroundDeployer extends Thread {
        @Override
        public void run() {
            while (!isInterrupted()) {
                Deployment deployment = queue.poll();
                if (deployment != null) {
                    doDeploy(deployment);
                } else {
                    try {
                        Thread.sleep(POLL_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void doDeploy(Deployment deployment) {
            try {
                isDeploying = true;
                f1R3FlyApi.deploy(deployment.rhoOrMettaExpression, deployment.useBiggerPhloLimit, deployment.language);
                retryCount = 0;
                isDeploying = false;
            } catch (Throwable e) {
                if (retryCount < MAX_RETRIES && !interrupted()) {
                    retryCount++;
                    logger.warn("Error during deployment. Retrying. Retry count: " + retryCount, e);
                    try {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                    doDeploy(deployment);
                } else {
                    logger.error("Error during deployment. Max retries reached. Stopping deployment.");
                    isDeploying = false;
                    retryCount = 0;
                    lastDeployError.set(e);
                }
            }
        }
    }

    public DeployDispatcher(F1r3flyApi f1R3FlyApi) {
        this.f1R3FlyApi = f1R3FlyApi;
        queue = new ConcurrentLinkedQueue<>();
        // single thread pool
        this.executorService = java.util.concurrent.Executors.newSingleThreadExecutor();
    }

    public void enqueueDeploy(Deployment deployment) {
        logger.debug("Enqueueing deployment: " + deployment);
        queue.add(deployment);
    }

    // dequeue in a separate thread: poll, deploy or wait if empty, repeat
    public void startBackgroundDeploy() {
        if (backgroundThread == null) {
            backgroundThread = new BackgroundDeployer();

            executorService.submit(backgroundThread);
        }
    }




    public void waitOnEmptyQueue() {
        while ((!queue.isEmpty() || isDeploying) && lastDeployError.get() == null) {
            try {
                logger.debug("Waiting for the queue to be empty. Queue size: " + queue.size() + ". Is deploying: " + isDeploying);
                Thread.sleep(WAITING_STEP_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (lastDeployError.get() != null) {
            throw new RuntimeException("Error during deployment", lastDeployError.get());
        }
    }

    // hard stop
    public void destroy() {
        logger.info("Destroying DeployDispatcher");
        queue.clear();
        if (backgroundThread != null) {
            backgroundThread.interrupt();
            backgroundThread = null;
        }
        executorService.shutdown();
    }


}
