package com.implix.jsonrpc;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Pair;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

class JsonProxy implements InvocationHandler {

    private Context context;
    private JsonRpcImplementation rpc;
    private int id = 0;
    private boolean batch = false;
    private final List<JsonRequest> batchRequests = new ArrayList<JsonRequest>();
    private JsonBatchMode mode = JsonBatchMode.NONE;

    public JsonProxy(Context context, JsonRpcImplementation rpc, JsonBatchMode mode) {
        this.rpc = rpc;
        this.context = context;
        this.mode = mode;
        if (mode == JsonBatchMode.MANUAL) {
            batch = true;
        }
    }

    private Method getMethod(Object obj, String name) {
        for (Method m : obj.getClass().getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    public static String getMethodName(Method method) {
        String name = method.getName();
        JsonMethod ann = method.getAnnotation(JsonMethod.class);
        if (ann != null) {
            if (!ann.name().equals("")) {
                name = ann.name();
            }
        }
        return name;
    }

    public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
        try {
            Method method = getMethod(this, m.getName());
            if (method != null) {
                return method.invoke(this, args);
            } else {
                String paramNames[] = null;
                String name = getMethodName(m);
                int timeout = rpc.getJsonConnector().getMethodTimeout();
                int cacheLifeTime = 0, cacheSize = 0;
                boolean async = false, notification = false, cachable = false;
                JsonMethod ann = m.getAnnotation(JsonMethod.class);
                if (ann != null) {
                    if (ann.paramNames().length > 0) {
                        paramNames = ann.paramNames();
                    }
                    async = ann.async();
                    cachable = ann.cachable();
                    cacheLifeTime = ann.cacheLifeTime();
                    notification = ann.notification();
                    cacheSize = ann.cacheSize();
                    if (ann.timeout() != 0) {
                        timeout = ann.timeout();
                    }
                }


                if (m.getReturnType().equals(Void.TYPE) && !async && notification) {
                    rpc.getJsonConnector().notify(name, paramNames, args, timeout, rpc.getApiKey());
                    return null;
                } else if (!async) {
                    return rpc.getJsonConnector().call(++id, name, paramNames, args, m.getGenericReturnType(), timeout, rpc.getApiKey(), cachable, cacheLifeTime, cacheSize);
                } else {
                    final JsonRequest request = callAsync(++id, name, paramNames, args, m.getGenericParameterTypes(), timeout, rpc.getApiKey(), cachable, cacheLifeTime, cacheSize);

                    if (batch) {
                        synchronized (batchRequests) {
                            batchRequests.add(request);
                            batch = true;
                        }
                        return null;
                    } else {

                        if (mode == JsonBatchMode.AUTO) {

                            synchronized (batchRequests) {
                                batchRequests.add(request);
                                batch = true;
                            }
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Thread.sleep(rpc.getAutoBatchTime());
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    JsonProxy.this.callBatch(new JsonBatch());
                                }
                            }).start();

                            return null;
                        } else {


                            Thread thread = new Thread(request);
                            thread.start();

                            if (m.getReturnType().equals(Thread.class)) {
                                return thread;
                            } else {
                                return null;
                            }

                        }


                    }

                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public JsonRequest callAsync(int id, String name, String[] params, Object[] args, Type[] types, int timeout, String apiKey, boolean cachable, int cacheLifeTime, int cacheSize) throws Exception {
        Object[] newArgs = null;
        JsonCallback<Object> callback = (JsonCallback<Object>) args[args.length - 1];
        if (args.length > 1) {
            newArgs = new Object[args.length - 1];
            System.arraycopy(args, 0, newArgs, 0, args.length - 1);
        }
        Type type = ((ParameterizedType) types[args.length - 1]).getActualTypeArguments()[0];
        return new JsonRequest(id, rpc, callback, name, params, newArgs, type, timeout, apiKey, cachable, cacheLifeTime, cacheSize);
    }

    public void callBatch(final JsonBatch batch) {
        List<JsonRequest> batches = null;
        if (batchRequests.size() > 0) {


            synchronized (batchRequests) {
                batches = new ArrayList<JsonRequest>(batchRequests.size());
                batches.addAll(batchRequests);
                batchRequests.clear();
                this.batch = false;
            }
            try {
                Map<Integer, Pair<JsonRequest, Object>> cacheObjects = new HashMap<Integer, Pair<JsonRequest, Object>>();
                if (rpc.isCacheEnabled()) {
                    for (int i = batches.size() - 1; i >= 0; i--) {
                        JsonRequest req = batches.get(0);
                        Object object = rpc.getCache().get(req.getName(), req.getArgs(), req.getCacheLifeTime());
                        if (req.isCachable() && object != null) {
                            cacheObjects.put(i, new Pair<JsonRequest, Object>(req, object));
                            batches.remove(i);
                        }
                    }
                }
                List<JsonResponseModel2> responses = null;
                if (batches.size() > 0) {
                    responses = sendBatchRequest(batches);
                    Collections.sort(responses);
                } else {
                    responses = new ArrayList<JsonResponseModel2>();
                }

                for (Map.Entry<Integer, Pair<JsonRequest, Object>> pairs : cacheObjects.entrySet()) {
                    responses.add(pairs.getKey(), new JsonResponseModel2(pairs.getValue().second));
                    batches.add(pairs.getKey(), pairs.getValue().first);
                }
                Collections.sort(batches,new Comparator<JsonRequest>() {
                    @Override
                    public int compare(JsonRequest lhs, JsonRequest rhs) {
                        return lhs.getId().compareTo(rhs.getId());
                    }
                });
                parseBatchResponse(batches, batch, responses);
            } catch (final Exception e) {
                if (mode == JsonBatchMode.AUTO) {
                    for (JsonRequest req : batches) {
                        req.invokeCallback(e);
                    }
                }
                rpc.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        batch.onError(e);
                    }
                });

            }


        } else {
            this.batch = false;
        }
    }

    class BatchTask implements Runnable {
        private Integer timeout;
        private List<JsonRequest> requests;
        private Thread thread = new Thread(this);
        private List<JsonResponseModel2> response = null;
        private Exception ex = null;

        public BatchTask(Integer timeout, List<JsonRequest> requests) {
            this.timeout = timeout;
            this.requests = requests;
        }

        public List<JsonResponseModel2> getResponse() {
            return this.response;
        }

        public Exception getEx() {
            return ex;
        }

        @Override
        public void run() {
            try {
                this.response = rpc.getJsonConnector().callBatch(this.requests, this.timeout);
            } catch (Exception e) {
                this.ex = e;
            }
        }

        public void execute() {
            thread.start();
        }

        public void join() throws InterruptedException {
            thread.join();
        }

    }

    private List<List<JsonRequest>> assignRequestsToConnections(List<JsonRequest> list, final int partsNo) {
        if (rpc.isTimeProfiler()) {
            return timeAssignRequests(list, partsNo);
        } else {
            return simpleAssignRequests(list, partsNo);
        }
    }

    private int getSmallestPart( long[] parts)
    {
        int res=0;

        for (int i=0;i<parts.length;i++)
        {
            if (parts[i] < parts[res])
            {
                res=i;
            }
        }
        return res;
    }

    private List<List<JsonRequest>> timeAssignRequests(List<JsonRequest> list, final int partsNo) {
        List<List<JsonRequest>> parts = new ArrayList<List<JsonRequest>>(partsNo);
        long[] weights = new long[partsNo];
        for (int i = 0; i < partsNo; i++) {
            parts.add(new ArrayList<JsonRequest>());
        }
        Collections.sort(list);
        for(JsonRequest req : list)
        {
            int i = getSmallestPart(weights);
            weights[i]+=req.getWeight();
            parts.get(i).add(req);
        }

        return parts;
    }

    private static List<List<JsonRequest>> simpleAssignRequests(List<JsonRequest> list, final int partsNo) {
        int i = 0;
        List<List<JsonRequest>> parts = new ArrayList<List<JsonRequest>>(partsNo);
        for (i = 0; i < partsNo; i++) {
            parts.add(new ArrayList<JsonRequest>());
        }
        i = 0;
        for (JsonRequest elem : list) {
            parts.get(i).add(elem);
            i++;
            if (i == partsNo) {
                i = 0;
            }
        }

        return parts;
    }

    private int calculateTimeout(List<JsonRequest> batches) {
        int timeout = 0;
        if (rpc.getTimeoutMode() == JsonBatchTimeoutMode.TIMEOUTS_SUM) {
            for (JsonRequest req : batches) {
                timeout += req.getTimeout();
            }
        } else {
            for (JsonRequest req : batches) {
                timeout = Math.max(timeout, req.getTimeout());
            }
        }
        return timeout;
    }

    private List<JsonResponseModel2> sendBatchRequest(List<JsonRequest> batches) throws Exception {
        int conn = isWifi() ? rpc.getMaxWifiConnections() : rpc.getMaxMobileConnections();

        if (batches.size() > 1 && conn > 1) {
            int connections = Math.min(batches.size(), conn);
            List<List<JsonRequest>> requestParts = assignRequestsToConnections(batches, connections);
            List<JsonResponseModel2> response = new ArrayList<JsonResponseModel2>(batches.size());
            List<BatchTask> tasks = new ArrayList<BatchTask>(connections);
            for (List<JsonRequest> requests : requestParts) {

                BatchTask task = new BatchTask(calculateTimeout(requests), requests);
                tasks.add(task);
            }
            for (BatchTask task : tasks) {
                task.execute();
            }
            for (BatchTask task : tasks) {
                task.join();
                if (task.ex != null) {
                    throw task.getEx();
                } else {
                    response.addAll(task.getResponse());
                }
            }
            return response;
        } else {

            return rpc.getJsonConnector().callBatch(batches, calculateTimeout(batches));
        }
    }

    private void parseBatchResponse(List<JsonRequest> requests, JsonBatch batch, List<JsonResponseModel2> responses) {
        Object[] results = new Object[requests.size()];
        Exception ex = null;
        int i = 0;
        for (JsonRequest request : requests) {
            try {
                JsonResponseModel2 response = responses.get(i);
                if (response.cacheObject != null) {
                    results[i] = response.cacheObject;
                } else {
                    if (response.error != null) {
                        throw new JsonException(request.getName() + ": " + response.error.message, response.error.code);
                    }

                    if (request.getType() != Void.class) {
                        try {
                            results[i] = parseResponse(response.result, request.getType());
                        } catch (JsonSyntaxException e) {
                            throw new JsonException(request.getName(), e);
                        }
                        if (rpc.isCacheEnabled() && request.isCachable()) {
                            rpc.getCache().put(request.getName(), request.getArgs(), results[i], request.getCacheSize());
                        }
                    }
                }
                request.invokeCallback(results[i]);
            } catch (Exception e) {
                ex = e;
                request.invokeCallback(new JsonException(request.getName(), e));
            }
            i++;
        }

        if (ex == null) {
            JsonRequest.invokeBatchCallback(rpc, batch, results);
        } else {
            JsonRequest.invokeBatchCallback(rpc, batch, ex);
        }

    }

    private boolean isWifi() {
        final ConnectivityManager connectManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo wifi = connectManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifi != null && wifi.getState() == NetworkInfo.State.CONNECTED;
    }

    private <T> T parseResponse(JsonElement result, Type type) {
        return rpc.getParser().fromJson(result, type);
    }

}
