package com.eesara.drive.auth.service;

import com.eesara.drive.auth.dto.LoginRequest;
import com.eesara.drive.auth.dto.LoginResponse;
import com.eesara.drive.auth.dto.RegisterRequest;
import com.eesara.drive.auth.dto.ForgotPasswordRequest;
import com.eesara.drive.auth.dto.ResetPasswordRequest;

public interface AuthService {

    void register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void requestPasswordReset(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

}
