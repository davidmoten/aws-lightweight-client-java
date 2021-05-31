package com.github.davidmoten.aws.lw.client;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.github.davidmoten.aws.lw.client.internal.util.HttpUtils;
import com.github.davidmoten.xml.XmlElement;

public final class Request {
    private final Client client;
    private String regionName;
    private String url;
    private HttpMethod method = HttpMethod.GET;
    private final Map<String, List<String>> headers = new HashMap<>();
    private byte[] requestBody;

    Request(Client client, String url) {
        this.client = client;
        this.url = url;
        this.regionName = client.regionName();
    }

    static Request clientAndUrl(Client client, String url) {
        return new Request(client, url);
    }

    public Request method(HttpMethod method) {
        this.method = method;
        return this;
    }

    public Request query(String name, String value) {
        if (!url.contains("?")) {
            url += "?";
        }
        if (!url.endsWith("?")) {
            url += "&";
        }
        url += HttpUtils.urlEncode(name, false) + "=" + HttpUtils.urlEncode(value, false);
        return this;
    }

    public Request header(String name, String value) {
        RequestHelper.put(headers, name, value);
        return this;
    }

    public Request metadata(String name, String value) {
        return header("x-amz-meta-" + name, value);
    }

    public Request requestBody(byte[] requestBody) {
        this.requestBody = requestBody;
        return this;
    }

    public Request requestBody(String requestBody) {
        this.requestBody = requestBody.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    public Request regionName(String regionName) {
        this.regionName = regionName;
        return this;
    }

    /**
     * Opens a connection and makes the request. This method returns all the response
     * information including headers, status code, request body. If an error status
     * code is encountered (outside 200-299) then an exception is <b>not</b> thrown
     * (unlike the other methods .response*).
     * 
     * @return all response information
     */
    public Response response() {
        return RequestHelper.request(url, method.toString(), RequestHelper.combineHeaders(headers), requestBody,
                client.serviceName(), regionName, client.credentials());
    }

    public byte[] responseAsBytes() {
        Response r = response();
        if (r.statusCode() >= 200 && r.statusCode() <= 299) {
            return r.content();
        } else {
            throw new ServiceException(r.statusCode(), new String(r.content(), StandardCharsets.UTF_8));
        }
    }

    public void execute() {
        responseAsBytes();
    }

    public String responseAsUtf8() {
        return new String(responseAsBytes(), StandardCharsets.UTF_8);
    }

    public XmlElement responseAsXml() {
        return XmlElement.parse(responseAsUtf8());
    }

    public void responseAsUtf8(Consumer<String> consumer) {
        consumer.accept(responseAsUtf8());
    }

}