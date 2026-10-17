package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock MongoTemplate mongoTemplate;
    @Mock AuditoriaRepository repository;

    private Map<String, String> mapa(String... pares) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put(pares[i], pares[i + 1]);
        }
        return m;
    }

    @Test
    void mascaraTelefoneMantendoDddEUltimosQuatroDigitos() {
        assertEquals("(11) *****-1234", AuditoriaService.mascararTelefone("(11) 99999-1234"));
        assertEquals("(11) ****-1234", AuditoriaService.mascararTelefone("1188881234"));
        assertNull(AuditoriaService.mascararTelefone(null));
    }

    @Test
    void gravaSomenteOsCamposAlterados() {
        AuditoriaService service = new AuditoriaService(mongoTemplate, repository);
        service.registrar("ALTERACAO_CLIENTE", "CLIENTE", "c1",
                mapa("logradouro", "Rua das Flores", "cidade", "Sao Paulo"),
                mapa("logradouro", "Av. Brasil", "cidade", "Sao Paulo"),
                null);

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(mongoTemplate).insert(captor.capture());
        Auditoria a = captor.getValue();

        assertEquals("ALTERACAO_CLIENTE", a.operacao());
        assertEquals("CLIENTE", a.entidade());
        assertEquals("c1", a.entidadeId());
        assertNotNull(a.dataHora());
        assertNotNull(a.usuario());
        assertEquals(1, a.alteracoes().size());
        assertEquals("logradouro", a.alteracoes().get(0).campo());
        assertEquals("Rua das Flores", a.alteracoes().get(0).valorAnterior());
        assertEquals("Av. Brasil", a.alteracoes().get(0).valorNovo());
    }

    @Test
    void naoGravaQuandoNadaMudou() {
        AuditoriaService service = new AuditoriaService(mongoTemplate, repository);
        service.registrar("ALTERACAO_CLIENTE", "CLIENTE", "c1",
                mapa("cidade", "Sao Paulo"), mapa("cidade", "Sao Paulo"), null);
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void telefoneAlteradoEhGravadoMascarado() {
        AuditoriaService service = new AuditoriaService(mongoTemplate, repository);
        service.registrar("ALTERACAO_CLIENTE", "CLIENTE", "c1",
                mapa("telefone", "11999991234"), mapa("telefone", "11888881234"),
                null, Set.of("telefone"));

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(mongoTemplate).insert(captor.capture());
        Auditoria.Alteracao alt = captor.getValue().alteracoes().get(0);
        assertEquals("(11) *****-1234", alt.valorAnterior());
        assertEquals("(11) *****-1234 (alterado)", alt.valorNovo());
        assertFalse(alt.valorNovo().contains("8888"));
    }

    @Test
    void guardaJustificativa() {
        AuditoriaService service = new AuditoriaService(mongoTemplate, repository);
        service.registrar("CANCELAMENTO_PEDIDO", "PEDIDO", "p1",
                mapa("status", "RECEBIDO"), mapa("status", "CANCELADO"), "cliente desistiu");

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(mongoTemplate).insert(captor.capture());
        assertEquals("cliente desistiu", captor.getValue().justificativa());
    }
}
