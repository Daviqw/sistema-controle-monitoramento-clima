package br.edu.ucsal.clima.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "usuario")
@Getter @Setter @NoArgsConstructor
public class Usuario {
    @Id
    private UUID id;
    @Column(nullable = false, length = 160)
    private String nome;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;
    @Column(nullable = false)
    private boolean ativo = true;
    @Column(name = "data_cadastro", nullable = false)
    private OffsetDateTime dataCadastro;
    @Column(name = "data_atualizacao", nullable = false)
    private OffsetDateTime dataAtualizacao;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuario_perfil",
      joinColumns = @JoinColumn(name = "usuario_id"),
      inverseJoinColumns = @JoinColumn(name = "perfil_id"))
    private Set<Perfil> perfis = new HashSet<>();

    @PrePersist
    void antesDeInserir() {
        if (id == null) id = UUID.randomUUID();
        if (dataCadastro == null) dataCadastro = OffsetDateTime.now();
        if (dataAtualizacao == null) dataAtualizacao = dataCadastro;
    }
    @PreUpdate void antesDeAtualizar() { dataAtualizacao = OffsetDateTime.now(); }
}
