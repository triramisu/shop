package com.shop.identity.internal.service.policy;

import com.shop.identity.internal.entity.User;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class UserOwnershipPolicy {

    public void requireOwner(String actorUsername, User resourceOwner) {
        if (actorUsername == null
                || resourceOwner == null
                || !resourceOwner.getUsername().equalsIgnoreCase(actorUsername)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
