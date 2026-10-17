package com.umc.pediupatrao.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StatusPedidoTest {

    @Test
    void fluxoValidoSegueAOrdemObrigatoria() {
        assertTrue(StatusPedido.RECEBIDO.podeIrPara(StatusPedido.EM_PREPARACAO));
        assertTrue(StatusPedido.EM_PREPARACAO.podeIrPara(StatusPedido.PRONTO));
        assertTrue(StatusPedido.PRONTO.podeIrPara(StatusPedido.SAIU_PARA_ENTREGA));
        assertTrue(StatusPedido.PRONTO.podeIrPara(StatusPedido.RETIRADO));
        assertTrue(StatusPedido.SAIU_PARA_ENTREGA.podeIrPara(StatusPedido.FINALIZADO));
        assertTrue(StatusPedido.RETIRADO.podeIrPara(StatusPedido.FINALIZADO));
    }

    @Test
    void naoPermitePularEtapas() {
        assertFalse(StatusPedido.RECEBIDO.podeIrPara(StatusPedido.PRONTO));
        assertFalse(StatusPedido.EM_PREPARACAO.podeIrPara(StatusPedido.FINALIZADO));
        assertFalse(StatusPedido.PRONTO.podeIrPara(StatusPedido.FINALIZADO));
    }

    @Test
    void naoVoltaEtapas() {
        assertFalse(StatusPedido.PRONTO.podeIrPara(StatusPedido.EM_PREPARACAO));
        assertFalse(StatusPedido.FINALIZADO.podeIrPara(StatusPedido.RECEBIDO));
    }

    @Test
    void estadosFinaisNaoTemProximo() {
        assertTrue(StatusPedido.CANCELADO.proximos().isEmpty());
        assertTrue(StatusPedido.FINALIZADO.proximos().isEmpty());
    }

    @Test
    void antesDaSaidaSoValeParaTresEstados() {
        assertTrue(StatusPedido.RECEBIDO.antesDaSaida());
        assertTrue(StatusPedido.EM_PREPARACAO.antesDaSaida());
        assertTrue(StatusPedido.PRONTO.antesDaSaida());
        assertFalse(StatusPedido.SAIU_PARA_ENTREGA.antesDaSaida());
        assertFalse(StatusPedido.RETIRADO.antesDaSaida());
        assertFalse(StatusPedido.CANCELADO.antesDaSaida());
    }

    @Test
    void converteTextoComEspacosEAcentos() {
        assertEquals(StatusPedido.EM_PREPARACAO, StatusPedido.de("em preparação"));
        assertEquals(StatusPedido.PRONTO, StatusPedido.de(" pronto "));
        assertNull(StatusPedido.de("EM_PREPARO"));
        assertNull(StatusPedido.de(null));
    }
}
