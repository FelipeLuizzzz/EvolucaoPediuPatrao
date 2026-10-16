package com.umc.pediupatrao.entity;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "auditoria")
public record Auditoria(
        @Id String id,
        String usuario,
        String perfil,
        Instant dataHora,
        String operacao,
        String entidade,
        String entidadeId,
        List<Alteracao> alteracoes,
        String justificativa) {

    public record Alteracao(String campo, String valorAnterior, String valorNovo) {
    }
}
