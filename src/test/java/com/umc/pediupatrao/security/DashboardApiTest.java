package com.umc.pediupatrao.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.DashboardController;
import com.umc.pediupatrao.dto.DashboardDtos.DashboardResumo;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardController.class)
@Import(SecurityConfig.class)
class DashboardApiTest {

    @Autowired MockMvc mvc;

    @MockitoBean DashboardService dashboardService;
    @MockitoBean UsuarioDetailsService usuarioDetailsService;

    @BeforeEach
    void setUp() {
        DashboardResumo resumo = new DashboardResumo(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), 4, 3, 1,
                new BigDecimal("175.00"), new BigDecimal("58.33"), new BigDecimal("5.00"), 8);
        when(dashboardService.resumo(any(), any(), any(), any())).thenReturn(resumo);
        when(dashboardService.vendasPorPeriodo(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(dashboardService.pedidosPorStatus(any(), any(), any())).thenReturn(List.of());
        when(dashboardService.produtosMaisVendidos(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(dashboardService.vendasPorTipoEntrega(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void semLoginRetorna401() throws Exception {
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoAcessaDashboard() throws Exception {
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteConsultaResumo() throws Exception {
        mvc.perform(get("/api/dashboard/resumo?inicio=2026-11-01&fim=2026-11-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faturamento").value(175.00))
                .andExpect(jsonPath("$.pedidosValidos").value(3))
                .andExpect(jsonPath("$.pedidosCancelados").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminConsultaTodosOsEndpoints() throws Exception {
        mvc.perform(get("/api/dashboard/vendas-por-periodo")).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/pedidos-por-status")).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/produtos-mais-vendidos?limite=3")).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/vendas-por-tipo-entrega")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void dataEmFormatoInvalidoRetorna400() throws Exception {
        mvc.perform(get("/api/dashboard/resumo?inicio=01-11-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void regraDeNegocioRetorna400ComMensagem() throws Exception {
        when(dashboardService.resumo(any(), any(), any(), any()))
                .thenThrow(new RegraNegocioException("A data inicial não pode ser posterior à data final."));
        mvc.perform(get("/api/dashboard/resumo?inicio=2026-11-05&fim=2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A data inicial não pode ser posterior à data final."));
    }
}
