package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;
    private String clienteId;
    private String clienteNome;
    private String tipoEntrega; 
    private String status;      

    private List<ItemPedido> itens = new ArrayList<>();
    private BigDecimal subtotal;
    private BigDecimal descontoPercentual;
    private BigDecimal valorDesconto;
    private BigDecimal total;

    private String criadoPor;
    private String criadoPorPerfil;
    private Instant dataEntrada;

    private String descontoAplicadoPor;
    private Instant descontoAplicadoEm;

    private Instant saidaEm;
    private String saidaPor;

    private Instant finalizadoEm;
    private String finalizadoPor;

    private Instant canceladoEm;
    private String canceladoPor;
    private String justificativaCancelamento;

    private List<MovimentoStatus> historico = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }

    public String getTipoEntrega() { return tipoEntrega; }
    public void setTipoEntrega(String tipoEntrega) { this.tipoEntrega = tipoEntrega; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDescontoPercentual() { return descontoPercentual; }
    public void setDescontoPercentual(BigDecimal descontoPercentual) { this.descontoPercentual = descontoPercentual; }

    public BigDecimal getValorDesconto() { return valorDesconto; }
    public void setValorDesconto(BigDecimal valorDesconto) { this.valorDesconto = valorDesconto; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getCriadoPor() { return criadoPor; }
    public void setCriadoPor(String criadoPor) { this.criadoPor = criadoPor; }

    public String getCriadoPorPerfil() { return criadoPorPerfil; }
    public void setCriadoPorPerfil(String criadoPorPerfil) { this.criadoPorPerfil = criadoPorPerfil; }

    public Instant getDataEntrada() { return dataEntrada; }
    public void setDataEntrada(Instant dataEntrada) { this.dataEntrada = dataEntrada; }

    public String getDescontoAplicadoPor() { return descontoAplicadoPor; }
    public void setDescontoAplicadoPor(String descontoAplicadoPor) { this.descontoAplicadoPor = descontoAplicadoPor; }

    public Instant getDescontoAplicadoEm() { return descontoAplicadoEm; }
    public void setDescontoAplicadoEm(Instant descontoAplicadoEm) { this.descontoAplicadoEm = descontoAplicadoEm; }

    public Instant getSaidaEm() { return saidaEm; }
    public void setSaidaEm(Instant saidaEm) { this.saidaEm = saidaEm; }

    public String getSaidaPor() { return saidaPor; }
    public void setSaidaPor(String saidaPor) { this.saidaPor = saidaPor; }

    public Instant getFinalizadoEm() { return finalizadoEm; }
    public void setFinalizadoEm(Instant finalizadoEm) { this.finalizadoEm = finalizadoEm; }

    public String getFinalizadoPor() { return finalizadoPor; }
    public void setFinalizadoPor(String finalizadoPor) { this.finalizadoPor = finalizadoPor; }

    public Instant getCanceladoEm() { return canceladoEm; }
    public void setCanceladoEm(Instant canceladoEm) { this.canceladoEm = canceladoEm; }

    public String getCanceladoPor() { return canceladoPor; }
    public void setCanceladoPor(String canceladoPor) { this.canceladoPor = canceladoPor; }

    public String getJustificativaCancelamento() { return justificativaCancelamento; }
    public void setJustificativaCancelamento(String justificativaCancelamento) { this.justificativaCancelamento = justificativaCancelamento; }

    public List<MovimentoStatus> getHistorico() { return historico; }
    public void setHistorico(List<MovimentoStatus> historico) { this.historico = historico; }

}
