package br.edu.ucsal.clima.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter @Setter @NoArgsConstructor
public class AuditLog {
    @Id private UUID id;
    @Column(name = "data_hora", nullable = false) private OffsetDateTime dataHora;
    @Column(name = "usuario_id") private UUID usuarioId;
    @Column(length = 30) private String perfil;
    @Column(nullable = false, length = 40) private String acao;
    @Column(nullable = false, length = 80) private String entidade;
    @Column(name = "entidade_id", length = 80) private String entidadeId;
    @Column(nullable = false, columnDefinition = "jsonb") private String detalhes = "{}";
    @Column(columnDefinition = "inet") private String ip;
    @PrePersist void preparar() {
        if (id == null) id = UUID.randomUUID();
        if (dataHora == null) dataHora = OffsetDateTime.now();
        if (detalhes == null) detalhes = "{}";
    }
}
