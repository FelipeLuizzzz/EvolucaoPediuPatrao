package com.umc.pediupatrao.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.AuditoriaController;
import com.umc.pediupatrao.controller.PedidoController;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


@WebMvcTest(controllers = {PedidoController.class, AuditoriaController.class})
@Import(SecurityConfig.class)
class SegurancaApiTest {

    @Autowired MockMvc mvc;

    @MockitoBean PedidoService pedidoService;
    @MockitoBean AuditoriaService auditoriaService;
    @MockitoBean UsuarioDetailsService usuarioDetailsService;

    private static final String DESCONTO = "{\"percentual\":10}";
    private static final String CANCELAMENTO = "{\"justificativa\":\"cliente desistiu\"}";
    private static final String NOVO_PEDIDO =
            "{\"clienteId\":\"c1\",\"tipoEntrega\":\"ENTREGA\",\"itens\":[{\"produtoId\":\"p1\",\"quantidade\":1}]}";


    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoAplicaDesconto() throws Exception {
        mvc.perform(post("/api/pedidos/p1/desconto").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminNaoAplicaDesconto() throws Exception {
        mvc.perform(post("/api/pedidos/p1/desconto").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteAplicaDesconto() throws Exception {
        when(pedidoService.aplicarDesconto(eq("p1"), any())).thenReturn(new Pedido());
        mvc.perform(post("/api/pedidos/p1/desconto").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isOk());
    }


    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoCancelaPedido() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelamento").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminNaoCancelaPedido() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelamento").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteCancelaPedido() throws Exception {
        when(pedidoService.cancelar(eq("p1"), any())).thenReturn(new Pedido());
        mvc.perform(post("/api/pedidos/p1/cancelamento").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isOk());
    }


    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteRegistraPedido() throws Exception {
        when(pedidoService.criarPedido(any())).thenReturn(new Pedido());
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(NOVO_PEDIDO))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoAlteraStatus() throws Exception {
        mvc.perform(put("/api/pedidos/p1/status").param("status", "PRONTO").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminConsultaPedidosMasNaoCria() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isOk());
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(NOVO_PEDIDO))
                .andExpect(status().isForbidden());
    }


    @Test
    void semLoginRetorna401NaApi() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void semTokenCsrfOperacaoSensivelEhBloqueada() throws Exception {
        mvc.perform(post("/api/pedidos/p1/desconto")
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden());
    }


    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoConsultaAuditoria() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteConsultaAuditoria() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminConsultaAuditoria() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void auditoriaNaoPermiteAlterarNemExcluir() throws Exception {
        mvc.perform(delete("/api/auditoria").with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/auditoria").with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/auditoria").with(csrf())).andExpect(status().isMethodNotAllowed());
    }
}
