package io.f1r3fly.fs.examples.storage.inmemory;

import io.f1r3fly.fs.examples.Config;
import io.f1r3fly.fs.examples.storage.background.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.grcp.client.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.grcp.listener.NotificationConstructor;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import org.slf4j.Logger;

import java.util.Arrays;

public abstract class DeployablePath {

    protected Config config;
    protected static Logger log = org.slf4j.LoggerFactory.getLogger(MemoryDirectory.class);


    protected void enqueueMutation(String rholangExpression) {
        DeployDispatcher.Deployment deployment = new DeployDispatcher.Deployment(
            rholangExpression,
            true,
            F1r3flyApi.RHOLANG
        );

        this.config.deployDispatcher.enqueueDeploy(deployment);
    }


    public void triggerRenameNotification(String oldPath, String newPath) {
        log.info("sync: notifying. reason: {}, old path: {}, new path: {}", NotificationConstructor.NotificationReasons.RENAMED, oldPath, newPath);
        NotificationConstructor.NotificationPayload payload = new NotificationConstructor.NotificationPayload(
            NotificationConstructor.NotificationReasons.RENAMED,
            oldPath,
            newPath
        );
        triggerNotification(payload);

    }
    public void triggerNotificationWithReason(String reason) {
        String absolutePath = getAbsolutePath();
        log.info("sync: notifying. reason: {} path {}", reason, absolutePath);
        NotificationConstructor.NotificationPayload payload = new NotificationConstructor.NotificationPayload(
            reason,
            absolutePath,
            null
        );
        triggerNotification(payload);
    }

    private void triggerNotification(NotificationConstructor.NotificationPayload notificationPayload) {

        enqueueMutation(RholangExpressionConstructor.triggerNotificationForSubscribers(
            config.mountName,
            notificationPayload,
            config.clientHost,
            config.clientPort
        ));
    }

    public abstract String getAbsolutePath();
}
