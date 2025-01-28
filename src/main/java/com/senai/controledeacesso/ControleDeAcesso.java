package com.senai.controledeacesso;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ControleDeAcesso {
    // Caminho para a pasta ControleDeAcesso no diretório do usuário
    private static final GerenciarArquivo gerenciarArquivo = new GerenciarArquivo();
    // Caminho para o arquivo bancoDeDados.txt e para a pasta imagens
    public static final File pastaImagens = gerenciarArquivo.getArquivoImagens();

    static volatile boolean modoCadastrarIdAcesso = false;
    static int idUsuarioRecebidoPorHTTP = 0;
    static String dispositivoRecebidoPorHTTP = "Disp1";

    static String brokerUrl = "tcp://localhost:1883";  // Exemplo de
    static String topico = "IoTKIT1/UID";

    static CLienteMQTT conexaoMQTT;
    static ServidorHTTPS servidorHTTPS;
    static Scanner scanner = new Scanner(System.in);
    static ExecutorService executorIdentificarAcessos = Executors.newFixedThreadPool(4);
    static ExecutorService executorCadastroIdAcesso = Executors.newSingleThreadExecutor();

    public static void main(String[] args) {
        gerenciarArquivo.verificarEstruturaDeDiretorios();
        Usuario.carregarUsuarios(gerenciarArquivo.getArquivoBancoDeDados());
        RegistroDeAcesso.carregarDadosDoArquivoRegistroDeAcesso(gerenciarArquivo.getArquivoRegistroDeAcesso());
        conexaoMQTT = new CLienteMQTT(brokerUrl, topico, ControleDeAcesso::processarMensagemMQTTRecebida);
        servidorHTTPS = new ServidorHTTPS(); // Inicia o servidor HTTPS
        menuPrincipal();

        // Finaliza o todos os processos abertos ao sair do programa
        scanner.close();
        executorIdentificarAcessos.shutdown();
        executorCadastroIdAcesso.shutdown();
        conexaoMQTT.desconectar();
        servidorHTTPS.pararServidorHTTPS();
    }

    private static void menuPrincipal() {
        int opcao;
        do {
            String menu = """
                    _________________________________________________________
                    |   Escolha uma opção:                                  |
                    |       1- Exibir cadastro completo                     |
                    |       2- Inserir novo cadastro                        |
                    |       3- Atualizar cadastro por id                    |
                    |       4- Deletar um cadastro por id                   |
                    |       5- Exibir horários de acesso                    |
                    |       6- Associar TAG ou cartão de acesso ao usuário  |
                    |       7- Sair                                         |
                    _________________________________________________________
                    """;
            System.out.println(menu);
            opcao = scanner.nextInt();
            scanner.nextLine();

            switch (opcao) {
                case 1:
                    Usuario.exibirUsuarios();
                    break;
                case 2:
                    Usuario.cadastrarUsuario(scanner);
                    break;
                case 3:
                    Usuario.atualizarUsuario(scanner);
                    break;
                case 4:
                    Usuario.deletarUsuario(scanner);
                    break;
                case 5:
                    RegistroDeAcesso.exibirAcesso(scanner);
                    break;
                case 6:
                    Usuario.aguardarCadastroDeIdAcesso();
                    break;
                case 7:
                    System.out.println("Fim do programa!");
                    break;
                default:
                    System.out.println("Opção inválida!");
            }

        } while (opcao != 7);
    }

    private static void processarMensagemMQTTRecebida(String mensagem) {
        System.out.println("mensagem recebida: "+mensagem);
        if (!modoCadastrarIdAcesso) {
            executorIdentificarAcessos.submit(() -> RegistroDeAcesso.criarNovoRegistroDeAcesso(mensagem, Usuario.getListaUsuarios())); // Processa em thread separada
        } else {
            Usuario.cadastrarNovoIdAcesso(mensagem, scanner); // Processa em thread separada
            modoCadastrarIdAcesso = false;
            idUsuarioRecebidoPorHTTP = 0;
        }
    }


}
