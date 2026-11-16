package com.auttar;

import com.auttar.CtfCatalogo.CodigoOperacao;
import com.auttar.CtfCatalogo.CodigoRetornoBiblioteca;
import com.auttar.CtfCatalogo.CodigoRetornoTransacao;
import com.auttar.CtfCatalogo.Subcampo;
import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Testes unitários e de fluxo simulado para o cliente Java CTFClient Auttar.
 */
public class CtfClientServiceTest {

    @Before
    public void configurarLoggerParaTeste() {
        TefAuditLogger.setArquivoHabilitado(false);
    }

    @Test
    public void testBufferNativoFormatacaoTiposANeExtracao() {
        assertEquals("000000002550", BufferNativoUtil.formatarValorCentavos(new BigDecimal("25.50")));
        assertEquals("05", BufferNativoUtil.formatarNumerico("5", 2));
        assertEquals("PDV_JAVA  ", BufferNativoUtil.formatarAlfanumerico("PDV_JAVA", 10));

        byte[] buffer = new byte[16];
        BufferNativoUtil.escreverBuffer(buffer, "00", (byte) 0);
        assertEquals("00", BufferNativoUtil.extrairStringC(buffer));

        BufferNativoUtil.limparBuffers(buffer);
        assertEquals("", BufferNativoUtil.extrairStringC(buffer));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBufferNativoRejeitaValorNegativoFailFast() {
        BufferNativoUtil.formatarValorCentavos(new BigDecimal("-10.00"));
    }

    @Test
    public void testCatalogoCodigosDeRetornoESubcampos() {
        assertTrue(CodigoRetornoBiblioteca.isConfirmacaoSucesso(0));
        assertTrue(CodigoRetornoBiblioteca.isConfirmacaoSucesso(13));
        assertFalse(CodigoRetornoBiblioteca.isConfirmacaoSucesso(18));
        assertTrue(CodigoRetornoBiblioteca.isSemTransacaoParaConfirmar(18));

        assertTrue(CodigoRetornoTransacao.isAprovado("00"));
        assertTrue(CodigoRetornoTransacao.isAprovado("13"));
        assertFalse(CodigoRetornoTransacao.isAprovado("05"));

        assertEquals("7031:NsuCtf", Subcampo.formatarIdentificador("7031"));
        assertEquals("7564:CodigoAutorizacao", Subcampo.formatarIdentificador("7564"));
        assertEquals("0000", Subcampo.formatarIdentificador("0000"));
        assertEquals("006", CodigoOperacao.Operacao006.codigoFormatado());
        assertEquals("007", CodigoOperacao.Operacao007.codigoFormatado());
        assertEquals("280", CodigoOperacao.ConfirmacaoPorNsu.codigoFormatado());
    }

    @Test
    public void testResolveLibraryPathComCtfClientHome() {
        String winPath = CtfClientLibrary.resolveLibraryPath(null, "C:\\Auttar\\CTFClient", true);
        assertTrue(winPath.endsWith("CTFClient" + File.separator + "bin" + File.separator + "ctfclient.dll"));

        String linuxPath = CtfClientLibrary.resolveLibraryPath(null, "/opt/CTFClient", false);
        assertTrue(linuxPath.endsWith("CTFClient" + File.separator + "bin" + File.separator + "libctfclient.so"));
    }

    @Test
    public void testValidacoesFailFastEMascaramentoSegredos() {
        CtfClientService service = new CtfClientService(null, "20261113");

        TransacaoTefRequest parceladoInvalido = new TransacaoTefRequest(
                CodigoOperacao.CreditoParceladoSemJuros, new BigDecimal("100.00"), "");
        parceladoInvalido.setNumeroParcelas(1);
        assertFalhaValidacao(service, parceladoInvalido);

        TransacaoTefRequest cancelamentoSemNsu = new TransacaoTefRequest(
                CodigoOperacao.CancelamentoGenerico, new BigDecimal("50.00"), "");
        assertFalhaValidacao(service, cancelamentoSemNsu);

        TransacaoTefRequest confirmacaoNsuSemDados = new TransacaoTefRequest(
                CodigoOperacao.ConfirmacaoPorNsu, BigDecimal.ZERO, "");
        assertFalhaValidacao(service, confirmacaoNsuSemDados);

        TransacaoTefRequest confirmacaoNsuValida = new TransacaoTefRequest(
                CodigoOperacao.ConfirmacaoPorNsu, BigDecimal.ZERO, "");
        confirmacaoNsuValida.setDataOriginal("141126");
        confirmacaoNsuValida.setNsuOriginal("301211");
        service.validarRequisicao(confirmacaoNsuValida);
        assertEquals("[7012=301211;7161=141126]", service.construirDadosEstendidos(confirmacaoNsuValida));

        TransacaoTefRequest authSemSenha = new TransacaoTefRequest(
                CodigoOperacao.AutenticacaoTerminal, BigDecimal.ZERO, "");
        authSemSenha.setLoginAutenticacao("hti");
        authSemSenha.setCnpjAutenticacao("41602316000130");
        assertFalhaValidacao(service, authSemSenha);

        String dados = "[7917=hti;7918=MinhaSenha123;7919=41602316000130;7006=5432109876543210]";
        String mascarado = TefAuditLogger.mascararDadosEstendidos(dados);
        assertEquals("[7917=hti;7918=***;7919=41602316000130;7006=543210******3210]", mascarado);
        assertEquals("***", TefAuditLogger.mascararSubcampo("7918", "MinhaSenha123"));
        assertEquals("543210******3210", TefAuditLogger.mascararSubcampo("7006", "5432109876543210"));
    }

    @Test
    public void testFluxoSimuladoVendaMultitransacaoERecuperacaoIdempotente() throws Exception {
        final String[][] subcamposSimulados = {
                {"7000", "00"},
                {"7031", "300781"},
                {"7564", "123456"},
                {"7942", "GETNET"},
                {"7389", "MASTERCARD"},
                {"7311", "13112026"},
                {"7312", "015900"}
        };

        final Map<String, String> camposRecebidosFinalizacao = new HashMap<>();

        CtfClientLibrary stub = new CtfClientLibrary() {
            private int passo = 0;

            @Override
            public void iniciaClientCTF(byte[] r, String t, String v, String n, String ns,
                                        String ips, String c, String l, String i, String p) {
                BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
            }

            @Override
            public void iniciaTransacaoCTF(byte[] r, String op, String v, String doc, String dc, String nt) {
                passo = 0;
                BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
            }

            @Override
            public void iniciaTransacaoCTFext(byte[] r, String op, String v, String doc, String dc, String nt, String d) {
                passo = 0;
                BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
            }

            @Override
            public void continuaTransacaoCTF(byte[] r, byte[] cmd, byte[] nsc, byte[] psc, byte[] tam, byte[] aux) {
                if (passo < subcamposSimulados.length) {
                    String[] item = subcamposSimulados[passo++];
                    BufferNativoUtil.escreverBuffer(r, "99", (byte) 0);
                    BufferNativoUtil.escreverBuffer(cmd, "00", (byte) 0);
                    BufferNativoUtil.escreverBuffer(nsc, item[0], (byte) 0);
                    BufferNativoUtil.escreverBuffer(psc, item[1], (byte) ' ');
                    BufferNativoUtil.escreverBuffer(tam, String.format("%05d", item[1].length()), (byte) 0);
                    BufferNativoUtil.escreverBuffer(aux, "0", (byte) 0);
                } else {
                    BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
                }
            }

            @Override
            public void finalizaTransacaoCTF(byte[] r, String confirmar, String numTrans, String dataCli) {
                camposRecebidosFinalizacao.put(numTrans + "_confirmar", confirmar);
                camposRecebidosFinalizacao.put(numTrans + "_dataCli", dataCli);
                if ("1".equals(confirmar) && "01".equals(numTrans)) {
                    BufferNativoUtil.escreverBuffer(r, "13", (byte) 0);
                } else if ("0".equals(confirmar) && "02".equals(numTrans)) {
                    BufferNativoUtil.escreverBuffer(r, "18", (byte) 0);
                } else if ("0".equals(confirmar) && "03".equals(numTrans)) {
                    BufferNativoUtil.escreverBuffer(r, "56", (byte) 0);
                } else {
                    BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
                }
            }

            @Override
            public void finalizaTransacaoCTFext(byte[] r, String c, String nt, String dc, String d) {
                finalizaTransacaoCTF(r, c, nt, dc);
            }
        };

        CtfClientService service = new CtfClientService(stub, "20261114");
        assertTrue(service.inicializarCliente());

        TransacaoTefRequest req = new TransacaoTefRequest(CodigoOperacao.Debito, new BigDecimal("25.00"), "CUPOM01");
        assertEquals(0, service.iniciarTransacao(req, "01"));

        Map<String, String> campos = new HashMap<>();
        InteracaoTefCallback callback = new InteracaoTefCallback() {
            @Override public void exibirMensagem(String m, String a, boolean t) {}
            @Override public String confirmarOperacao(String m) { return "1"; }
            @Override public String selecionarOpcaoMenu(String o, String t, boolean tmp) { return "1"; }
            @Override public boolean aguardarTecla(String m) { return true; }
            @Override public String capturarDado(String n, int max, boolean z, String m) { return "1234"; }
        };

        assertEquals(0, service.executarLoopTransacao(req, campos, callback));
        TransacaoTefResponse resp = service.avaliarResposta(req, campos);
        assertTrue(resp.isAprovada());
        assertEquals("300781", resp.getNsuCtf());
        assertEquals("123456", resp.getCodigoAutorizacao());

        // Multitransação: num_trans "01" retorna 13 e "02" retorna 00 -> bloco confirmado com sucesso
        assertTrue(service.confirmarBlocoTransacoes(2));
    }

    @Test
    public void testCtfProcessManagerDescobertaPortaEParsingPid() throws Exception {
        assertTrue(CtfProcessManager.getMeuPid() > 0);

        File tempXml = Files.createTempFile("configCTFClient_", ".xml").toFile();
        tempXml.deleteOnExit();
        String xml = "<Configuration><Parameter name=\"portclient\">2500</Parameter></Configuration>";
        Files.write(tempXml.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        assertEquals(2500, CtfProcessManager.extrairPortaDeXml(tempXml));
        assertEquals(-1, CtfProcessManager.extrairPortaDeXml(new File("nao_existe_xml_12345.xml")));
        assertEquals("C:\\CustomCTF", CtfProcessManager.resolverCtfClientHome("C:\\CustomCTF", true));

        long meuPid = CtfProcessManager.getMeuPid();
        List<String> linhasNetstat = Arrays.asList(
                "  Proto  Endereço local         Endereço externo       Estado           PID",
                "  TCP    127.0.0.1:2500         0.0.0.0:0              LISTENING       43210",
                "  TCP    127.0.0.1:2500         127.0.0.1:55123        ESTABLISHED     " + meuPid,
                "  TCP    0.0.0.0:8080           0.0.0.0:0              LISTENING       9988"
        );
        Set<Long> pids = CtfProcessManager.parseNetstatListeningPids(linhasNetstat, 2500);
        assertEquals(1, pids.size());
        assertTrue(pids.contains(43210L));
        assertFalse(pids.contains(meuPid));
    }

    @Test
    public void testQuestionamentoTransacaoPendente7358() throws Exception {
        assertTrue(CtfClientService.isQuestionamentoTransacaoPendente("7358"));
        assertEquals("1", CtfClientService.normalizarResposta7358("1"));
        assertEquals("1", CtfClientService.normalizarResposta7358("S"));
        assertEquals("2", CtfClientService.normalizarResposta7358("2"));
        assertEquals("2", CtfClientService.normalizarResposta7358("N"));
        assertEquals(null, CtfClientService.normalizarResposta7358("C"));

        String tela1 = "TRANSACAO PENDENTE DE CONFIRMACAO;;NSU: 300781;DATA DA VENDA: 14/11/26;VALOR DA VENDA: 10,00;DESEJA CONFIRMAR?;;;[1] - SIM;[2] - NAO";
        String tela2 = "ESSA DECISAO IRA DESFAZER;A TRANSACAO NA AUTORIZADORA;E O CLIENTE NAO SERA COBRADO.;CONFIRMAR DECISAO?;;;[1] - SIM;[2] - NAO";
        assertEquals("1", CtfClientService.resolverRespostaAutomatica7358("CONFIRMAR", tela1));
        assertEquals("2", CtfClientService.resolverRespostaAutomatica7358("DESFAZER", tela1));
        assertEquals("1", CtfClientService.resolverRespostaAutomatica7358("DESFAZER", tela2));

        // Simula o protocolo de MicVerificaPendenciaPagamento (Comando 01 + Comando 07 num_sc=7358 tam_sc=00001)
        final String[] respostas7358 = new String[2];
        CtfClientLibrary fakeLib = new CtfClientLibrary() {
            private int passo = 0;

            @Override
            public void iniciaClientCTF(byte[] r, String t, String v, String n, String s, String l, String c, String lg, String i, String p) {
                BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
            }

            @Override
            public void iniciaTransacaoCTF(byte[] r, String o, String v, String d, String da, String nt) {
                BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
            }

            @Override
            public void iniciaTransacaoCTFext(byte[] r, String o, String v, String d, String da, String nt, String s) {
                BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
            }

            @Override
            public void continuaTransacaoCTF(byte[] r, byte[] cmd, byte[] numSc, byte[] pSc, byte[] tamSc, byte[] aux) {
                passo++;
                if (passo == 1) {
                    BufferNativoUtil.escreverBuffer(r, "99", (byte) 0);
                    BufferNativoUtil.escreverBuffer(cmd, "01", (byte) 0);
                    BufferNativoUtil.escreverBuffer(pSc, tela1, (byte) ' ');
                    BufferNativoUtil.escreverBuffer(tamSc, String.format("%05d", tela1.length()), (byte) 0);
                } else if (passo == 2) {
                    BufferNativoUtil.escreverBuffer(r, "99", (byte) 0);
                    BufferNativoUtil.escreverBuffer(cmd, "07", (byte) 0);
                    BufferNativoUtil.escreverBuffer(numSc, "7358", (byte) 0);
                    BufferNativoUtil.escreverBuffer(pSc, "", (byte) ' ');
                    BufferNativoUtil.escreverBuffer(tamSc, "00001", (byte) 0);
                    BufferNativoUtil.escreverBuffer(aux, "1", (byte) 0);
                } else if (passo == 3) {
                    respostas7358[0] = BufferNativoUtil.extrairStringC(pSc, 1);
                    BufferNativoUtil.escreverBuffer(r, "99", (byte) 0);
                    BufferNativoUtil.escreverBuffer(cmd, "01", (byte) 0);
                    BufferNativoUtil.escreverBuffer(pSc, tela2, (byte) ' ');
                    BufferNativoUtil.escreverBuffer(tamSc, String.format("%05d", tela2.length()), (byte) 0);
                } else if (passo == 4) {
                    BufferNativoUtil.escreverBuffer(r, "99", (byte) 0);
                    BufferNativoUtil.escreverBuffer(cmd, "07", (byte) 0);
                    BufferNativoUtil.escreverBuffer(numSc, "7358", (byte) 0);
                    BufferNativoUtil.escreverBuffer(pSc, "", (byte) ' ');
                    BufferNativoUtil.escreverBuffer(tamSc, "00001", (byte) 0);
                    BufferNativoUtil.escreverBuffer(aux, "1", (byte) 0);
                } else if (passo == 5) {
                    respostas7358[1] = BufferNativoUtil.extrairStringC(pSc, 1);
                    BufferNativoUtil.escreverBuffer(r, "99", (byte) 0);
                    BufferNativoUtil.escreverBuffer(cmd, "00", (byte) 0);
                    BufferNativoUtil.escreverBuffer(numSc, "7000", (byte) 0);
                    BufferNativoUtil.escreverBuffer(pSc, "00", (byte) ' ');
                    BufferNativoUtil.escreverBuffer(tamSc, "00002", (byte) 0);
                } else {
                    BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
                }
            }

            @Override
            public void finalizaTransacaoCTF(byte[] r, String c, String nt, String d) {
                BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
            }

            @Override
            public void finalizaTransacaoCTFext(byte[] r, String c, String nt, String dc, String d) {
                BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
            }
        };

        CtfClientService service = new CtfClientService(fakeLib, "20261114");
        assertTrue(service.isQuestionamentoTransacaoPendenteHabilitado());
        assertEquals("DESFAZER", service.getPoliticaQuestionamentoPendencia());
        Map<String, String> campos = new HashMap<>();
        TransacaoTefRequest req = new TransacaoTefRequest(
                CodigoOperacao.Debito, new BigDecimal("10.00"), "12345");
        assertEquals(0, service.executarLoopTransacao(req, campos, null));
        assertEquals("2", respostas7358[0]);
        assertEquals("1", respostas7358[1]);
        assertEquals("1", campos.get("7358"));

        // Verifica sincronização programática do confirmardesfazimento no configCTFClient.xml
        File tempXml = Files.createTempFile("configCTFClient_7358_", ".xml").toFile();
        tempXml.deleteOnExit();
        Files.write(tempXml.toPath(),
                "<ParameterSet><Parameter name=\"confirmardesfazimento\">false</Parameter></ParameterSet>"
                        .getBytes(StandardCharsets.UTF_8));
        assertTrue(CtfClientService.aplicarConfigConfirmarDesfazimentoXml(tempXml, true));
        assertFalse(CtfClientService.aplicarConfigConfirmarDesfazimentoXml(tempXml, true));
        String xmlLido = new String(Files.readAllBytes(tempXml.toPath()), StandardCharsets.UTF_8);
        assertTrue(xmlLido.contains("<Parameter name=\"confirmardesfazimento\">true</Parameter>"));
    }

    @Test
    public void testAutoAtendimentoTimeoutCartaoDuasFasesEDataFiscalDinamica() throws Exception {
        // 1. Data fiscal dinâmica vs fixa
        CtfClientService serviceDinamico = new CtfClientService();
        assertEquals(BufferNativoUtil.getDataAtual(), serviceDinamico.getDataCli());
        assertEquals(30, serviceDinamico.getTimeoutLeituraCartaoSegundos());
        assertEquals(5, serviceDinamico.getAvisoPresencaCartaoSegundos());

        // 2. Escopo estrito da detecção de tela de leitura do cartão
        assertTrue(CtfClientService.isMensagemEsperaCartao("APROXIME, INSIRA;OU PASSE O CARTAO"));
        assertTrue(CtfClientService.isMensagemEsperaCartao("INSIRA OU PASSE O CARTAO"));
        assertFalse(CtfClientService.isMensagemEsperaCartao("SELECIONADO;DEBITO A VISTA"));
        assertFalse(CtfClientService.isMensagemEsperaCartao("SOLICITE A SENHA"));
        assertFalse(CtfClientService.isMensagemEsperaCartao("AGUARDE"));

        // 3. Cenário A: Timer em 2 fases (1s espera + 1s aviso), renovação de presença e posterior cancelamento por inatividade
        final List<String> mensagensExibidas = new java.util.ArrayList<>();
        final List<String> comandosRespondidosNo08 = new java.util.ArrayList<>();
        final boolean[] renovarNaPrimeiraPergunta = new boolean[] { false };

        CtfClientLibrary libTimeoutCartao = new CtfClientLibrary() {
            private int passo = 0;

            @Override
            public void iniciaClientCTF(byte[] r, String t, String v, String n, String s, String l, String c, String lg, String i, String p) {
                BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
            }

            @Override
            public void iniciaTransacaoCTF(byte[] r, String o, String v, String d, String da, String nt) {
                BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
            }

            @Override
            public void iniciaTransacaoCTFext(byte[] r, String o, String v, String d, String da, String nt, String s) {
                BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
            }

            @Override
            public void continuaTransacaoCTF(byte[] r, byte[] cmd, byte[] numSc, byte[] pSc, byte[] tamSc, byte[] aux) {
                passo++;
                if (passo == 1) {
                    String msgCartao = "APROXIME, INSIRA;OU PASSE O CARTAO";
                    BufferNativoUtil.escreverBuffer(r, "99", (byte) 0);
                    BufferNativoUtil.escreverBuffer(cmd, "01", (byte) 0);
                    BufferNativoUtil.escreverBuffer(pSc, msgCartao, (byte) ' ');
                    BufferNativoUtil.escreverBuffer(tamSc, String.format("%05d", msgCartao.length()), (byte) 0);
                    return;
                }

                String cmdAnterior = BufferNativoUtil.extrairStringC(cmd);
                if (passo > 2) {
                    comandosRespondidosNo08.add(cmdAnterior);
                    if ("08".equals(cmdAnterior)) {
                        // Simula comportamento real do CTFClient: após receber 08, envia Comando 02 (OPERACAO CANCELADA?)
                        String tituloCanc = "OPERACAO CANCELADA?";
                        BufferNativoUtil.escreverBuffer(r, "99", (byte) 0);
                        BufferNativoUtil.escreverBuffer(cmd, "02", (byte) 0);
                        BufferNativoUtil.escreverBuffer(pSc, tituloCanc, (byte) ' ');
                        BufferNativoUtil.escreverBuffer(tamSc, String.format("%05d", tituloCanc.length()), (byte) 0);
                        return;
                    }
                    if ("02".equals(cmdAnterior)) {
                        // Em seguida, envia Comando 05 (1:SIM;2:NAO)
                        String menuCanc = "1:SIM;2:NAO";
                        BufferNativoUtil.escreverBuffer(r, "99", (byte) 0);
                        BufferNativoUtil.escreverBuffer(cmd, "05", (byte) 0);
                        BufferNativoUtil.escreverBuffer(pSc, menuCanc, (byte) ' ');
                        BufferNativoUtil.escreverBuffer(tamSc, String.format("%05d", menuCanc.length()), (byte) 0);
                        return;
                    }
                    if ("05".equals(cmdAnterior)) {
                        // Verifica que o CtfClientService respondeu "1" (SIM) automaticamente sem bloquear no callback
                        assertEquals("1", BufferNativoUtil.extrairStringC(pSc, 1));
                        BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
                        return;
                    }
                }

                try {
                    Thread.sleep(1100L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (passo == 3) {
                    // Na iteração após o aviso "VOCE AINDA ESTA AI?", simula que o cliente confirmou presença 1 vez
                    renovarNaPrimeiraPergunta[0] = true;
                } else {
                    renovarNaPrimeiraPergunta[0] = false;
                }

                BufferNativoUtil.escreverBuffer(r, "99", (byte) 0);
                BufferNativoUtil.escreverBuffer(cmd, "08", (byte) 0);
                BufferNativoUtil.escreverBuffer(pSc, "", (byte) ' ');
                BufferNativoUtil.escreverBuffer(tamSc, "00000", (byte) 0);
            }

            @Override
            public void finalizaTransacaoCTF(byte[] r, String c, String nt, String d) {
                BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
            }

            @Override
            public void finalizaTransacaoCTFext(byte[] r, String c, String nt, String dc, String d) {
                BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
            }
        };

        CtfClientService serviceTimeout = new CtfClientService(libTimeoutCartao, "20261115");
        serviceTimeout.setTimeoutLeituraCartaoSegundos(1);
        serviceTimeout.setAvisoPresencaCartaoSegundos(1);

        InteracaoTefCallback cbTimeout = new InteracaoTefCallback() {
            @Override
            public void exibirMensagem(String mensagem, String aux, boolean isTituloMenu) {
                mensagensExibidas.add(mensagem);
            }

            @Override
            public String confirmarOperacao(String mensagem) { return "1"; }

            @Override
            public String selecionarOpcaoMenu(String opcoes, String titulo, boolean isTemporizado) {
                throw new AssertionError("Nao deveria bloquear no menu OPERACAO CANCELADA?: " + titulo + " / " + opcoes);
            }

            @Override
            public boolean aguardarTecla(String mensagem) { return true; }

            @Override
            public String capturarDado(String numSc, int tamanhoMaximo, boolean aceitaZeros, String ultimaMensagem) { return "1"; }

            @Override
            public boolean confirmarPresencaLeituraCartao() {
                return renovarNaPrimeiraPergunta[0];
            }
        };

        TransacaoTefRequest req = new TransacaoTefRequest(CodigoOperacao.Debito, new BigDecimal("15.00"), "DOC01");
        int retLoop = serviceTimeout.executarLoopTransacao(req, new HashMap<String, String>(), cbTimeout);
        assertEquals(0, retLoop);
        assertTrue(mensagensExibidas.stream().anyMatch(m -> m.startsWith("VOCE AINDA ESTA AI?")));
        assertTrue(mensagensExibidas.stream().anyMatch(m -> m.startsWith("TEMPO RENOVADO")));
        assertTrue(mensagensExibidas.stream().anyMatch(m -> m.startsWith("TEMPO ESGOTADO PARA LEITURA DO CARTAO")));
        assertTrue(comandosRespondidosNo08.contains("08"));
        assertEquals("05", comandosRespondidosNo08.get(comandosRespondidosNo08.size() - 1));

        // 4. Cenário B: Quando a tela avança para "SOLICITE A SENHA", o timer do cartão é desativado e não cancela
        CtfClientLibrary libSenhaSemTimeout = new CtfClientLibrary() {
            private int passo = 0;

            @Override
            public void iniciaClientCTF(byte[] r, String t, String v, String n, String s, String l, String c, String lg, String i, String p) {}

            @Override
            public void iniciaTransacaoCTF(byte[] r, String o, String v, String d, String da, String nt) {}

            @Override
            public void iniciaTransacaoCTFext(byte[] r, String o, String v, String d, String da, String nt, String s) {}

            @Override
            public void continuaTransacaoCTF(byte[] r, byte[] cmd, byte[] numSc, byte[] pSc, byte[] tamSc, byte[] aux) {
                passo++;
                if (passo == 1) {
                    String msgCartao = "APROXIME, INSIRA;OU PASSE O CARTAO";
                    BufferNativoUtil.escreverBuffer(r, "99", (byte) 0);
                    BufferNativoUtil.escreverBuffer(cmd, "01", (byte) 0);
                    BufferNativoUtil.escreverBuffer(pSc, msgCartao, (byte) ' ');
                    BufferNativoUtil.escreverBuffer(tamSc, String.format("%05d", msgCartao.length()), (byte) 0);
                } else if (passo == 2) {
                    String msgSenha = "SOLICITE A SENHA";
                    BufferNativoUtil.escreverBuffer(r, "99", (byte) 0);
                    BufferNativoUtil.escreverBuffer(cmd, "01", (byte) 0);
                    BufferNativoUtil.escreverBuffer(pSc, msgSenha, (byte) ' ');
                    BufferNativoUtil.escreverBuffer(tamSc, String.format("%05d", msgSenha.length()), (byte) 0);
                } else if (passo == 3) {
                    try {
                        Thread.sleep(1500L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    BufferNativoUtil.escreverBuffer(r, "99", (byte) 0);
                    BufferNativoUtil.escreverBuffer(cmd, "08", (byte) 0);
                    BufferNativoUtil.escreverBuffer(pSc, "", (byte) ' ');
                    BufferNativoUtil.escreverBuffer(tamSc, "00000", (byte) 0);
                } else {
                    // Verifica que o Comando 08 na tela de senha respondeu "00" (não "08")
                    assertEquals("00", BufferNativoUtil.extrairStringC(cmd));
                    BufferNativoUtil.escreverBuffer(r, "00", (byte) 0);
                }
            }

            @Override
            public void finalizaTransacaoCTF(byte[] r, String c, String nt, String d) {}

            @Override
            public void finalizaTransacaoCTFext(byte[] r, String c, String nt, String dc, String d) {}
        };

        CtfClientService serviceSenha = new CtfClientService(libSenhaSemTimeout, "20261115");
        serviceSenha.setTimeoutLeituraCartaoSegundos(1);
        serviceSenha.setAvisoPresencaCartaoSegundos(0);
        assertEquals(0, serviceSenha.executarLoopTransacao(req, new HashMap<String, String>(), null));
    }

    private static void assertFalhaValidacao(CtfClientService service, TransacaoTefRequest request) {
        try {
            service.validarRequisicao(request);
            throw new AssertionError("Era esperada IllegalArgumentException para " + request.getOperacao());
        } catch (IllegalArgumentException esperado) {
            assertTrue(esperado.getMessage() != null && !esperado.getMessage().isEmpty());
        }
    }
}
