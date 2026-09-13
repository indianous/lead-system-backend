-- Permissões fixas (ver 03-entidades.md#Permission).
INSERT INTO permissions (id, key, description) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'CREATE_USER', 'Cadastrar, editar e ativar/desativar usuários'),
    ('a0000000-0000-0000-0000-000000000002', 'VIEW_ALL_LEADS', 'Ver todos os leads, independente do responsável'),
    ('a0000000-0000-0000-0000-000000000003', 'VIEW_OWN_LEADS', 'Ver apenas os leads sob responsabilidade do próprio usuário'),
    ('a0000000-0000-0000-0000-000000000004', 'EDIT_CATALOG', 'Cadastrar e editar produtos do catálogo'),
    ('a0000000-0000-0000-0000-000000000005', 'VIEW_METRICS', 'Ver métricas e relatórios do funil'),
    ('a0000000-0000-0000-0000-000000000006', 'TRIGGER_PROSPECTING', 'Disparar buscas de prospecção (busca local)');

-- Papéis iniciais (ver 03-entidades.md#Role). A matriz papel->permissão é um ponto de partida
-- razoável, ajustável depois pelo admin em /settings/roles; decisão documentada no plano da Etapa 1.
INSERT INTO roles (id, name, description) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'Salesperson', 'Vendedor: gerencia os próprios leads e pode disparar prospecção'),
    ('b0000000-0000-0000-0000-000000000002', 'Manager/Administrator', 'Gestor/administrador: acesso completo, incluindo administração de usuários e métricas');

-- Salesperson: VIEW_OWN_LEADS, TRIGGER_PROSPECTING
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003'),
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000006');

-- Manager/Administrator: todas as permissões
INSERT INTO role_permissions (role_id, permission_id)
    SELECT 'b0000000-0000-0000-0000-000000000002', id FROM permissions;
