import type { PerfilInterno } from '../core/auth/auth.models';

export type TipoNotificacao =
  | 'ESTATICA'
  | 'TEMPORARIA'
  | 'RESPOSTA_OBRIGATORIA'
  | 'PEDIDO_APOIO'
  | 'REGULARIZACAO_PROCEDIMENTO'
  | 'ALERTA';

export type EstadoNotificacao = 'ATIVA' | 'LIDA' | 'RESOLVIDA' | 'DESATIVADA' | 'REMOVIDA';

export type AcaoNotificacao = 'RESPONDER' | 'REABRIR' | 'LER' | 'RESOLVER' | 'REMOVER' | 'ANEXAR';

export interface NotificacaoResumo {
  id: string;
  tipo: TipoNotificacao;
  titulo: string;
  texto: string;
  processoId: string | null;
  numeroProcesso: string | null;
  idIgtSnit: string | null;
  descricaoIgt: string | null;
  prazo: string | null;
  estado: EstadoNotificacao;
  criadoEm: string;
  lidoEm: string | null;
  resolvidoEm: string | null;
  criadoPorNome: string | null;
  numeroMensagens: number;
  numeroDocumentos: number;
}

export interface MensagemNotificacao {
  id: string;
  autorId: string;
  autorNome: string;
  texto: string;
  criadoEm: string;
}

export interface DocumentoNotificacao {
  id: string;
  nomeFicheiro: string;
  tamanhoBytes: number;
  criadoEm: string;
}

export interface DownloadAnexoNotificacao {
  url: string;
}

export interface NotificacaoDetalhe extends NotificacaoResumo {
  perfilDestino: PerfilInterno | null;
  destinatarioUtilizadorNome: string | null;
  destinatarioEntidadeNome: string | null;
  mensagens: MensagemNotificacao[];
  documentos: DocumentoNotificacao[];
  acoesDisponiveis: AcaoNotificacao[];
}

export interface CriarMensagemNotificacaoResposta {
  mensagem: MensagemNotificacao;
  estado: EstadoNotificacao;
}

export interface RemocaoNotificacao {
  linhasAtualizadas: number;
}

export interface NotificacaoFiltro {
  estado?: EstadoNotificacao[];
  tipo?: TipoNotificacao[];
  processoId?: string;
  desde?: string;
  ate?: string;
}

export interface DestinatarioNotificacao {
  entidadeId?: number;
  utilizadorId?: string;
}

export interface CriarNotificacaoRequest {
  tipo: TipoNotificacao;
  titulo: string;
  texto: string;
  processoId?: string;
  idIgtSnit?: string;
  descricaoIgt?: string;
  prazo?: string;
  destinatarios: DestinatarioNotificacao[];
}

export interface CriarNotificacaoResponse {
  notificacoes: NotificacaoResumo[];
  loteId: string | null;
}
