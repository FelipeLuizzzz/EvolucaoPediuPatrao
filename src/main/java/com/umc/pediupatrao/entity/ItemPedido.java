package com.umc.pediupatrao.entity;

import java.math.BigDecimal;

public record ItemPedido(String produtoId, String nome, BigDecimal precoUnitario,
                         int quantidade, BigDecimal subtotal) {
}
