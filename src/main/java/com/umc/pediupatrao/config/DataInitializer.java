package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, PasswordEncoder encoder) {
        return args -> {
            garantir(repo, encoder, "admin", "teste123", Perfil.ADMIN);
            garantir(repo, encoder, "gerente", "teste123", Perfil.GERENTE);
            garantir(repo, encoder, "atendente", "teste123", Perfil.ATENDENTE);
        };
    }

    private void garantir(UsuarioRepository repo, PasswordEncoder encoder, String username, String senha, Perfil perfil) {
        repo.findByUsername(username).ifPresentOrElse(
                u -> {
                    if (Perfil.de(u.getRole()) == null) { // corrige usuário legado sem perfil válido
                        u.setRole(perfil.name());
                        repo.save(u);
                    }
                },
                () -> {
                    Usuario u = new Usuario();
                    u.setUsername(username);
                    u.setPassword(encoder.encode(senha));
                    u.setRole(perfil.name());
                    repo.save(u);
                    System.out.println("Usuário criado: " + username + " (" + perfil + ")");
                }
        );
    }
}