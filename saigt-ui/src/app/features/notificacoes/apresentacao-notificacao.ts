import type { TomSelo } from '../../shared/ui/selo/selo.component';
import type { EstadoNotificacao, NotificacaoResumo, TipoNotificacao } from '../../models/notificacao.model';

export type IconeNotificacao = 'triangulo' | 'relogio' | 'megafone' | 'balao' | 'documento';

export interface ApresentacaoTipo {
  rotulo: string;
  tom: TomSelo;
  icone: IconeNotificacao;
}

export interface SeloEstado {
  rotulo: string;
  tom: TomSelo;
}

type Classificavel = Pick<NotificacaoResumo, 'tipo' | 'estado' | 'processoId'>;

export const OPCOES_TIPO_NOTIFICACAO: readonly { valor: TipoNotificacao; rotulo: string }[] = [
  { valor: 'ESTATICA', rotulo: 'Estática' },
  { valor: 'TEMPORARIA', rotulo: 'Temporária' },
  { valor: 'RESPOSTA_OBRIGATORIA', rotulo: 'Resposta obrigatória' },
  { valor: 'PEDIDO_APOIO', rotulo: 'Pedido de apoio' },
  { valor: 'REGULARIZACAO_PROCEDIMENTO', rotulo: 'Regularização de procedimento' },
  { valor: 'ALERTA', rotulo: 'Alerta' },
];

const TIPOS_COM_LEITURA: readonly TipoNotificacao[] = ['TEMPORARIA', 'ALERTA'];
const TIPOS_COM_CONVERSACAO: readonly TipoNotificacao[] = ['RESPOSTA_OBRIGATORIA', 'PEDIDO_APOIO', 'REGULARIZACAO_PROCEDIMENTO'];

export function apresentacaoTipo(notificacao: Pick<NotificacaoResumo, 'tipo' | 'processoId'>): ApresentacaoTipo {
  switch (notificacao.tipo) {
    case 'ALERTA':
      return { rotulo: 'Alerta', tom: 'erro', icone: 'triangulo' };
    case 'TEMPORARIA':
      return { rotulo: 'Temporária', tom: 'cinza', icone: 'relogio' };
    case 'ESTATICA':
      return { rotulo: 'Estática', tom: 'info', icone: 'megafone' };
    case 'RESPOSTA_OBRIGATORIA':
      return { rotulo: 'Resp. Obrig.', tom: 'violeta', icone: 'balao' };
    case 'PEDIDO_APOIO':
      return notificacao.processoId
        ? { rotulo: 'Apoio Proc.', tom: 'ciano', icone: 'documento' }
        : { rotulo: 'Apoio Geral', tom: 'sucesso', icone: 'balao' };
    case 'REGULARIZACAO_PROCEDIMENTO':
      return { rotulo: 'Regularização', tom: 'aviso', icone: 'documento' };
  }
}

export function ehNaoLida(notificacao: Pick<NotificacaoResumo, 'tipo' | 'estado'>): boolean {
  return notificacao.estado === 'ATIVA' && TIPOS_COM_LEITURA.includes(notificacao.tipo);
}

export function temConversacao(tipo: TipoNotificacao): boolean {
  return TIPOS_COM_CONVERSACAO.includes(tipo);
}

const SELO_FIM: Record<Exclude<EstadoNotificacao, 'ATIVA'>, SeloEstado> = {
  LIDA: { rotulo: 'Lida', tom: 'cinza' },
  RESOLVIDA: { rotulo: 'Resolvida', tom: 'sucesso' },
  DESATIVADA: { rotulo: 'Desativada', tom: 'cinza' },
  REMOVIDA: { rotulo: 'Removida', tom: 'cinza' },
};

export function seloEstado(notificacao: Classificavel): SeloEstado | null {
  if (notificacao.estado !== 'ATIVA') {
    return SELO_FIM[notificacao.estado];
  }
  if (ehNaoLida(notificacao)) {
    return { rotulo: 'Não lida', tom: 'info' };
  }
  return temConversacao(notificacao.tipo) ? { rotulo: 'Pend. Resposta', tom: 'aviso' } : null;
}

export function remetente(criadoPorNome: string | null): string {
  return criadoPorNome ?? 'SAIGT — Sistema';
}

const PARTES_LISBOA = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Europe/Lisbon',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

function partesEmLisboa(iso: string): Record<string, string> {
  return Object.fromEntries(PARTES_LISBOA.formatToParts(new Date(iso)).map((parte) => [parte.type, parte.value]));
}

export function formatarDataHora(iso: string): string {
  const p = partesEmLisboa(iso);
  return `${p['year']}-${p['month']}-${p['day']} ${p['hour']}:${p['minute']}`;
}

/** `dd/MM/yyyy HH:mm` no fuso de Lisboa (listagens do backoffice). */
export function formatarDataHoraTabela(iso: string): string {
  const p = partesEmLisboa(iso);
  return `${p['day']}/${p['month']}/${p['year']} ${p['hour']}:${p['minute']}`;
}

export function formatarDia(iso: string): string {
  const p = partesEmLisboa(iso);
  return `${p['year']}-${p['month']}-${p['day']}`;
}

const MINUTO_MS = 60_000;

export function tempoRelativo(iso: string, agora: Date = new Date()): string {
  const minutos = Math.floor((agora.getTime() - new Date(iso).getTime()) / MINUTO_MS);
  if (minutos < 1) {
    return 'agora';
  }
  if (minutos < 60) {
    return `${minutos} min atrás`;
  }
  if (minutos < 24 * 60) {
    return `${Math.floor(minutos / 60)}h atrás`;
  }
  return formatarDia(iso);
}

export function tamanhoEmKb(bytes: number): string {
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

/** Para a pesquisa local: sem maiúsculas nem acentos. */
export function normalizarPesquisa(texto: string): string {
  return texto.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();
}
