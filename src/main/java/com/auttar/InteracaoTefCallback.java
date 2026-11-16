package com.auttar;

/**
 * Callback de interação entre o motor TEF ({@link CtfClientService}) e a interface
 * da Automação Comercial durante o laço da função {@code continuaTransacaoCTF}.
 */
public interface InteracaoTefCallback {

    /** Comando 01 / 02: Exibe mensagem ou título de menu. */
    void exibirMensagem(String mensagem, String auxDisplay, boolean isTituloMenu);

    /** Comando 03: Limpa o display indicado por {@code auxDisplay} ("1"=operador, "2"=cliente, "3"=ambos). */
    default void limparDisplay(String auxDisplay) {
    }

    /** Comando 04: Solicita confirmação SIM ("1") / NÃO ("2"). Retornar null/"CANCELAR" aciona comando 08. */
    String confirmarOperacao(String mensagemContexto);

    /** Comando 05 / 09: Exibe menu e retorna opção escolhida (ou null/"CANCELAR" para comando 08). */
    String selecionarOpcaoMenu(String opcoesMenu, String tituloContexto, boolean temporizado);

    /** Comando 06: Aguarda confirmação/tecla do operador. Retorna false para cancelar (comando 08). */
    boolean aguardarTecla(String mensagemContexto);

    /** Comando 07: Captura dado digitado. {@code aceitaZerosEsquerda} vem do parâmetro {@code aux}. */
    String capturarDado(String numSc, int tamanhoMaximo, boolean aceitaZerosEsquerda, String mensagemContexto);

    /** Comando 08: Verifica se o operador solicitou cancelamento durante operação longa. */
    default boolean verificarCancelamento() {
        return false;
    }

    /**
     * Consulta não-bloqueante chamada durante os segundos de aviso de inatividade na leitura do cartão
     * ("Você ainda está aí?"). Retornar {@code true} renova o tempo de espera do cartão.
     */
    default boolean confirmarPresencaLeituraCartao() {
        return false;
    }
}
