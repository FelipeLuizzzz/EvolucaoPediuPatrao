package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDtos.DashboardResumo;
import com.umc.pediupatrao.dto.DashboardDtos.ProdutoRanking;
import com.umc.pediupatrao.dto.DashboardDtos.StatusQuantidade;
import com.umc.pediupatrao.dto.DashboardDtos.TipoEntregaVenda;
import com.umc.pediupatrao.dto.DashboardDtos.VendaPeriodo;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final int PERIODO_PADRAO_DIAS = 30;
    private static final int PERIODO_MAXIMO_DIAS = 366;
    private static final int LIMITE_PADRAO = 5;
    private static final int LIMITE_MAXIMO = 50;
    private static final List<String> TIPOS = List.of("ENTREGA", "RETIRADA");

    private final PedidoRepository pedidoRepository;

    public DashboardService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    private record Consulta(LocalDate inicio, LocalDate fim, List<Pedido> pedidos) {
        List<Pedido> validos() {
            return pedidos.stream()
                    .filter(p -> !StatusPedido.CANCELADO.name().equals(p.getStatus()))
                    .toList();
        }
    }

    public DashboardResumo resumo(LocalDate inicio, LocalDate fim, String tipoEntrega, String status) {
        Consulta c = consultar(inicio, fim, tipoEntrega, status);
        List<Pedido> validos = c.validos();
        BigDecimal faturamento = validos.stream().map(p -> valor(p.getTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descontos = validos.stream().map(p -> valor(p.getValorDesconto())).reduce(BigDecimal.ZERO, BigDecimal::add);
        long itens = validos.stream().flatMap(p -> itensDe(p).stream()).mapToLong(ItemPedido::quantidade).sum();
        BigDecimal ticket = validos.isEmpty()
                ? BigDecimal.ZERO
                : faturamento.divide(BigDecimal.valueOf(validos.size()), 2, RoundingMode.HALF_UP);
        return new DashboardResumo(c.inicio(), c.fim(), c.pedidos().size(), validos.size(),
                c.pedidos().size() - validos.size(), dinheiro(faturamento), dinheiro(ticket), dinheiro(descontos), itens);
    }

    public List<VendaPeriodo> vendasPorPeriodo(LocalDate inicio, LocalDate fim, String tipoEntrega, String status,
                                               String agrupamento) {
        boolean porMes = agruparPorMes(agrupamento);
        Consulta c = consultar(inicio, fim, tipoEntrega, status);
        Map<String, Long> quantidades = new LinkedHashMap<>();
        Map<String, BigDecimal> totais = new LinkedHashMap<>();
        if (porMes) {
            for (YearMonth m = YearMonth.from(c.inicio()); !m.isAfter(YearMonth.from(c.fim())); m = m.plusMonths(1)) {
                quantidades.put(m.toString(), 0L);
                totais.put(m.toString(), BigDecimal.ZERO);
            }
        } else {
            for (LocalDate d = c.inicio(); !d.isAfter(c.fim()); d = d.plusDays(1)) {
                quantidades.put(d.toString(), 0L);
                totais.put(d.toString(), BigDecimal.ZERO);
            }
        }
        for (Pedido p : c.validos()) {
            if (p.getDataEntrada() == null) continue;
            LocalDate dia = p.getDataEntrada().atZone(ZONA).toLocalDate();
            String chave = porMes ? YearMonth.from(dia).toString() : dia.toString();
            quantidades.merge(chave, 1L, Long::sum);
            totais.merge(chave, valor(p.getTotal()), BigDecimal::add);
        }
        return quantidades.keySet().stream()
                .map(k -> new VendaPeriodo(k, quantidades.get(k), dinheiro(totais.get(k))))
                .toList();
    }

    public List<StatusQuantidade> pedidosPorStatus(LocalDate inicio, LocalDate fim, String tipoEntrega) {
        Consulta c = consultar(inicio, fim, tipoEntrega, null);
        Map<StatusPedido, Long> contagem = new EnumMap<>(StatusPedido.class);
        for (StatusPedido s : StatusPedido.values()) {
            contagem.put(s, 0L);
        }
        for (Pedido p : c.pedidos()) {
            StatusPedido s = StatusPedido.de(p.getStatus());
            if (s != null) contagem.merge(s, 1L, Long::sum);
        }
        return contagem.entrySet().stream()
                .map(e -> new StatusQuantidade(e.getKey().name(), e.getValue()))
                .toList();
    }

    public List<ProdutoRanking> produtosMaisVendidos(LocalDate inicio, LocalDate fim, String tipoEntrega,
                                                     String status, Integer limite) {
        int max = limite == null ? LIMITE_PADRAO : limite;
        if (max < 1 || max > LIMITE_MAXIMO) {
            throw new RegraNegocioException("O limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        Consulta c = consultar(inicio, fim, tipoEntrega, status);
        Map<String, String> nomes = new LinkedHashMap<>();
        Map<String, Long> quantidades = new LinkedHashMap<>();
        Map<String, BigDecimal> faturamentos = new LinkedHashMap<>();
        for (Pedido p : c.validos()) {
            for (ItemPedido i : itensDe(p)) {
                String chave = i.produtoId() != null ? i.produtoId() : i.nome();
                nomes.putIfAbsent(chave, i.nome());
                quantidades.merge(chave, (long) i.quantidade(), Long::sum);
                faturamentos.merge(chave, valor(i.subtotal()), BigDecimal::add);
            }
        }
        return quantidades.keySet().stream()
                .map(k -> new ProdutoRanking(k, nomes.get(k), quantidades.get(k), dinheiro(faturamentos.get(k))))
                .sorted(Comparator.comparingLong(ProdutoRanking::quantidade).reversed()
                        .thenComparing(ProdutoRanking::faturamento, Comparator.reverseOrder()))
                .limit(max)
                .toList();
    }

    public List<TipoEntregaVenda> vendasPorTipoEntrega(LocalDate inicio, LocalDate fim, String status) {
        Consulta c = consultar(inicio, fim, null, status);
        Map<String, Long> quantidades = new LinkedHashMap<>();
        Map<String, BigDecimal> totais = new LinkedHashMap<>();
        for (String tipo : TIPOS) {
            quantidades.put(tipo, 0L);
            totais.put(tipo, BigDecimal.ZERO);
        }
        for (Pedido p : c.validos()) {
            if (!quantidades.containsKey(p.getTipoEntrega())) continue;
            quantidades.merge(p.getTipoEntrega(), 1L, Long::sum);
            totais.merge(p.getTipoEntrega(), valor(p.getTotal()), BigDecimal::add);
        }
        return quantidades.keySet().stream()
                .map(k -> new TipoEntregaVenda(k, quantidades.get(k), dinheiro(totais.get(k))))
                .toList();
    }

    private Consulta consultar(LocalDate inicio, LocalDate fim, String tipoEntrega, String status) {
        LocalDate dataFim = fim != null ? fim : LocalDate.now(ZONA);
        LocalDate dataInicio = inicio != null ? inicio : dataFim.minusDays(PERIODO_PADRAO_DIAS - 1);
        if (dataInicio.isAfter(dataFim)) {
            throw new RegraNegocioException("A data inicial não pode ser posterior à data final.");
        }
        if (ChronoUnit.DAYS.between(dataInicio, dataFim) + 1 > PERIODO_MAXIMO_DIAS) {
            throw new RegraNegocioException("O período máximo de consulta é de " + PERIODO_MAXIMO_DIAS + " dias.");
        }
        String tipo = normalizarTipo(tipoEntrega);
        StatusPedido statusFiltro = normalizarStatus(status);
        Instant de = dataInicio.atStartOfDay(ZONA).toInstant();
        Instant ate = dataFim.plusDays(1).atStartOfDay(ZONA).toInstant();
        List<Pedido> pedidos = pedidoRepository.buscarPorPeriodo(de, ate).stream()
                .filter(p -> tipo == null || tipo.equals(p.getTipoEntrega()))
                .filter(p -> statusFiltro == null || statusFiltro.name().equals(p.getStatus()))
                .toList();
        return new Consulta(dataInicio, dataFim, pedidos);
    }

    private String normalizarTipo(String tipoEntrega) {
        if (tipoEntrega == null || tipoEntrega.isBlank()) return null;
        String tipo = tipoEntrega.trim().toUpperCase();
        if (!TIPOS.contains(tipo)) {
            throw new RegraNegocioException("Tipo de entrega inválido. Use ENTREGA ou RETIRADA.");
        }
        return tipo;
    }

    private StatusPedido normalizarStatus(String status) {
        if (status == null || status.isBlank()) return null;
        StatusPedido s = StatusPedido.de(status);
        if (s == null) {
            String validos = Arrays.stream(StatusPedido.values()).map(Enum::name).collect(Collectors.joining(", "));
            throw new RegraNegocioException("Status inválido. Use: " + validos + ".");
        }
        return s;
    }

    private boolean agruparPorMes(String agrupamento) {
        if (agrupamento == null || agrupamento.isBlank() || agrupamento.trim().equalsIgnoreCase("DIA")) return false;
        if (agrupamento.trim().equalsIgnoreCase("MES")) return true;
        throw new RegraNegocioException("Agrupamento inválido. Use DIA ou MES.");
    }

    private List<ItemPedido> itensDe(Pedido p) {
        return p.getItens() == null ? List.of() : p.getItens();
    }

    private BigDecimal valor(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal dinheiro(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
