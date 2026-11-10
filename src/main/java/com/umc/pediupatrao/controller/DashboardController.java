package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.DashboardDtos.DashboardResumo;
import com.umc.pediupatrao.dto.DashboardDtos.ProdutoRanking;
import com.umc.pediupatrao.dto.DashboardDtos.StatusQuantidade;
import com.umc.pediupatrao.dto.DashboardDtos.TipoEntregaVenda;
import com.umc.pediupatrao.dto.DashboardDtos.VendaPeriodo;
import com.umc.pediupatrao.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public DashboardResumo resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String tipoEntrega,
            @RequestParam(required = false) String status) {
        return dashboardService.resumo(inicio, fim, tipoEntrega, status);
    }

    @GetMapping("/vendas-por-periodo")
    public List<VendaPeriodo> vendasPorPeriodo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String tipoEntrega,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String agrupamento) {
        return dashboardService.vendasPorPeriodo(inicio, fim, tipoEntrega, status, agrupamento);
    }

    @GetMapping("/pedidos-por-status")
    public List<StatusQuantidade> pedidosPorStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String tipoEntrega) {
        return dashboardService.pedidosPorStatus(inicio, fim, tipoEntrega);
    }

    @GetMapping("/produtos-mais-vendidos")
    public List<ProdutoRanking> produtosMaisVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String tipoEntrega,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.produtosMaisVendidos(inicio, fim, tipoEntrega, status, limite);
    }

    @GetMapping("/vendas-por-tipo-entrega")
    public List<TipoEntregaVenda> vendasPorTipoEntrega(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status) {
        return dashboardService.vendasPorTipoEntrega(inicio, fim, status);
    }
}
