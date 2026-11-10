package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.DashboardDtos.DashboardResumo;
import com.umc.pediupatrao.dto.DashboardDtos.ProdutoRanking;
import com.umc.pediupatrao.dto.DashboardDtos.StatusQuantidade;
import com.umc.pediupatrao.dto.DashboardDtos.TipoEntregaVenda;
import com.umc.pediupatrao.dto.DashboardDtos.VendaPeriodo;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate D1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 11, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 11, 3);

    @Mock PedidoRepository pedidoRepository;

    DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(pedidoRepository);
    }

    private Pedido pedido(LocalDate dia, String status, String tipo, String total, String desconto, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setDataEntrada(dia.atTime(12, 0).atZone(ZONA).toInstant());
        p.setStatus(status);
        p.setTipoEntrega(tipo);
        p.setTotal(new BigDecimal(total));
        p.setValorDesconto(new BigDecimal(desconto));
        p.setItens(List.of(itens));
        return p;
    }

    private ItemPedido item(String id, String nome, int qtd, String subtotal) {
        BigDecimal sub = new BigDecimal(subtotal);
        return new ItemPedido(id, nome, sub.divide(BigDecimal.valueOf(qtd)), qtd, sub);
    }

    private void repositorioRetorna(Pedido... pedidos) {
        when(pedidoRepository.buscarPorPeriodo(any(Instant.class), any(Instant.class))).thenReturn(List.of(pedidos));
    }

    private void assertValor(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual), "esperado " + esperado + " mas foi " + atual);
    }

    private void cenarioPadrao() {
        repositorioRetorna(
                pedido(D1, "FINALIZADO", "ENTREGA", "100.00", "0.00", item("p1", "Pizza Calabresa", 2, "100.00")),
                pedido(D3, "FINALIZADO", "RETIRADA", "45.00", "5.00",
                        item("p1", "Pizza Calabresa", 1, "50.00"), item("p2", "Refrigerante", 1, "12.00")),
                pedido(D3, "CANCELADO", "ENTREGA", "80.00", "0.00", item("p3", "Pizza Portuguesa", 1, "80.00")),
                pedido(D3, "RECEBIDO", "ENTREGA", "30.00", "0.00", item("p2", "Refrigerante", 3, "30.00"))
        );
    }

    @Test
    void resumoIgnoraCanceladosNoFaturamento() {
        cenarioPadrao();
        DashboardResumo r = service.resumo(D1, D3, null, null);
        assertEquals(4, r.totalPedidos());
        assertEquals(3, r.pedidosValidos());
        assertEquals(1, r.pedidosCancelados());
        assertValor("175.00", r.faturamento());
        assertValor("58.33", r.ticketMedio());
        assertValor("5.00", r.totalDescontos());
        assertEquals(7, r.itensVendidos());
    }

    @Test
    void resumoSemPedidosRetornaZeros() {
        repositorioRetorna();
        DashboardResumo r = service.resumo(D1, D3, null, null);
        assertEquals(0, r.totalPedidos());
        assertValor("0.00", r.faturamento());
        assertValor("0.00", r.ticketMedio());
    }

    @Test
    void resumoFiltraPorTipoEntrega() {
        cenarioPadrao();
        DashboardResumo r = service.resumo(D1, D3, "retirada", null);
        assertEquals(1, r.totalPedidos());
        assertValor("45.00", r.faturamento());
    }

    @Test
    void resumoFiltraPorStatus() {
        cenarioPadrao();
        DashboardResumo r = service.resumo(D1, D3, null, "cancelado");
        assertEquals(1, r.totalPedidos());
        assertEquals(1, r.pedidosCancelados());
        assertValor("0.00", r.faturamento());
    }

    @Test
    void vendasPorDiaPreencheDiasSemVenda() {
        cenarioPadrao();
        List<VendaPeriodo> lista = service.vendasPorPeriodo(D1, D3, null, null, "DIA");
        assertEquals(3, lista.size());
        assertEquals("2026-11-01", lista.get(0).periodo());
        assertValor("100.00", lista.get(0).total());
        assertEquals(0, lista.get(1).quantidade());
        assertValor("0.00", lista.get(1).total());
        assertEquals(2, lista.get(2).quantidade());
        assertValor("75.00", lista.get(2).total());
    }

    @Test
    void vendasPorMesAgrupaNoMes() {
        cenarioPadrao();
        List<VendaPeriodo> lista = service.vendasPorPeriodo(D1, D3, null, null, "MES");
        assertEquals(1, lista.size());
        assertEquals("2026-11", lista.get(0).periodo());
        assertEquals(3, lista.get(0).quantidade());
        assertValor("175.00", lista.get(0).total());
    }

    @Test
    void pedidosPorStatusRetornaTodosOsStatus() {
        cenarioPadrao();
        List<StatusQuantidade> lista = service.pedidosPorStatus(D1, D3, null);
        assertEquals(7, lista.size());
        assertEquals(2, lista.stream().filter(s -> s.status().equals("FINALIZADO")).findFirst().orElseThrow().quantidade());
        assertEquals(1, lista.stream().filter(s -> s.status().equals("CANCELADO")).findFirst().orElseThrow().quantidade());
        assertEquals(0, lista.stream().filter(s -> s.status().equals("PRONTO")).findFirst().orElseThrow().quantidade());
    }

    @Test
    void produtosMaisVendidosOrdenaPorQuantidadeEIgnoraCancelados() {
        cenarioPadrao();
        List<ProdutoRanking> ranking = service.produtosMaisVendidos(D1, D3, null, null, null);
        assertEquals(2, ranking.size());
        assertEquals("Refrigerante", ranking.get(0).nome());
        assertEquals(4, ranking.get(0).quantidade());
        assertValor("42.00", ranking.get(0).faturamento());
        assertEquals("Pizza Calabresa", ranking.get(1).nome());
        assertEquals(3, ranking.get(1).quantidade());
        assertValor("150.00", ranking.get(1).faturamento());
    }

    @Test
    void produtosMaisVendidosRespeitaLimite() {
        cenarioPadrao();
        List<ProdutoRanking> ranking = service.produtosMaisVendidos(D1, D3, null, null, 1);
        assertEquals(1, ranking.size());
        assertEquals("Refrigerante", ranking.get(0).nome());
    }

    @Test
    void vendasPorTipoEntregaSomaApenasValidos() {
        cenarioPadrao();
        List<TipoEntregaVenda> lista = service.vendasPorTipoEntrega(D1, D3, null);
        assertEquals(2, lista.size());
        TipoEntregaVenda entrega = lista.stream().filter(t -> t.tipoEntrega().equals("ENTREGA")).findFirst().orElseThrow();
        assertEquals(2, entrega.quantidade());
        assertValor("130.00", entrega.total());
    }

    @Test
    void dataInicialPosteriorALancaErro() {
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> service.resumo(D3, D1, null, null));
        assertTrue(e.getMessage().contains("data inicial"));
    }

    @Test
    void periodoMuitoLongoLancaErro() {
        assertThrows(RegraNegocioException.class, () -> service.resumo(D1.minusDays(400), D1, null, null));
    }

    @Test
    void tipoEntregaInvalidoLancaErro() {
        assertThrows(RegraNegocioException.class, () -> service.resumo(D1, D3, "VOANDO", null));
    }

    @Test
    void statusInvalidoLancaErro() {
        assertThrows(RegraNegocioException.class, () -> service.resumo(D1, D3, null, "XYZ"));
    }

    @Test
    void agrupamentoInvalidoLancaErro() {
        assertThrows(RegraNegocioException.class, () -> service.vendasPorPeriodo(D1, D3, null, null, "SEMANA"));
    }

    @Test
    void limiteInvalidoLancaErro() {
        assertThrows(RegraNegocioException.class, () -> service.produtosMaisVendidos(D1, D3, null, null, 0));
        assertThrows(RegraNegocioException.class, () -> service.produtosMaisVendidos(D1, D3, null, null, 51));
    }
}
