package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock UsuarioRepository repo;
    @Mock PasswordEncoder encoder;

    UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(repo, encoder);
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void logadoComo(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, null));
    }

    private Usuario usuario(String id, String username, String role) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setRole(role);
        u.setPassword("HASH_ANTIGO");
        return u;
    }

    private Usuario dados(String username, String senha, String role) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setPassword(senha);
        u.setRole(role);
        return u;
    }


    @Test
    void criarUsuarioCriptografaSenhaENormalizaPerfil() {
        when(encoder.encode("senha123")).thenReturn("HASH");
        when(repo.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        Usuario salvo = service.salvarUsuario(dados("novo.user", "senha123", "gerente"));

        assertEquals("HASH", salvo.getPassword());
        assertEquals("GERENTE", salvo.getRole());
    }

    @Test
    void perfilInvalidoOuLegadoEhRejeitado() {
        for (String role : new String[]{"SUPERUSER", "USER", "", null}) {
            assertThrows(RegraNegocioException.class,
                    () -> service.salvarUsuario(dados("novo.user", "senha123", role)));
        }
        verify(repo, never()).save(any(Usuario.class));
    }

    @Test
    void usernameInvalidoEhRejeitado() {
        assertThrows(RegraNegocioException.class,
                () -> service.salvarUsuario(dados("a", "senha123", "ATENDENTE")));
        verify(repo, never()).save(any(Usuario.class));
    }

    @Test
    void usernameDuplicadoEhRejeitado() {
        when(repo.findByUsername("joao")).thenReturn(Optional.of(usuario("u1", "joao", "ATENDENTE")));
        assertThrows(RegraNegocioException.class,
                () -> service.salvarUsuario(dados("joao", "senha123", "ATENDENTE")));
        verify(repo, never()).save(any(Usuario.class));
    }

    @Test
    void senhaCurtaEhRejeitada() {
        assertThrows(RegraNegocioException.class,
                () -> service.salvarUsuario(dados("novo.user", "123", "ATENDENTE")));
        verify(repo, never()).save(any(Usuario.class));
    }


    @Test
    void usuarioNaoPodeSeAutoelevarParaAdmin() {
        logadoComo("atendente");
        Usuario existente = usuario("u1", "atendente", "ATENDENTE");
        when(repo.findById("u1")).thenReturn(Optional.of(existente));
        when(repo.findByUsername("atendente")).thenReturn(Optional.of(existente));

        assertThrows(RegraNegocioException.class,
                () -> service.atualizarUsuario("u1", dados("atendente", null, "ADMIN")));
        verify(repo, never()).save(any(Usuario.class));
    }

    @Test
    void adminNaoAlteraOProprioPerfil() {
        logadoComo("admin");
        Usuario existente = usuario("u1", "admin", "ADMIN");
        when(repo.findById("u1")).thenReturn(Optional.of(existente));
        when(repo.findByUsername("admin")).thenReturn(Optional.of(existente));

        assertThrows(RegraNegocioException.class,
                () -> service.atualizarUsuario("u1", dados("admin", null, "GERENTE")));
        verify(repo, never()).save(any(Usuario.class));
    }

    @Test
    void adminNaoAlteraOProprioUsername() {
        logadoComo("admin");
        when(repo.findById("u1")).thenReturn(Optional.of(usuario("u1", "admin", "ADMIN")));

        assertThrows(RegraNegocioException.class,
                () -> service.atualizarUsuario("u1", dados("admin2", null, "ADMIN")));
        verify(repo, never()).save(any(Usuario.class));
    }

    @Test
    void adminAlteraPerfilDeOutroUsuarioSemMexerNaSenha() {
        logadoComo("admin");
        Usuario existente = usuario("u2", "joao", "ATENDENTE");
        when(repo.findById("u2")).thenReturn(Optional.of(existente));
        when(repo.findByUsername("joao")).thenReturn(Optional.of(existente));
        when(repo.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        Usuario atualizado = service.atualizarUsuario("u2", dados("joao", null, "gerente"));

        assertEquals("GERENTE", atualizado.getRole());
        assertEquals("HASH_ANTIGO", atualizado.getPassword());
        verifyNoInteractions(encoder);
    }


    @Test
    void usuarioNaoExcluiAPropriaConta() {
        logadoComo("admin");
        when(repo.findById("u1")).thenReturn(Optional.of(usuario("u1", "admin", "ADMIN")));

        assertThrows(RegraNegocioException.class, () -> service.deletarUsuario("u1"));
        verify(repo, never()).deleteById(anyString());
    }

    @Test
    void adminExcluiOutroUsuario() {
        logadoComo("admin");
        when(repo.findById("u2")).thenReturn(Optional.of(usuario("u2", "joao", "ATENDENTE")));

        service.deletarUsuario("u2");

        verify(repo).deleteById("u2");
    }
}