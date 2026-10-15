-- Adequacao ao PDF v03: um DMC pode possuir VARIAS redes.
-- Esta migration preserva todos os registros existentes.
ALTER TABLE rede_monitoramento DROP CONSTRAINT IF EXISTS rede_monitoramento_dmc_id_key;
CREATE INDEX IF NOT EXISTS idx_rede_dmc ON rede_monitoramento(dmc_id);
-- Exemplo adicional de redes solicitado no enunciado (sem duplicar existentes).
INSERT INTO rede_monitoramento(sigla,descricao,dmc_id)
SELECT 'RADAR-LITORAL','Rede de Radares Meteorológicos do Litoral',id FROM dmc WHERE nome='5º DISME'
ON CONFLICT (sigla) DO NOTHING;
INSERT INTO rede_monitoramento(sigla,descricao,dmc_id)
SELECT 'CHUVA-SEMIARIDO','Rede de Monitoramento de Chuvas do Semiárido',id FROM dmc WHERE nome='5º DISME'
ON CONFLICT (sigla) DO NOTHING;
INSERT INTO rede_monitoramento(sigla,descricao,dmc_id)
SELECT 'CONV-NEABA','Rede de Estações Convencionais',id FROM dmc WHERE nome='NEA-BA'
ON CONFLICT (sigla) DO NOTHING;
INSERT INTO rede_tipo_monitoramento(rede_id,tipo)
SELECT id,'RADAR' FROM rede_monitoramento WHERE sigla='RADAR-LITORAL' ON CONFLICT DO NOTHING;
INSERT INTO rede_tipo_monitoramento(rede_id,tipo)
SELECT id,'PRECIPITACAO' FROM rede_monitoramento WHERE sigla='CHUVA-SEMIARIDO' ON CONFLICT DO NOTHING;
INSERT INTO rede_tipo_monitoramento(rede_id,tipo)
SELECT id,'CONVENCIONAL' FROM rede_monitoramento WHERE sigla='CONV-NEABA' ON CONFLICT DO NOTHING;
