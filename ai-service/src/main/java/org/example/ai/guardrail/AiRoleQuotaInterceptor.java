package org.example.ai.guardrail;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.security.AiSecurityUtil;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Consumes a role quota before an annotated controller method can invoke an AI service. */
public class AiRoleQuotaInterceptor implements HandlerInterceptor {

    private final AiRoleQuotaService quotaService;

    public AiRoleQuotaInterceptor(AiRoleQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !isProtected(method)) {
            return true;
        }
        quotaService.consume(AiSecurityUtil.requireSub(), AiSecurityUtil.currentRoleSet());
        return true;
    }

    private boolean isProtected(HandlerMethod method) {
        return AnnotatedElementUtils.hasAnnotation(method.getMethod(), AiQuotaProtected.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), AiQuotaProtected.class);
    }
}
