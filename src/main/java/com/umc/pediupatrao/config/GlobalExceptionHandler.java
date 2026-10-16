package com.umc.pediupatrao.config;

import com.umc.pediupatrao.exception.RegraNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RegraNegocioException.class)
    public Object tratarRegraNegocio(RegraNegocioException ex, HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", ex.getMessage()));
        }
        FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
        flash.put("erro", ex.getMessage());
        return "redirect:" + destino(uri);
    }

    private String destino(String uri) {
        if (uri.startsWith("/usuarios")) return "/usuarios";
        if (uri.startsWith("/clientes")) return "/clientes";
        if (uri.startsWith("/pedidos")) return "/pedidos";
        return "/";
    }
}