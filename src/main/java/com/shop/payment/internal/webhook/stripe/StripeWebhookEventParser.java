package com.shop.payment.internal.webhook.stripe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class StripeWebhookEventParser {

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "checkout.session.completed",
            "checkout.session.async_payment_succeeded",
            "checkout.session.async_payment_failed",
            "checkout.session.expired");

    private final ObjectMapper objectMapper;

    StripeWebhookEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    StripeWebhookEvent parse(byte[] rawPayload) {
        try {
            StripeEventPayload payload = objectMapper.readValue(rawPayload, StripeEventPayload.class);
            if (!"event".equals(payload.object())) {
                throw invalidPayload();
            }
            String eventId = requireIdentifier(payload.id(), "evt_", 150);
            String eventType = requireText(payload.type(), 100);
            if (!eventType.matches("[a-z0-9_.]+")) {
                throw invalidPayload();
            }
            Instant createdAt = toInstant(payload.created());
            if (payload.data() == null || payload.data().object() == null) {
                throw invalidPayload();
            }
            JsonNode object = payload.data().object();
            String objectId = requireText(text(object, "id"), 150);
            if (!SUPPORTED_EVENT_TYPES.contains(eventType)) {
                return new StripeWebhookEvent(
                        eventId,
                        eventType,
                        createdAt,
                        payload.apiVersion(),
                        requireBoolean(payload.liveMode()),
                        objectId,
                        null);
            }
            if (!"checkout.session".equals(text(object, "object"))) {
                throw invalidPayload();
            }
            StripeWebhookEvent.StripeCheckoutSessionData session = new StripeWebhookEvent.StripeCheckoutSessionData(
                    parseUuid(metadata(object, "payment_attempt_id")),
                    parseUuid(metadata(object, "order_id")),
                    parseUuid(text(object, "client_reference_id")),
                    requirePositiveLong(object, "amount_total"),
                    requireCurrency(text(object, "currency")),
                    requireText(text(object, "status"), 30).toLowerCase(Locale.ROOT),
                    requireText(text(object, "payment_status"), 30).toLowerCase(Locale.ROOT));
            validateEventState(eventType, session);
            return new StripeWebhookEvent(
                    eventId,
                    eventType,
                    createdAt,
                    requireText(payload.apiVersion(), 50),
                    requireBoolean(payload.liveMode()),
                    requireIdentifier(objectId, "cs_", 150),
                    session);
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidPayload();
        }
    }

    private static void validateEventState(String eventType, StripeWebhookEvent.StripeCheckoutSessionData session) {
        boolean valid =
                switch (eventType) {
                    case "checkout.session.completed" ->
                        "complete".equals(session.sessionStatus())
                                && ("paid".equals(session.paymentStatus()) || "unpaid".equals(session.paymentStatus()));
                    case "checkout.session.async_payment_succeeded" ->
                        "complete".equals(session.sessionStatus()) && "paid".equals(session.paymentStatus());
                    case "checkout.session.async_payment_failed" ->
                        "complete".equals(session.sessionStatus()) && "unpaid".equals(session.paymentStatus());
                    case "checkout.session.expired" ->
                        "expired".equals(session.sessionStatus()) && "unpaid".equals(session.paymentStatus());
                    default -> false;
                };
        if (!valid) {
            throw invalidPayload();
        }
    }

    private static String metadata(JsonNode object, String name) {
        JsonNode metadata = object.get("metadata");
        return metadata == null ? null : text(metadata, name);
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || !value.isString() ? null : value.asString();
    }

    private static long requirePositiveLong(JsonNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 1) {
            throw invalidPayload();
        }
        return value.longValue();
    }

    private static String requireCurrency(String value) {
        String normalized = requireText(value, 3).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw invalidPayload();
        }
        return normalized;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(requireText(value, 36));
        } catch (IllegalArgumentException exception) {
            throw invalidPayload();
        }
    }

    private static Instant toInstant(Long epochSecond) {
        if (epochSecond == null || epochSecond < 0) {
            throw invalidPayload();
        }
        try {
            return Instant.ofEpochSecond(epochSecond);
        } catch (DateTimeException exception) {
            throw invalidPayload();
        }
    }

    private static boolean requireBoolean(Boolean value) {
        if (value == null) {
            throw invalidPayload();
        }
        return value;
    }

    private static String requireIdentifier(String value, String prefix, int maximumLength) {
        String normalized = requireText(value, maximumLength);
        if (!normalized.startsWith(prefix) || !normalized.matches("[A-Za-z0-9_]+")) {
            throw invalidPayload();
        }
        return normalized;
    }

    private static String requireText(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw invalidPayload();
        }
        String normalized = value.strip();
        if (normalized.length() > maximumLength) {
            throw invalidPayload();
        }
        return normalized;
    }

    private static AppException invalidPayload() {
        return new AppException(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StripeEventPayload(
            String id,
            String object,
            String type,
            Long created,
            @JsonProperty("api_version") String apiVersion,
            @JsonProperty("livemode") Boolean liveMode,
            StripeEventData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StripeEventData(JsonNode object) {}
}
