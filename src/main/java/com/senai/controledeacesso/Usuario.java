package com.senai.controledeacesso;

import java.io.*;
import java.util.*;
import java.util.concurrent.Future;

public class Usuario {
    private static final List<Usuario> listaDeUsuarios = new ArrayList<>();
    private long id;
    private UUID idAcesso;
    private String nome;
    private String telefone;
    private String email;
    private String caminhoImagem;

    public Usuario() {}

    public Usuario(long id, UUID idAcesso, String nome, String telefone, String email) {
        this.id = id;
        this.idAcesso = idAcesso;
        this.nome = nome;
        this.telefone = telefone;
        this.email = email;
    }

    public Usuario(long id, UUID idAcesso, String nome, String telefone, String email, String caminhoImagem) {
        this.id = id;
        this.idAcesso = idAcesso;
        this.nome = nome;
        this.telefone = telefone;
        this.email = email;
        this.caminhoImagem = caminhoImagem;
    }

    public long getId() {
        return id;
    }

    public static List<Usuario> getListaUsuarios() {
        return listaDeUsuarios;
    }

    public void setId(long id) {
        this.id = id;
    }

    public UUID getIdAcesso() {
        return idAcesso;
    }

    public void setIdAcesso(UUID idAcesso) {
        this.idAcesso = idAcesso;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCaminhoImagem() {
        return caminhoImagem;
    }

    public void setCaminhoImagem(String caminhoImagem) {
        this.caminhoImagem = caminhoImagem;
    }

//    public static void carregarUsuarios(File arquivo) {
//        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
//            String linha;
//            while ((linha = reader.readLine()) != null) {
//                if (!linha.trim().isEmpty()) {
//                    String[] dados = linha.split(" | ");
//                    Usuario usuario = new Usuario(
//                            Long.parseLong(dados[0]),
//                            dados[1].equals("null") ? null : UUID.fromString(dados[1]),
//                            dados[2],
//                            dados[3],
//                            dados[4],
//                            dados[5].equals("null") ? null : dados[5]
//                    );
//                    listaDeUsuarios.add(usuario);
//                }
//            }
//        } catch (IOException e) {
//            throw new RuntimeException("Erro ao carregar usuários", e);
//        }
//    }

    public static void carregarUsuarios(File arquivo) {
        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                if (!linha.trim().isEmpty()) {
                    // Divide a linha usando " | " como separador literal
                    String[] dados = linha.split("\\s*\\|\\s*");

                    Usuario usuario = new Usuario(
                            Long.parseLong(dados[0]),
                            "null".equals(dados[1]) ? null : UUID.fromString(dados[1]),
                            dados[2],
                            dados[3],
                            dados[4],
                            "null".equals(dados[5]) ? null : dados[5]
                    );

                    listaDeUsuarios.add(usuario);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao carregar usuários", e);
        }
    }




    public static void inserirUsuariosNoArquivo(File arquivo) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(arquivo))) {
            for (Usuario usuario : listaDeUsuarios) {
                String linha = usuario.getId() + " | " +
                        (usuario.getIdAcesso() == null ? "null" : usuario.getIdAcesso().toString()) + " | " +
                        usuario.getNome() + " | " +
                        usuario.getTelefone() + " | " +
                        usuario.getEmail() + " | " +
                        usuario.getCaminhoImagem();
                writer.write(linha);
                writer.newLine();
            }
            System.out.println("Usuários salvos no arquivo com sucesso.");
        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar usuários no arquivo", e);
        }
    }


    public static void exibirUsuarios() {
        System.out.println("ID | Id Acesso | Nome | Telefone | Email");
        for (Usuario usuario : listaDeUsuarios) {
            System.out.println(usuario);
        }
    }

    public static void cadastrarUsuario(Scanner scanner) {
        GerenciarArquivo gerenciarArquivo = new GerenciarArquivo();

        System.out.print("Digite o nome: ");
        String nome = scanner.nextLine();
        System.out.print("Digite o telefone: ");
        String telefone = scanner.nextLine();
        System.out.print("Digite o email: ");
        String email = scanner.nextLine();

        Usuario usuario = new Usuario((long) (listaDeUsuarios.size() + 1), null, nome, telefone, email);
        listaDeUsuarios.add(usuario);
        inserirUsuariosNoArquivo(gerenciarArquivo.getArquivoBancoDeDados());

        System.out.println("Usuário cadastrado com sucesso: " + usuario);
    }

    public static void aguardarCadastroDeIdAcesso() {
        ControleDeAcesso.modoCadastrarIdAcesso = true;
        System.out.println("Aguardando nova tag ou cartão para associar ao usuário");
        // Usar Future para aguardar até que o cadastro de ID seja concluído
        Future<?> future = ControleDeAcesso.executorCadastroIdAcesso.submit(() -> {
            while (ControleDeAcesso.modoCadastrarIdAcesso) {
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

    public static void cadastrarNovoIdAcesso(String novoIdAcesso, Scanner scanner) {
        boolean encontrado = false;
        String idUsuarioEscolhido = String.valueOf(ControleDeAcesso.idUsuarioRecebidoPorHTTP);
        String dispositivoEscolhido = ControleDeAcesso.dispositivoRecebidoPorHTTP;
        GerenciarArquivo gerenciarArquivo = new GerenciarArquivo();

        List<Usuario> listaDeUsuarios = Usuario.getListaUsuarios();


        if (ControleDeAcesso.idUsuarioRecebidoPorHTTP == 0) {
            for (Usuario usuario : listaDeUsuarios) {
                System.out.println(usuario.getId() + " - " + usuario.getNome());
            }
            System.out.print("Digite o ID do usuário para associar ao novo idAcesso: ");
            idUsuarioEscolhido = scanner.nextLine();
            ControleDeAcesso.conexaoMQTT.publicarMensagem(ControleDeAcesso.topico, dispositivoEscolhido);
            System.out.println("publicou a mensagem");
        }

        ControleDeAcesso.modoCadastrarIdAcesso = true;
        System.out.println("saiu do publicar");

        for(Usuario usuario: listaDeUsuarios){
            System.out.println("Comparando id");
            System.out.println("novoIdAcesso: "+novoIdAcesso);
            if(usuario.getId() == Integer.parseInt(idUsuarioEscolhido)){
                System.out.println("vai pegar o uuid");

                usuario.setIdAcesso(UUID.fromString(novoIdAcesso));
                System.out.println("Pegou o uuid");
                System.out.println("id de acesso " + novoIdAcesso + " associado ao usuário " + usuario.getNome());
                ControleDeAcesso.conexaoMQTT.publicarMensagem("cadastro/disp", "CadastroConcluido");
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

    public static Usuario buscarUsuarioPorId(long id) {
        return listaDeUsuarios.stream()
                .filter(usuario -> usuario.getId() == id)
                .findFirst()
                .orElse(null);
    }


    public static void atualizarUsuario(Scanner scanner) {
        GerenciarArquivo gerenciarArquivo = new GerenciarArquivo();
        exibirUsuarios();
        System.out.print("Digite o ID do usuário a ser atualizado: ");
        long id = scanner.nextLong();
        scanner.nextLine();

        Usuario usuario = buscarUsuarioPorId(id);

        if (usuario != null) {
            System.out.print("Novo nome (atual: " + usuario.getNome() + "): ");
            usuario.setNome(scanner.nextLine());
            System.out.print("Novo telefone (atual: " + usuario.getTelefone() + "): ");
            usuario.setTelefone(scanner.nextLine());
            System.out.print("Novo email (atual: " + usuario.getEmail() + "): ");
            usuario.setEmail(scanner.nextLine());

            System.out.println("Usuário atualizado com sucesso: " + usuario);

            listaDeUsuarios.set((int) id-1, usuario);
            inserirUsuariosNoArquivo(gerenciarArquivo.getArquivoBancoDeDados());
        } else {
            System.out.println("Usuário não encontrado.");
        }
    }

    public static void deletarUsuario(Scanner scanner) {
        GerenciarArquivo gerenciarArquivo = new GerenciarArquivo();

        exibirUsuarios();
        System.out.print("Digit o ID do usuário a ser deletado: ");
        long id = scanner.nextLong();
        scanner.nextLine();

        Usuario usuario = buscarUsuarioPorId(id);

        if (listaDeUsuarios.remove(usuario)) {
            System.out.println("Usuário deletado com sucesso.");
            inserirUsuariosNoArquivo(gerenciarArquivo.getArquivoBancoDeDados());
        } else {
            System.out.println("Usuário não encontrado.");
        }
    }
    public static void deletarUsuario(long id) {
        GerenciarArquivo gerenciarArquivo = new GerenciarArquivo();

        Usuario usuario = buscarUsuarioPorId(id);

        if (listaDeUsuarios.remove(usuario)) {
            System.out.println("Usuário deletado com sucesso.");
            inserirUsuariosNoArquivo(gerenciarArquivo.getArquivoBancoDeDados());
        } else {
            System.out.println("Usuário não encontrado.");
        }
    }

    @Override
    public String toString() {
        return id +
                " | " + (idAcesso == null ? "null" : idAcesso) +
                " | " + nome +
                " | " + telefone +
                " | " + email;

    }
}

