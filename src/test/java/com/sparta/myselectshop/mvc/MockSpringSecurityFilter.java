package com.sparta.myselectshop.mvc;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;


// Controller 를 테스트 할 때, Security 가 방해를 해서 테스트하기 힘들기 때문에
// MockSecurity 를 만들어서 가짜 Security Filter 를 만들어서 사용을 한다.
public class MockSpringSecurityFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        SecurityContextHolder.getContext() // SecurityContextHolder : 인증 객체를 담고있는 컨텍스트를 담는 공간. SecurityContext 에 접근하기 위해 필요함 .getContext 를 하면 SecurityContext 가 반환된다.
                .setAuthentication((Authentication) ((HttpServletRequest) req).getUserPrincipal());
        chain.doFilter(req, res);
    }

    @Override
    public void destroy() {
        SecurityContextHolder.clearContext();
    }
}