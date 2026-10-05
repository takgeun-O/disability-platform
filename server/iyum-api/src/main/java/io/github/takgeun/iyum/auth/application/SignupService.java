package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberAgreement;
import io.github.takgeun.iyum.member.domain.MemberInputPolicy;
import io.github.takgeun.iyum.member.domain.TermsCode;
import io.github.takgeun.iyum.member.infrastructure.MemberAgreementRepository;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SignupService {

    private final MemberRepository memberRepository;
    private final MemberAgreementRepository memberAgreementRepository;
    private final SignupTermsPolicy signupTermsPolicy;
    private final PasswordEncoder passwordEncoder;

    /**
     * @Transactional : 두 리파지토리의 저장을 묶는다. (정상적으로 끝나면 회원과 약관 동의 이력이 함께 커밋)
     */
    @Transactional
    public SignupResult signup(SignupCommand command) {
        String email = MemberInputPolicy.normalizeEmail(command.email());

        String nickname = MemberInputPolicy.normalizedNickname(command.nickname());

        Map<TermsCode, String> validatedTerms =
                signupTermsPolicy.validateAndResolve(command.agreements());

        if (memberRepository.existsByEmail(email)) {
            throw new SignupConflictException(
                    SignupConflictException.Code.EMAIL_ALREADY_EXISTS
            );
        }

        if (memberRepository.existsByNicknameIgnoreCase(nickname)) {
            throw new SignupConflictException(
                    SignupConflictException.Code.NICKNAME_ALREADY_EXISTS
            );
        }

        String passwordHash = passwordEncoder.encode(command.rawPassword());

        Member member = Member.createPending(email, passwordHash, nickname);

        Member savedMember;

        try {
            savedMember = memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException exception) {
            throw translateMemberConstraintViolation(exception);
        }

        Instant agreedAt = Instant.now();

        List<MemberAgreement> agreements = validatedTerms.entrySet()
                .stream()
                .map(entry -> MemberAgreement.recordConsent(
                        savedMember.getId(),
                        entry.getKey(),
                        entry.getValue(),
                        agreedAt
                ))
                .toList();

        // 참고로 saveAllAndFlush 는 커밋이 아니다.
        // flush는 영속성 컨텍스트의 변경 사항을 DB에 SQL로 반영하는 작업임
        // 따라서 회원 저장 SQL의 제약 위반을 이 지점에서 확인 가능함.
        // SQL이 실행되었더라도 트랜잭션이 커밋된 것은 아니므로, 뒤의 약관 저장에서 실패하면 회원 저장도 롤백됨.
        memberAgreementRepository.saveAllAndFlush(agreements);  // 저장 후 즉시 flush

        return new SignupResult(
                savedMember.getId(),
                savedMember.getStatus()
        );
    }

    private RuntimeException translateMemberConstraintViolation(
            DataIntegrityViolationException exception
    ) {
        for (
                Throwable cause = exception;
                cause != null;
                cause = cause.getCause()
        ) {
            if(cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();

                if("uk_members_email".equals(constraintName)) {
                    return new SignupConflictException(
                            SignupConflictException.Code.EMAIL_ALREADY_EXISTS,
                            exception
                    );
                }

                if("uk_members_nickname_lower".equals(constraintName)) {
                    return new SignupConflictException(
                            SignupConflictException.Code.NICKNAME_ALREADY_EXISTS,
                            exception
                    );
                }
            }
        }

        return exception;
    }
}
