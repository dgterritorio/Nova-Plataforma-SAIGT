import { PerfilInterno } from '../../../models/utilizador.model';

export type Acesso = 'PERMITIDO' | 'PARCIAL' | 'SEM_ACESSO';

export interface PermissaoPerfil {
  nome: string;
  nota?: string;
  acesso: Record<PerfilInterno, Acesso>;
}

export interface GrupoPermissoes {
  titulo: string;
  permissoes: readonly PermissaoPerfil[];
}

export const DESCRICAO_PERFIL: Record<PerfilInterno, string> = {
  MASTER: 'Validação, Centro de Notificações e acesso total ao BackOffice.',
  TECNICO: 'Validação de processos e acesso parcial ao BackOffice.',
  VISITANTE: 'Consulta, pesquisa e exportação de qualquer processo.',
};

const TODOS: Record<PerfilInterno, Acesso> = { MASTER: 'PERMITIDO', TECNICO: 'PERMITIDO', VISITANTE: 'PERMITIDO' };
const NENHUM: Record<PerfilInterno, Acesso> = { MASTER: 'SEM_ACESSO', TECNICO: 'SEM_ACESSO', VISITANTE: 'SEM_ACESSO' };
const MASTER_E_TECNICO: Record<PerfilInterno, Acesso> = { MASTER: 'PERMITIDO', TECNICO: 'PERMITIDO', VISITANTE: 'SEM_ACESSO' };
const SO_MASTER: Record<PerfilInterno, Acesso> = { MASTER: 'PERMITIDO', TECNICO: 'SEM_ACESSO', VISITANTE: 'SEM_ACESSO' };

/**
 * Matriz «Perfis de Utilizador» da EF v1.5, com os textos e as notas do Figma. Só informa: quem decide é o servidor.
 */
export const MATRIZ_PERMISSOES_PERFIS: readonly GrupoPermissoes[] = [
  {
    titulo: 'Processos',
    permissoes: [
      { nome: 'Consultar qualquer processo', acesso: TODOS },
      { nome: 'Pesquisar, filtrar e exportar processos', acesso: TODOS },
      {
        nome: 'Criar / continuar / corrigir processo',
        nota: 'Só para utilizadores autenticados via INCM. A DGT usa o seu acesso INCM.',
        acesso: NENHUM,
      },
    ],
  },
  {
    titulo: 'Validação',
    permissoes: [
      { nome: 'Validar processo', acesso: MASTER_E_TECNICO },
      { nome: 'Enviar PCM', acesso: MASTER_E_TECNICO },
      { nome: 'Enviar INCM para publicação', nota: 'É sempre a DGT que envia à INCM.', acesso: MASTER_E_TECNICO },
    ],
  },
  {
    titulo: 'Centro de Notificações',
    permissoes: [
      { nome: 'Aceder ao Centro de Notificações', acesso: SO_MASTER },
      { nome: 'Criar notícias gerais', acesso: SO_MASTER },
      { nome: 'Criar pedidos de resposta obrigatória no processo', acesso: SO_MASTER },
      { nome: 'Responder a pedidos de apoio e de regularização', acesso: SO_MASTER },
      { nome: 'Receber alertas de sistema e de erros de integração', acesso: SO_MASTER },
    ],
  },
  {
    titulo: 'BackOffice',
    permissoes: [
      {
        nome: 'Aceder ao BackOffice',
        nota: 'Master: acesso total; Técnico: acesso parcial.',
        acesso: { MASTER: 'PERMITIDO', TECNICO: 'PARCIAL', VISITANTE: 'SEM_ACESSO' },
      },
      { nome: 'Análises e Relatórios (KPIs)', acesso: SO_MASTER },
      { nome: 'Listar e pesquisar processos no BackOffice', acesso: MASTER_E_TECNICO },
      { nome: 'Ver estatísticas e histórico', acesso: MASTER_E_TECNICO },
      { nome: 'Alterar owner do processo', acesso: SO_MASTER },
      { nome: 'Alterar estado do processo', acesso: SO_MASTER },
      { nome: 'Alterar IDUS', acesso: SO_MASTER },
      { nome: 'Anular processo (motivo obrigatório)', acesso: SO_MASTER },
      { nome: 'Consultar e atualizar Plantas SAIGT/SNIT', acesso: MASTER_E_TECNICO },
      { nome: 'Configurar workflows e precedências', acesso: SO_MASTER },
    ],
  },
  {
    titulo: 'Administração',
    permissoes: [
      { nome: 'Gerir utilizadores INCM/DGT e atribuir perfil DGT', acesso: SO_MASTER },
      { nome: 'Gerir entidades (criar entidade em falta e atualizar)', acesso: SO_MASTER },
      { nome: 'Gerir listas de valores', acesso: SO_MASTER },
    ],
  },
  {
    titulo: 'Conta',
    permissoes: [
      { nome: 'Editar dados pessoais (email, telefone e morada; nome não)', nota: 'Através do perfil do utilizador.', acesso: TODOS },
    ],
  },
];
