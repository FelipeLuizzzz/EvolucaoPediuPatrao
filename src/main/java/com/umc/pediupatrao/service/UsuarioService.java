package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario salvarUsuario(Usuario dados) {
        String username = validarUsername(dados.getUsername());
        if (usuarioRepository.findByUsername(username).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com este username.");
        }
        Perfil perfil = validarPerfil(dados.getRole());
        validarSenha(dados.getPassword());

        Usuario novo = new Usuario();
        novo.setUsername(username);
        novo.setPassword(passwordEncoder.encode(dados.getPassword()));
        novo.setRole(perfil.name());
        return usuarioRepository.save(novo);
    }

    public Usuario atualizarUsuario(String id, Usuario dados) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado."));

        String username = validarUsername(dados.getUsername());
        usuarioRepository.findByUsername(username)
                .filter(u -> !u.getId().equals(id))
                .ifPresent(u -> { throw new RegraNegocioException("Já existe um usuário com este username."); });
        Perfil perfil = validarPerfil(dados.getRole());

        if (existente.getUsername().equals(usuarioLogado())) {
            if (!perfil.name().equals(existente.getRole())) {
                throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
            }
            if (!username.equals(existente.getUsername())) {
                throw new RegraNegocioException("Você não pode alterar o seu próprio username.");
            }
        }

        existente.setUsername(username);
        existente.setRole(perfil.name());
        if (dados.getPassword() != null && !dados.getPassword().isBlank()) {
            validarSenha(dados.getPassword());
            existente.setPassword(passwordEncoder.encode(dados.getPassword()));
        }
        return usuarioRepository.save(existente);
    }

    public void deletarUsuario(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado."));
        if (existente.getUsername().equals(usuarioLogado())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        usuarioRepository.deleteById(id);
    }

    private String validarUsername(String username) {
        if (username == null || !username.trim().matches("[A-Za-z0-9._-]{3,50}")) {
            throw new RegraNegocioException("Username inválido: use de 3 a 50 caracteres (letras, números, ponto, hífen ou underline).");
        }
        return username.trim();
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < 6 || senha.length() > 72) {
            throw new RegraNegocioException("A senha deve ter entre 6 e 72 caracteres.");
        }
    }

    private Perfil validarPerfil(String role) {
        Perfil perfil = Perfil.de(role);
        if (perfil == null) {
            throw new RegraNegocioException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE.");
        }
        return perfil;
    }

    private String usuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}