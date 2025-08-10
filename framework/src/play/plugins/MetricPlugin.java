package play.plugins;

import io.prometheus.client.Counter;
import io.prometheus.client.hotspot.DefaultExports;
import play.PlayPlugin;
import play.metrics.SystemInfo;
import play.mvc.Http;

public class MetricPlugin extends PlayPlugin {

    private Counter requests;
    private Counter exeptions;
    private Counter success;
    private SystemInfo systemInfo;

    @Override
    public void onApplicationStart() {
        if(requests == null) {
            requests =  Counter.build().name("http_requests_total").help("Total play request.").labelNames("method", "path").register();
        }
        if(exeptions == null) {
            exeptions = Counter.build().name("http_request_exceptions_total").help("Total play exception request.").labelNames("method", "path").register();
        }
        if(success == null) {
            success = Counter.build().name("http_request_success_total").help("Total play success request.").labelNames("method", "path").register();
        }
        if(systemInfo == null) {
            systemInfo = new SystemInfo().register();
            systemInfo.collect();
        }
        DefaultExports.initialize();
    }

    @Override
    public void routeRequest(Http.Request request) {
        if(request == null)
            return;
        requests.labels(request.method, request.path).inc();
    }

    @Override
    public void onInvocationSuccess() {
        if(success == null || Http.Request.current() == null)
            return;
        success.labels(Http.Request.current().method, Http.Request.current().path).inc();
    }

    @Override
    public void onInvocationException(Throwable e) {
        if(exeptions == null || Http.Request.current() == null)
            return;
        exeptions.labels(Http.Request.current().method, Http.Request.current().path).inc();
    }
}
