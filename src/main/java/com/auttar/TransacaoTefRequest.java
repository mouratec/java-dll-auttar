package com.auttar;

import com.auttar.CtfCatalogo.CodigoOperacao;
import java.math.BigDecimal;

/**
 * POJO com os parâmetros de entrada para iniciar uma transação TEF no CTFClient.
 */
public class TransacaoTefRequest {

    private CodigoOperacao operacao;
    private BigDecimal valor = BigDecimal.ZERO;
    private String documentoFiscal = "";

    private String nsuOriginal;
    private String dataOriginal;

    private String numeroCartao;
    private String vencimentoCartao;

    private Integer numeroParcelas;

    private String identificadorPix;
    private String pspPix;

    private String loginAutenticacao;
    private String senhaAutenticacao;
    private String cnpjAutenticacao;

    public TransacaoTefRequest() {
    }

    public TransacaoTefRequest(CodigoOperacao operacao, BigDecimal valor, String documentoFiscal) {
        this.operacao = operacao;
        this.valor = valor != null ? valor : BigDecimal.ZERO;
        this.documentoFiscal = documentoFiscal != null ? documentoFiscal : "";
    }

    public CodigoOperacao getOperacao() {
        return operacao;
    }

    public void setOperacao(CodigoOperacao operacao) {
        this.operacao = operacao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getDocumentoFiscal() {
        return documentoFiscal;
    }

    public void setDocumentoFiscal(String documentoFiscal) {
        this.documentoFiscal = documentoFiscal;
    }

    public String getNsuOriginal() {
        return nsuOriginal;
    }

    public void setNsuOriginal(String nsuOriginal) {
        this.nsuOriginal = nsuOriginal;
    }

    public String getDataOriginal() {
        return dataOriginal;
    }

    public void setDataOriginal(String dataOriginal) {
        this.dataOriginal = dataOriginal;
    }

    public String getNumeroCartao() {
        return numeroCartao;
    }

    public void setNumeroCartao(String numeroCartao) {
        this.numeroCartao = numeroCartao;
    }

    public String getVencimentoCartao() {
        return vencimentoCartao;
    }

    public void setVencimentoCartao(String vencimentoCartao) {
        this.vencimentoCartao = vencimentoCartao;
    }

    public Integer getNumeroParcelas() {
        return numeroParcelas;
    }

    public void setNumeroParcelas(Integer numeroParcelas) {
        this.numeroParcelas = numeroParcelas;
    }

    public String getIdentificadorPix() {
        return identificadorPix;
    }

    public void setIdentificadorPix(String identificadorPix) {
        this.identificadorPix = identificadorPix;
    }

    public String getPspPix() {
        return pspPix;
    }

    public void setPspPix(String pspPix) {
        this.pspPix = pspPix;
    }

    public String getLoginAutenticacao() {
        return loginAutenticacao;
    }

    public void setLoginAutenticacao(String loginAutenticacao) {
        this.loginAutenticacao = loginAutenticacao;
    }

    public String getSenhaAutenticacao() {
        return senhaAutenticacao;
    }

    public void setSenhaAutenticacao(String senhaAutenticacao) {
        this.senhaAutenticacao = senhaAutenticacao;
    }

    public String getCnpjAutenticacao() {
        return cnpjAutenticacao;
    }

    public void setCnpjAutenticacao(String cnpjAutenticacao) {
        this.cnpjAutenticacao = cnpjAutenticacao;
    }
}
