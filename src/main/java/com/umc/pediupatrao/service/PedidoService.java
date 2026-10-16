package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.MovimentoStatus;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.util.ContextoUsuario;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {

    private static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20.00");
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int QUANTIDADE_MAXIMA = 100;

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoria;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
                         ProdutoRepository produtoRepository, AuditoriaService auditoria) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoria = auditoria;
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll(Sort.by(Sort.Direction.DESC, "dataEntrada"));
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido criarPedido(PedidoRequest req) {
        if (req == null) {
            throw new RegraNegocioException("Dados do pedido não informados.");
        }
        if (req.clienteId() == null || req.clienteId().isBlank()) {
            throw new RegraNegocioException("Informe o cliente do pedido.");
        }
        Cliente cliente = clienteRepository.findById(req.clienteId())
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado."));

        String tipo = req.tipoEntrega() == null ? "" : req.tipoEntrega().trim().toUpperCase();
        if (!tipo.equals("ENTREGA") && !tipo.equals("RETIRADA")) {
            throw new RegraNegocioException("Tipo de entrega inválido. Use ENTREGA ou RETIRADA.");
        }
        if (req.itens() == null || req.itens().isEmpty()) {
            throw new RegraNegocioException("O pedido deve ter pelo menos um item.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (PedidoRequest.Item it : req.itens()) {
            if (it == null || it.produtoId() == null || it.produtoId().isBlank()) {
                throw new RegraNegocioException("Item sem produto informado.");
            }
            if (it.quantidade() < 1 || it.quantidade() > QUANTIDADE_MAXIMA) {
                throw new RegraNegocioException("A quantidade de cada item deve ficar entre 1 e " + QUANTIDADE_MAXIMA + ".");
            }
            Produto produto = produtoRepository.findById(it.produtoId())
                    .orElseThrow(() -> new RegraNegocioException("Produto não encontrado."));
            if (Boolean.FALSE.equals(produto.getAtivo())) {
                throw new RegraNegocioException("O produto '" + produto.getNome() + "' está inativo.");
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("O produto '" + produto.getNome() + "' está sem preço válido.");
            }
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal sub = preco.multiply(BigDecimal.valueOf(it.quantidade()));
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), preco, it.quantidade(), sub));
            subtotal = subtotal.add(sub);
        }

        Instant agora = Instant.now();
        String usuario = ContextoUsuario.nome();

        Pedido p = new Pedido();
        p.setClienteId(cliente.getId());
        p.setClienteNome(cliente.getNome());
        p.setTipoEntrega(tipo);
        p.setItens(itens);
        p.setSubtotal(subtotal);
        p.setDescontoPercentual(BigDecimal.ZERO.setScale(2));
        p.setValorDesconto(BigDecimal.ZERO.setScale(2));
        p.setTotal(subtotal);
        p.setStatus(StatusPedido.RECEBIDO.name());
        p.setCriadoPor(usuario);
        p.setCriadoPorPerfil(ContextoUsuario.perfil());
        p.setDataEntrada(agora);
        p.setHistorico(new ArrayList<>(List.of(new MovimentoStatus(StatusPedido.RECEBIDO.name(), agora, usuario))));

        Pedido salvo = pedidoRepository.save(p);
        auditoria.registrar("INCLUSAO_PEDIDO", "PEDIDO", salvo.getId(), Map.of(), mapa(salvo), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido atualizarStatus(String id, String status) {
        Pedido p = buscar(id);
        StatusPedido atual = statusAtual(p);
        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode ser alterado.");
        }
        StatusPedido novo = StatusPedido.de(status);
        if (novo == null) {
            throw new RegraNegocioException("Status inválido.");
        }
        if (novo == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Para cancelar use a operação de cancelamento, que exige justificativa.");
        }
        if (!atual.podeIrPara(novo)) {
            throw new RegraNegocioException("Transição inválida: " + atual + " -> " + novo + ".");
        }
        if (novo == StatusPedido.SAIU_PARA_ENTREGA && !"ENTREGA".equals(p.getTipoEntrega())) {
            throw new RegraNegocioException("Este pedido é para retirada; use o status RETIRADO.");
        }
        if (novo == StatusPedido.RETIRADO && !"RETIRADA".equals(p.getTipoEntrega())) {
            throw new RegraNegocioException("Este pedido é para entrega; use o status SAIU_PARA_ENTREGA.");
        }

        Map<String, String> antes = mapa(p);
        Instant agora = Instant.now();
        String usuario = ContextoUsuario.nome();

        if (novo == StatusPedido.SAIU_PARA_ENTREGA || novo == StatusPedido.RETIRADO) {
            p.setSaidaEm(agora);
            p.setSaidaPor(usuario);
        }
        if (novo == StatusPedido.FINALIZADO) {
            p.setFinalizadoEm(agora);
            p.setFinalizadoPor(usuario);
        }
        p.setStatus(novo.name());
        registrarMovimento(p, novo, agora, usuario);

        Pedido salvo = pedidoRepository.save(p);
        auditoria.registrar("ALTERACAO_STATUS_PEDIDO", "PEDIDO", salvo.getId(), antes, mapa(salvo), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual) {
        if (percentual == null || percentual.signum() <= 0) {
            throw new RegraNegocioException("O desconto deve ser um percentual maior que zero.");
        }
        BigDecimal pct = percentual.setScale(2, RoundingMode.HALF_UP);
        if (pct.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new RegraNegocioException("O desconto máximo permitido é de 20%.");
        }

        Pedido p = buscar(id);
        StatusPedido atual = statusAtual(p);
        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode ser alterado.");
        }
        if (!atual.antesDaSaida()) {
            throw new RegraNegocioException("O desconto só pode ser aplicado antes da saída para entrega ou retirada.");
        }
        if (p.getSubtotal() == null) {
            throw new RegraNegocioException("Pedido sem itens não pode receber desconto.");
        }

        Map<String, String> antes = mapa(p);
        BigDecimal valorDesconto = p.getSubtotal().multiply(pct).divide(CEM, 2, RoundingMode.HALF_UP);
        p.setDescontoPercentual(pct);
        p.setValorDesconto(valorDesconto);
        p.setTotal(p.getSubtotal().subtract(valorDesconto)); // total sempre calculado no servidor
        p.setDescontoAplicadoPor(ContextoUsuario.nome());
        p.setDescontoAplicadoEm(Instant.now());

        Pedido salvo = pedidoRepository.save(p);
        auditoria.registrar("DESCONTO_PEDIDO", "PEDIDO", salvo.getId(), antes, mapa(salvo), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        if (justificativa == null || justificativa.trim().length() < 5) {
            throw new RegraNegocioException("Informe a justificativa do cancelamento (mínimo de 5 caracteres).");
        }
        Pedido p = buscar(id);
        StatusPedido atual = statusAtual(p);
        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido já está cancelado.");
        }
        if (!atual.antesDaSaida()) {
            throw new RegraNegocioException("O pedido não pode ser cancelado depois da saída para entrega ou retirada.");
        }

        Map<String, String> antes = mapa(p);
        Instant agora = Instant.now();
        String usuario = ContextoUsuario.nome();
        p.setStatus(StatusPedido.CANCELADO.name());
        p.setCanceladoEm(agora);
        p.setCanceladoPor(usuario);
        p.setJustificativaCancelamento(justificativa.trim());
        registrarMovimento(p, StatusPedido.CANCELADO, agora, usuario);

        Pedido salvo = pedidoRepository.save(p); // dados históricos preservados
        auditoria.registrar("CANCELAMENTO_PEDIDO", "PEDIDO", salvo.getId(), antes, mapa(salvo), justificativa.trim());
        return salvo;
    }

    private Pedido buscar(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Pedido não encontrado."));
    }

    private StatusPedido statusAtual(Pedido p) {
        StatusPedido s = StatusPedido.de(p.getStatus());
        if (s == null) {
            throw new RegraNegocioException("Pedido com status antigo ('" + p.getStatus() + "') não pode ser alterado.");
        }
        return s;
    }

    private void registrarMovimento(Pedido p, StatusPedido status, Instant agora, String usuario) {
        List<MovimentoStatus> h = p.getHistorico() == null ? new ArrayList<>() : new ArrayList<>(p.getHistorico());
        h.add(new MovimentoStatus(status.name(), agora, usuario));
        p.setHistorico(h);
    }

    private Map<String, String> mapa(Pedido p) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clienteId", p.getClienteId());
        m.put("tipoEntrega", p.getTipoEntrega());
        m.put("status", p.getStatus());
        m.put("itens", resumirItens(p.getItens()));
        m.put("subtotal", texto(p.getSubtotal()));
        m.put("descontoPercentual", texto(p.getDescontoPercentual()));
        m.put("valorDesconto", texto(p.getValorDesconto()));
        m.put("total", texto(p.getTotal()));
        m.put("saidaEm", p.getSaidaEm() == null ? null : p.getSaidaEm().toString());
        m.put("saidaPor", p.getSaidaPor());
        m.put("finalizadoPor", p.getFinalizadoPor());
        m.put("canceladoPor", p.getCanceladoPor());
        m.put("justificativaCancelamento", p.getJustificativaCancelamento());
        return m;
    }

    private String resumirItens(List<ItemPedido> itens) {
        if (itens == null || itens.isEmpty()) return null;
        return itens.stream()
                .map(i -> i.quantidade() + "x " + i.nome() + " (" + i.precoUnitario().toPlainString() + ")")
                .collect(Collectors.joining("; "));
    }

    private String texto(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
