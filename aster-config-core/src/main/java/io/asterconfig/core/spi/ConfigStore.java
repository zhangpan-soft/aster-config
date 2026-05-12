package io.asterconfig.core.spi;

import io.asterconfig.core.model.ConfigDocument;
import io.asterconfig.core.model.ConfigDraft;
import io.asterconfig.core.model.ConfigItem;
import io.asterconfig.core.model.ConfigQuery;
import io.asterconfig.core.model.ConfigScope;
import io.asterconfig.core.model.PublishRequest;
import io.asterconfig.core.model.PublishResult;
import io.asterconfig.core.model.ReleaseRecord;
import io.asterconfig.core.model.SourceFormat;

import java.util.List;
import java.util.Optional;

public interface ConfigStore {

    List<ConfigItem> listItems(ConfigQuery query);

    List<ConfigDraft> listDrafts(ConfigScope scope);

    ConfigDraft saveDraft(ConfigDraft draft);

    void saveDrafts(List<ConfigDraft> drafts);

    void discardDraft(String draftId);

    ConfigDocument saveDocument(ConfigDocument document, List<ConfigDraft> generatedDrafts);

    Optional<ConfigDocument> getDocument(ConfigScope scope, SourceFormat sourceFormat);

    PublishResult publish(PublishRequest request);

    List<ReleaseRecord> listReleases(ConfigScope scope, int limit);

    long currentRevision(ConfigScope scope);
}
