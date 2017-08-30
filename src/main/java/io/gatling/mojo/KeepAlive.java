package io.gatling.mojo;

import okhttp3.*;

import java.io.IOException;

public class KeepAlive implements Runnable {

    private static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    private OkHttpClient client = new OkHttpClient();

    private String productName;
    private String dashboardName;
    private String testRunId;
    private String buildResultsUrl;
    private String productRelease;
    private String targetsIoUrl;

    public KeepAlive(String productName, String dashboardName, String testRunId, String buildResultsUrl, String productRelease, String targetsIoUrl) {
        this.productName = productName;
        this.dashboardName = dashboardName;
        this.testRunId = testRunId;
        this.buildResultsUrl = buildResultsUrl;
        this.productRelease = productRelease;
        this.targetsIoUrl = targetsIoUrl;
    }

    @Override
    public void run() {
        String json = targetsIoJson(productName, dashboardName, testRunId, buildResultsUrl, productRelease);
        System.out.println(String.join(" ", "Keep alive call for endpoint:", targetsIoUrl, "with json:", json));
        try {
            String result = post(targetsIoUrl + "/running-test/keep-alive", json);
            System.out.println("Result: " + result);
        } catch (IOException e) {
            System.out.println("ERROR: failed to call keep-alive url: " + e.getMessage());
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

    private String targetsIoJson(String productName, String dashboardName, String testRunId, String buildResultsUrl, String productRelease) {
        String rampUpPeriod = "30";
        String duration = "600";
        return String.join("","{'testRunId':'",testRunId, "','dashboardName':'", dashboardName, "','productName':'", productName, "','productRelease':'", productRelease, "','buildResultsUrl':'", buildResultsUrl, "','rampUpPeriod':", rampUpPeriod, ",'duration':", duration, "}").replace("'", "\"");
    }
}
