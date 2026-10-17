package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock PedidoRepository pedidoRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock AuditoriaService auditoria;

    PedidoService service;

    @BeforeEach
    void setUp() {
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository, auditoria);
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Pedido pedido(String status, String tipo, String subtotal) {
        Pedido p = new Pedido();
        p.setId("ped1");
        p.setStatus(status);
        p.setTipoEntrega(tipo);
        p.setSubtotal(new BigDecimal(subtotal));
        p.setTotal(new BigDecimal(subtotal));
        p.setDescontoPercentual(BigDecimal.ZERO);
        p.setValorDesconto(BigDecimal.ZERO);
        p.setItens(List.of(new ItemPedido("p1", "Pizza", new BigDecimal(subtotal), 1, new BigDecimal(subtotal))));
        return p;
    }

    private void pedidoExiste(Pedido p) {
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(p));
    }

    @Test
    void criarPedidoCalculaTotalNoServidor() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Ana");
        Produto prod = new Produto();
        prod.setId("p1");
        prod.setNome("Pizza");
        prod.setPreco(40.0);
        prod.setAtivo(true);
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(c));
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(prod));

        Pedido p = service.criarPedido(new PedidoRequest("c1", "entrega",
                List.of(new PedidoRequest.Item("p1", 2))));

        assertEquals(0, new BigDecimal("80.00").compareTo(p.getTotal()));
        assertEquals("RECEBIDO", p.getStatus());
        assertEquals("ENTREGA", p.getTipoEntrega());
        assertNotNull(p.getDataEntrada());
        assertNotNull(p.getCriadoPor());
        assertEquals(1, p.getHistorico().size());
        verify(auditoria).registrar(eq("INCLUSAO_PEDIDO"), eq("PEDIDO"), any(), anyMap(), anyMap(), isNull());
    }

    @Test
    void criarPedidoRejeitaQuantidadeInvalida() {
        Cliente c = new Cliente();
        c.setId("c1");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(c));
        assertThrows(RegraNegocioException.class, () -> service.criarPedido(
                new PedidoRequest("c1", "ENTREGA", List.of(new PedidoRequest.Item("p1", 0)))));
    }

    @Test
    void criarPedidoRejeitaPedidoSemItens() {
        Cliente c = new Cliente();
        c.setId("c1");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(c));
        assertThrows(RegraNegocioException.class,
                () -> service.criarPedido(new PedidoRequest("c1", "ENTREGA", List.of())));
    }

    @Test
    void criarPedidoRejeitaProdutoInativo() {
        Cliente c = new Cliente();
        c.setId("c1");
        Produto prod = new Produto();
        prod.setId("p1");
        prod.setNome("Pizza");
        prod.setPreco(40.0);
        prod.setAtivo(false);
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(c));
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(prod));
        assertThrows(RegraNegocioException.class, () -> service.criarPedido(
                new PedidoRequest("c1", "ENTREGA", List.of(new PedidoRequest.Item("p1", 1)))));
    }


    @Test
    void transicaoValidaRegistraSaidaEResponsavel() {
        pedidoExiste(pedido("PRONTO", "ENTREGA", "100.00"));
        Pedido p = service.atualizarStatus("ped1", "SAIU_PARA_ENTREGA");
        assertEquals("SAIU_PARA_ENTREGA", p.getStatus());
        assertNotNull(p.getSaidaEm());
        assertNotNull(p.getSaidaPor());
        verify(auditoria).registrar(eq("ALTERACAO_STATUS_PEDIDO"), eq("PEDIDO"), any(), anyMap(), anyMap(), isNull());
    }

    @Test
    void naoFinalizaPedidoEmPreparacao() {
        pedidoExiste(pedido("EM_PREPARACAO", "ENTREGA", "100.00"));
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("ped1", "FINALIZADO"));
    }

    @Test
    void naoAlteraStatusDeCancelado() {
        pedidoExiste(pedido("CANCELADO", "ENTREGA", "100.00"));
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("ped1", "EM_PREPARACAO"));
    }

    @Test
    void retiradaNaoPodeSairParaEntrega() {
        pedidoExiste(pedido("PRONTO", "RETIRADA", "100.00"));
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("ped1", "SAIU_PARA_ENTREGA"));
    }

    @Test
    void cancelamentoNaoPassaPeloEndpointDeStatus() {
        pedidoExiste(pedido("RECEBIDO", "ENTREGA", "100.00"));
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("ped1", "CANCELADO"));
    }


    @Test
    void descontoValidoRecalculaTotal() {
        pedidoExiste(pedido("RECEBIDO", "ENTREGA", "200.00"));
        Pedido p = service.aplicarDesconto("ped1", new BigDecimal("10"));
        assertEquals(0, new BigDecimal("20.00").compareTo(p.getValorDesconto()));
        assertEquals(0, new BigDecimal("180.00").compareTo(p.getTotal()));
        verify(auditoria).registrar(eq("DESCONTO_PEDIDO"), eq("PEDIDO"), any(), anyMap(), anyMap(), isNull());
    }

    @Test
    void descontoAcimaDe20PorCentoEhRejeitado() {
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("ped1", new BigDecimal("20.01")));
    }

    @Test
    void descontoZeroOuNegativoEhRejeitado() {
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("ped1", BigDecimal.ZERO));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("ped1", new BigDecimal("-5")));
    }

    @Test
    void descontoDepoisDaSaidaEhRejeitado() {
        pedidoExiste(pedido("SAIU_PARA_ENTREGA", "ENTREGA", "100.00"));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("ped1", new BigDecimal("10")));
    }

    @Test
    void cancelarExigeJustificativa() {
        assertThrows(RegraNegocioException.class, () -> service.cancelar("ped1", "  "));
        assertThrows(RegraNegocioException.class, () -> service.cancelar("ped1", null));
    }

    @Test
    void cancelarDepoisDaSaidaEhRejeitado() {
        pedidoExiste(pedido("RETIRADO", "RETIRADA", "100.00"));
        assertThrows(RegraNegocioException.class, () -> service.cancelar("ped1", "cliente desistiu"));
    }

    @Test
    void cancelarGuardaJustificativaEPreservaDados() {
        pedidoExiste(pedido("EM_PREPARACAO", "ENTREGA", "100.00"));
        Pedido p = service.cancelar("ped1", "cliente desistiu");
        assertEquals("CANCELADO", p.getStatus());
        assertEquals("cliente desistiu", p.getJustificativaCancelamento());
        assertNotNull(p.getCanceladoEm());
        assertEquals(1, p.getItens().size()); // histórico preservado
        assertEquals(0, new BigDecimal("100.00").compareTo(p.getTotal()));
        verify(auditoria).registrar(eq("CANCELAMENTO_PEDIDO"), eq("PEDIDO"), any(), anyMap(), anyMap(), eq("cliente desistiu"));
    }

    @Test
    void naoCancelaDuasVezes() {
        pedidoExiste(pedido("CANCELADO", "ENTREGA", "100.00"));
        assertThrows(RegraNegocioException.class, () -> service.cancelar("ped1", "motivo qualquer"));
    }
}
