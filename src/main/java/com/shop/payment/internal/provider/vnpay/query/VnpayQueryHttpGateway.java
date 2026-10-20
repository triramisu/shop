package com.shop.payment.internal.provider.vnpay.query;

import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentStatusQueryRequest;
import com.shop.payment.provider.PaymentStatusQueryResult;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

final class VnpayQueryHttpGateway implements VnpayQueryGateway {
    static final String SANDBOX_ENDPOINT = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private static final int MAX_RESPONSE_BYTES = 16_384;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;
    private final VnpayQueryCodec codec;
    private final HttpUrl endpoint;

    VnpayQueryHttpGateway(OkHttpClient client, VnpayQueryCodec codec) {
        this(client, codec, HttpUrl.get(SANDBOX_ENDPOINT));
    }

    // Endpoint injection is package-private for loopback tests, never an operator-supplied URL.
    VnpayQueryHttpGateway(OkHttpClient client, VnpayQueryCodec codec, HttpUrl endpoint) {
        this.client = client;
        this.codec = codec;
        this.endpoint = endpoint;
    }

    @Override
    public PaymentStatusQueryResult query(PaymentStatusQueryRequest query) {
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .post(oneShotJson(codec.requestBody(query)))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw httpFailure(response.code());
            }
            ResponseBody body = response.body();
            if (body == null
                    || body.contentType() == null
                    || !body.contentType().type().equals("application")
                    || !body.contentType().subtype().equals("json")
                    || body.contentLength() > MAX_RESPONSE_BYTES) {
                throw VnpayQueryCodec.protocol("VNPAY_QUERY_BODY_INVALID");
            }
            byte[] bytes = body.byteStream().readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw VnpayQueryCodec.protocol("VNPAY_QUERY_BODY_TOO_LARGE");
            }
            return codec.response(bytes, query);
        } catch (InterruptedIOException exception) {
            throw new PaymentProviderException(PaymentProviderErrorType.TIMEOUT, "VNPAY_QUERY_TIMEOUT");
        } catch (IOException exception) {
            throw new PaymentProviderException(
                    PaymentProviderErrorType.TEMPORARY_UNAVAILABLE, "VNPAY_QUERY_IO_FAILURE");
        }
    }

    private static RequestBody oneShotJson(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        // retryOnConnectionFailure(false) alone does not prevent OkHttp's 503 Retry-After: 0 follow-up.
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return JSON;
            }

            @Override
            public long contentLength() {
                return bytes.length;
            }

            @Override
            public boolean isOneShot() {
                return true;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.write(bytes);
            }
        };
    }

    private static PaymentProviderException httpFailure(int status) {
        PaymentProviderErrorType type =
                switch (status) {
                    case 400, 404, 409, 422 -> PaymentProviderErrorType.INVALID_REQUEST;
                    case 401, 403 -> PaymentProviderErrorType.AUTHENTICATION_FAILED;
                    case 429 -> PaymentProviderErrorType.RATE_LIMITED;
                    default ->
                        status >= 500
                                ? PaymentProviderErrorType.TEMPORARY_UNAVAILABLE
                                : PaymentProviderErrorType.PROTOCOL_ERROR;
                };
        return new PaymentProviderException(type, "VNPAY_QUERY_HTTP_" + status);
    }
}
