package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.member.domain.TermsCode;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.takgeun.iyum.auth.application.SignupTermsException.Code.*;
import static io.github.takgeun.iyum.member.domain.TermsCode.*;
import static org.assertj.core.api.Assertions.*;

public class SignupTermsPolicyTest {

    private final SignupTermsPolicy policy = new SignupTermsPolicy();

    @Test
    void 필수_약관의_현재_버전에_모두_동의하면_통과한다() {
        Map<TermsCode, String> result = policy.validateAndResolve(validAgreements());

        assertThat(result)
                .hasSize(2)
                .containsEntry(SERVICE_TERMS, "dev-v1")
                .containsEntry(PRIVACY_COLLECTION_USE, "dev-v1");
    }

    @Test
    void 동의_목록이_없으면_실패한다() {
        assertFailure(
                () -> policy.validateAndResolve(null),
                REQUIRED_AGREEMENT_MISSING
        );

        assertFailure(
                () -> policy.validateAndResolve(List.of()),
                REQUIRED_AGREEMENT_MISSING
        );
    }

    @Test
    void 필수_약관이_누락되면_실패한다() {
        List<SignupAgreement> agreements = List.of(
                new SignupAgreement(
                        SERVICE_TERMS,
                        "dev-v1",
                        true
                )
        );

        // 필수 약관 중 하나인 PRIVACY_COLLECTION_USE 없이 곧바로 policy.validateAndResolve 진행
        assertFailure(
                () -> policy.validateAndResolve(agreements),
                REQUIRED_AGREEMENT_MISSING
        );
    }

    @Test
    void 필수_약관에_동의하지_않으면_실패한다() {
        List<SignupAgreement> agreements = validAgreements();

        agreements.set(
                0,
                new SignupAgreement(SERVICE_TERMS, "dev-v1", false)
        );

        assertFailure(
                () -> policy.validateAndResolve(agreements),
                REQUIRED_AGREEMENT_NOT_ACCEPTED
        );
    }

    @Test
    void 동의_여부가_null이면_동의로_인정하지_않는다() {
        List<SignupAgreement> agreements = validAgreements();

        agreements.set(
                0,
                new SignupAgreement(SERVICE_TERMS, "dev-v1", null)
        );

        assertFailure(
                () -> policy.validateAndResolve(agreements),
                REQUIRED_AGREEMENT_NOT_ACCEPTED
        );
    }

    @Test
    void 이전_버전에_동의하면_최신_버전으로_바꾸지_않고_거절한다() {
        List<SignupAgreement> agreements = validAgreements();

        agreements.set(
                0,
                new SignupAgreement(SERVICE_TERMS, "dev-v0", true)
        );

        assertFailure(
                () -> policy.validateAndResolve(agreements),
                TERMS_VERSION_MISMATCH
        );
    }

    @Test
    void 동일한_약관을_중복_제출하면_실패한다() {
        List<SignupAgreement> agreements = validAgreements();

        agreements.add(
                new SignupAgreement(SERVICE_TERMS, "dev-v1", true)
        );

        assertFailure(
                () -> policy.validateAndResolve(agreements),
                DUPLICATE_AGREEMENT
        );
    }

    @Test
    void 약관_버전이_누락되면_실패한다() {
        List<SignupAgreement> agreements = validAgreements();

        agreements.set(
                0,
                new SignupAgreement(SERVICE_TERMS, null, true)
        );

        assertFailure(
                () -> policy.validateAndResolve(agreements),
                INVALID_AGREEMENT
        );
    }

    private List<SignupAgreement> validAgreements() {
        return new ArrayList<>(List.of(
                new SignupAgreement(
                        SERVICE_TERMS,
                        "dev-v1",
                        true
                ),
                new SignupAgreement(
                        PRIVACY_COLLECTION_USE,
                        "dev-v1",
                        true
                )
        ));
    }

    private void assertFailure(
            Runnable action,
            SignupTermsException.Code expectedCode
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        SignupTermsException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
                );
    }
}
