package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "dashboard.seed-demo", havingValue = "true")
public class DashboardDemoDataSeeder {

    private static final String[][] CARDAPIO = {
            {"Pizza Calabresa", "PIZZA", "49.90"},
            {"Pizza Mussarela", "PIZZA", "44.90"},
            {"Pizza Portuguesa", "PIZZA", "52.90"},
            {"Pizza Frango com Catupiry", "PIZZA", "54.90"},
            {"Refrigerante 2L", "BEBIDA", "12.00"},
            {"Suco Natural", "BEBIDA", "9.50"}
    };

    private static final StatusPedido[] PESOS = {
            StatusPedido.FINALIZADO, StatusPedido.FINALIZADO, StatusPedido.FINALIZADO, StatusPedido.FINALIZADO,
            StatusPedido.FINALIZADO, StatusPedido.FINALIZADO, StatusPedido.CANCELADO, StatusPedido.RECEBIDO,
            StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO
    };

    @Bean
    CommandLineRunner semearDadosDemonstracao(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository) {
        return args -> {
            if (pedidoRepository.count() > 0) return;

            List<Produto> produtos = produtoRepository.findAll();
            if (produtos.isEmpty()) {
                for (String[] linha : CARDAPIO) {
                    Produto p = new Produto();
                    p.setNome(linha[0]);
                    p.setTipo(linha[1]);
                    p.setPreco(Double.valueOf(linha[2]));
                    p.setAtivo(true);
                    produtos.add(produtoRepository.save(p));
                }
            }

            Random random = new Random(42);
            Instant agora = Instant.now();
            List<Pedido> pedidos = new ArrayList<>();
            for (int n = 1; n <= 120; n++) {
                Pedido pedido = new Pedido();
                pedido.setClienteNome("Cliente Demo " + n);
                pedido.setTipoEntrega(random.nextInt(100) < 65 ? "ENTREGA" : "RETIRADA");
                pedido.setCriadoPor("atendente");
                pedido.setCriadoPorPerfil("ATENDENTE");
                pedido.setDataEntrada(agora.minus(random.nextInt(40), ChronoUnit.DAYS)
                        .minus(random.nextInt(600), ChronoUnit.MINUTES));

                List<ItemPedido> itens = new ArrayList<>();
                BigDecimal subtotal = BigDecimal.ZERO;
                int qtdItens = 1 + random.nextInt(3);
                for (int i = 0; i < qtdItens; i++) {
                    Produto produto = produtos.get(random.nextInt(produtos.size()));
                    int quantidade = 1 + random.nextInt(3);
                    BigDecimal preco = BigDecimal.valueOf(produto.getPreco());
                    BigDecimal sub = preco.multiply(BigDecimal.valueOf(quantidade));
                    itens.add(new ItemPedido(produto.getId(), produto.getNome(), preco, quantidade, sub));
                    subtotal = subtotal.add(sub);
                }

                BigDecimal percentual = random.nextInt(100) < 15 ? BigDecimal.TEN : BigDecimal.ZERO;
                BigDecimal desconto = subtotal.multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                StatusPedido status = PESOS[random.nextInt(PESOS.length)];
                if (status == StatusPedido.SAIU_PARA_ENTREGA && !"ENTREGA".equals(pedido.getTipoEntrega())) {
                    status = StatusPedido.RETIRADO;
                }
                if (status == StatusPedido.RETIRADO && !"RETIRADA".equals(pedido.getTipoEntrega())) {
                    status = StatusPedido.SAIU_PARA_ENTREGA;
                }
                if (status == StatusPedido.CANCELADO) {
                    pedido.setCanceladoEm(pedido.getDataEntrada().plus(10, ChronoUnit.MINUTES));
                    pedido.setCanceladoPor("gerente");
                    pedido.setJustificativaCancelamento("Cancelamento de demonstração");
                }

                pedido.setItens(itens);
                pedido.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
                pedido.setDescontoPercentual(percentual);
                pedido.setValorDesconto(desconto);
                pedido.setTotal(subtotal.subtract(desconto).setScale(2, RoundingMode.HALF_UP));
                pedido.setStatus(status.name());
                pedidos.add(pedido);
            }
            pedidoRepository.saveAll(pedidos);
            System.out.println("Dados de demonstração do dashboard criados: " + pedidos.size() + " pedidos");
        };
    }
}
