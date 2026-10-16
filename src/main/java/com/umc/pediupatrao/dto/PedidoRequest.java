package com.umc.pediupatrao.dto;

import java.util.List;

public record PedidoRequest(String clienteId, String tipoEntrega, List<Item> itens) {
    public record Item(String produtoId, int quantidade) {
    }
}
