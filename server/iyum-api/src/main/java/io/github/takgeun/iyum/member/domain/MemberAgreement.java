package io.github.takgeun.iyum.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * | 필드 | Java 타입 | 역할 |
 * |---|---|---|
 * | `id` | `Long` | 동의 기록 식별자 |
 * | `memberId` | `Long` | 동의한 회원 ID |
 * | `termsCode` | `TermsCode` | 약관 종류 |
 * | `termsVersion` | `String` | 동의한 약관 버전 |
 * | `agreedAt` | `Instant` | 서버에서 기록하는 동의 시각 |
 */
@Entity
@Table(name = "member_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @ManyToOne Member member 대신 Long memberId로 연결
    // 동의 기록 저장할 때 회원 객체 전체를 조회할 필요가 없고,
    // 현재 기능에 필요한 정보도 회원 ID 뿐이기 때문
    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "terms_code",
            nullable = false,
            updatable = false,
            length = 32
    )
    private TermsCode termsCode;

    @Column(
            name = "terms_version",
            nullable = false,
            updatable = false,
            length = 50
    )
    private String termsVersion;

    @Column(
            name = "agreed_at",
            nullable = false,
            updatable = false
    )
    private Instant agreedAt;

    private MemberAgreement(
            Long memberId,
            TermsCode termsCode,
            String termsVersion,
            Instant agreedAt
    ) {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException(
                    "유효한 회원 ID가 필요합니다."
            );
        }

        if (termsCode == null) {
            throw new IllegalArgumentException(
                    "약관 코드가 필요합니다."
            );
        }

        if (termsVersion == null
                || termsVersion.isBlank()
                || termsVersion.length() > 50) {
            throw new IllegalArgumentException(
                    "약관 버전은 공백이 아닌 50자 이하의 값이어야 합니다."
            );
        }

        if(agreedAt == null) {
            throw new IllegalArgumentException(
                    "동의 시각이 필요합니다."
            );
        }

        this.memberId = memberId;
        this.termsCode = termsCode;
        this.termsVersion = termsVersion;
        this.agreedAt = agreedAt;
    }

    // 여기서는 최신 약관 버전인지 검사하지 않음.
    // 가입 시 유효한 버전인지는 SignupTermsPolicy가 검사하고
    // 엔티티는 그 결과를 기록함.
    public static MemberAgreement recordConsent(
            Long memberId,
            TermsCode termsCode,
            String termsVersion,
            Instant agreedAt
    ) {
        return new MemberAgreement(
                memberId,
                termsCode,
                termsVersion,
                agreedAt
        );
    }
}
