package io.asterconfig.core.spi;

import io.asterconfig.core.model.PublishResult;

public interface ConfigPublishListener {

    void onPublish(PublishResult result);
}
