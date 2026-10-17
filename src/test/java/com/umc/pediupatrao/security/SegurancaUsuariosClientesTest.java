package com.umc.pediupatrao.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.ClienteController;
import com.umc.pediupatrao.controller.UsuarioController;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {UsuarioController.class, ClienteController.class})
@Import(SecurityConfig.class)
class SegurancaUsuariosClientesTest {

    @Autowired MockMvc mvc;

    @MockitoBean UsuarioService usuarioService;
    @MockitoBean ClienteService clienteService;
    @MockitoBean UsuarioDetailsService usuarioDetailsService;

    private static final String USUARIO_JSON = "{\"username\":\"novo.user\",\"password\":\"senha123\",\"role\":\"ATENDENTE\"}";
    private static final String ELEVAR_JSON = "{\"username\":\"atendente\",\"role\":\"ADMIN\"}";
    private static final String CLIENTE_JSON = "{\"nome\":\"Ana\",\"telefone\":\"11999991234\"}";


    @Test
    void semLoginUsuariosRetorna401() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoListaUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteNaoListaNemCriaUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(USUARIO_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminListaUsuarios() throws Exception {
        when(usuarioService.listarTodos()).thenReturn(List.of());
        mvc.perform(get("/api/usuarios")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCriaUsuario() throws Exception {
        when(usuarioService.salvarUsuario(any())).thenReturn(new Usuario());
        mvc.perform(post("/api/usuarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(USUARIO_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoConsegueSeAutoelevarPelaApi() throws Exception {
        mvc.perform(put("/api/usuarios/u1").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(ELEVAR_JSON))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuarioService);
    }


    @Test
    void semLoginClientesRetorna401() throws Exception {
        mvc.perform(get("/api/clientes")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminNaoAcessaClientes() throws Exception {
        mvc.perform(get("/api/clientes")).andExpect(status().isForbidden());
        mvc.perform(post("/api/clientes").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CLIENTE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteConsultaClientesMasNaoCadastraNemAltera() throws Exception {
        when(clienteService.listarClientes()).thenReturn(List.of());
        mvc.perform(get("/api/clientes")).andExpect(status().isOk());
        mvc.perform(post("/api/clientes").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CLIENTE_JSON))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/clientes/c1").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CLIENTE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteExcluiCliente() throws Exception {
        mvc.perform(delete("/api/clientes/c1").with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteCadastraMasNaoExcluiCliente() throws Exception {
        when(clienteService.salvar(any())).thenReturn(new Cliente());
        mvc.perform(post("/api/clientes").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CLIENTE_JSON))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/clientes/c1").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void semTokenCsrfCadastroDeClienteEhBloqueado() throws Exception {
        mvc.perform(post("/api/clientes")
                .contentType(MediaType.APPLICATION_JSON).content(CLIENTE_JSON))
                .andExpect(status().isForbidden());
    }
}