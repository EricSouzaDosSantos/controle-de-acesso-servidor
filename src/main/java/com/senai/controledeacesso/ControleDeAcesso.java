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

    static String[][] matrizCadastro = {{"", ""}};
    public static String[][] matrizRegistrosDeAcesso = {{"", "", ""}};// inicia a matriz com uma linha e duas colunas com "" para que na primeira vez não apareça null na tabela de registros

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
                    aguardarCadastroDeIdAcesso();
                    break;
                case 7:
                    System.out.println("Fim do programa!");
                    break;
                default:
                    System.out.println("Opção inválida!");
            }

        } while (opcao != 7);
    }

    private static void aguardarCadastroDeIdAcesso() {
        modoCadastrarIdAcesso = true;
        System.out.println("Aguardando nova tag ou cartão para associar ao usuário");
        // Usar Future para aguardar até que o cadastro de ID seja concluído
        Future<?> future = executorCadastroIdAcesso.submit(() -> {
            while (modoCadastrarIdAcesso) {
                // Loop em execução enquanto o modoCadastrarIdAcesso estiver ativo
                try {
                    Thread.sleep(100); // Evita uso excessivo de CPU
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        try {
            future.get(); // Espera até que o cadastro termine
        } catch (Exception e) {
            System.err.println("Erro ao aguardar cadastro: " + e.getMessage());
        }
    }

    private static void processarMensagemMQTTRecebida(String mensagem) {
        System.out.println("mensagem recebida: "+mensagem);
        if (!modoCadastrarIdAcesso) {
            executorIdentificarAcessos.submit(() -> RegistroDeAcesso.criarNovoRegistroDeAcesso(mensagem, Usuario.getListaUsuarios())); // Processa em thread separada
        } else {
            cadastrarNovoIdAcesso(mensagem); // Processa em thread separada
            modoCadastrarIdAcesso = false;
            idUsuarioRecebidoPorHTTP = 0;
        }
    }

    private static void cadastrarNovoIdAcesso(String novoIdAcesso) {
        boolean encontrado = false;
        String idUsuarioEscolhido = String.valueOf(idUsuarioRecebidoPorHTTP);
        String dispositivoEscolhido = dispositivoRecebidoPorHTTP;

        List<Usuario> listaDeUsuarios = Usuario.getListaUsuarios();


        if (idUsuarioRecebidoPorHTTP == 0) {
            for (Usuario usuario : listaDeUsuarios) {
                System.out.println(usuario.getId() + " - " + usuario.getNome());
            }
            System.out.print("Digite o ID do usuário para associar ao novo idAcesso: ");
            idUsuarioEscolhido = scanner.nextLine();
            conexaoMQTT.publicarMensagem(topico, dispositivoEscolhido);
            System.out.println("publicou a mensagem");
        }

        modoCadastrarIdAcesso = true;
        System.out.println("saiu do publicar");

        for(Usuario usuario: listaDeUsuarios){
            System.out.println("Comparando id");
            System.out.println("novoIdAcesso: "+novoIdAcesso);
            if(usuario.getId() == Integer.parseInt(idUsuarioEscolhido)){
                System.out.println("vai pegar o uuid");

                usuario.setIdAcesso(UUID.fromString(novoIdAcesso));
                System.out.println("Pegou o uuid");
                System.out.println("id de acesso " + novoIdAcesso + " associado ao usuário " + usuario.getNome());
                conexaoMQTT.publicarMensagem("cadastro/disp", "CadastroConcluido");
                encontrado = true;
                Usuario.inserirUsuariosNoArquivo(gerenciarArquivo.getArquivoBancoDeDados());
                break;
            }
            System.out.println("Comparou");
        }
        // Se não encontrou o usuário, imprime uma mensagem
        if (!encontrado) {
            System.out.println("Usuário com id" + idUsuarioEscolhido + " não encontrado.");
        }
    }
}
