export interface DataCaducidade {
  /** `yyyy-MM-dd`, para o `datetime` de `<time>`. */
  iso: string;
  /** `dd/MM/yyyy`. */
  texto: string;
}

export type SituacaoCaducidade =
  | { tipo: 'CADUCADA'; data: DataCaducidade | null }
  | { tipo: 'PROXIMA'; data: DataCaducidade; dias: number }
  | { tipo: 'NORMAL'; data: DataCaducidade }
  | { tipo: 'SEM_DATA' };

const MS_POR_DIA = 86_400_000;

const DIA_EM_LISBOA = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Europe/Lisbon',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

interface DiaCivil {
  ano: number;
  mes: number;
  dia: number;
}

/** Lê só o dia civil: `new Date('yyyy-MM-dd')` seria meia-noite UTC e podia cair no dia anterior. */
function lerDia(valor: string | null): DiaCivil | null {
  const partes = /^(\d{4})-(\d{2})-(\d{2})/.exec(valor ?? '');
  return partes ? { ano: Number(partes[1]), mes: Number(partes[2]), dia: Number(partes[3]) } : null;
}

function numeroDoDia({ ano, mes, dia }: DiaCivil): number {
  return Date.UTC(ano, mes - 1, dia) / MS_POR_DIA;
}

function diasNoMes(ano: number, mes: number): number {
  return new Date(Date.UTC(ano, mes, 0)).getUTCDate();
}

/** 31/03 menos um mês é 28/02 (29/02 em ano bissexto), não 03/03. */
function menosUmMes({ ano, mes, dia }: DiaCivil): DiaCivil {
  const anterior = mes === 1 ? { ano: ano - 1, mes: 12 } : { ano, mes: mes - 1 };
  return { ...anterior, dia: Math.min(dia, diasNoMes(anterior.ano, anterior.mes)) };
}

function doisDigitos(valor: number): string {
  return String(valor).padStart(2, '0');
}

function paraData(dia: DiaCivil): DataCaducidade {
  const dd = doisDigitos(dia.dia);
  const mm = doisDigitos(dia.mes);
  return { iso: `${dia.ano}-${mm}-${dd}`, texto: `${dd}/${mm}/${dia.ano}` };
}

export function lerDataCaducidade(valor: string | null): DataCaducidade | null {
  const dia = lerDia(valor);
  return dia ? paraData(dia) : null;
}

export function diaEmLisboa(instante: Date): string {
  return DIA_EM_LISBOA.format(instante);
}

/** O primeiro aviso é a 1 mês da data (TR-111); a caducidade não bloqueia nada (FR-025). */
export function situacaoCaducidade(dataCaducidade: string | null, estado: string | null, hoje: Date): SituacaoCaducidade {
  const dia = lerDia(dataCaducidade);
  if (estado === 'CADUCADA') {
    return { tipo: 'CADUCADA', data: dia ? paraData(dia) : null };
  }
  if (estado !== 'EM_CURSO' || !dia) {
    return { tipo: 'SEM_DATA' };
  }
  const hojeEmLisboa = numeroDoDia(lerDia(diaEmLisboa(hoje)) as DiaCivil);
  if (hojeEmLisboa < numeroDoDia(menosUmMes(dia))) {
    return { tipo: 'NORMAL', data: paraData(dia) };
  }
  // Passada a data, a Dinâmica fica EM_CURSO até o job de caducidade correr.
  return { tipo: 'PROXIMA', data: paraData(dia), dias: Math.max(0, numeroDoDia(dia) - hojeEmLisboa) };
}

export function textoPrazo(dias: number): string {
  if (dias === 0) {
    return 'Caduca hoje';
  }
  return dias === 1 ? 'Falta 1 dia' : `Faltam ${dias} dias`;
}
