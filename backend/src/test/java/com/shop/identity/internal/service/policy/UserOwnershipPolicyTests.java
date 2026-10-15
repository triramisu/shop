package com.shop.identity.internal.service.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shop.identity.internal.entity.User;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

class UserOwnershipPolicyTests {

    private final UserOwnershipPolicy policy = new UserOwnershipPolicy();

    @Test
    void acceptsTheAuthenticatedOwnerIgnoringUsernameCase() {
        User owner = user("Profile.Owner");

        assertThatCode(() -> policy.requireOwner("profile.owner", owner)).doesNotThrowAnyException();
    }

    @Test
    void hidesAResourceOwnedByAnotherUser() {
        User owner = user("profile-owner");

        assertThatThrownBy(() -> policy.requireOwner("different-user", owner))
                .isInstanceOfSatisfying(AppException.class, this::assertResourceNotFound);
    }

    @Test
    void rejectsAMissingAuthenticatedIdentity() {
        assertThatThrownBy(() -> policy.requireOwner(null, user("profile-owner")))
                .isInstanceOfSatisfying(AppException.class, this::assertResourceNotFound);
    }

    @Test
    void rejectsAMissingResourceOwner() {
        assertThatThrownBy(() -> policy.requireOwner("profile-owner", null))
                .isInstanceOfSatisfying(AppException.class, this::assertResourceNotFound);
    }

    private void assertResourceNotFound(AppException exception) {
        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private User user(String username) {
        User user = mock(User.class);
        when(user.getUsername()).thenReturn(username);
        return user;
    }
}
