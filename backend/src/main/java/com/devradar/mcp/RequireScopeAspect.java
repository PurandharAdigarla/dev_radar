package com.devradar.mcp;

import com.devradar.domain.ApiKeyScope;
import com.devradar.security.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequireScopeAspect {

    @Around("@annotation(com.devradar.mcp.RequireScope)")
    public Object enforceScope(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        RequireScope anno = sig.getMethod().getAnnotation(RequireScope.class);
        ApiKeyScope required = anno.value();
        ApiKeyScope actual = SecurityUtils.getCurrentApiKeyScope();

        if (actual == null) {
            throw new McpScopeException("No API key scope on request");
        }
        if (required == ApiKeyScope.WRITE && actual != ApiKeyScope.WRITE) {
            throw new McpScopeException("Scope '" + required + "' required but got '" + actual + "'");
        }
        return pjp.proceed();
    }
}
