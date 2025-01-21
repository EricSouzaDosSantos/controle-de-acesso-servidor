package com.senai.controledeacesso;

import java.io.*;
import java.util.*;

public class Usuario {
    private static final List<Usuario> usuarios = new ArrayList<>();
    private long id;
    private UUID idAcesso;
    private String nome;
    private String telefone;
    private String email;

    public Usuario(long id, UUID idAcesso, String nome, String telefone, String email) {
        this.id = id;
        this.idAcesso = idAcesso;
        this.nome = nome;
        this.telefone = telefone;
        this.email = email;
    }

    public long getId() {
        return id;
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

    public static void carregarUsuarios(File arquivo) {
        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                if (!linha.trim().isEmpty()) {
                    String[] dados = linha.split("\\s+");
                    Usuario usuario = new Usuario(
                            Long.parseLong(dados[0]),
                            dados[1].equals("null") ? null : UUID.fromString(dados[1]),
                            dados[2],
                            dados[3],
                            dados[4]
                    );
                    usuarios.add(usuario);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao carregar usuários", e);
        }
    }

    public static void exibirUsuarios() {
        System.out.println("ID | Id Acesso | Nome | Telefone | Email");
        for (Usuario usuario : usuarios) {
            System.out.println(usuario);
        }
    }

    public static void cadastrarUsuario(Scanner scanner) {
        System.out.print("Digite o nome: ");
        String nome = scanner.nextLine();
        System.out.print("Digite o telefone: ");
        String telefone = scanner.nextLine();
        System.out.print("Digite o email: ");
        String email = scanner.nextLine();

        Usuario usuario = new Usuario((long) (usuarios.size() + 1), null, nome, telefone, email);
        usuarios.add(usuario);

        System.out.println("Usuário cadastrado com sucesso: " + usuario);
    }

    private static Usuario buscarUsuarioPorId(long id) {
        return usuarios.stream()
                .filter(usuario -> usuario.getId() == id)
                .findFirst()
                .orElse(null);
    }


    public static void atualizarUsuario(Scanner scanner) {
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
        } else {
            System.out.println("Usuário não encontrado.");
        }
    }

    public static void deletarUsuario(Scanner scanner) {
        exibirUsuarios();
        System.out.print("Digit o ID do usuário a ser deletado: ");
        long id = scanner.nextLong();
        scanner.nextLine();

        Usuario usuario = buscarUsuarioPorId(id);

        if (usuarios.remove(usuario)) {
            System.out.println("Usuário deletado com sucesso.");
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

