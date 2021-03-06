package org.zuzuk.tasks.remote.base;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.ObjectParser;

import org.zuzuk.utils.Lc;
import org.zuzuk.utils.Utils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URLEncoder;

/**
 * Created by Gavriil Sitnikov on 04/09/2014.
 * Base HTTP request
 */
public abstract class HttpRequest<T> extends RemoteRequest<T> {
    private final static String CACHE_PARAMETER_SEPARATOR = "#";
    private final static int CACHE_MAX_KEY_SIZE = 128;
    protected final static HttpTransport DefaultHttpTransport = new NetHttpTransport();

    /* Returns base url without parameters */
    protected abstract String getUrl();

    /* Return if response should be logged */
    protected boolean doLogResponse() {
        return false;
    }

    /* Returns data parser */
    protected abstract ObjectParser getParser();

    protected HttpRequest(Class<T> responseResultType) {
        super(responseResultType);
    }

    /* Builds HttpRequest */
    protected abstract com.google.api.client.http.HttpRequest buildRequest(HttpRequestFactory factory, GenericUrl url) throws Exception;

    protected com.google.api.client.http.HttpRequest buildRequest() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        setupHeaders(headers);

        GenericUrl url = new GenericUrl(getUrl());
        setupUrlParameters(url);

        return buildRequest(DefaultHttpTransport.createRequestFactory(), url)
                .setHeaders(headers)
                .setParser(getParser());
    }

    @Override
    public T execute() throws Exception {
        com.google.api.client.http.HttpRequest request = buildRequest();

        Lc.d("Url requested: " + request.getUrl().toString());

        T response = null;
        if (doLogResponse()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(request.execute().getContent()));
            StringBuilder responseString = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseString.append(line);
            }
            Lc.d("Response for: " + request.getUrl().toString() + '\n' + responseString);
            response = getParser().parseAndClose(new StringReader(responseString.toString()), getResultType());
        } else {
            response = request.execute().parseAs(getResultType());
        }

        response = handleResponse(response);
        return response;
    }

    @Override
    public T loadDataFromNetwork() throws Exception {
        return execute();
    }

    /* Setups headers of HttpRequest */
    protected void setupHeaders(HttpHeaders headers) {
    }

    /* Setups headers of HttpRequest */
    protected void setupUrlParameters(GenericUrl url) {
    }

    /* Handle response. Use it to do something after request successfully executes */
    protected T handleResponse(T response) throws Exception {
        return response;
    }

    /* Returns content as string */
    protected String httpContentToString(HttpContent httpContent) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        httpContent.writeTo(outputStream);
        return outputStream.toString("UTF-8");
    }

    @Override
    public String getCacheKey() {
        StringBuilder fileNameSafeCacheKey = new StringBuilder();
        try {
            com.google.api.client.http.HttpRequest request = buildRequest();
            fileNameSafeCacheKey.append(URLEncoder.encode(request.getUrl().build(), "UTF-8").replace("%", CACHE_PARAMETER_SEPARATOR));
            fileNameSafeCacheKey.append(CACHE_PARAMETER_SEPARATOR).append(request.getHeaders().toString());
            if (request.getContent() != null) {
                fileNameSafeCacheKey.append(CACHE_PARAMETER_SEPARATOR).append(httpContentToString(request.getContent()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String cacheKeyMd5 = Utils.md5(fileNameSafeCacheKey.toString());
        int length = fileNameSafeCacheKey.length();
        return fileNameSafeCacheKey.substring(Math.max(0, length - CACHE_MAX_KEY_SIZE), length) + CACHE_PARAMETER_SEPARATOR + cacheKeyMd5;
    }
}
