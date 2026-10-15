-- Schema inicial do Sistema de Controle e Monitoramento do Clima.
-- Timestamps são armazenados com fuso horário. Exclusão lógica será preferida quando houver histórico.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE perfil (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(30) NOT NULL UNIQUE,
    descricao VARCHAR(200),
    data_cadastro TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE permissao (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(80) NOT NULL UNIQUE,
    descricao VARCHAR(200)
);

CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    senha_hash VARCHAR(100) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_cadastro TIMESTAMPTZ NOT NULL DEFAULT now(),
    data_atualizacao TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usuario_perfil (
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE RESTRICT,
    perfil_id BIGINT NOT NULL REFERENCES perfil(id) ON DELETE RESTRICT,
    data_atribuicao TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, perfil_id)
);

CREATE TABLE perfil_permissao (
    perfil_id BIGINT NOT NULL REFERENCES perfil(id) ON DELETE RESTRICT,
    permissao_id BIGINT NOT NULL REFERENCES permissao(id) ON DELETE RESTRICT,
    PRIMARY KEY (perfil_id, permissao_id)
);

CREATE TABLE ogm (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sigla VARCHAR(30) NOT NULL UNIQUE,
    nome VARCHAR(180) NOT NULL,
    email_central VARCHAR(254),
    telefone_central VARCHAR(30),
    diretor_geral_nome VARCHAR(180) NOT NULL,
    diretor_geral_tecnico_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO'
        CHECK (status IN ('ATIVO', 'INATIVO')),
    data_cadastro TIMESTAMPTZ NOT NULL DEFAULT now(),
    data_atualizacao TIMESTAMPTZ NOT NULL DEFAULT now(),
    justificativa_inativacao TEXT
);

CREATE TABLE ogm_coordenadoria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ogm_id UUID NOT NULL REFERENCES ogm(id) ON DELETE RESTRICT,
    nome VARCHAR(160) NOT NULL,
    nomeado VARCHAR(180) NOT NULL,
    data_cadastro TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (ogm_id, nome)
);

CREATE TABLE dmc (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ogm_id UUID NOT NULL REFERENCES ogm(id) ON DELETE RESTRICT,
    nome VARCHAR(180) NOT NULL,
    email_central VARCHAR(254),
    telefone_central VARCHAR(30),
    meteorologista_chefe_nome VARCHAR(180) NOT NULL,
    meteorologista_chefe_tecnico_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO'
        CHECK (status IN ('ATIVO', 'INATIVO')),
    data_cadastro TIMESTAMPTZ NOT NULL DEFAULT now(),
    data_atualizacao TIMESTAMPTZ NOT NULL DEFAULT now(),
    justificativa_inativacao TEXT,
    CONSTRAINT uq_dmc_ogm UNIQUE (ogm_id, nome)
);

