package com.shop.payment.internal.webhook.stripe;

import java.time.Instant;

record VerifiedStripeSignature(Instant signedAt) {}
