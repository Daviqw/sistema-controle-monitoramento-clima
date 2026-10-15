package br.edu.ucsal.clima.service;

import br.edu.ucsal.clima.domain.entity.AuditLog;
import br.edu.ucsal.clima.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AuditoriaService {
    private final AuditLogRepository repository;
    public AuditoriaService(AuditLogRepository repository) { this.repository = repository; }
    @Transactional
    public void registrar(String acao, String entidade, String entidadeId, String detalhes, String ip) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setAcao(acao);
        log.setEntidade(entidade);
        log.setEntidadeId(entidadeId);
        log.setDetalhes(detalhes == null ? "{}" : detalhes);
        log.setIp(ip);
        repository.save(log);
    }
}
