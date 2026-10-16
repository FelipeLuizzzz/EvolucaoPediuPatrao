package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.util.ContextoUsuario;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    private final MongoTemplate mongoTemplate;
    private final AuditoriaRepository repository;

    public AuditoriaService(MongoTemplate mongoTemplate, AuditoriaRepository repository) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
    }

    public void registrar(String operacao, String entidade, String entidadeId,
                          Map<String, String> antes, Map<String, String> depois, String justificativa) {
        registrar(operacao, entidade, entidadeId, antes, depois, justificativa, Set.of());
    }

    /**
     * @param camposTelefone campos cujo valor é telefone: a mudança é detectada pelo valor real,
     *                       mas só a versão mascarada é gravada (proteção de dado pessoal).
     */
    public void registrar(String operacao, String entidade, String entidadeId,
                          Map<String, String> antes, Map<String, String> depois,
                          String justificativa, Set<String> camposTelefone) {
        List<Auditoria.Alteracao> alteracoes = diff(antes, depois, camposTelefone);
        if (alteracoes.isEmpty()) {
            return; 
        }
        Auditoria registro = new Auditoria(
                new ObjectId().toHexString(),
                ContextoUsuario.nome(),
                ContextoUsuario.perfil(),
                Instant.now(),
                operacao,
                entidade,
                entidadeId,
                alteracoes,
                justificativa);
        mongoTemplate.insert(registro);
    }

    public List<Auditoria> consultar(String entidade, String entidadeId, int limite) {
        Pageable pagina = PageRequest.of(0, Math.max(1, Math.min(limite, 500)));
        boolean temEntidade = entidade != null && !entidade.isBlank();
        boolean temId = entidadeId != null && !entidadeId.isBlank();
        if (temEntidade && temId) {
            return repository.findByEntidadeAndEntidadeIdOrderByDataHoraDesc(entidade.trim().toUpperCase(), entidadeId.trim(), pagina);
        }
        if (temEntidade) {
            return repository.findByEntidadeOrderByDataHoraDesc(entidade.trim().toUpperCase(), pagina);
        }
        return repository.findAllByOrderByDataHoraDesc(pagina);
    }

    private List<Auditoria.Alteracao> diff(Map<String, String> antes, Map<String, String> depois, Set<String> camposTelefone) {
        Set<String> campos = new LinkedHashSet<>(depois.keySet());
        campos.addAll(antes.keySet());
        List<Auditoria.Alteracao> resultado = new ArrayList<>();
        for (String campo : campos) {
            String ant = antes.get(campo);
            String novo = depois.get(campo);
            if (!Objects.equals(ant, novo)) {
                if (camposTelefone.contains(campo)) {
                    String antMasc = mascararTelefone(ant);
                    String novoMasc = mascararTelefone(novo);
                    if (antMasc != null && antMasc.equals(novoMasc)) {
                        novoMasc = novoMasc + " (alterado)"; // mascara igual: sinaliza a mudanca sem expor o numero
                    }
                    ant = antMasc;
                    novo = novoMasc;
                }
                resultado.add(new Auditoria.Alteracao(campo, ant, novo));
            }
        }
        return resultado;
    }

    static String mascararTelefone(String telefone) {
        if (telefone == null) return null;
        String d = telefone.replaceAll("\\D", "");
        if (d.length() <= 4) return "****";
        return "(" + d.substring(0, 2) + ") " + "*".repeat(Math.max(0, d.length() - 6)) + "-" + d.substring(d.length() - 4);
    }
}
