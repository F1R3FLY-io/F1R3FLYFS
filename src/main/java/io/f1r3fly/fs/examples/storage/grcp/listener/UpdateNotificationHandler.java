package io.f1r3fly.fs.examples.storage.grcp.listener;

import casper.ExternalCommunicationServiceCommon;
import casper.v1.ExternalCommunicationServiceV1;

public interface UpdateNotificationHandler {
    ExternalCommunicationServiceV1.UpdateNotificationResponse handle(ExternalCommunicationServiceCommon.UpdateNotification notification);
}
