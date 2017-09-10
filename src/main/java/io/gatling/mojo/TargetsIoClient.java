package io.gatling.mojo;

import okhttp3.*;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;

public class TargetsIoClient {

    private Logger logger = new SystemOutLogger();

    private static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();

    private final String productName;
    private final String dashboardName;
    private final String testType;
    private final String testRunId;
    private final String buildResultsUrl;
    private final String productRelease;
    private final String targetsIoUrl;
    private final String rampupTimeSeconds;

    public TargetsIoClient(String productName, String dashboardName, String testType, String testRunId, String buildResultsUrl, String productRelease, String rampupTimeInSeconds, String targetsIoUrl) {
        this.productName = productName;
        this.dashboardName = dashboardName;
        this.testType = testType;
        this.testRunId = testRunId;
        this.buildResultsUrl = buildResultsUrl;
        this.productRelease = productRelease;
        this.rampupTimeSeconds = rampupTimeInSeconds;
        this.targetsIoUrl = targetsIoUrl;
    }

    public void injectLogger(Logger logger) {
        this.logger = logger;
    }

    public void callTargetsIoFor(Action action) {
        String json = targetsIoJson(productName, dashboardName, testRunId, buildResultsUrl, productRelease, rampupTimeSeconds);
        logger.info(String.join(" ", "Call for", action.getName(), "endpoint:", targetsIoUrl, "with json:", json));
        try {
            String result = post(targetsIoUrl + "/running-test/" + action.getName(), json);
            logger.info("Result: " + result);
        } catch (IOException e) {
            logger.error("failed to call keep-alive url: " + e.getMessage());
        }
    }

    private String post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            return responseBody == null ? "null" : responseBody.string();
        }
    }

    private String targetsIoJson(String productName, String dashboardName, String testRunId, String buildResultsUrl, String productRelease, String rampupTimeSeconds) {
        return String.join("","{'testRunId':'",testRunId, "','dashboardName':'", dashboardName, "','productName':'", productName, "','productRelease':'", productRelease, "','buildResultsUrl':'", buildResultsUrl, "','rampUpPeriod':", rampupTimeSeconds, "}").replace("'", "\"");
    }

    /**
     * Call asserts for this test run.
     * @return json string such as {"meetsRequirement":true,"benchmarkResultPreviousOK":true,"benchmarkResultFixedOK":true}
     * @throws IOException when call fails
     */
    public String callCheckAsserts() throws IOException, MojoExecutionException {
        // example: https://targets-io.com/benchmarks/DASHBOARD/NIGHTLY/TEST-RUN-831
        String url = String.join("/",targetsIoUrl, "benchmarks", dashboardName, testType, testRunId);
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        int retries = 0;
        final int MAX_RETRIES = 10;
        final long sleepInMillis = 3000;
        String assertions = null;

        try (Response response = client.newCall(request).execute()) {

            while (retries < MAX_RETRIES) {
                if (response.code() == 200) {
                    ResponseBody responseBody = response.body();
                    assertions = responseBody == null ? "null" : responseBody.string();
                    break;
                } else {
                    logger.warn("failed to retrieve assertions for url [" + url + "] code [" + response.code() + "] retry [" + retries + "/" + MAX_RETRIES + "] " + response.message());
                }
                try {
                    Thread.sleep(sleepInMillis);
                } catch (InterruptedException e) {
                    // ignore
                }
                retries = retries + 1;
            }
            if (retries == MAX_RETRIES) {
                throw new MojoExecutionException("Unable to retrieve assertions for url [" + url + "]");
            }
        }
        return assertions;
    }

    public enum Action {
        KeepAlive("keep-alive"), End("end");

        private String name;

        Action(String name) {
            this.name = name;
        }
        public String getName() { return name; }
    }

    public static class KeepAliveRunner implements Runnable {

        private final TargetsIoClient client;

        public KeepAliveRunner(TargetsIoClient client) {
            this.client = client;
        }

        @Override
        public void run() {
            client.callTargetsIoFor(Action.KeepAlive);
        }
    }

    public interface Logger {
        void info(String message);
        void warn(String message);
        void error(String message);
    }

    public static class SystemOutLogger implements Logger {
        public void info(String message) {
            System.out.println("INFO:  " + message);
        }

        public void warn(String message) {
            System.out.println("WARN:  " + message);
        }

        public void error(String message) {
            System.out.println("ERROR: " + message);
        }
    }

}
