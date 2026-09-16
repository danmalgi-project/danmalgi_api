package com.danmalgi.backend.global.grpc.context;

import com.danmalgi.backend.user.domain.model.User;
import io.grpc.Context;

public class GrpcContext {
    public static Context.Key<Long> USER_ID = Context.key("UserId");
    public static Context.Key<String> DEVICE_ID = Context.key("DeviceId");
    public static Context.Key<User> USER = Context.key("user");
    public static Context.Key<String> JWT_TOKEN = Context.key("JwtToken");
}
