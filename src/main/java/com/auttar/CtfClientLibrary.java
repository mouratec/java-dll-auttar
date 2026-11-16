package com.auttar;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Ponte JNA (Java Native Access) para a biblioteca nativa CTFClient da Auttar
 * ({@code ctfclient.dll} no Windows / {@code libctfclient.so} no Linux).
 *
 * <p>Mapeia as 6 funções nativas responsáveis por inicializar o cliente,
 * abrir uma transação, conduzir o loop interativo e confirmar/desfazer a operação.</p>
 */
public interface CtfClientLibrary extends Library {

    static String getLibraryPath() {
        return resolveLibraryPath(
                System.getProperty("jna.library.path"),
                System.getenv("CTFCLIENT_HOME"),
                Platform.isWindows());
    }

    /**
     * Resolve o caminho da biblioteca nativa na seguinte ordem de prioridade:
     * <ol>
     *   <li>Propriedade {@code jna.library.path} (se o arquivo existir nela)</li>
     *   <li>Variável de ambiente {@code CTFCLIENT_HOME} ({@code /bin/<lib>}, {@code /lib/<lib>} ou raiz)</li>
     *   <li>Diretório atual de execução (modo portátil)</li>
     *   <li>Caminhos padrões do sistema operacional</li>
     * </ol>
     */
    static String resolveLibraryPath(String jnaPath, String ctfClientHome, boolean isWindows) {
        String libName = isWindows ? "ctfclient.dll" : "libctfclient.so";

        if (BufferNativoUtil.naoVazio(jnaPath)) {
            File forcedLib = new File(jnaPath.trim(), libName);
            if (forcedLib.exists()) {
                return forcedLib.getAbsolutePath();
            }
        }

        if (BufferNativoUtil.naoVazio(ctfClientHome)) {
            File homeDir = new File(ctfClientHome.trim());
            File inBin = new File(new File(homeDir, "bin"), libName);
            if (inBin.exists()) {
                return inBin.getAbsolutePath();
            }
            if (!isWindows) {
                File inLib = new File(new File(homeDir, "lib"), libName);
                if (inLib.exists()) {
                    return inLib.getAbsolutePath();
                }
            }
            File inRoot = new File(homeDir, libName);
            if (inRoot.exists()) {
                return inRoot.getAbsolutePath();
            }
            return inBin.getAbsolutePath();
        }

        File localLib = new File(libName);
        if (localLib.exists()) {
            return localLib.getAbsolutePath();
        }

        if (isWindows) {
            File ctfRaiz = new File("C:\\CTFClient\\bin", libName);
            if (ctfRaiz.exists()) {
                return ctfRaiz.getAbsolutePath();
            }
            File progFilesX86 = new File("C:\\Program Files (x86)\\Auttar\\CTFClient\\bin", libName);
            if (progFilesX86.exists()) {
                return progFilesX86.getAbsolutePath();
            }
            File progFiles = new File("C:\\Program Files\\Auttar\\CTFClient\\bin", libName);
            if (progFiles.exists()) {
                return progFiles.getAbsolutePath();
            }
            return ctfRaiz.getAbsolutePath();
        }

        File linuxBin = new File("/opt/CTFClient/bin", libName);
        if (linuxBin.exists()) {
            return linuxBin.getAbsolutePath();
        }
        return "/opt/CTFClient/lib/" + libName;
    }

    static Map<String, Object> createLibraryOptions() {
        Map<String, Object> options = new HashMap<>();
        if (Platform.isWindows()) {
            options.put(Library.OPTION_CALLING_CONVENTION, Function.ALT_CONVENTION);
        }
        return options;
    }

    String LIBRARY_PATH = getLibraryPath();
    Map<String, Object> LIBRARY_OPTIONS = createLibraryOptions();

    /** Instância lazy/thread-safe para permitir testes unitários sem a DLL física. */
    final class Holder {
        private static volatile CtfClientLibrary instance;

        private Holder() {
        }

        public static CtfClientLibrary getInstance() {
            if (instance == null) {
                synchronized (Holder.class) {
                    if (instance == null) {
                        TefAuditLogger.log("init", "Carregando biblioteca nativa -> " + LIBRARY_PATH);
                        instance = Native.load(LIBRARY_PATH, CtfClientLibrary.class, LIBRARY_OPTIONS);
                    }
                }
            }
            return instance;
        }
    }

    static CtfClientLibrary getInstance() {
        return Holder.getInstance();
    }

    void iniciaClientCTF(
            byte[] resultado,
            String terminal,
            String versao,
            String nome_ac,
            String num_sites,
            String lista_ips,
            String criptografia,
            String log,
            String interativo,
            String parametros);

    void iniciaTransacaoCTF(
            byte[] resultado,
            String operacao,
            String valor,
            String num_doc,
            String data_cli,
            String num_trans);

    void iniciaTransacaoCTFext(
            byte[] resultado,
            String operacao,
            String valor,
            String num_doc,
            String data_cli,
            String num_trans,
            String dados);

    void continuaTransacaoCTF(
            byte[] resultado,
            byte[] comando,
            byte[] num_sc,
            byte[] p_sc,
            byte[] tam_sc,
            byte[] aux);

    void finalizaTransacaoCTF(
            byte[] resultado,
            String confirmar,
            String num_trans,
            String data_cli);

    void finalizaTransacaoCTFext(
            byte[] resultado,
            String confirmar,
            String num_trans,
            String data_cli,
            String dados);
}
