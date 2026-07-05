package io.quarkus.micrometer.runtime.binder;

import java.net.URI;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.core.MultivaluedMap;

import io.quarkus.micrometer.runtime.HttpClientMetricsTagsContributor;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;

public final class RestClientMetricsTagsContext implements HttpClientMetricsTagsContributor.Context {

    private final HttpRequest request;
    private final HttpResponse response;

    public RestClientMetricsTagsContext(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        this.request = new RestClientHttpRequest(requestContext);
        this.response = new RestClientHttpResponse(responseContext);
    }

    @Override
    public HttpRequest request() {
        return request;
    }

    @Override
    public HttpResponse response() {
        return response;
    }

    private static MultiMap headers(MultivaluedMap<String, ?> headers) {
        MultiMap result = MultiMap.caseInsensitiveMultiMap();
        for (var entry : headers.entrySet()) {
            for (Object value : entry.getValue()) {
                if (value != null) {
                    result.add(entry.getKey(), value.toString());
                }
            }
        }
        return result;
    }

    private static final class RestClientHttpRequest implements HttpRequest {
        private final ClientRequestContext requestContext;
        private final MultiMap headers;

        private RestClientHttpRequest(ClientRequestContext requestContext) {
            this.requestContext = requestContext;
            this.headers = headers(requestContext.getHeaders());
        }

        @Override
        public long id() {
            return 0;
        }

        @Override
        public HttpVersion version() {
            return HttpVersion.HTTP_1_1;
        }

        @Override
        public String uri() {
            URI uri = requestContext.getUri();
            return uri == null ? "" : uri.toString();
        }

        @Override
        public String absoluteURI() {
            return uri();
        }

        @Override
        public HttpMethod method() {
            String method = requestContext.getMethod();
            if (method == null) {
                return HttpMethod.OTHER;
            }
            try {
                return HttpMethod.valueOf(method);
            } catch (IllegalArgumentException e) {
                return HttpMethod.OTHER;
            }
        }

        @Override
        public MultiMap headers() {
            return headers;
        }

        @Override
        public SocketAddress remoteAddress() {
            URI uri = requestContext.getUri();
            if (uri == null || uri.getHost() == null) {
                return SocketAddress.inetSocketAddress(0, "none");
            }
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            return SocketAddress.inetSocketAddress(port, uri.getHost());
        }
    }

    private static final class RestClientHttpResponse implements HttpResponse {
        private final ClientResponseContext responseContext;
        private final MultiMap headers;

        private RestClientHttpResponse(ClientResponseContext responseContext) {
            this.responseContext = responseContext;
            this.headers = headers(responseContext.getHeaders());
        }

        @Override
        public int statusCode() {
            return responseContext.getStatus();
        }

        @Override
        public MultiMap headers() {
            return headers;
        }
    }
}
