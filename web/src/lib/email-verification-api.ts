// 요청 및 응답 타입
// 이 함수를 호출할 때 어떤 객체를 넘겨야 하는지 TypeScript가 검사해준다.
export type EmailVerificationRequest = {
    token: string;
};

export type EmailVerificationResponse = {
    status: "ACTIVE"; // 정확히 ACTIVE라는 값만 허용한다.
};

// JAVA에서의 사용자 정의 예외와 비슷
export class EmailVerificationApiError extends Error {
    // status: 0은 실제 HTTP 상태 코드가 아니라 응답을 받지 못했거나
    // 요청 전에 실패했음을 표현하는 프론트엔드 내부 값
    status: number;
    code: string;

    constructor(status: number, code: string, message: string) {
        super(message);

        this.name = "EmailVerificationApiError";
        this.status = status;
        this.code = code;
    }
}

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value);
}

export async function verifyEmail(
    request: EmailVerificationRequest,
    csrfToken: string,
): Promise<EmailVerificationResponse> {
    const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;

    if (!baseUrl) {
        throw new EmailVerificationApiError(
            0,
            "API_NOT_CONFIGURED",
            "인증 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
        );
    }

    const url = baseUrl.replace(/\/$/, "") + "/api/v1/auth/email/verify";

    let response: Response;

    try {
        response = await fetch(url, {
            method: "POST",
            credentials: "include", 
            cache: "no-store",
            referrerPolicy: "no-referrer",
            headers: {
                Accept: "application/json",
                "Content-Type": "application/json",
                "X-CSRF-TOKEN": csrfToken,
            },
            // 기존 CSRF 조회에서 사용한 세션 쿠키를 인증 POST에도 보내도록 한다.
            body: JSON.stringify(request),
            signal: AbortSignal.timeout(15_000),
        });
    } catch {
        // 서버에서 이미 처리했을 수도 있으므로 자동 재전송하지 않는다.
        throw new EmailVerificationApiError(
            0,
            "NETWORK_ERROR",
            "서버와 통신하지 못해 인증 결과를 확인할 수 없습니다. 연결을 확인한 뒤 다시 시도해 주세요.",
        );
    }

    let body: unknown;

    try {
        body = await response.json();
    } catch {
        // HTML 오류 페이지나 빈 응답도 안전하게 처리
        body = null;
    }

    if (response.status >= 500) {
        throw new EmailVerificationApiError(
            response.status,
            "SERVER_ERROR",
            "서버 오류로 인증 결과를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        );
    }

    // HTTP 오류 확인
    // fetch()는 서버가 400, 403, 500을 반환해도 응답 객체를 반환할 수 있다.
    // 따라서 catch만으로는 부족하고 HTTP 상태를 직접 확인해야 한다.
    if (!response.ok) {
        const code =
            isObject(body) && typeof body.code === "string"
                ? body.code
                : "UNEXPECTED_RESPONSE";

        let message =
            "이메일 인증 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";

        if (response.status === 400 && code === "EMAIL_VERIFICATION_INVALID") {
            message = "유효하지 않거나 사용할 수 없는 이메일 인증 링크입니다.";
        } else if (
            response.status === 400 &&
            (code === "VALIDATION_FAILED" || code === "INVALID_REQUEST_BODY")
        ) {
            message =
                "인증 요청 정보가 올바르지 않습니다. 메일의 인증 링크를 다시 확인해 주세요.";
        } else if (response.status === 403 && code === "CSRF_TOKEN_INVALID") {
            message =
                "보안 확인 정보가 유효하지 않습니다. 인증 버튼을 다시 눌러 주세요.";
        }

        throw new EmailVerificationApiError(response.status, code, message);
    }

    if (
        response.status !== 200
        || !isObject(body)
        || body.status !== 'ACTIVE'
    ) {
        throw new EmailVerificationApiError(
            response.status,
            "UNEXPECTED_RESPONSE",
            "서버 응답을 확인하지 못해 인증 결과를 알 수 없습니다. 잠시 후 다시 확인해 주세요.",
        );
    }

    // response.status === 200 && body.status === 'ACTIVE' 일 때만 인증 성공으로 반환
    return { status: "ACTIVE" };
}
