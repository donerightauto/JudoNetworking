package com.judocallbacks.controllers.json.rpc;

import com.google.gson22.GsonBuilder;
import com.google.gson22.JsonSyntaxException;
import com.google.gson22.stream.JsonReader;
import com.judocallbacks.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: jbogacki
 * Date: 23.07.2013
 * Time: 23:02
 * To change this template use File | Settings | File Templates.
 */
public class JsonRpc1Controller extends JsonRpcController {

    public JsonRpc1Controller() {
    }

    public JsonRpc1Controller(GsonBuilder builder) {
        super(builder);
    }


    @Override
    protected Object createRequestModel(String name, Object params, Integer id) {
        return new JsonRpcRequestModel(name, params, id);
    }


    @Override
    public RequestResult parseResponse(RequestInterface request, InputStream stream, Map<String, List<String>> headers) {
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(stream, "UTF-8"));
            JsonRpcResponseModel1 response;
            try {
                response = gson.fromJson(reader, JsonRpcResponseModel1.class);
            } catch (JsonSyntaxException ex) {
                throw new RequestException("Wrong server response. Did you select the correct protocol controller?", ex);
            }
            if (response == null) {
                throw new RequestException("Empty server response.");
            }
            if (response.error != null) {
                throw new RequestException(response.error);
            }
            reader.close();
            if (!request.getReturnType().equals(Void.TYPE) && !request.getReturnType().equals(Void.class)) {
                Object result=gson.fromJson(response.result, request.getReturnType());
                if(!request.isAllowEmptyResult() && result==null)
                {
                    throw new RequestException("Empty result.");
                }
                return new RequestSuccessResult(request.getId(), result);
            }
            return new RequestSuccessResult(request.getId(), null);
        } catch (Exception e) {
            return new ErrorResult(request.getId(), e);
        }
    }



    protected class JsonRpcResponseModel1 extends JsonRpcResponseModel {
        String error;
    }

}