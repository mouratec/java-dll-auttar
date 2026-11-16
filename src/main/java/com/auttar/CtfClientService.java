package com.auttar;

import com.auttar.CtfCatalogo.CodigoOperacao;
import com.auttar.CtfCatalogo.CodigoRetornoBiblioteca;
import com.auttar.CtfCatalogo.CodigoRetornoTransacao;
import com.auttar.CtfCatalogo.ComandoInterativo;
import com.auttar.CtfCatalogo.Subcampo;
import com.sun.jna.Platform;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço principal de integração com a biblioteca nativa CTFClient (Facade).
 * Encapsula validações Fail-Fast, manipulação de buffers JNA, máquina de estados
 * de {@code continuaTransacaoCTF}, multitransação e auditoria estruturada.
 */
public class CtfClientService {

    /** Tamanho de buffer (99 KB) suficiente para receber comprovantes longos e payloads de QR Code/PIX. */
    public static final int TAMANHO_BUFFER_SUBCAMPO = 99000;

    /** Tempo padrão (em segundos) de espera inicial na tela "INSIRA, PASSE OU APROXIME O CARTAO". */
    public static final int DEFAULT_TIMEOUT_CARTAO_SEGUNDOS = 30;

    /** Tempo padrão (em segundos) do aviso "VOCE AINDA ESTA AI?" antes de cancelar a espera do cartão. */
    public static final int DEFAULT_AVISO_PRESENCA_SEGUNDOS = 5;

    private static final Pattern PATTERN_CONFIRMAR_DESFAZIMENTO = Pattern.compile(
            "(<Parameter\\s+name=\"confirmardesfazimento\"\\s*>)([^<]*)(</Parameter>)");

    private final Object lockTransacao = new Object();
    private final String dataCliFixa;
    private CtfClientLibrary biblioteca;
    private boolean questionamentoTransacaoPendenteHabilitado =
            !"false".equalsIgnoreCase(envOuPadrao("AUTTAR_HABILITAR_7358", "true"));
    private String politicaQuestionamentoPendencia = envOuPadrao("AUTTAR_POLITICA_7358", "DESFAZER");
    private int timeoutLeituraCartaoSegundos = BufferNativoUtil.parseIntSeguro(
            envOuPadrao("AUTTAR_TIMEOUT_CARTAO_SEGUNDOS", String.valueOf(DEFAULT_TIMEOUT_CARTAO_SEGUNDOS)),
            DEFAULT_TIMEOUT_CARTAO_SEGUNDOS);
    private int avisoPresencaCartaoSegundos = BufferNativoUtil.parseIntSeguro(
            envOuPadrao("AUTTAR_AVISO_PRESENCA_SEGUNDOS", String.valueOf(DEFAULT_AVISO_PRESENCA_SEGUNDOS)),
            DEFAULT_AVISO_PRESENCA_SEGUNDOS);

    public CtfClientService() {
        this.biblioteca = null;
        this.dataCliFixa = null;
    }

    public CtfClientService(CtfClientLibrary biblioteca, String dataCli) {
        this.biblioteca = biblioteca;
        this.dataCliFixa = BufferNativoUtil.naoVazio(dataCli) ? dataCli.trim() : null;
    }

    private CtfClientLibrary lib() {
        if (biblioteca == null) {
            biblioteca = CtfClientLibrary.getInstance();
        }
        return biblioteca;
    }

    /**
     * Retorna a data fiscal ({@code YYYYMMDD}). Quando instanciado pelo construtor padrão,
     * avalia dinamicamente a data atual do sistema a cada chamada (suporte 24/7 com virada de meia-noite).
     */
    public String getDataCli() {
        return dataCliFixa != null ? dataCliFixa : BufferNativoUtil.getDataAtual();
    }

    public boolean isQuestionamentoTransacaoPendenteHabilitado() {
        return questionamentoTransacaoPendenteHabilitado;
    }

    /**
     * Habilita ou desabilita o questionamento de transação pendente ({@code 7358})
     * sincronizando {@code <Parameter name="confirmardesfazimento">} no {@code configCTFClient.xml}
     * antes da inicialização do cliente CTF. Padrão: {@code true}.
     */
    public void setQuestionamentoTransacaoPendenteHabilitado(boolean habilitado) {
        this.questionamentoTransacaoPendenteHabilitado = habilitado;
    }

    public String getPoliticaQuestionamentoPendencia() {
        return politicaQuestionamentoPendencia;
    }

    /**
     * Define como responder ao subcampo {@code 7358} (Questionamento de transação pendente):
     * {@code "DESFAZER"} (padrão em auto-atendimento: desfaz automaticamente),
     * {@code "CONFIRMAR"} (confirma automaticamente) ou {@code "PERGUNTAR"} (delega ao operador).
     */
    public void setPoliticaQuestionamentoPendencia(String politicaQuestionamentoPendencia) {
        this.politicaQuestionamentoPendencia = BufferNativoUtil.naoVazio(politicaQuestionamentoPendencia)
                ? politicaQuestionamentoPendencia.trim().toUpperCase()
                : "DESFAZER";
    }

    public int getTimeoutLeituraCartaoSegundos() {
        return timeoutLeituraCartaoSegundos;
    }

    /**
     * Define o tempo limite (em segundos) exclusivamente para a tela de espera de leitura do cartão
     * ({@code APROXIME, INSIRA OU PASSE O CARTAO}). {@code 0} ou negativo desativa o timer.
     */
    public void setTimeoutLeituraCartaoSegundos(int timeoutLeituraCartaoSegundos) {
        this.timeoutLeituraCartaoSegundos = timeoutLeituraCartaoSegundos;
    }

    public int getAvisoPresencaCartaoSegundos() {
        return avisoPresencaCartaoSegundos;
    }