CREATE TABLE tecnico (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    matricula VARCHAR(50) NOT NULL UNIQUE,
    nome_completo VARCHAR(180) NOT NULL,
    email_contato VARCHAR(254) NOT NULL,
    telefone_contato VARCHAR(30),
    dmc_id UUID NOT NULL REFERENCES dmc(id) ON DELETE RESTRICT,
    usuario_id UUID UNIQUE REFERENCES usuario(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO'
        CHECK (status IN ('ATIVO', 'INATIVO')),
    data_cadastro TIMESTAMPTZ NOT NULL DEFAULT now(),
    data_atualizacao TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE ogm
    ADD CONSTRAINT fk_ogm_diretor_tecnico
    FOREIGN KEY (diretor_geral_tecnico_id) REFERENCES tecnico(id) ON DELETE RESTRICT;
ALTER TABLE dmc
    ADD CONSTRAINT fk_dmc_chefe_tecnico
    FOREIGN KEY (meteorologista_chefe_tecnico_id) REFERENCES tecnico(id) ON DELETE RESTRICT;

CREATE TABLE rede_monitoramento (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sigla VARCHAR(40) NOT NULL UNIQUE,
    descricao VARCHAR(240) NOT NULL,
    dmc_id UUID NOT NULL UNIQUE REFERENCES dmc(id) ON DELETE RESTRICT,
    meteorologista_responsavel_id UUID REFERENCES tecnico(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO'
        CHECK (status IN ('ATIVO', 'INATIVO')),
    data_cadastro TIMESTAMPTZ NOT NULL DEFAULT now(),
    data_atualizacao TIMESTAMPTZ NOT NULL DEFAULT now(),
    justificativa_inativacao TEXT
);

CREATE TABLE rede_tipo_monitoramento (
    rede_id UUID NOT NULL REFERENCES rede_monitoramento(id) ON DELETE CASCADE,
    tipo VARCHAR(40) NOT NULL
        CHECK (tipo IN ('TEMPERATURA', 'UMIDADE', 'PRESSAO', 'VENTO', 'PRECIPITACAO',
                        'RADAR', 'CONVENCIONAL', 'OCEANOGRAFICO', 'ALERTA_DESLIZAMENTO')),
    PRIMARY KEY (rede_id, tipo)
);

CREATE TABLE perfil_usuario_final (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL UNIQUE REFERENCES usuario(id) ON DELETE RESTRICT,
    nome VARCHAR(180) NOT NULL,
    documento VARCHAR(30) NOT NULL UNIQUE,
    dmc_id UUID NOT NULL REFERENCES dmc(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO'
        CHECK (status IN ('ATIVO', 'INATIVO')),
    data_cadastro TIMESTAMPTZ NOT NULL DEFAULT now(),
    data_atualizacao TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE estacao_meteorologica (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sigla VARCHAR(50) NOT NULL UNIQUE,
    nome VARCHAR(180) NOT NULL,
    tipo VARCHAR(30) NOT NULL
        CHECK (tipo IN ('AUTOMATICA', 'CONVENCIONAL', 'RADAR_METEOROLOGICO', 'BOIA_OCEANOGRAFICA')),
    descricao_complementar TEXT,
    capacidade_maxima INTEGER NOT NULL CHECK (capacidade_maxima > 0),
    latitude NUMERIC(9,6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9,6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    rede_id UUID NOT NULL REFERENCES rede_monitoramento(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'DISPONIVEL'
        CHECK (status IN ('DISPONIVEL', 'INDISPONIVEL', 'INATIVA', 'MANUTENCAO')),
    data_cadastro TIMESTAMPTZ NOT NULL DEFAULT now(),
    data_atualizacao TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE estacao_sensor (
    estacao_id UUID NOT NULL REFERENCES estacao_meteorologica(id) ON DELETE CASCADE,
    sensor VARCHAR(30) NOT NULL
        CHECK (sensor IN ('TEMPERATURA', 'UMIDADE', 'PRESSAO', 'VENTO', 'PLUVIOMETRO')),
    PRIMARY KEY (estacao_id, sensor)
);

CREATE TABLE software_estacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estacao_id UUID NOT NULL REFERENCES estacao_meteorologica(id) ON DELETE CASCADE,
    nome VARCHAR(160) NOT NULL,
    url_download TEXT NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('PROPRIETARIO', 'SOFTWARE_LIVRE')),
    objetivo_uso TEXT NOT NULL,
    variaveis_monitoradas TEXT[] NOT NULL DEFAULT '{}',
    data_cadastro TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE solicitacao_uso_estacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estacao_id UUID NOT NULL REFERENCES estacao_meteorologica(id) ON DELETE RESTRICT,
    usuario_solicitante_id UUID NOT NULL REFERENCES perfil_usuario_final(id) ON DELETE RESTRICT,
    nome_usuario VARCHAR(180) NOT NULL,
    dmc_id UUID NOT NULL REFERENCES dmc(id) ON DELETE RESTRICT,
    data_uso DATE NOT NULL,
    hora_inicial TIME NOT NULL,
    hora_final TIME NOT NULL,
    data_solicitacao TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
        CHECK (status IN ('PENDENTE', 'APROVADA', 'REPROVADA', 'FINALIZADA', 'CANCELADA', 'INATIVA')),
    justificativa_reprovacao TEXT,
    tecnico_decisor_id UUID REFERENCES tecnico(id) ON DELETE RESTRICT,
    data_decisao TIMESTAMPTZ,
    CONSTRAINT ck_solicitacao_horario CHECK (hora_final > hora_inicial)
);
CREATE INDEX idx_solicitacao_uso_estacao_data_status
    ON solicitacao_uso_estacao(estacao_id, data_uso, status);

CREATE TABLE solicitacao_instalacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estacao_id UUID NOT NULL REFERENCES estacao_meteorologica(id) ON DELETE RESTRICT,
    solicitante_id UUID NOT NULL REFERENCES perfil_usuario_final(id) ON DELETE RESTRICT,
    item_tipo VARCHAR(20) NOT NULL CHECK (item_tipo IN ('SENSOR', 'SOFTWARE')),
    sensor VARCHAR(30) CHECK (sensor IS NULL OR sensor IN ('TEMPERATURA', 'UMIDADE', 'PRESSAO', 'VENTO', 'PLUVIOMETRO')),
    nome_software VARCHAR(160),
    url_download TEXT,
    tipo_software VARCHAR(20) CHECK (tipo_software IS NULL OR tipo_software IN ('PROPRIETARIO', 'SOFTWARE_LIVRE')),
    objetivo_uso TEXT,
    variaveis_monitoradas TEXT[] NOT NULL DEFAULT '{}',
    data_solicitacao TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
        CHECK (status IN ('PENDENTE', 'APROVADA', 'REPROVADA', 'INSTALADA', 'INATIVA')),
    justificativa_reprovacao TEXT,
    tecnico_decisor_id UUID REFERENCES tecnico(id) ON DELETE RESTRICT,
    data_decisao TIMESTAMPTZ,
    CONSTRAINT ck_instalacao_item CHECK (
        (item_tipo = 'SENSOR' AND sensor IS NOT NULL AND nome_software IS NULL)
        OR
        (item_tipo = 'SOFTWARE' AND sensor IS NULL AND nome_software IS NOT NULL
         AND url_download IS NOT NULL AND tipo_software IS NOT NULL AND objetivo_uso IS NOT NULL)
    )
);

CREATE TABLE historico_inatividade (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_entidade VARCHAR(50) NOT NULL,
    entidade_id UUID NOT NULL,
    motivo TEXT NOT NULL,
    usuario_responsavel_id UUID REFERENCES usuario(id) ON DELETE RESTRICT,
    data_inatividade TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_historico_inatividade_entidade ON historico_inatividade(tipo_entidade, entidade_id);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_hora TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_id UUID REFERENCES usuario(id) ON DELETE SET NULL,
    perfil VARCHAR(30),
    acao VARCHAR(40) NOT NULL
        CHECK (acao IN ('CREATE', 'UPDATE', 'INACTIVATE', 'APPROVE', 'REJECT', 'LOGIN',
                        'ACCESS_DENIED', 'DELETE', 'RELEASE', 'INSTALL')),
    entidade VARCHAR(80) NOT NULL,
    entidade_id VARCHAR(80),
    detalhes JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip INET
);
CREATE INDEX idx_audit_data_hora ON audit_log(data_hora);
CREATE INDEX idx_audit_usuario ON audit_log(usuario_id);
CREATE INDEX idx_audit_acao ON audit_log(acao);
CREATE INDEX idx_audit_entidade ON audit_log(entidade);

-- Integridade de negócio: um técnico não pode ser chefe de dois DMCs nem diretor de duas OGMs.
CREATE UNIQUE INDEX uq_dmc_chefe_tecnico
    ON dmc(meteorologista_chefe_tecnico_id)
    WHERE meteorologista_chefe_tecnico_id IS NOT NULL;
CREATE UNIQUE INDEX uq_ogm_diretor_tecnico
    ON ogm(diretor_geral_tecnico_id)
    WHERE diretor_geral_tecnico_id IS NOT NULL;
