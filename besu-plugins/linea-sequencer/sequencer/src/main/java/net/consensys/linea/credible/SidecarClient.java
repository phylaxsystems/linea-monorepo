package net.consensys.linea.credible;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Sidecar JSON RPC client
 * Requires dependencies: okhttp3 and jackson-databind
 */
public class SidecarClient {
    
    // JSON RPC Request class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcRequest {
        @JsonProperty("jsonrpc")
        private String jsonrpc = "2.0";
        
        @JsonProperty("method")
        private String method;
        
        @JsonProperty("params")
        private Object params;
        
        @JsonProperty("id")
        private String id;
        
        public JsonRpcRequest() {}
        
        public JsonRpcRequest(String method, Object params, String id) {
            this.method = method;
            this.params = params;
            this.id = id;
        }
        
        // Getters and setters
        public String getJsonrpc() { return jsonrpc; }
        public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }
        
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        
        public Object getParams() { return params; }
        public void setParams(Object params) { this.params = params; }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }
    
    // JSON RPC Response class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcResponse<T> {
        @JsonProperty("jsonrpc")
        private String jsonrpc;
        
        @JsonProperty("result")
        private T result;
        
        @JsonProperty("error")
        private JsonRpcError error;
        
        @JsonProperty("id")
        private String id;
        
        public JsonRpcResponse() {}
        
        // Getters and setters
        public String getJsonrpc() { return jsonrpc; }
        public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }
        
        public T getResult() { return result; }
        public void setResult(T result) { this.result = result; }
        
        public JsonRpcError getError() { return error; }
        public void setError(JsonRpcError error) { this.error = error; }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public boolean hasError() { return error != null; }
    }
    
    // JSON RPC Error class
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcError {
        @JsonProperty("code")
        private int code;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("data")
        private Object data;
        
        public JsonRpcError() {}
        
        // Getters and setters
        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
        
        @Override
        public String toString() {
            return String.format("JsonRpcError{code=%d, message='%s', data=%s}", code, message, data);
        }
    }
    
    // JSON RPC Exception class
    public static class JsonRpcException extends Exception {
        private final JsonRpcError error;
        
        public JsonRpcException(JsonRpcError error) {
            super(error.getMessage());
            this.error = error;
        }
        
        public JsonRpcException(String message) {
            super(message);
            this.error = null;
        }
        
        public JsonRpcException(String message, Throwable cause) {
            super(message, cause);
            this.error = null;
        }
        
        public JsonRpcError getError() { return error; }
    }
    
    // Main Client Implementation
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    
    public SidecarClient(String baseUrl) {
        this(baseUrl, createDefaultHttpClient());
    }
    
    public SidecarClient(String baseUrl, OkHttpClient httpClient) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }
    
    private static OkHttpClient createDefaultHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    // Synchronous call with generic result type
    public <T> T call(String method, Object params, TypeReference<T> resultType) throws JsonRpcException {
        JsonRpcResponse<T> response = callForResponse(method, params, resultType);
        
        if (response.hasError()) {
            throw new JsonRpcException(response.getError());
        }
        
        return response.getResult();
    }
    
    // Synchronous call with Class result type
    public <T> T call(String method, Object params, Class<T> resultClass) throws JsonRpcException {
        return call(method, params, new TypeReference<T>() {
            public Class<T> getRawClass() {
                return resultClass;
            }
        });
    }
    
    // Synchronous call returning raw response
    @SuppressWarnings("unchecked")
    public <T> JsonRpcResponse<T> callForResponse(String method, Object params, TypeReference<T> resultType) throws JsonRpcException {
        try {
            String requestId = UUID.randomUUID().toString();
            JsonRpcRequest request = new JsonRpcRequest(method, params, requestId);
            
            String requestJson = objectMapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(requestJson, JSON);
            
            Request httpRequest = new Request.Builder()
                    .url(baseUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new JsonRpcException("HTTP error: " + response.code() + " " + response.message());
                }
                
                if (response.body() == null) {
                    throw new JsonRpcException("Empty response body");
                }
                
                String responseBody = response.body().string();
                
                // Parse as generic JsonRpcResponse first
                JsonRpcResponse<Object> genericResponse = objectMapper.readValue(responseBody, 
                    new TypeReference<JsonRpcResponse<Object>>() {});
                
                // Create typed response
                JsonRpcResponse<T> typedResponse = new JsonRpcResponse<>();
                typedResponse.setJsonrpc(genericResponse.getJsonrpc());
                typedResponse.setId(genericResponse.getId());
                typedResponse.setError(genericResponse.getError());
                
                // Convert result to proper type if not null and no error
                if (genericResponse.getResult() != null && !genericResponse.hasError()) {
                    T convertedResult = objectMapper.convertValue(genericResponse.getResult(), resultType);
                    typedResponse.setResult(convertedResult);
                }
                
                return typedResponse;
            }
        } catch (IOException e) {
            throw new JsonRpcException("Network error", e);
        }
    }
    
    // Asynchronous call
    public <T> CompletableFuture<T> callAsync(String method, Object params, TypeReference<T> resultType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return call(method, params, resultType);
            } catch (JsonRpcException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    // Asynchronous call with Class result type
    public <T> CompletableFuture<T> callAsync(String method, Object params, Class<T> resultClass) {
        return callAsync(method, params, new TypeReference<T>() {
            public Class<T> getRawClass() {
                return resultClass;
            }
        });
    }
    
    // Batch call support
    public List<JsonRpcResponse<Object>> batchCall(List<JsonRpcRequest> requests) throws JsonRpcException {
        try {
            String requestJson = objectMapper.writeValueAsString(requests);
            RequestBody body = RequestBody.create(requestJson, JSON);
            
            Request httpRequest = new Request.Builder()
                    .url(baseUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new JsonRpcException("HTTP error: " + response.code() + " " + response.message());
                }
                
                if (response.body() == null) {
                    throw new JsonRpcException("Empty response body");
                }
                
                String responseBody = response.body().string();
                return objectMapper.readValue(responseBody, 
                    new TypeReference<List<JsonRpcResponse<Object>>>() {});
            }
        } catch (IOException e) {
            throw new JsonRpcException("Network error", e);
        }
    }
    
    // Notification (no response expected)
    public void notify(String method, Object params) throws JsonRpcException {
        try {
            JsonRpcRequest request = new JsonRpcRequest(method, params, null); // null id for notification
            
            String requestJson = objectMapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(requestJson, JSON);
            
            Request httpRequest = new Request.Builder()
                    .url(baseUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new JsonRpcException("HTTP error: " + response.code() + " " + response.message());
                }
            }
        } catch (IOException e) {
            throw new JsonRpcException("Network error", e);
        }
    }
    
    // Builder pattern for client configuration
    public static class Builder {
        private String baseUrl;
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration readTimeout = Duration.ofSeconds(30);
        private Duration writeTimeout = Duration.ofSeconds(30);
        private Authenticator authenticator;
        
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }
        
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }
        
        public Builder writeTimeout(Duration timeout) {
            this.writeTimeout = timeout;
            return this;
        }
        
        public Builder authenticator(Authenticator authenticator) {
            this.authenticator = authenticator;
            return this;
        }
        
        public SidecarClient build() {
            if (baseUrl == null) {
                throw new IllegalArgumentException("baseUrl is required");
            }
            
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(connectTimeout)
                    .readTimeout(readTimeout)
                    .writeTimeout(writeTimeout);
            
            if (authenticator != null) {
                clientBuilder.authenticator(authenticator);
            }
            
            return new SidecarClient(baseUrl, clientBuilder.build());
        }
    }
    
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}