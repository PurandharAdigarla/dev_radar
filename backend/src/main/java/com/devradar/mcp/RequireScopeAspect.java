package com.devradar.mcp;

import com.devradar.domain.ApiKeyScope;
import com.devradar.security.SecurityUtils;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequireScopeAspect {

    private final MeterRegistry meters;

    public RequireScopeAspect(MeterRegistry meters) { this.meters = meters; }

    @Around("@annotation(com.devradar.mcp.RequireScope)")
    public Object enforceScope(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        RequireScope anno = sig.getMethod().getAnnotation(RequireScope.class);
        ApiKeyScope required = anno.value();
        ApiKeyScope actual = SecurityUtils.getCurrentApiKeyScope();

        if (actual == null) {
            meters.counter("mcp.tool.calls", "tool", sig.getName(), "status", "denied_scope").increment();
            throw new McpScopeException("No API key scope on request");
        }
        if (required == ApiKeyScope.WRITE && actual != ApiKeyScope.WRITE) {
            meters.counter("mcp.tool.calls", "tool", sig.getName(), "status", "denied_scope").increment();
            throw new McpScopeException("Scope '" + required + "' required but got '" + actual + "'");
        }
        return pjp.proceed();
    }
}
