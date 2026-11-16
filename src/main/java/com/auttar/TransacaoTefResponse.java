package com.auttar;

import com.auttar.CtfCatalogo.CodigoOperacao;
import com.auttar.CtfCatalogo.Subcampo;
import java.util.HashMap;
import java.util.Map;

/**
 * POJO estruturando o resultado e os subcampos retornados pelo comando {@code 00}
 * de {@code continuaTransacaoCTF}.
 */
public class TransacaoTefResponse {

    private TransacaoTefRequest operacaoRequest;
    private boolean aprovada;
    private String codigoRetorno = "";
    private String codigoErro = "";
    private String mensagemRetorno = "";
    private Map<String, String> camposRetornados = new HashMap<>();

    public String getNsuCtf() {
        return camposRetornados.get(Subcampo.NsuCtf.codigo());
    }

    public String getCodigoAutorizacao() {
        String cAut = camposRetornados.get(Subcampo.CodigoAutorizacao.codigo());
        if (BufferNativoUtil.naoVazio(cAut)) {
            return cAut;
        }
        return camposRetornados.get(Subcampo.NsuRedeAdquirente.codigo());
    }

    public String getComprovanteCliente() {
        String comp = camposRetornados.get(Subcampo.CupomCliente.codigo());
        return comp != null ? comp.replace('\\', '\n') : null;
    }

    public String getComprovanteLojista() {
        String comp = camposRetornados.get(Subcampo.CupomLojista.codigo());
        return comp != null ? comp.replace('\\', '\n') : null;
    }

    public String getCupomReduzido() {
        String comp = camposRetornados.get(Subcampo.CupomReduzido.codigo());
        return comp != null ? comp.replace('\\', '\n') : null;
    }

    public String getMensagemDisplay() {
        String msg = camposRetornados.get(Subcampo.MensagemDisplay.codigo());
        return msg != null ? msg.replace('#', '\n') : null;
    }

    public String getDataTransacaoCtf() {
        return camposRetornados.get(Subcampo.DataTransacaoCtf.codigo());
    }

    public String getHoraTransacaoCtf() {
        return camposRetornados.get(Subcampo.HoraTransacaoCtf.codigo());
    }

    public String getIdentificadorPix() {
        return camposRetornados.get(Subcampo.IdentificadorPix.codigo());
    }

    public String getPspPix() {
        return camposRetornados.get(Subcampo.PspPix.codigo());
    }

    public String getRedeAdquirente() {
        return camposRetornados.get(Subcampo.NomeRedeAdquirente.codigo());
    }

    public String getNomeBandeira() {
        return camposRetornados.get(Subcampo.NomeBandeira.codigo());
    }

    public CodigoOperacao getOperacao() {
        return operacaoRequest != null ? operacaoRequest.getOperacao() : null;
    }

    public TransacaoTefRequest getOperacaoRequest() {
        return operacaoRequest;
    }

    public void setOperacaoRequest(TransacaoTefRequest operacaoRequest) {
        this.operacaoRequest = operacaoRequest;
    }

    public boolean isAprovada() {
        return aprovada;
    }

    public void setAprovada(boolean aprovada) {
        this.aprovada = aprovada;
    }

    public String getCodigoRetorno() {
        return codigoRetorno;
    }

    public void setCodigoRetorno(String codigoRetorno) {
        this.codigoRetorno = codigoRetorno;
    }

    public String getCodigoErro() {
        return codigoErro;
    }

    public void setCodigoErro(String codigoErro) {
        this.codigoErro = codigoErro;
    }

    public String getMensagemRetorno() {
        return mensagemRetorno;
    }

    public void setMensagemRetorno(String mensagemRetorno) {
        this.mensagemRetorno = mensagemRetorno;
    }

    public Map<String, String> getCamposRetornados() {
        return camposRetornados;
    }

    public void setCamposRetornados(Map<String, String> camposRetornados) {
        this.camposRetornados = camposRetornados != null ? camposRetornados : new HashMap<>();
    }
}
