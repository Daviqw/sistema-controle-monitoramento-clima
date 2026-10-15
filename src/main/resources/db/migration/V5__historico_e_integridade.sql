-- Novas alterações preservam as migrations e os dados anteriores.
ALTER TABLE tecnico ADD COLUMN justificativa_inativacao TEXT;
ALTER TABLE estacao_meteorologica ADD COLUMN justificativa_inativacao TEXT;
ALTER TABLE solicitacao_uso_estacao ADD COLUMN justificativa_inativacao TEXT;
ALTER TABLE solicitacao_instalacao ADD COLUMN justificativa_inativacao TEXT;
CREATE UNIQUE INDEX uq_usuario_email_normalizado ON usuario(lower(email));

CREATE OR REPLACE FUNCTION clima_registrar_mutacao() RETURNS trigger AS $$
DECLARE
  registro_id UUID;
  acao_evento VARCHAR(40);
  status_anterior TEXT;
  status_novo TEXT;
  responsavel UUID;
BEGIN
  responsavel := NULLIF(current_setting('clima.usuario_id',true),'')::UUID;
  registro_id := CASE WHEN TG_OP='DELETE' THEN OLD.id ELSE NEW.id END;
  acao_evento := CASE WHEN TG_OP='INSERT' THEN 'CREATE' WHEN TG_OP='DELETE' THEN 'DELETE' ELSE 'UPDATE' END;
  IF TG_TABLE_NAME='usuario' AND TG_OP='UPDATE' AND (to_jsonb(OLD)->>'ativo')='true' AND (to_jsonb(NEW)->>'ativo')='false' THEN
    acao_evento := 'INACTIVATE';
  END IF;
  IF TG_OP='UPDATE' THEN
    status_anterior := to_jsonb(OLD)->>'status';
    status_novo := to_jsonb(NEW)->>'status';
    IF status_anterior IS DISTINCT FROM status_novo THEN
      IF status_novo IN ('INATIVO','INATIVA','MANUTENCAO') THEN
        acao_evento := 'INACTIVATE';
        INSERT INTO historico_inatividade(tipo_entidade,entidade_id,motivo,usuario_responsavel_id)
        VALUES(TG_TABLE_NAME,registro_id,COALESCE(NULLIF(to_jsonb(NEW)->>'justificativa_inativacao',''),'Inativacao administrativa'),responsavel);
      ELSIF status_novo='APROVADA' THEN acao_evento:='APPROVE';
      ELSIF status_novo='REPROVADA' THEN acao_evento:='REJECT';
      ELSIF status_novo='INSTALADA' THEN acao_evento:='INSTALL';
      ELSIF status_novo='FINALIZADA' THEN acao_evento:='RELEASE';
      END IF;
    END IF;
  END IF;
  INSERT INTO audit_log(acao,entidade,entidade_id,usuario_id,detalhes)
  VALUES(acao_evento,TG_TABLE_NAME,registro_id::text,responsavel,
    jsonb_build_object('operacao',TG_OP,'status_anterior',status_anterior,'status_novo',status_novo));
  RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER clima_audit_usuario AFTER INSERT OR UPDATE OR DELETE ON usuario
FOR EACH ROW EXECUTE FUNCTION clima_registrar_mutacao();
