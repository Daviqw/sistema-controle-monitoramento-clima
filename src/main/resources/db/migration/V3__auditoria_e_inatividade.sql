-- Não altera nem apaga os dados já existentes.
-- Auditoria no banco registra operações mesmo quando executadas fora dos controllers.
CREATE OR REPLACE FUNCTION clima_registrar_mutacao() RETURNS trigger AS $$
DECLARE
  registro_id UUID;
  acao_evento VARCHAR(40);
  status_anterior TEXT;
  status_novo TEXT;
BEGIN
  registro_id := CASE WHEN TG_OP='DELETE' THEN OLD.id ELSE NEW.id END;
  acao_evento := CASE WHEN TG_OP='INSERT' THEN 'CREATE' WHEN TG_OP='DELETE' THEN 'DELETE' ELSE 'UPDATE' END;
  IF TG_OP='UPDATE' THEN
    status_anterior := to_jsonb(OLD)->>'status';
    status_novo := to_jsonb(NEW)->>'status';
    IF status_anterior IS DISTINCT FROM status_novo THEN
      IF status_novo IN ('INATIVO','INATIVA') THEN
        acao_evento := 'INACTIVATE';
        INSERT INTO historico_inatividade(tipo_entidade,entidade_id,motivo)
        VALUES(TG_TABLE_NAME,registro_id,COALESCE(NULLIF(to_jsonb(NEW)->>'justificativa_inativacao',''),'Inativacao administrativa'));
      ELSIF status_novo='APROVADA' THEN acao_evento:='APPROVE';
      ELSIF status_novo='REPROVADA' THEN acao_evento:='REJECT';
      ELSIF status_novo='INSTALADA' THEN acao_evento:='INSTALL';
      END IF;
    END IF;
  END IF;
  INSERT INTO audit_log(acao,entidade,entidade_id,detalhes)
  VALUES(acao_evento,TG_TABLE_NAME,registro_id::text,
    jsonb_build_object('operacao',TG_OP,'status_anterior',status_anterior,'status_novo',status_novo));
  RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

DO $$ DECLARE tabela TEXT; BEGIN
  FOREACH tabela IN ARRAY ARRAY['ogm','dmc','rede_monitoramento','tecnico','estacao_meteorologica',
    'solicitacao_uso_estacao','solicitacao_instalacao','perfil_usuario_final'] LOOP
    EXECUTE format('CREATE TRIGGER clima_audit_%I AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION clima_registrar_mutacao()',tabela,tabela);
  END LOOP;
END $$;
CREATE INDEX IF NOT EXISTS idx_uso_conflito_aprovada
 ON solicitacao_uso_estacao(estacao_id,data_uso,hora_inicial,hora_final) WHERE status='APROVADA';
