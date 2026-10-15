-- Perfis e permissões base. Senhas iniciais são provisionadas em runtime, nunca em migration.
INSERT INTO perfil (nome, descricao) VALUES
    ('ADMINISTRADOR', 'Administra cadastros, perfis, auditoria e relatórios'),
    ('COLABORADOR', 'Gerencia estações e decide solicitações'),
    ('USUARIO', 'Solicita uso e instalação de itens em estações')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO permissao (codigo, descricao) VALUES
    ('OGM_GERENCIAR', 'Gerenciar organismos'),
    ('DMC_GERENCIAR', 'Gerenciar centros meteorológicos'),
    ('REDE_GERENCIAR', 'Gerenciar redes'),
    ('TECNICO_GERENCIAR', 'Gerenciar técnicos'),
    ('USUARIO_GERENCIAR', 'Gerenciar usuários e perfis'),
    ('AUDITORIA_CONSULTAR', 'Consultar auditoria'),
    ('ESTACAO_GERENCIAR', 'Gerenciar estações'),
    ('SOLICITACAO_DECIDIR', 'Aprovar e reprovar solicitações'),
    ('SOLICITACAO_PROPRIA', 'Gerenciar solicitações próprias'),
    ('RELATORIO_CONSULTAR', 'Consultar relatórios')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO perfil_permissao (perfil_id, permissao_id)
SELECT p.id, pe.id FROM perfil p CROSS JOIN permissao pe
WHERE (p.nome = 'ADMINISTRADOR')
   OR (p.nome = 'COLABORADOR' AND pe.codigo IN ('ESTACAO_GERENCIAR','SOLICITACAO_DECIDIR','RELATORIO_CONSULTAR'))
   OR (p.nome = 'USUARIO' AND pe.codigo IN ('SOLICITACAO_PROPRIA','RELATORIO_CONSULTAR'))
ON CONFLICT DO NOTHING;

INSERT INTO ogm (sigla, nome, email_central, telefone_central, diretor_geral_nome) VALUES
    ('INMET', 'Instituto Nacional de Meteorologia', 'contato@inmet.gov.br', NULL, 'Diretor-Geral do INMET'),
    ('CPTEC-INPE', 'Centro de Previsão de Tempo e Estudos Climáticos / INPE', 'contato@cptec.inpe.br', NULL, 'Diretor-Geral do CPTEC/INPE'),
    ('CEMADEN', 'Centro Nacional de Monitoramento e Alertas de Desastres Naturais', 'contato@cemaden.gov.br', NULL, 'Diretor-Geral do CEMADEN'),
    ('SIMBA', 'Sistema Integrado de Monitoramento de Bacias', NULL, NULL, 'Diretor-Geral do SIMBA')
ON CONFLICT (sigla) DO NOTHING;

INSERT INTO dmc (ogm_id, nome, email_central, meteorologista_chefe_nome)
SELECT id, '5º DISME', 'disme5@inmet.gov.br', 'Chefia do 5º DISME' FROM ogm WHERE sigla = 'INMET'
ON CONFLICT (ogm_id, nome) DO NOTHING;
INSERT INTO dmc (ogm_id, nome, email_central, meteorologista_chefe_nome)
SELECT id, 'DMC-REC', NULL, 'Chefia do DMC-REC' FROM ogm WHERE sigla = 'INMET'
ON CONFLICT (ogm_id, nome) DO NOTHING;
INSERT INTO dmc (ogm_id, nome, meteorologista_chefe_nome)
SELECT id, 'DMC-OB', 'Chefia do DMC-OB' FROM ogm WHERE sigla = 'INMET'
ON CONFLICT (ogm_id, nome) DO NOTHING;
INSERT INTO dmc (ogm_id, nome, meteorologista_chefe_nome)
SELECT id, 'DMC-LS', 'Chefia do DMC-LS' FROM ogm WHERE sigla = 'INMET'
ON CONFLICT (ogm_id, nome) DO NOTHING;
INSERT INTO dmc (ogm_id, nome, meteorologista_chefe_nome)
SELECT id, 'DMC-SEM', 'Chefia do DMC-SEM' FROM ogm WHERE sigla = 'INMET'
ON CONFLICT (ogm_id, nome) DO NOTHING;
INSERT INTO dmc (ogm_id, nome, meteorologista_chefe_nome)
SELECT id, 'NEA-BA', 'Chefia do NEA-BA' FROM ogm WHERE sigla = 'CEMADEN'
ON CONFLICT (ogm_id, nome) DO NOTHING;
INSERT INTO dmc (ogm_id, nome, meteorologista_chefe_nome)
SELECT id, 'NEA-SP', 'Chefia do NEA-SP' FROM ogm WHERE sigla = 'CEMADEN'
ON CONFLICT (ogm_id, nome) DO NOTHING;

-- O enunciado pede uma rede por DMC (DMC é UNIQUE em rede_monitoramento).
INSERT INTO rede_monitoramento (sigla, descricao, dmc_id)
SELECT 'REDE-5DISME', 'Rede de Estações Automáticas do Recôncavo', id FROM dmc WHERE nome = '5º DISME'
ON CONFLICT (sigla) DO NOTHING;
INSERT INTO rede_tipo_monitoramento (rede_id, tipo)
SELECT id, 'TEMPERATURA' FROM rede_monitoramento WHERE sigla = 'REDE-5DISME'
ON CONFLICT DO NOTHING;

INSERT INTO rede_monitoramento (sigla, descricao, dmc_id)
SELECT 'REDE-NEABA', 'Rede de Alerta de Deslizamentos', id FROM dmc WHERE nome = 'NEA-BA'
ON CONFLICT (sigla) DO NOTHING;
INSERT INTO rede_tipo_monitoramento (rede_id, tipo)
SELECT id, 'ALERTA_DESLIZAMENTO' FROM rede_monitoramento WHERE sigla = 'REDE-NEABA'
ON CONFLICT DO NOTHING;

-- Usuário de demonstração não é criado aqui: o hash BCrypt deve ser gerado pela aplicação.
-- A Fase 2 implementará o provisionamento seguro usando ADMIN_INITIAL_EMAIL/PASSWORD.
