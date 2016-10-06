package com.github.kubatatami.judonetworking.transports;

import com.github.kubatatami.judonetworking.AsyncResult;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Created by Kuba on 16/10/15.
 */
public abstract class OkHttpOAuth2 {

    private String tokenType;

    private String accessToken;

    private long tokenLifeTime;

    private AsyncResult asyncResult;

    private Interceptor oAuthInterceptor = new Interceptor() {
        @Override
        public Response intercept(Chain chain) throws IOException {
            if (asyncResult != null) {
                await();
            }
            Request request = chain.request();
            if (tokenLifeTime != 0 && tokenLifeTime < System.currentTimeMillis()) {
                callForToken();
            }
            if (accessToken != null && tokenType != null) {
                request = request.newBuilder()
                        .header("Authorization", tokenType + " " + accessToken).build();
            }
            return chain.proceed(request);
        }
    };

    private Authenticator oAuthAuthenticator = new Authenticator() {
        @Override
        public Request authenticate(Route route, Response response) throws IOException {
            String prevAccessToken = accessToken;
            synchronized (this) {
                if (canDoTokenRequest()) {
                    if (prevAccessToken.equals(accessToken)) {
                        callForToken();
                    }
                    if (accessToken != null) {
                        return response.request();
                    }
                }
                return null;
            }
        }
    };

    private void callForToken() throws IOException {
        asyncResult = doTokenRequest();
        await();
    }

    private void await() throws IOException {
        try {
            asyncResult.await();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    public void prepareOkHttpToOAuth(OkHttpClient.Builder okHttpClient) {
        okHttpClient.networkInterceptors().add(oAuthInterceptor);
        okHttpClient.authenticator(oAuthAuthenticator);
    }

    public void setOAuthToken(String tokenType, String accessToken) {
        this.tokenType = tokenType;
        this.accessToken = accessToken;
    }

    public void setTokenLifeTime(long tokenLifeTime) {
        this.tokenLifeTime = tokenLifeTime;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public long getTokenLifeTime() {
        return tokenLifeTime;
    }

    protected abstract AsyncResult doTokenRequest();

    protected abstract boolean canDoTokenRequest();

}
