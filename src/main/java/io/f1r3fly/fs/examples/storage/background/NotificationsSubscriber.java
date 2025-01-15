package io.f1r3fly.fs.examples.storage.background;

import io.f1r3fly.fs.examples.Config;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.grcp.client.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import rhoapi.RhoTypes;

import java.util.List;

public class NotificationsSubscriber {

    public static void subscribe(Config config) {
        boolean isFirstSubscription = true;
        try {
            List<RhoTypes.Par> any = config.f1r3flyAPI.findDataByName("@/" + config.mountName + "/clients");
            isFirstSubscription = any.isEmpty();
        } catch (NoDataByPath e) {
            // no channel at all
            isFirstSubscription = true;
        }

        String rhoExpression = isFirstSubscription ?
            RholangExpressionConstructor.createFirstSubscription(config.clientHost, config.clientPort, config.mountName) :
            RholangExpressionConstructor.appendSubscription(config.clientHost, config.clientPort, config.mountName);

        enqueueDeployment(config, rhoExpression);
    }

    private static void enqueueDeployment(Config config, String rholangExpression) {
        config.deployDispatcher.enqueueDeploy(new DeployDispatcher.Deployment(rholangExpression, false, F1r3flyApi.RHOLANG));
    }

    public static void unsubscribe(Config config) {
        enqueueDeployment(config, RholangExpressionConstructor.removeSubscription(config.clientHost, config.clientPort, config.mountName));
    }

}
