/**
 * Adaptive CAPTCHA feature grouped as a vertical slice.
 *
 * <p>The authentication controller delegates CAPTCHA decisions to the service layer. Services enforce the failure
 * threshold and one-time challenge rules, repositories persist state, and the cleanup service removes expired data.
 */
package com.shop.identity.internal.captcha;
