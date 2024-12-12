package com.senai.controledeacesso;

import java.util.UUID;

public class Usuario {
    private long id;
    private UUID idAcesso;
    private String nome;
    private String telefone;
    private String email;

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

    @Override
    public String toString() {
        return
                id + "    " + idAcesso +
                "     " + nome +
                "   " + telefone  +
                "  " + email;
    }
}

