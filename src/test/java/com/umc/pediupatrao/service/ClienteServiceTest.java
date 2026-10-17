package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock ClienteRepository repo;
    @Mock AuditoriaService auditoria;
    @Captor ArgumentCaptor<Map<String, String>> antes;
    @Captor ArgumentCaptor<Map<String, String>> depois;

    ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(repo, auditoria);
    }

    private Cliente cliente(String logradouro) {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Ana");
        c.setTelefone("(11) 99999-1234");
        c.setLogradouro(logradouro);
        c.setCidade("Sao Paulo");
        return c;
    }

    @Test
    void alteracaoDeEnderecoEhAuditadaComValorAnteriorENovo() {
        when(repo.findById("c1")).thenReturn(Optional.of(cliente("Rua das Flores")));
        when(repo.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        service.salvar(cliente("Av. Brasil"));

        verify(auditoria).registrar(eq("ALTERACAO_CLIENTE"), eq("CLIENTE"), eq("c1"),
                antes.capture(), depois.capture(), isNull(), anySet());
        assertEquals("Rua das Flores", antes.getValue().get("logradouro"));
        assertEquals("Av. Brasil", depois.getValue().get("logradouro"));
    }

    @Test
    void inclusaoEhAuditada() {
        when(repo.save(any(Cliente.class))).thenAnswer(i -> {
            Cliente c = i.getArgument(0);
            c.setId("novo1");
            return c;
        });
        Cliente novo = cliente("Rua A");
        novo.setId(null);

        service.salvar(novo);

        verify(auditoria).registrar(eq("INCLUSAO_CLIENTE"), eq("CLIENTE"), eq("novo1"),
                any(), any(), isNull(), anySet());
    }

    @Test
    void exclusaoEhAuditada() {
        when(repo.findById("c1")).thenReturn(Optional.of(cliente("Rua A")));

        service.excluir("c1");

        verify(auditoria).registrar(eq("EXCLUSAO_CLIENTE"), eq("CLIENTE"), eq("c1"),
                any(), any(), isNull(), anySet());
        verify(repo).deleteById("c1");
    }

    @Test
    void clienteSemNomeEhRejeitado() {
        Cliente c = cliente("Rua A");
        c.setNome(" ");
        assertThrows(RegraNegocioException.class, () -> service.salvar(c));
        verifyNoInteractions(auditoria);
    }

    @Test
    void atualizarClienteInexistenteEhRejeitado() {
        when(repo.findById("c1")).thenReturn(Optional.empty());
        assertThrows(RegraNegocioException.class, () -> service.salvar(cliente("Rua A")));
    }
}