    /**
     * Define o tempo adicional (em segundos) do aviso "VOCE AINDA ESTA AI?" exibido após o
     * {@code timeoutLeituraCartaoSegundos} antes de enviar o comando {@code 08} de cancelamento.
     */
    public void setAvisoPresencaCartaoSegundos(int avisoPresencaCartaoSegundos) {
        this.avisoPresencaCartaoSegundos = Math.max(0, avisoPresencaCartaoSegundos);
    }

    /** Inicializa lendo variáveis de ambiente {@code AUTTAR_*} com fallback padrão HTTPS. */
    public boolean inicializarCliente() {
        return inicializarCliente(
                envOuPadrao("AUTTAR_TERMINAL", "000000000000"),
                envOuPadrao("AUTTAR_VERSAO", "1.0.0"),
                envOuPadrao("AUTTAR_NOME_AC", "PDV_JAVA"),
                envOuPadrao("AUTTAR_NUM_SITES", "00"),
                envOuPadrao("AUTTAR_LISTA_IPS", "HTTPS"),
                envOuPadrao("AUTTAR_CRIPTOGRAFIA", "0"),
                envOuPadrao("AUTTAR_LOG", "1"),
                envOuPadrao("AUTTAR_INTERATIVO", "1"),
                envOuPadrao("AUTTAR_PARAMETROS", "[suporte-https=1]"));
    }

    public boolean inicializarCliente(
            String terminal,
            String versao,
            String nomeAc,
            String numSites,
            String listaIps,
            String criptografia,
            String log,
            String interativo,
            String parametros) {

        long inicio = System.currentTimeMillis();
        byte[] resultado = new byte[16];
        BufferNativoUtil.escreverBuffer(resultado, "00", (byte) 0);

        String terminalFmt = BufferNativoUtil.formatarAlfanumerico(terminal, 12);
        String versaoFmt = BufferNativoUtil.formatarAlfanumerico(versao, 10);
        String nomeAcFmt = BufferNativoUtil.formatarAlfanumerico(nomeAc, 20);
        String numSitesFmt = BufferNativoUtil.formatarNumerico(numSites, 2);
        String parametrosFmt = BufferNativoUtil.naoVazio(parametros) ? parametros.trim() : "[]";

        try {
            if (biblioteca == null) {
                CtfProcessManager.encerrarProcessosOrfaosAntesDeIniciar();
                sincronizarConfigConfirmarDesfazimentoXml(questionamentoTransacaoPendenteHabilitado);
            }
            lib().iniciaClientCTF(
                    resultado,
                    terminalFmt,
                    versaoFmt,
                    nomeAcFmt,
                    numSitesFmt,
                    listaIps,
                    criptografia,
                    log,
                    interativo,
                    parametrosFmt);

            String resposta = BufferNativoUtil.extrairStringC(resultado);
            int cod = BufferNativoUtil.parseIntSeguro(resposta, -1);
            long duracaoMs = System.currentTimeMillis() - inicio;

            if (cod == CodigoRetornoBiblioteca.SUCESSO) {
                TefAuditLogger.log("init", "iniciaClientCTF OK",
                        String.format("terminal:[%s] duracaoMs:[%d]", terminalFmt.trim(), duracaoMs));
                CtfProcessManager.registrarMonitoramentoInicial();
                return true;
            }
            TefAuditLogger.log("init", "iniciaClientCTF ERRO",
                    String.format("resultado:[%s] %s duracaoMs:[%d]",
                            resposta, CodigoRetornoBiblioteca.descrever(cod), duracaoMs));
            return false;
        } catch (Throwable ex) {
            long duracaoMs = System.currentTimeMillis() - inicio;
            TefAuditLogger.log("init", "iniciaClientCTF ERRO FATAL",
                    String.format("%s: %s duracaoMs:[%d]", ex.getClass().getSimpleName(), ex.getMessage(), duracaoMs));
            return false;
        }
    }

    /** Validação Fail-Fast dos parâmetros obrigatórios antes de invocar a DLL. */
    public void validarRequisicao(TransacaoTefRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Requisição TEF não pode ser nula.");
        }
        CodigoOperacao op = request.getOperacao();
        if (op == null) {
            throw new IllegalArgumentException("Código da operação TEF é obrigatório.");
        }
        if (request.getValor() != null && request.getValor().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("O valor da transação não pode ser negativo.");
        }

