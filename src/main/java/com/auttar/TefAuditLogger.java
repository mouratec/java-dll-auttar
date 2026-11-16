package com.auttar;

import com.auttar.CtfCatalogo.Subcampo;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logger transacional de observabilidade e auditoria das chamadas à DLL CTFClient.
 * Emite no console e grava em arquivo diário ({@code logs/ctfclient_YYYYMMDD.log}) com UTF-8,
 * ID de correlação ({@code txId}), duração e mascaramento de segredos ({@code 7918} e {@code 7006}).
 */
public final class TefAuditLogger {

    private static final DateTimeFormatter FORMATO_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter FORMATO_DATA_LOG = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final AtomicLong SEQUENCIA_TX = new AtomicLong(0);
    private static final String DIR_LOGS = "logs";

    private static volatile boolean arquivoHabilitado = true;

    private TefAuditLogger() {
    }

    public static void setArquivoHabilitado(boolean habilitado) {
        arquivoHabilitado = habilitado;
    }

    public static String gerarTxId(String dataCli) {
        long seq = SEQUENCIA_TX.incrementAndGet();
        String data = BufferNativoUtil.naoVazio(dataCli)
                ? dataCli.trim()
                : LocalDate.now().format(FORMATO_DATA_LOG);
        return String.format("%s-%04d", data, seq);
    }

    public static void log(String evento, String detalhes) {
        log(null, evento, detalhes);
    }

    public static synchronized void log(String txId, String evento, String detalhes) {
        String timestamp = LocalDateTime.now().format(FORMATO_TIMESTAMP);
        String prefixoTx = BufferNativoUtil.naoVazio(txId) ? "[" + txId.trim() + "] " : "";
        String linha = BufferNativoUtil.naoVazio(detalhes)
                ? String.format("%s | %s%s | %s", timestamp, prefixoTx, evento, detalhes)
                : String.format("%s | %s%s", timestamp, prefixoTx, evento);

        System.out.println(linha);
        if (arquivoHabilitado) {
            gravarEmArquivo(linha);
        }
    }

    private static void gravarEmArquivo(String linha) {
        File dir = new File(DIR_LOGS);
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        String nomeArquivo = "ctfclient_" + LocalDate.now().format(FORMATO_DATA_LOG) + ".log";
        File arquivo = new File(dir, nomeArquivo);
        try (PrintWriter out = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(arquivo, true), StandardCharsets.UTF_8)))) {
            out.println(linha);
        } catch (IOException e) {
            System.err.println("[LOG ERRO] Falha ao gravar log de auditoria: " + e.getMessage());
        }
    }

    /**
     * Mascara dados sensíveis ({@code 7918} senha e {@code 7006} PAN aberto)
     * na string {@code dados} enviada em {@code iniciaTransacaoCTFext}.
     */
    public static String mascararDadosEstendidos(String dados) {
        if (dados == null || dados.isEmpty()) {
            return "[]";
        }
        String mascarado = dados.replaceAll("(7918=)[^;\\]]+", "$1***");
        int idxPan = mascarado.indexOf("7006=");
        if (idxPan >= 0) {
            int ini = idxPan + 5;
            int fim = mascarado.indexOf(';', ini);
            if (fim < 0) {
                fim = mascarado.indexOf(']', ini);
            }
            if (fim < 0) {
                fim = mascarado.length();
            }
            String pan = mascarado.substring(ini, fim);
            mascarado = mascarado.substring(0, ini) + mascararCartao(pan) + mascarado.substring(fim);
        }
        return mascarado;
    }

    /** Mascara o valor de subcampos sensíveis identificados em {@link Subcampo}. */
    public static String mascararSubcampo(String numSc, String valor) {
        if (valor == null) {
            return "";
        }
        String cod = numSc != null ? numSc.trim() : "";
        if (Subcampo.SenhaAutenticacao.codigo().equals(cod) && !valor.isEmpty()) {
            return "***";
        }
        if (Subcampo.NumeroCartao.codigo().equals(cod) && !valor.isEmpty()) {
            return mascararCartao(valor);
        }
        return valor;
    }

    private static String mascararCartao(String cartao) {
        String limpo = cartao.trim();
        if (limpo.contains("*")) {
            return limpo;
        }
        if (limpo.length() <= 10) {
            return "***";
        }
        int qtdOculta = limpo.length() - 10;
        StringBuilder sb = new StringBuilder(limpo.length());
        sb.append(limpo, 0, 6);
        for (int i = 0; i < qtdOculta; i++) {
            sb.append('*');
        }
        sb.append(limpo.substring(limpo.length() - 4));
        return sb.toString();
    }
}
