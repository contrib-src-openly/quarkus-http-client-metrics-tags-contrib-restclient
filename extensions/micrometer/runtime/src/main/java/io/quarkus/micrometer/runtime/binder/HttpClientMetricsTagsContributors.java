package io.quarkus.micrometer.runtime.binder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.logging.Logger;

import io.micrometer.core.instrument.Tags;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.micrometer.runtime.HttpClientMetricsTagsContributor;

public final class HttpClientMetricsTagsContributors {

    private static final Logger log = Logger.getLogger(HttpClientMetricsTagsContributors.class);

    private final List<HttpClientMetricsTagsContributor> contributors;

    public HttpClientMetricsTagsContributors() {
        this.contributors = resolve();
    }

    public Tags apply(Tags tags, HttpClientMetricsTagsContributor.Context context) {
        Tags result = tags;
        for (int i = 0; i < contributors.size(); i++) {
            try {
                Tags additionalTags = contributors.get(i).contribute(context);
                result = result.and(additionalTags);
            } catch (Exception e) {
                log.debug("Unable to obtain additional tags", e);
            }
        }
        return result;
    }

    private List<HttpClientMetricsTagsContributor> resolve() {
        ArcContainer arcContainer;
        try {
            arcContainer = Arc.container();
        } catch (IllegalStateException e) {
            return Collections.emptyList();
        }
        if (arcContainer == null) {
            return Collections.emptyList();
        }
        var handles = arcContainer.listAll(HttpClientMetricsTagsContributor.class);
        if (handles.isEmpty()) {
            return Collections.emptyList();
        }
        List<HttpClientMetricsTagsContributor> resolved = new ArrayList<>(handles.size());
        for (var handle : handles) {
            resolved.add(handle.get());
        }
        return List.copyOf(resolved);
    }
}
