package com.umc.pediupatrao.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardResumo(LocalDate inicio, LocalDate fim, long totalPedidos, long pedidosValidos,
                                  long pedidosCancelados, BigDecimal faturamento, BigDecimal ticketMedio,
                                  BigDecimal totalDescontos, long itensVendidos) {
    }

    public record VendaPeriodo(String periodo, long quantidade, BigDecimal total) {
    }

    public record StatusQuantidade(String status, long quantidade) {
    }

    public record ProdutoRanking(String produtoId, String nome, long quantidade, BigDecimal faturamento) {
    }

    public record TipoEntregaVenda(String tipoEntrega, long quantidade, BigDecimal total) {
    }
}
