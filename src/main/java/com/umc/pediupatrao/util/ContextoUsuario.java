package com.umc.pediupatrao.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class ContextoUsuario {

    private ContextoUsuario() {
    }

    public static String nome() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "sistema";
    }

    public static String perfil() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            for (GrantedAuthority a : auth.getAuthorities()) {
                if (a.getAuthority().startsWith("ROLE_")) {
                    return a.getAuthority().substring(5);
                }
            }
        }
        return "N/A";
    }
}
