/** `valor` nulo nos KPIs que são só gráfico; `serie` nula nos KPIs sem gráfico. `calculadoEm` é igual em todos os KPIs do pedido. */
export interface ValorKpi {
  codigo: string;
  designacao: string;
  valor: number | null;
  serie: PontoSerie[] | null;
  calculadoEm: string;
}

/** `rotulo`: nome do estado, código do tipo de Instrumento ou mês `AAAA-MM`. */
export interface PontoSerie {
  rotulo: string;
  valor: number;
}

export interface PreferenciaKpi {
  kpiCodigo: string;
  designacao: string;
  visivel: boolean;
  ordem: number;
}

export interface PreferenciaKpiPedido {
  kpiCodigo: string;
  visivel: boolean;
  ordem: number;
}