        switch (op) {
            case CreditoParceladoSemJuros:
            case CreditoParceladoComJuros:
                if (request.getNumeroParcelas() == null || request.getNumeroParcelas() < 2) {
                    throw new IllegalArgumentException(
                            op.name() + " exige numeroParcelas >= 2.");
                }
                break;

            case CreditoDigitado:
                if (!BufferNativoUtil.naoVazio(request.getNumeroCartao())
                        || !BufferNativoUtil.naoVazio(request.getVencimentoCartao())) {
                    throw new IllegalArgumentException(
                            "CreditoDigitado exige numeroCartao (7006) e vencimentoCartao (7010).");
                }
                break;

            case CancelamentoGenerico:
            case ConfirmacaoPorNsu:
            case DesfazimentoPorNsu:
            case CancelamentoCreditoDigitado:
                if (!BufferNativoUtil.naoVazio(request.getNsuOriginal())
                        || !BufferNativoUtil.naoVazio(request.getDataOriginal())) {
                    throw new IllegalArgumentException(
                            op.name() + " exige nsuOriginal (7012) e dataOriginal (7161).");
                }
                break;

            case AutenticacaoTerminal:
                if (!BufferNativoUtil.naoVazio(request.getLoginAutenticacao())
                        || !BufferNativoUtil.naoVazio(request.getSenhaAutenticacao())
                        || !BufferNativoUtil.naoVazio(request.getCnpjAutenticacao())) {
                    throw new IllegalArgumentException(
                            "AutenticacaoTerminal (801) exige login (7917), senha (7918) e cnpj (7919).");
                }
                break;

            default:
                break;
        }
    }

    public int iniciarTransacao(TransacaoTefRequest request, String numTrans) {
        return iniciarTransacao(null, request, numTrans, getDataCli());
    }

    int iniciarTransacao(String txId, TransacaoTefRequest request, String numTrans) {
        return iniciarTransacao(txId, request, numTrans, getDataCli());
    }

    int iniciarTransacao(String txId, TransacaoTefRequest request, String numTrans, String dataCliAlvo) {
        validarRequisicao(request);

        byte[] resultado = new byte[16];
        String operacao = request.getOperacao().codigoFormatado();
        String valor = BufferNativoUtil.formatarValorCentavos(request.getValor());
        String numDoc = BufferNativoUtil.formatarAlfanumerico(request.getDocumentoFiscal(), 20);
        String numTransFmt = BufferNativoUtil.formatarNumerico(numTrans, 2);
        String dataCliUsada = BufferNativoUtil.naoVazio(dataCliAlvo) ? dataCliAlvo.trim() : getDataCli();
        String dados = construirDadosEstendidos(request);

        if (dados.isEmpty()) {
            TefAuditLogger.log(txId, "-> iniciaTransacaoCTF",
                    String.format("op:[%s] valor:[%s] num_doc:[%s] data_cli:[%s] num_trans:[%s]",
                            operacao, valor, numDoc.trim(), dataCliUsada, numTransFmt));
            lib().iniciaTransacaoCTF(resultado, operacao, valor, numDoc, dataCliUsada, numTransFmt);
        } else {
            TefAuditLogger.log(txId, "-> iniciaTransacaoCTFext",
                    String.format("op:[%s] valor:[%s] num_doc:[%s] data_cli:[%s] num_trans:[%s] dados:%s",
                            operacao, valor, numDoc.trim(), dataCliUsada, numTransFmt,
                            TefAuditLogger.mascararDadosEstendidos(dados)));
            lib().iniciaTransacaoCTFext(resultado, operacao, valor, numDoc, dataCliUsada, numTransFmt, dados);
        }

        String resStr = BufferNativoUtil.extrairStringC(resultado);
        int ret = BufferNativoUtil.parseIntSeguro(resStr, -1);
        if (ret != CodigoRetornoBiblioteca.SUCESSO) {
            TefAuditLogger.log(txId, "<- iniciaTransacaoCTF ERRO",
                    String.format("resultado:[%s] Falha ao iniciar transação (%d): %s",
                            resStr, ret, CodigoRetornoBiblioteca.descrever(ret)));
        } else {
            TefAuditLogger.log(txId, "<- iniciaTransacaoCTF OK", String.format("resultado:[%s]", resStr));
        }
        return ret;
    }

    public int continuarTransacao(
            String txId,
            byte[] resultado,
            byte[] comando,
            byte[] numSc,
            byte[] pSc,
            byte[] tamSc,
            byte[] aux) {

        lib().continuaTransacaoCTF(resultado, comando, numSc, pSc, tamSc, aux);

        String resStr = BufferNativoUtil.extrairStringC(resultado);
        int ret = BufferNativoUtil.parseIntSeguro(resStr, -1);

        String cmdStr = BufferNativoUtil.formatarNumerico(BufferNativoUtil.extrairStringC(comando), 2);
        String numScRaw = BufferNativoUtil.formatarNumerico(BufferNativoUtil.extrairStringC(numSc), 4);
        String numScAudit = Subcampo.formatarIdentificador(numScRaw);
        int tamScVal = BufferNativoUtil.parseIntSeguro(BufferNativoUtil.extrairStringC(tamSc), 0);
        String tamScFmt = String.format("%05d", tamScVal);
        String auxStr = BufferNativoUtil.extrairStringC(aux);
        String pScVal = BufferNativoUtil.extrairStringC(pSc, tamScVal);
        String pScLog = TefAuditLogger.mascararSubcampo(numScRaw, pScVal);

        if (ret == CodigoRetornoBiblioteca.AGUARDANDO_CONTINUACAO) {
            TefAuditLogger.log(txId, "<- continuaTransacaoCTF",
                    String.format("resultado:[%s] comando:[%s] num_sc:[%s] tam_sc:[%s] aux:[%s] p_sc:[%s]",
                            resStr, cmdStr, numScAudit, tamScFmt, auxStr, pScLog));
        } else if (ret == CodigoRetornoBiblioteca.SUCESSO) {
            TefAuditLogger.log(txId, "<- continuaTransacaoCTF OK",
                    String.format("resultado:[%s] %s", resStr, CodigoRetornoBiblioteca.descrever(ret)));
        } else {
            TefAuditLogger.log(txId, "<- continuaTransacaoCTF ERRO",
                    String.format("resultado:[%s] %s", resStr, CodigoRetornoBiblioteca.descrever(ret)));
        }

        return ret;
    }

    public int executarLoopTransacao(
            TransacaoTefRequest request,
            Map<String, String> camposRetornados,
            InteracaoTefCallback callback) {
        return executarLoopTransacao(null, request, camposRetornados, callback);
    }

    int executarLoopTransacao(
            String txId,
            TransacaoTefRequest request,
            Map<String, String> camposRetornados,
            InteracaoTefCallback callback) {

        byte[] resultado = new byte[16];
        byte[] comando = new byte[16];
        byte[] numSc = new byte[16];
        byte[] pSc = new byte[TAMANHO_BUFFER_SUBCAMPO];
        byte[] tamSc = new byte[16];
        byte[] aux = new byte[16];

        BufferNativoUtil.limparBuffers(resultado, comando, numSc, pSc, tamSc, aux);
        BufferNativoUtil.escreverBuffer(comando, "00", (byte) 0);
        BufferNativoUtil.escreverBuffer(numSc, "0000", (byte) 0);
        BufferNativoUtil.escreverBuffer(pSc, "", (byte) ' ');
        BufferNativoUtil.escreverBuffer(tamSc, "00000", (byte) 0);

        String ultimaMensagemDisplay = "";
        long[] estadoEsperaCartao = new long[] { 0L, 0L };
        int ret = CodigoRetornoBiblioteca.AGUARDANDO_CONTINUACAO;

        while (ret == CodigoRetornoBiblioteca.AGUARDANDO_CONTINUACAO) {
            BufferNativoUtil.escreverBuffer(resultado, "", (byte) 0);
            ret = continuarTransacao(txId, resultado, comando, numSc, pSc, tamSc, aux);
            if (ret != CodigoRetornoBiblioteca.AGUARDANDO_CONTINUACAO) {
                break;
            }

            ultimaMensagemDisplay = tratarComando(
                    comando, numSc, pSc, tamSc, aux,
                    camposRetornados, ultimaMensagemDisplay, estadoEsperaCartao, callback);
        }

        return ret;
    }

    private String tratarComando(
            byte[] comando,
            byte[] numSc,
            byte[] pSc,
            byte[] tamSc,
            byte[] aux,
            Map<String, String> camposRetornados,
            String ultimaMensagemDisplay,
            long[] estadoEsperaCartao,
            InteracaoTefCallback callback) {

        String comandoStr = BufferNativoUtil.extrairStringC(comando);
        int comandoAtual = BufferNativoUtil.parseIntSeguro(comandoStr, -1);
        ComandoInterativo cmd = ComandoInterativo.porCodigo(comandoAtual);

        String numScStr = BufferNativoUtil.extrairStringC(numSc);
        int tamScValor = BufferNativoUtil.parseIntSeguro(BufferNativoUtil.extrairStringC(tamSc), 0);
        String pScAtual = BufferNativoUtil.extrairStringC(pSc, tamScValor);
        String auxStr = BufferNativoUtil.extrairStringC(aux);

        String comandoResposta = BufferNativoUtil.formatarNumerico(comandoStr, 2);
        String numScResposta = "0000";
        String pScResposta = "";
        String tamScResposta = "00000";
        String auxResposta = null;

        if (cmd != null) {
            if (cmd != ComandoInterativo.VerificarCancelamento
                    && cmd != ComandoInterativo.LimparDisplay
                    && cmd != ComandoInterativo.ExibirMensagem) {
                estadoEsperaCartao[0] = 0L;
                estadoEsperaCartao[1] = 0L;
            }

            switch (cmd) {
                case RetornoSubcampo:
                    if (BufferNativoUtil.naoVazio(numScStr) && camposRetornados != null) {
                        String trimmed = numScStr.trim();
                        String chave = trimmed.length() > 4 ? trimmed.substring(0, 4) : trimmed;
                        camposRetornados.put(chave, pScAtual);
                    }
                    break;

                case ExibirMensagem:
                    ultimaMensagemDisplay = pScAtual;
                    if (isMensagemEsperaCartao(pScAtual)) {
                        if (estadoEsperaCartao[0] == 0L) {
                            estadoEsperaCartao[0] = System.currentTimeMillis();
                            estadoEsperaCartao[1] = 0L;
                        }
                    } else {
                        estadoEsperaCartao[0] = 0L;
                        estadoEsperaCartao[1] = 0L;
                    }
                    if (callback != null) {
                        callback.exibirMensagem(pScAtual, auxStr, false);
                    }
                    break;

                case ExibirTituloMenu:
                    ultimaMensagemDisplay = pScAtual;
                    if (callback != null && !isTituloConfirmacaoCancelamento(pScAtual)) {
                        callback.exibirMensagem(pScAtual, auxStr, true);
                    }
                    break;

                case LimparDisplay:
                    if (callback != null) {
                        callback.limparDisplay(auxStr);
                    }
                    break;

                case ConfirmarSimNao: {
                    String resp = callback != null ? callback.confirmarOperacao(ultimaMensagemDisplay) : "1";
                    if (resp == null || "CANCELAR".equalsIgnoreCase(resp)) {
                        comandoResposta = ComandoInterativo.VerificarCancelamento.codigoFormatado();
                    } else {
                        pScResposta = "1".equals(resp.trim()) ? "1" : "2";
                        tamScResposta = "00001";
                    }
                    break;
                }

                case MenuOpcoes: {
                    String escolha;
                    if (isAutoConfirmarCancelamentoOperacao(pScAtual, ultimaMensagemDisplay)) {
                        escolha = "1";
                    } else {
                        escolha = callback != null
                                ? callback.selecionarOpcaoMenu(pScAtual, ultimaMensagemDisplay, false)
                                : "1";
                    }
                    if (isAutoReconsultaPix(pScAtual, ultimaMensagemDisplay) && camposRetornados != null) {
                        camposRetornados.put("PixReconsultado", "true");
                    }
                    if (escolha == null || "CANCELAR".equalsIgnoreCase(escolha)) {
                        comandoResposta = ComandoInterativo.VerificarCancelamento.codigoFormatado();
                    } else {
                        String limpa = escolha.trim();
                        pScResposta = limpa;
                        tamScResposta = String.format("%05d", limpa.getBytes(BufferNativoUtil.CHARSET_DLL).length);
                    }
                    break;
                }

                case AguardarTecla:
                    if (callback != null && !callback.aguardarTecla(ultimaMensagemDisplay)) {
                        comandoResposta = ComandoInterativo.VerificarCancelamento.codigoFormatado();
                    }
                    break;

                case CapturarDado: {
                    int maxLen = BufferNativoUtil.parseIntSeguro(pScAtual.trim(), 0);
                    if (maxLen <= 0 && tamScValor > 0) {
                        maxLen = tamScValor;
                    }
                    boolean aceitaZeros = "1".equals(auxStr);
                    String dado;
                    if (isQuestionamentoTransacaoPendente(numScStr)) {
                        if (maxLen <= 0) {
                            maxLen = 1;
                        }
                        String respAuto = resolverRespostaAutomatica7358(
                                politicaQuestionamentoPendencia, ultimaMensagemDisplay);
                        String bruto = respAuto != null
                                ? respAuto
                                : (callback != null
                                        ? callback.capturarDado(numScStr, maxLen, aceitaZeros, ultimaMensagemDisplay)
                                        : "1");
                        dado = normalizarResposta7358(bruto);
                        if (dado != null && camposRetornados != null) {
                            camposRetornados.put(Subcampo.QuestionamentoTransacaoPendente.codigo(), dado);
                        }
                    } else {
                        dado = callback != null
                                ? callback.capturarDado(numScStr, maxLen, aceitaZeros, ultimaMensagemDisplay)
                                : null;
                    }
                    if (dado == null) {
                        comandoResposta = ComandoInterativo.VerificarCancelamento.codigoFormatado();
                    } else {
                        numScResposta = BufferNativoUtil.formatarNumerico(numScStr, 4);
                        pScResposta = dado;
                        tamScResposta = String.format("%05d", dado.getBytes(BufferNativoUtil.CHARSET_DLL).length);
                    }
                    break;
                }

                case VerificarCancelamento: {
                    boolean cancelar = callback != null && callback.verificarCancelamento();
                    if (!cancelar && estadoEsperaCartao[0] > 0L && timeoutLeituraCartaoSegundos > 0) {
                        if (callback != null && callback.confirmarPresencaLeituraCartao()) {
                            estadoEsperaCartao[0] = System.currentTimeMillis();
                            estadoEsperaCartao[1] = 0L;
                            callback.exibirMensagem(
                                    "TEMPO RENOVADO;Aproxime, insira ou passe o cartao", "0", false);
                        } else {
                            long decorridoMs = System.currentTimeMillis() - estadoEsperaCartao[0];
                            long limiteInicialMs = timeoutLeituraCartaoSegundos * 1000L;
                            long limiteTotalMs =
                                    (timeoutLeituraCartaoSegundos + Math.max(0, avisoPresencaCartaoSegundos)) * 1000L;
                            if (decorridoMs >= limiteTotalMs) {
                                cancelar = true;
                                estadoEsperaCartao[0] = 0L;
                                estadoEsperaCartao[1] = 0L;
                                if (callback != null) {
                                    callback.exibirMensagem(
                                            "TEMPO ESGOTADO PARA LEITURA DO CARTAO;Operacao cancelada por inatividade",
                                            "0",
                                            false);
                                }
                            } else if (avisoPresencaCartaoSegundos > 0
                                    && decorridoMs >= limiteInicialMs
                                    && estadoEsperaCartao[1] == 0L) {
                                estadoEsperaCartao[1] = 1L;
                                if (callback != null) {
                                    callback.exibirMensagem(
                                            "VOCE AINDA ESTA AI?;Aproxime/insira o cartao ou pressione [ENTER] em "
                                                    + avisoPresencaCartaoSegundos + "s para continuar...",
                                            "0",
                                            false);
                                }
                            }
                        }
                    }
                    comandoResposta = cancelar
                            ? ComandoInterativo.VerificarCancelamento.codigoFormatado()
                            : ComandoInterativo.RetornoSubcampo.codigoFormatado();
                    break;
                }

                case MenuOpcoesTemporizado: {
                    if ("1".equals(pScAtual)) {
                        auxResposta = "0";
                    } else {
                        String menuOuStatus = "0".equals(pScAtual) ? "" : pScAtual;
                        String escolha = callback != null
                                ? callback.selecionarOpcaoMenu(menuOuStatus, ultimaMensagemDisplay, true)
                                : "";
                        if (escolha == null || "CANCELAR".equalsIgnoreCase(escolha)) {
                            comandoResposta = ComandoInterativo.VerificarCancelamento.codigoFormatado();
                        } else if (escolha.trim().isEmpty()) {
                            auxResposta = "0";
                        } else {
                            String limpa = escolha.trim();
                            pScResposta = limpa;
                            tamScResposta = String.format("%05d", limpa.getBytes(BufferNativoUtil.CHARSET_DLL).length);
                            auxResposta = "1";
                        }
                    }
                    break;
                }
            }
        }

        BufferNativoUtil.limparBuffers(comando, numSc, pSc, tamSc);
        BufferNativoUtil.escreverBuffer(comando, comandoResposta, (byte) 0);
        BufferNativoUtil.escreverBuffer(numSc, numScResposta, (byte) 0);
        BufferNativoUtil.escreverBuffer(pSc, pScResposta, (byte) ' ');
        BufferNativoUtil.escreverBuffer(tamSc, tamScResposta, (byte) 0);
        if (auxResposta != null) {
            BufferNativoUtil.limparBuffers(aux);
            BufferNativoUtil.escreverBuffer(aux, auxResposta, (byte) 0);
        }

        return ultimaMensagemDisplay;
    }

    /**
     * Identifica se a mensagem de display ({@code Comando 01}) corresponde exclusivamente à tela
     * de espera de leitura do cartão no PinPad ({@code APROXIME}, {@code INSIRA} ou {@code PASSE O CARTAO}).
     */
    public static boolean isMensagemEsperaCartao(String mensagemDisplay) {
        if (!BufferNativoUtil.naoVazio(mensagemDisplay)) {
            return false;
        }
        String msg = mensagemDisplay.toUpperCase().replace("Ã", "A");
        return msg.contains("APROXIME") || msg.contains("INSIRA") || msg.contains("PASSE O CARTAO");
    }

    public static boolean isAutoReconsultaPix(String menuTexto, String tituloDisplay) {
        if (menuTexto == null || tituloDisplay == null) {
            return false;
        }
        String menuNorm = menuTexto.toUpperCase().replace("Ã", "A");
        String tituloNorm = tituloDisplay.toUpperCase().replace("Ã", "A");
        return menuNorm.contains("SIM")
                && menuNorm.contains("NAO")
                && (tituloNorm.contains("NOVAMENTE") || tituloNorm.contains("CONSULTA"));
    }

    /**
     * Identifica se o título de menu ({@code Comando 02}) corresponde à pergunta {@code OPERACAO CANCELADA?}
     * exibida pelo CTFClient após {@code Comando 08} ou tecla ANULA no PinPad.
     */
    public static boolean isTituloConfirmacaoCancelamento(String tituloDisplay) {
        if (!BufferNativoUtil.naoVazio(tituloDisplay)) {
            return false;
        }
        String tituloNorm = tituloDisplay.toUpperCase().replace("Ã", "A").replace("Ç", "C");
        return tituloNorm.contains("OPERACAO CANCELADA");
    }

    /**
     * Identifica o menu de confirmação de cancelamento ({@code Comando 02}: {@code OPERACAO CANCELADA?}
     * + {@code Comando 05}: {@code 1:SIM;2:NAO}) para auto-responder {@code "1"} ({@code SIM}) sem travar o terminal.
     */
    public static boolean isAutoConfirmarCancelamentoOperacao(String menuTexto, String tituloDisplay) {
        if (menuTexto == null || !isTituloConfirmacaoCancelamento(tituloDisplay)) {
            return false;
        }
        String menuNorm = menuTexto.toUpperCase().replace("Ã", "A");
        return menuNorm.contains("SIM") && menuNorm.contains("NAO");
    }

    /** Verifica se o subcampo solicitado no comando {@code 07} é o {@code 7358} (Questionamento de transação pendente). */
    public static boolean isQuestionamentoTransacaoPendente(String numSc) {
        if (numSc == null) {
            return false;
        }
        return Subcampo.QuestionamentoTransacaoPendente.codigo()
                .equals(BufferNativoUtil.formatarNumerico(numSc, 4));
    }

    /**
     * Normaliza a resposta do subcampo {@code 7358} (1 dígito numérico: {@code "1"} = SIM, {@code "2"} = NÃO).
     * Retorna {@code null} quando o operador solicita o cancelamento da operação ({@code C} / {@code CANCELAR}).
     */
    public static String normalizarResposta7358(String entrada) {
        if (entrada == null) {
            return null;
        }
        String limpa = entrada.trim().toUpperCase();
        if (limpa.isEmpty() || limpa.startsWith("C")) {
            return null;
        }
        if ("1".equals(limpa) || limpa.startsWith("S")) {
            return "1";
        }
        if ("2".equals(limpa) || limpa.startsWith("N")) {
            return "2";
        }
        return null;
    }

    /**
     * Resolve automaticamente a resposta para o subcampo {@code 7358} caso uma política automática
     * ({@code CONFIRMAR} ou {@code DESFAZER}) esteja configurada, levando em conta a tela exibida
     * pelo CTFClient ({@code DESEJA CONFIRMAR?}, {@code ESSA DECISAO IRA DESFAZER} ou {@code DESFAZER TRANSACAO?}).
     * Retorna {@code null} quando a política é {@code PERGUNTAR}.
     */
    public static String resolverRespostaAutomatica7358(String politica, String mensagemDisplay) {
        if (!BufferNativoUtil.naoVazio(politica)) {
            return null;
        }
        String pol = politica.trim().toUpperCase();
        String msg = mensagemDisplay != null ? mensagemDisplay.toUpperCase().replace("Ã", "A") : "";

        if ("CONFIRMAR".equals(pol) || "AUTO_CONFIRMAR".equals(pol)) {
            if (msg.contains("DESFAZER")) {
                return "2";
            }
            return "1";
        }
        if ("DESFAZER".equals(pol) || "AUTO_DESFAZER".equals(pol)) {
            if (msg.contains("DESFAZER")) {
                return "1";
            }
            return "2";
        }
        return null;
    }

    private static void sincronizarConfigConfirmarDesfazimentoXml(boolean habilitar) {
        String ctfHome = CtfProcessManager.resolverCtfClientHome(
                System.getenv("CTFCLIENT_HOME"), Platform.isWindows());
        if (!BufferNativoUtil.naoVazio(ctfHome)) {
            return;
        }
        File xml = new File(new File(ctfHome, "bin"), "configCTFClient.xml");
        try {
            if (aplicarConfigConfirmarDesfazimentoXml(xml, habilitar)) {
                TefAuditLogger.log("init", "configCTFClient.xml atualizado",
                        String.format("confirmardesfazimento=[%s] arquivo:[%s]",
                                habilitar, xml.getAbsolutePath()));
            }
        } catch (IOException e) {
            TefAuditLogger.log("init", "Aviso ao atualizar configCTFClient.xml", e.getMessage());
        }
    }

    /**
     * Atualiza {@code <Parameter name="confirmardesfazimento">} no XML do CTFClient quando o valor atual
     * difere do desejado. Retorna {@code true} se o arquivo foi modificado.
     */
    static boolean aplicarConfigConfirmarDesfazimentoXml(File xmlFile, boolean habilitar) throws IOException {
        if (xmlFile == null || !xmlFile.isFile()) {
            return false;
        }
        String conteudo = new String(Files.readAllBytes(xmlFile.toPath()), StandardCharsets.UTF_8);
        Matcher matcher = PATTERN_CONFIRMAR_DESFAZIMENTO.matcher(conteudo);
        if (!matcher.find()) {
            return false;
        }
        String desejado = Boolean.toString(habilitar);
        if (desejado.equalsIgnoreCase(matcher.group(2).trim())) {
            return false;
        }
        String atualizado = matcher.replaceFirst("$1" + desejado + "$3");
        Files.write(xmlFile.toPath(), atualizado.getBytes(StandardCharsets.UTF_8));
        return true;
    }

    public TransacaoTefResponse avaliarResposta(TransacaoTefRequest request, Map<String, String> campos) {
        Map<String, String> mapa = campos != null ? campos : new HashMap<>();
        String codRetorno = mapa.get(Subcampo.CodigoRetornoTransacao.codigo());
        String codErro = mapa.get(Subcampo.CodigoErro.codigo());
        String codResposta = mapa.get(Subcampo.CodigoResposta.codigo());

        boolean aprovada = CodigoRetornoTransacao.isAprovado(codRetorno)
                && (codErro == null || codErro.trim().isEmpty() || "0000".equals(codErro.trim()));

        if (aprovada && request != null
                && (request.getOperacao() == CodigoOperacao.ConsultaPix
                || request.getOperacao() == CodigoOperacao.DevolucaoPix)
                && BufferNativoUtil.naoVazio(codResposta)) {
            aprovada = "00".equals(codResposta.trim());
        }

        TransacaoTefResponse response = new TransacaoTefResponse();
        response.setOperacaoRequest(request);
        response.setAprovada(aprovada);
        response.setCodigoRetorno(codRetorno != null ? codRetorno : "ERRO");
        response.setCodigoErro(codErro != null ? codErro : "");
        response.setMensagemRetorno(aprovada
                ? "Transação Aprovada"
                : CodigoRetornoTransacao.descrever(codRetorno));
        response.setCamposRetornados(mapa);
        return response;
    }

    public int encerrarCliente() {
        return CtfProcessManager.encerrarProcessoCtfClient();
    }

    public boolean finalizarTransacao(String confirmar, String numTrans, Map<String, String> camposRetornados) {
        int cod = finalizarTransacaoComCodigo(null, confirmar, numTrans, getDataCli(), camposRetornados);
        return CodigoRetornoBiblioteca.isConfirmacaoSucesso(cod);
    }

    public int finalizarTransacaoComCodigo(String confirmar, String numTrans, Map<String, String> camposRetornados) {
        return finalizarTransacaoComCodigo(null, confirmar, numTrans, getDataCli(), camposRetornados);
    }

    int finalizarTransacaoComCodigo(
            String txId,
            String confirmar,
            String numTrans,
            Map<String, String> camposRetornados) {
        return finalizarTransacaoComCodigo(txId, confirmar, numTrans, getDataCli(), camposRetornados);
    }

    int finalizarTransacaoComCodigo(
            String txId,
            String confirmar,
            String numTrans,
            String dataCliAlvo,
            Map<String, String> camposRetornados) {

        long inicio = System.currentTimeMillis();
        String numTransFmt = BufferNativoUtil.formatarNumerico(numTrans, 2);
        String dataCliUsada = (dataCliAlvo == null || dataCliAlvo.trim().isEmpty()) ? getDataCli() : dataCliAlvo.trim();

        TefAuditLogger.log(txId, "-> finalizaTransacaoCTF",
                String.format("confirmar:[%s] num_trans:[%s] data_cli:[%s]", confirmar, numTransFmt, dataCliUsada));

        byte[] resultado = new byte[16];
        lib().finalizaTransacaoCTF(resultado, confirmar, numTransFmt, dataCliUsada);

        String retorno = BufferNativoUtil.extrairStringC(resultado);
        int cod = BufferNativoUtil.parseIntSeguro(retorno, -1);
        long duracaoMs = System.currentTimeMillis() - inicio;

        if (camposRetornados != null) {
            camposRetornados.put("ResultadoFinalizacao", retorno);
        }

        if (CodigoRetornoBiblioteca.isConfirmacaoSucesso(cod)) {
            TefAuditLogger.log(txId, "<- finalizaTransacaoCTF OK",
                    String.format("resultado:[%s] %s duracaoMs:[%d]",
                            retorno, CodigoRetornoBiblioteca.descrever(cod), duracaoMs));
        } else {
            TefAuditLogger.log(txId, "<- finalizaTransacaoCTF ERRO",
                    String.format("resultado:[%s] %s duracaoMs:[%d]",
                            retorno, CodigoRetornoBiblioteca.descrever(cod), duracaoMs));
        }
        return cod;
    }

    /** Confirma em sequência cada pagamento aprovado de uma venda com múltiplos cartões ("01" até {@code qtdTransacoes}). */
    public boolean confirmarBlocoTransacoes(int qtdTransacoes) {
        if (qtdTransacoes <= 0) {
            return true;
        }
        boolean todasOk = true;
        for (int i = 1; i <= qtdTransacoes; i++) {
            String numTrans = String.format("%02d", i);
            int cod = finalizarTransacaoComCodigo(null, "1", numTrans, null);
            if (!CodigoRetornoBiblioteca.isConfirmacaoSucesso(cod)) {
                todasOk = false;
            }
        }
        return todasOk;
    }

    /** Desfaz todas as transações de uma venda com múltiplos cartões enviando uma única chamada com {@code confirmar="0"} e {@code num_trans="01"}. */
    public boolean desfazerBlocoTransacoes(int qtdTransacoes) {
        if (qtdTransacoes <= 0) {
            return true;
        }
        int cod = finalizarTransacaoComCodigo(null, "0", "01", null);
        return CodigoRetornoBiblioteca.isConfirmacaoSucesso(cod);
    }

    /** Executa o ciclo completo da transação com correlação, exclusão mútua e log de resumo. */
    public TransacaoTefResponse executarTransacaoCompleta(
            TransacaoTefRequest request,
            String numTrans,
            boolean autoConfirmar,
            InteracaoTefCallback callback) {

        synchronized (lockTransacao) {
            long inicio = System.currentTimeMillis();
            String dataCliTransacao = getDataCli();
            String txId = TefAuditLogger.gerarTxId(dataCliTransacao);
            Map<String, String> campos = new HashMap<>();

            try {
                int retInicio = iniciarTransacao(txId, request, numTrans, dataCliTransacao);
                if (retInicio != CodigoRetornoBiblioteca.SUCESSO) {
                    return criarErro(request, String.valueOf(retInicio),
                            "Falha ao iniciar transação (" + retInicio + "): "
                                    + CodigoRetornoBiblioteca.descrever(retInicio),
                            campos);
                }

                int retLoop = executarLoopTransacao(txId, request, campos, callback);
                TransacaoTefResponse response = avaliarResposta(request, campos);
                CodigoOperacao op = request.getOperacao();

                if (retLoop != CodigoRetornoBiblioteca.SUCESSO) {
                    if (op.permiteDesfazimento() && op != CodigoOperacao.PagamentoPix) {
                        finalizarTransacaoComCodigo(txId, "0", numTrans, dataCliTransacao, campos);
                    }
                    response.setAprovada(false);
                    response.setCodigoRetorno(String.valueOf(retLoop));
                    response.setMensagemRetorno(
                            "Erro/Cancelamento (" + retLoop + "): " + CodigoRetornoBiblioteca.descrever(retLoop));
                    return response;
                }

                boolean pixReconsultado = "true".equals(campos.get("PixReconsultado"));
                boolean precisaFinalizar = op.exigeConfirmacao() && autoConfirmar;
                if (op == CodigoOperacao.PagamentoPix && (!response.isAprovada() || pixReconsultado)) {
                    precisaFinalizar = false;
                }

                if (precisaFinalizar) {
                    String confirmar = response.isAprovada() ? "1" : "0";
                    int codFim = finalizarTransacaoComCodigo(txId, confirmar, numTrans, dataCliTransacao, campos);
                    if (response.isAprovada() && !CodigoRetornoBiblioteca.isConfirmacaoSucesso(codFim)) {
                        response.setAprovada(false);
                        response.setMensagemRetorno("Aprovada no servidor, porém falhou ao confirmar na DLL.");
                    }
                }

                long duracaoMs = System.currentTimeMillis() - inicio;
                TefAuditLogger.log(txId, "RESUMO",
                        String.format("op:[%s] status:[%s] ret7000:[%s] nsu:[%s] cAut:[%s] duracaoMs:[%d]",
                                op.codigoFormatado(),
                                response.isAprovada() ? "APROVADA" : "NEGADA_OU_PENDENTE",
                                response.getCodigoRetorno(),
                                BufferNativoUtil.naoVazio(response.getNsuCtf()) ? response.getNsuCtf() : "N/A",
                                BufferNativoUtil.naoVazio(response.getCodigoAutorizacao()) ? response.getCodigoAutorizacao() : "N/A",
                                duracaoMs));
                return response;
            } catch (IllegalArgumentException ex) {
                TefAuditLogger.log(txId, "VALIDACAO ERRO", ex.getMessage());
                return criarErro(request, "-1", ex.getMessage(), campos);
            }
        }
    }

    String construirDadosEstendidos(TransacaoTefRequest request) {
        if (request == null) {
            return "";
        }

        List<String> dados = new ArrayList<>();
        if (request.getNumeroParcelas() != null && request.getNumeroParcelas() > 0) {
            dados.add(Subcampo.NumeroParcelas.codigo() + "=" + String.format("%02d", request.getNumeroParcelas()));
        }

        if (request.getOperacao() != null) {
            switch (request.getOperacao()) {
                case ConsultaPix:
                case DevolucaoPix:
                    if (BufferNativoUtil.naoVazio(request.getIdentificadorPix())
                            && BufferNativoUtil.naoVazio(request.getPspPix())) {
                        dados.add(Subcampo.IdentificadorPix.codigo() + "=" + request.getIdentificadorPix().trim());
                        dados.add(Subcampo.PspPix.codigo() + "=" + request.getPspPix().trim());
                    } else if (BufferNativoUtil.naoVazio(request.getNsuOriginal())) {
                        dados.add(Subcampo.NsuCtfOriginal.codigo() + "=" + request.getNsuOriginal().trim());
                    }
                    if (BufferNativoUtil.naoVazio(request.getDataOriginal())) {
                        dados.add(Subcampo.DataTransacaoOriginal.codigo() + "=" + request.getDataOriginal().trim());
                    }
                    break;

                case CancelamentoGenerico:
                case ConfirmacaoPorNsu:
                case DesfazimentoPorNsu:
                    adicionarSeNaoVazio(dados, Subcampo.NsuCtfOriginal, request.getNsuOriginal());
                    adicionarSeNaoVazio(dados, Subcampo.DataTransacaoOriginal, request.getDataOriginal());
                    break;

                case CancelamentoCreditoDigitado:
                    adicionarSeNaoVazio(dados, Subcampo.NsuCtfOriginal, request.getNsuOriginal());
                    adicionarSeNaoVazio(dados, Subcampo.DataTransacaoOriginal, request.getDataOriginal());
                    adicionarSeNaoVazio(dados, Subcampo.NumeroCartao, request.getNumeroCartao());
                    break;

                case CreditoDigitado:
                    adicionarSeNaoVazio(dados, Subcampo.NumeroCartao, request.getNumeroCartao());
                    adicionarSeNaoVazio(dados, Subcampo.VencimentoCartao, request.getVencimentoCartao());
                    break;

                case AutenticacaoTerminal:
                    adicionarSeNaoVazio(dados, Subcampo.LoginAutenticacao, request.getLoginAutenticacao());
                    adicionarSeNaoVazio(dados, Subcampo.SenhaAutenticacao, request.getSenhaAutenticacao());
                    adicionarSeNaoVazio(dados, Subcampo.CnpjAutenticacao, request.getCnpjAutenticacao());
                    break;

                default:
                    break;
            }
        }

        return dados.isEmpty() ? "" : "[" + String.join(";", dados) + "]";
    }

    private static void adicionarSeNaoVazio(List<String> dados, Subcampo subcampo, String valor) {
        if (BufferNativoUtil.naoVazio(valor)) {
            dados.add(subcampo.codigo() + "=" + valor.trim());
        }
    }

    private static TransacaoTefResponse criarErro(
            TransacaoTefRequest request,
            String codRetorno,
            String mensagem,
            Map<String, String> campos) {
        TransacaoTefResponse response = new TransacaoTefResponse();
        response.setOperacaoRequest(request);
        response.setAprovada(false);
        response.setCodigoRetorno(codRetorno);
        response.setCodigoErro("INTEGRACAO");
        response.setMensagemRetorno(mensagem);
        response.setCamposRetornados(campos);
        return response;
    }

    private static String envOuPadrao(String chave, String padrao) {
        String valor = System.getenv(chave);
        return BufferNativoUtil.naoVazio(valor) ? valor.trim() : padrao;
    }
}
