package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ClienteService {

    private static final Set<String> CAMPOS_TELEFONE = Set.of("telefone");

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoria;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoria) {
        this.clienteRepository = clienteRepository;
        this.auditoria = auditoria;
    }

    public Cliente novoCliente(Cliente cliente) {
        cliente.setId(null);
        return salvar(cliente);
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    public void excluir(String id) {
        clienteRepository.findById(id).ifPresent(existente -> {
            auditoria.registrar("EXCLUSAO_CLIENTE", "CLIENTE", id, mapa(existente), Map.of(), null, CAMPOS_TELEFONE);
            clienteRepository.deleteById(id);
        });
    }

    public Cliente salvar(Cliente cliente) {
        validar(cliente);

        if (cliente.getId() == null || cliente.getId().isBlank()) {
            cliente.setId(null);
            Cliente salvo = clienteRepository.save(cliente);
            auditoria.registrar("INCLUSAO_CLIENTE", "CLIENTE", salvo.getId(), Map.of(), mapa(salvo), null, CAMPOS_TELEFONE);
            return salvo;
        }

        Cliente existente = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado para atualização."));
        Map<String, String> antes = mapa(existente);

        if (cliente.getEndereco() == null) { // formulário não envia o campo legado
            cliente.setEndereco(existente.getEndereco());
        }
        Cliente salvo = clienteRepository.save(cliente);
        auditoria.registrar("ALTERACAO_CLIENTE", "CLIENTE", salvo.getId(), antes, mapa(salvo), null, CAMPOS_TELEFONE);
        return salvo;
    }

    private void validar(Cliente c) {
        if (c.getNome() == null || c.getNome().isBlank()) {
            throw new RegraNegocioException("O nome do cliente é obrigatório.");
        }
        if (c.getTelefone() == null || c.getTelefone().isBlank()) {
            throw new RegraNegocioException("O telefone do cliente é obrigatório.");
        }
    }

    private Map<String, String> mapa(Cliente c) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("nome", n(c.getNome()));
        m.put("telefone", n(c.getTelefone()));
        m.put("cep", n(c.getCep()));
        m.put("logradouro", n(c.getLogradouro()));
        m.put("numero", n(c.getNumero()));
        m.put("complemento", n(c.getComplemento()));
        m.put("bairro", n(c.getBairro()));
        m.put("cidade", n(c.getCidade()));
        m.put("estado", n(c.getEstado()));
        m.put("endereco", n(c.getEndereco()));
        return m;
    }

    private String n(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
