package org.qbitspark.nexgatenotificationserver.provider.push;

import org.qbitspark.nexgatenotificationserver.dto.PushResult;

public interface PushProvider {
    String getProviderName();
    boolean isAvailable();
    PushResult sendPush(String userId, String title, String message, int priority);
}