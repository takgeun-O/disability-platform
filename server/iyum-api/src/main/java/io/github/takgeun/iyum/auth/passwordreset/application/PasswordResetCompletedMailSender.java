package io.github.takgeun.iyum.auth.passwordreset.application;

public interface PasswordResetCompletedMailSender {

    void send(PasswordResetCompleted event);
}
