package com.shop.shared.error;

import java.util.Locale;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ErrorMessageResolver {

    MessageSource messageSource;

    public String resolve(ErrorCode errorCode) {
        return resolve(errorCode.getMessageKey());
    }

    public String resolve(ErrorCode errorCode, Locale locale) {
        return resolve(errorCode.getMessageKey(), locale);
    }

    public String resolve(String messageKey, Object... arguments) {
        return resolve(messageKey, LocaleContextHolder.getLocale(), arguments);
    }

    public String resolve(String messageKey, Locale locale, Object... arguments) {
        return messageSource.getMessage(messageKey, arguments, messageKey, locale);
    }
}
