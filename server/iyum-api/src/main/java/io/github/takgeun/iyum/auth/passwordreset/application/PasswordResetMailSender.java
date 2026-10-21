package io.github.takgeun.iyum.auth.passwordreset.application;

public interface PasswordResetMailSender {

    void send(PasswordResetMailRequested requested);
}
