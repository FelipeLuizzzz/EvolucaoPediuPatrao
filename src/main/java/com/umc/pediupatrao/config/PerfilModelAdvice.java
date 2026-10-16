package com.umc.pediupatrao.config;

import com.umc.pediupatrao.util.ContextoUsuario;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class PerfilModelAdvice {

    @ModelAttribute("perfil")
    public String perfil() {
        return ContextoUsuario.perfil();
    }

    @ModelAttribute("usuarioNome")
    public String usuarioNome() {
        return ContextoUsuario.nome();
    }
}