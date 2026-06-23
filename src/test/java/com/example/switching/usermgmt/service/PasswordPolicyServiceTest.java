package com.example.switching.usermgmt.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class PasswordPolicyServiceTest {
    private final PasswordPolicyService policy = new PasswordPolicyService(14);

    @Test
    void acceptsStrongPasswordUnrelatedToIdentity() {
        assertThatCode(() -> policy.validate("Correct-Horse-7!", "operator.one", "operator@example.test"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWeakPatternsMissingClassesAndIdentityFragments() {
        assertThatThrownBy(() -> policy.validate("password-Password1!", "operator", "person@example.test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate("alllowercase123!", "operator", "person@example.test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate("Operator-Strong7!", "operator", "person@example.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
