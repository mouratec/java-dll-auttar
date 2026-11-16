package com.auttar;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Utilitários para formatar textos/números no padrão esperado pela DLL do CTFClient
 * e manipular com segurança os buffers {@code byte[]} trocados via JNA.
 */
public final class BufferNativoUtil {

    /** Codificação legada utilizada pelos fluxos Windows/Pinpad da DLL Auttar. */
    public static final Charset CHARSET_DLL = Charset.forName("CP850");

    private static final DateTimeFormatter FORMATO_DATA_AAAAMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private BufferNativoUtil() {
    }

    public static boolean naoVazio(String valor) {
        return valor != null && !valor.trim().isEmpty();
    }

    public static String getDataAtual() {
        return LocalDate.now().format(FORMATO_DATA_AAAAMMDD);
    }

    /**
     * Formata valor monetário em centavos (tipo N) preenchido com zeros à esquerda.
     * Aplica Fail-Fast rejeitando valores negativos.
     */
    public static String formatarValorCentavos(BigDecimal valor, int tamanho) {
        if (valor != null && valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("O valor da transação não pode ser negativo: " + valor);
        }
        BigDecimal base = valor != null ? valor : BigDecimal.ZERO;
        BigInteger centavos = base.multiply(new BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger();
        return formatarNumerico(centavos.toString(), tamanho);
    }

    public static String formatarValorCentavos(BigDecimal valor) {
        return formatarValorCentavos(valor, 12);
    }

    /**
     * Formata um campo numérico de tamanho fixo: mantém apenas dígitos
     * e completa com zeros à esquerda (ou trunca à esquerda se passar do limite).
     */
    public static String formatarNumerico(String valor, int tamanho) {
        String digitos = valor != null ? valor.replaceAll("\\D", "") : "";
        if (digitos.length() > tamanho) {
            return digitos.substring(digitos.length() - tamanho);
        }
        StringBuilder sb = new StringBuilder(tamanho);
        for (int i = digitos.length(); i < tamanho; i++) {
            sb.append('0');
        }
        sb.append(digitos);
        return sb.toString();
    }

    /**
     * Formata um campo de texto (alfanumérico) de tamanho fixo: completa com
     * espaços em branco à direita (ou corta o excesso à direita).
     */
    public static String formatarAlfanumerico(String valor, int tamanho) {
        String texto = valor != null ? valor : "";
        if (texto.length() >= tamanho) {
            return texto.substring(0, tamanho);
        }
        StringBuilder sb = new StringBuilder(tamanho);
        sb.append(texto);
        for (int i = texto.length(); i < tamanho; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /** Preenche o buffer nativo evitando lixo de memória de iterações anteriores. */
    public static void escreverBuffer(byte[] buffer, String conteudo, byte padByte) {
        if (buffer == null || buffer.length == 0) {
            return;
        }
        Arrays.fill(buffer, padByte);
        if (conteudo == null || conteudo.isEmpty()) {
            if (padByte == ' ' && buffer.length > 0) {
                buffer[buffer.length - 1] = 0;
            }
            return;
        }
        byte[] bytes = conteudo.getBytes(CHARSET_DLL);
        int maxCopy = (padByte == 0) ? buffer.length - 1 : buffer.length;
        int len = Math.min(bytes.length, maxCopy);
        System.arraycopy(bytes, 0, buffer, 0, len);
        if (padByte == 0) {
            buffer[len] = 0;
        }
    }

    public static void limparBuffers(byte[]... buffers) {
        if (buffers == null) {
            return;
        }
        for (byte[] buffer : buffers) {
            if (buffer != null) {
                Arrays.fill(buffer, (byte) 0);
            }
        }
    }

    /** Extrai string terminada em NUL ({@code 0x00}) com fallback UTF-8 -> CP850. */
    public static String extrairStringC(byte[] buffer) {
        return extrairStringC(buffer, -1);
    }

    public static String extrairStringC(byte[] buffer, int limiteExplicito) {
        if (buffer == null || buffer.length == 0) {
            return "";
        }
        int len = 0;
        int teto = (limiteExplicito >= 0 && limiteExplicito < buffer.length) ? limiteExplicito : buffer.length;
        while (len < teto && buffer[len] != 0) {
            len++;
        }
        if (len == 0) {
            return "";
        }
        String utf8 = new String(buffer, 0, len, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') >= 0) {
            return new String(buffer, 0, len, CHARSET_DLL);
        }
        return utf8;
    }

    public static int parseIntSeguro(String valor, int padrao) {
        if (valor == null) {
            return padrao;
        }
        String limpo = valor.trim();
        if (limpo.isEmpty()) {
            return padrao;
        }
        try {
            return Integer.parseInt(limpo);
        } catch (NumberFormatException e) {
            return padrao;
        }
    }
}
