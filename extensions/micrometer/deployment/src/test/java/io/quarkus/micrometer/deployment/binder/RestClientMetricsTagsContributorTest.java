package io.quarkus.micrometer.deployment.binder;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.micrometer.test.ClientDummyTag;
import io.quarkus.micrometer.test.ClientHeaderTag;
import io.quarkus.micrometer.test.Util;
import io.quarkus.test.QuarkusExtensionTest;

public class RestClientMetricsTagsContributorTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .setFlatClassPath(true)
            .withConfigurationResource("test-logging.properties")
            .overrideConfigKey("quarkus.micrometer.binder-enabled-default", "false")
            .overrideConfigKey("quarkus.micrometer.binder.http-client.enabled", "true")
            .overrideConfigKey("quarkus.micrometer.binder.http-server.enabled", "true")
            .overrideConfigKey("quarkus.micrometer.binder.vertx.enabled", "true")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.rest-client.contributor-client.url", "${test.url}")
            .withApplicationRoot((jar) -> jar.addClasses(Util.class, ClientDummyTag.class, ClientHeaderTag.class,
                    Resource.class, Resource.ContributorClient.class));

    @Inject
    MeterRegistry registry;

    @Test
    void restClientRequestMetricsIncludeContributorTags() throws InterruptedException {
        given()
                .get("/exercise")
                .then()
                .statusCode(200);

        given()
                .header("Foo", "bar")
                .get("/exercise")
                .then()
                .statusCode(200);

        Util.waitForMeters(registry.find("http.client.requests").timers(), 2);

        assertThat(registry.find("http.client.requests")
                .tag("uri", "/target")
                .tag("status", "200")
                .tag("outcome", "SUCCESS")
                .tag("dummy", "value")
                .tag("foo", "UNSET")
                .timers()).hasSize(1);
        assertThat(registry.find("http.client.requests")
                .tag("uri", "/target")
                .tag("status", "200")
                .tag("outcome", "SUCCESS")
                .tag("dummy", "value")
                .tag("foo", "bar")
                .timers()).hasSize(1);
        assertThat(registry.find("http.client.requests").timers())
                .as(Util.foundClientRequests(registry, "Expected one REST client timer per contributor tag combination."))
                .hasSize(2);
        assertThat(registry.find("http.client.requests").timers())
                .allSatisfy(timer -> assertThat(timer.getId().getTag("clientName")).isNotNull());
    }

    @Path("/")
    @Singleton
    public static class Resource {

        @RegisterRestClient(configKey = "contributor-client")
        public interface ContributorClient {

            @GET
            @Path("/target")
            @Produces(MediaType.TEXT_PLAIN)
            String get(@HeaderParam("Foo") String foo);
        }

        @Inject
        @RestClient
        ContributorClient client;

        @GET
        @Path("/target")
        @Produces(MediaType.TEXT_PLAIN)
        public String target() {
            return "ok";
        }

        @GET
        @Path("/exercise")
        @Produces(MediaType.TEXT_PLAIN)
        public String exercise(@HeaderParam("Foo") String foo) {
            return client.get(foo);
        }
    }
}
