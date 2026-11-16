import { ChangeDetectorRef, Component, ElementRef, Injector, Input, OnChanges, SimpleChanges, afterNextRender, inject, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, forkJoin, of } from 'rxjs';
import { ApiError, CampoErro } from '../../../core/models/error-response.model';
import { AuthService } from '../../../core/auth/auth.service';
import { EntidadeEmitente } from '../../../models/entidade.model';
import { ConcelhoElaboracao, ElaboracaoNova, TipoAtoDisponivel } from '../../../models/instrumento.model';
import { EntidadeService } from '../../../services/entidade.service';
import {
  CategoriaInstrumento,
  DadosEmitente,
  DadosFormularioRen,
  DadosFundamentacao,
  DadosLocalizacaoMotivo,
  DadosTramitacao,
  DinamicaAssociavel,
  ElementoAfetado,
  FormularioFixo,
  InstrumentoAbrangido,
  ItemTerritorio,
  LocalizacaoDetalhe,
  LocalizacaoPedido,
  ModalidadeRatificacao,
  MotivoAlteracao,
  MotivoProcedimento,
  OpcaoCimAm,
  OpcaoLista,
  PedeLocalizacao,
  PedeMotivoAlteracao,
  PerguntaGeoconform,
  ProcessoDetalhe,
  PublicacaoIncmDados,
  ROTULO_MODALIDADE_RATIFICACAO,
  RespostaSimultaneoPmot,
  SerieDr,
  RedeDefesa,
  TerritorioInstrumento,
  TipoInformacao,
  TipoListaFormulario,
  TipoMotivoAlteracao,
} from '../../../models/processo.model';
import { ListaValoresService } from '../../../services/lista-valores.service';
import { ReferenciaSnitService } from '../../../services/referencia-snit.service';
import { DinamicaAssociadaComponent } from '../dinamica-associada/dinamica-associada.component';
import { DesbloquearCampoComponent } from '../desbloquear-campo/desbloquear-campo.component';
import { MENSAGEM_CAMPO_BLOQUEADO, lerIntroduzPlantaNova, lerSimultaneoComPmot } from '../informacao-base';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';
import { formatarNomeConcelho } from '../../../shared/nome-concelho';

/**
 * A data de assinatura do ato não é deste cartão: está no bloco Informação Base do Texto do Ato. Um Procedimento sem
 * Informação Base (`dataAssinaturaNoFormulario`) pede-a aqui: o ecrã lê-a por `dataAssinaturaPedido`.
 */
export type FormularioInicialDados = Omit<PublicacaoIncmDados, 'dataAssinatura'> &
  DadosTramitacao &
  DadosEmitente &
  DadosFundamentacao &
  DadosLocalizacaoMotivo &
  DadosFormularioRen & {
    comGeoconform?: boolean | null;
    introduzPlantaNova?: boolean | null;
    dinamicaAssociadaId: string | null;
    /** Omitido (sem escolha, ou bloqueado) o servidor mantém o gravado. */
    tipoAtoId?: number | null;
    /** Só quando o processo escolhe a série; omitida (sem escolha, ou bloqueada) o servidor mantém a gravada. */
    serieDr?: SerieDr | null;
    redesDefesa?: string[];
    regiao?: string | null;
    cimAm?: string | null;
  };

/**
 * Valores gravados de um rascunho; qualquer um pode faltar. `introduzPlantaNova` não é coluna do processo: vem
 * de `dadosFormulario` (um processo lido) ou directamente do que acabou de se gravar.
 */
export type FormularioInicialValores = Partial<
  Pick<
    ProcessoDetalhe,
    keyof Omit<FormularioInicialDados, 'introduzPlantaNova' | 'localizacao' | 'motivosAlteracao' | 'redesDefesa' | 'motivoProcedimentoId'>
  >
> & {
  introduzPlantaNova?: boolean | null;
  /** Do detalhe (`motivoProcedimento`) ou do que acabou de se gravar (`motivoProcedimentoId`). */
  motivoProcedimento?: MotivoProcedimento | null;
  motivoProcedimentoId?: number | null;
  /** Como `introduzPlantaNova`: de `dadosFormulario` ou do que acabou de se gravar. */
  simultaneoComPmot?: RespostaSimultaneoPmot | null;
  /** Do detalhe (com o nome) ou do que acabou de se gravar (só os códigos). */
  redesDefesa?: (RedeDefesa | string)[] | null;
  regiaoDescricao?: string | null;
  cimAmDescricao?: string | null;
  dadosFormulario?: string | null;
  /** Só os formulários sem Informação Base a mostram neste cartão. */
  dataAssinatura?: string | null;
  localizacao?: LocalizacaoDetalhe | LocalizacaoPedido | null;
  motivosAlteracao?: (MotivoAlteracao & { descricao?: string | null })[];
};

interface LocalizacaoGravada extends LocalizacaoPedido {
  freguesiaDescricao: string | null;
  toponimoDescricao: string | null;
}

const TIPOS_MOTIVO: readonly { tipo: TipoMotivoAlteracao; rotulo: string }[] = [
  { tipo: 'MOTIVO_ALTERACAO_LEI', rotulo: 'Lei' },
  { tipo: 'MOTIVO_ALTERACAO_PROGRAMA', rotulo: 'Programa' },
  { tipo: 'MOTIVO_ALTERACAO_PLANO', rotulo: 'Plano' },
];

const ROTULO_APLICAVEL_TODA_AREA = 'Aplicável a toda a área do IGT';
const CLASSE_SERVIDAO_SGIFR = 'Sistema de Gestão Integrada de Fogos Rurais';
const AJUDA_REDES_DEFESA = 'Escolha as servidões em causa. Cada uma é publicada numa folha própria da mesma publicação.';
const MENSAGEM_MOTIVO_PROCEDIMENTO_EM_FALTA = 'Indique o motivo do procedimento.';
const MENSAGEM_DATA_ASSINATURA_EM_FALTA = 'Indique a data de assinatura do ato.';
const AJUDA_MUNICIPIO_SEM_REGIAO = 'Escolha primeiro a região.';
const PERGUNTA_CONFORMACAO ='A alteração tem como objetivo conformar o plano com uma lei, programa ou plano de hierarquia superior?';

export function caminhoDoControlo(path: string): string {
  if (path === 'localizacao.toponimoCodigo' || path === 'localizacao.toponimoTexto') {
    return 'localizacao.toponimo';
  }
  return path.startsWith('motivosAlteracao[') ? 'motivosAlteracao' : path;
}

function lerLocalizacao(valor: LocalizacaoDetalhe | LocalizacaoPedido | null | undefined): LocalizacaoGravada | null {
  if (!valor) {
    return null;
  }
  const freguesia = valor.freguesia;
  const comToponimo = 'toponimo' in valor;
  return {
    aplicavelTodaArea: valor.aplicavelTodaArea ?? null,
    freguesia: typeof freguesia === 'string' ? freguesia : (freguesia?.codigo ?? null),
    freguesiaDescricao: typeof freguesia === 'string' ? null : (freguesia?.descricao ?? null),
    toponimoCodigo: comToponimo ? (valor.toponimo?.codigo ?? null) : ((valor as LocalizacaoPedido).toponimoCodigo ?? null),
    toponimoDescricao: comToponimo ? (valor.toponimo?.descricao ?? null) : null,
    toponimoTexto: valor.toponimoTexto ?? null,
  };
}

const MENSAGEM_GEOCONFORM_EM_FALTA = 'Indique se utiliza o GeoConform.';
const MENSAGEM_ELEMENTOS_EM_FALTA = 'Escolha pelo menos um elemento fundamental.';
const MENSAGEM_LOCALIZACAO_EM_FALTA = `Escolha "${ROTULO_APLICAVEL_TODA_AREA}" ou indique a freguesia.`;
const MENSAGEM_CONFORMACAO_EM_FALTA = 'Indique se a alteração conforma o plano com uma lei, programa ou plano de hierarquia superior.';
const MENSAGEM_MOTIVO_EM_FALTA = 'Escolha pelo menos um motivo da alteração.';
const MENSAGEM_PLANTA_NOVA_EM_FALTA = 'Indique se a alteração introduz alguma planta nova.';
const MENSAGEM_PECAS_GRAFICAS_COM_GEOCONFORM ='Numa dinâmica com GeoConform, a alteração afeta sempre as Peças Gráficas.';
const MENSAGEM_PLANTA_NOVA_EXIGE_PECAS_ESCRITAS = 'Introduzir uma planta nova exige alteração ao regulamento: assinale as Peças Escritas.';
const AJUDA_PLANTA_NOVA = 'Introduzir uma planta nova exige alteração ao regulamento (Peças Escritas).';

const PARTES_DR: Record<string, string> = {
  PRIMEIRA: '1.ª Série',
  SEGUNDA_H: '2.ª Série - H',
  SEGUNDA_C: '2.ª Série - C',
};

/** As opções do seletor da série, com os rótulos da escolha (restantes servidões). */
const ROTULOS_SERIE_ESCOLHIVEL: Record<SerieDr, string> = {
  PRIMEIRA: '1.ª Série',
  SEGUNDA_H: '2.ª Série – H',
  SEGUNDA_C: '2.ª Série – C',
};

export const ELEMENTOS_AFETADOS: readonly { valor: ElementoAfetado; rotulo: string }[] = [
  { valor: 'PECAS_ESCRITAS', rotulo: 'Peças escritas (Regulamento)' },
  { valor: 'PECAS_GRAFICAS', rotulo: 'Peças gráficas (Plantas)' },
  { valor: 'OUTROS', rotulo: 'Outros' },
];

/** Chaves de `camposBloqueados` com controlo em `form`; `dataSuplemento` tem controlo à parte, como a data de assinatura. */
const CONTROLOS_BLOQUEAVEIS = [
  'sumario',
  'normaLegal',
  'tipoInformacao',
  'publicarSuplemento',
  'contactoTramitacao',
  'entidadeInstrutora',
  'entidadeEmitente',
  'contactoPublicacao',
  'elementosAfetados',
  'fundamentacaoRjigt',
  'introduzPlantaNova',
  'localizacao',
  'conformaHierarquiaSuperior',
  'motivosAlteracao',
  'dinamicaAssociadaId',
  'tipoAtoId',
  'serieDr',
] as const;

/** Escolhas da Elaboração de um IGT novo, que criam o Instrumento no primeiro «Guardar». */
export interface DadosElaboracaoNova {
  nutIICodigo: string;
  concelho: string;
  designacao: string;
  tipoAtoId: number;
}

type CampoElaboracaoNova = keyof DadosElaboracaoNova;

const FALTA_ELABORACAO_NOVA: Record<CampoElaboracaoNova, string> = {
  nutIICodigo: 'Escolha a NUT II.',
  concelho: 'Escolha o concelho.',
  designacao: 'Indique a designação do IGT.',
  tipoAtoId: 'Escolha o tipo de ato.',
};

const CODIGO_TIPO_ATO_NAO_AUTORIZADO = 'TIPO_ATO_NAO_AUTORIZADO';
const CAMPO_TIPO_ATO = 'tipoAtoId';

/** O 403 do tipo de ato não traz `campos`: passa a erro do campo, para ficar junto do seletor e não no banner. */
export function comErroNoTipoAto(erro: ApiError): ApiError {
  if (erro.codigo !== CODIGO_TIPO_ATO_NAO_AUTORIZADO || (erro.campos?.length ?? 0) > 0) {
    return erro;
  }
  return new ApiError(erro.codigo, erro.mensagem, erro.traceId, [{ path: CAMPO_TIPO_ATO, erro: erro.mensagem }]);
}

export function rotuloParteDr(serieDr: string | null): string {
  return (serieDr && PARTES_DR[serieDr]) || '—';
}

export function rotuloTerritorio(itens: readonly ItemTerritorio[] | undefined): string {
  return itens?.length ? itens.map((item) => item.nome ?? item.codigo).join(', ') : '—';
}

/** Os concelhos do território, com a designação do SNIT formatada como nas listagens. */
export function rotuloConcelhos(itens: readonly ItemTerritorio[] | undefined): string {
  return rotuloTerritorio(itens?.map((item) => ({ ...item, nome: item.nome ? formatarNomeConcelho(item.nome) : item.nome })));
}

/**
 * Cartão "Informação Geral" do formulário fixo `INFORMACAO_GERAL`, partilhado entre o passo Formulário do
 * assistente e a edição do detalhe. Campos por `serieDr`; `null` mostra todos, como no backend, que então só
 * exige os comuns.
 */
@Component({
  selector: 'app-processo-formulario-inicial',
  standalone: true,
  imports: [DesbloquearCampoComponent, DinamicaAssociadaComponent, ReactiveFormsModule, SeletorComponent],
  styleUrls: ['./processo-formulario-inicial.component.css'],
  template: `
    <section class="cartao-geral">
      <h2 class="cabecalho">
        <button type="button" class="alternar" [attr.aria-expanded]="!recolhido" aria-controls="informacao-geral-corpo" (click)="recolhido = !recolhido">
          Informação Geral
          <svg class="chevron" [class.chevron-recolhido]="recolhido" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.33" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M4 10l4-4 4 4" />
          </svg>
        </button>
      </h2>
      <div class="corpo" id="informacao-geral-corpo" [hidden]="recolhido">
        @if ((!elaboracaoNova || leitura) && (leitura || !temSeccaoPropria())) {
        <dl class="contexto">
          <div><dt>Tipo de instrumento</dt><dd>{{ tipoInstrumento || '—' }}</dd></div>
          <div><dt>Tipo de procedimento</dt><dd class="tipo-procedimento">{{ rotuloTipoProcedimento() }}</dd></div>
          @if (!semIgtAssociado()) {
            <div><dt>NUT II</dt><dd>{{ rotuloTerritorio(territorio?.nutsII) }}</dd></div>
            <div><dt>Concelho</dt><dd>{{ rotuloConcelhos(territorio?.concelhos) }}</dd></div>
            <div><dt>Designação</dt><dd>{{ designacaoInstrumento || '—' }}</dd></div>
          }
          @if (instrumentosAbrangidos; as instrumentos) {
            <div class="largo instrumentos-associados">
              <dt>Instrumentos associados</dt>
              <dd>
                @if (instrumentos.length > 0) {
                  <ul>
                    @for (instrumento of instrumentos; track instrumento.id) {
                      <li>{{ instrumento.designacao }} <span class="tipo-instrumento">({{ instrumento.tipoInstrumento }})</span></li>
                    }
                  </ul>
                } @else {
                  Sem IGT associado
                }
              </dd>
            </div>
          }
          @if (leitura) {
            <div><dt>Parte do DR</dt><dd>{{ parteDr() }}</dd></div>
            <div><dt>Tipo de ato</dt><dd>{{ tipoAtoNome || '—' }}</dd></div>
          }
        </dl>
        }

        @if (leitura) {
          <dl class="leitura">
            @if (pedeEntidadeInstrutora()) {
              <div class="largo"><dt>Entidade instrutora</dt><dd>{{ valoresIniciais?.entidadeInstrutora || '—' }}</dd></div>
            }
            <div class="largo"><dt>Identificação da entidade emitente</dt><dd>{{ valoresIniciais?.entidadeEmitente || '—' }}</dd></div>
            <div class="largo"><dt>Contato para efeitos de tramitação processual</dt><dd>{{ valoresIniciais?.contactoTramitacao || '—' }}</dd></div>
            <div class="largo"><dt>Contato para publicação</dt><dd>{{ valoresIniciais?.contactoPublicacao || '—' }}</dd></div>
            @if (mostraCamposSegundaSerie()) {
              <div><dt>Norma legal ou mera conveniência</dt><dd>{{ valoresIniciais?.normaLegal || '—' }}</dd></div>
              <div><dt>Tipo de informação</dt><dd>{{ rotuloTipoInformacao() }}</dd></div>
            }
            @if (formulario === 'SGIFR') {
              <div><dt>Classe de servidão</dt><dd>{{ classeServidaoSgifr }}</dd></div>
              <div class="largo"><dt>Tipos de servidão</dt><dd>{{ rotuloRedesDefesa() }}</dd></div>
              @if (pedeRegiao || valoresIniciais?.regiao) {
                <div class="leitura-regiao"><dt>Região</dt><dd>{{ rotuloRegiao() }}</dd></div>
              }
              @if (pedeCimAm || valoresIniciais?.cimAm) {
                <div class="leitura-cim-am"><dt>CIM / AM</dt><dd>{{ rotuloCimAm() }}</dd></div>
              }
            }
            @if (formulario === 'REN') {
              @if (valoresIniciais?.motivoProcedimento; as motivo) {
                <div class="largo"><dt>Motivo do procedimento</dt><dd>{{ motivo.descricao }}</dd></div>
              }
              @if (valoresIniciais?.diplomaCorrigido; as diploma) {
                <div class="largo"><dt>Diploma que corrige ou retifica</dt><dd>{{ diploma }}</dd></div>
              }
              @if (valoresIniciais?.regiaoCodigo; as regiao) {
                <div><dt>Região</dt><dd>{{ nomeRegiao(regiao) }}</dd></div>
              }
              @if (valoresIniciais?.municipioCodigo; as municipio) {
                <div><dt>Município</dt><dd>{{ nomeMunicipio(municipio) }}</dd></div>
              }
            }
            @if (leituraLocalizacao(); as localizacao) {
              <div><dt>Freguesia</dt><dd>{{ localizacao.freguesia }}</dd></div>
              <div><dt>Topónimo</dt><dd>{{ localizacao.toponimo }}</dd></div>
            }
            @if (pedeMotivoAlteracao === 'COM_PERGUNTA') {
              <div class="largo"><dt>{{ perguntaConformacao }}</dt><dd>{{ rotuloSimNao(valoresIniciais?.conformaHierarquiaSuperior) }}</dd></div>
            }
            @if (leituraMotivos(); as motivos) {
              <div class="largo"><dt>Motivo da Alteração</dt><dd>{{ motivos }}</dd></div>
            }
            <div class="largo"><dt>Sumário conteúdo do ato</dt><dd>{{ valoresIniciais?.sumario || '—' }}</dd></div>
            @if (mostraDataAssinaturaNaLeitura()) {
              <div><dt>Data de Assinatura do ato</dt><dd class="data-assinatura">{{ rotuloDataAssinatura() }}</dd></div>
            }
            @if (pedePublicacaoNoCartao()) {
              <div><dt>Data de Assinatura do ato</dt><dd class="data-assinatura">{{ rotuloDataAssinatura() }}</dd></div>
              <div><dt>Publicação</dt><dd class="publicacao">{{ rotuloPublicacao() }}</dd></div>
            } @else if (mostraCheckboxSuplemento()) {
              <div><dt>Publicar suplemento</dt><dd>{{ valoresIniciais?.publicarSuplemento ? 'Sim' : 'Não' }}</dd></div>
            }
            @if (pedeElementosAfetados) {
              <div class="largo"><dt>Elementos fundamentais afetados</dt><dd>{{ rotuloElementosAfetados() }}</dd></div>
            }
            @if (formulario === 'PUBLICACAO_ALTERACAO' && rotuloPlantaNova(); as rotulo) {
              <div class="largo"><dt>A alteração introduz alguma planta nova?</dt><dd>{{ rotulo }}</dd></div>
            }
            @if (comGeoconform !== null) {
              <div class="largo"><dt>Utiliza a plataforma Geoconform</dt><dd>{{ comGeoconform ? 'Sim' : 'Não' }}</dd></div>
            }
            @if (pedeFundamentacaoRjigt) {
              <div class="largo"><dt>Fundamentação RJIGT</dt><dd>{{ rotuloFundamentacaoRjigt() }}</dd></div>
            }
            @if (rotuloIgtAssociado(); as igt) {
              <div class="largo igt-associado"><dt>IGT associado</dt><dd>{{ igt }}</dd></div>
            }
          </dl>
        } @else {
        <form class="campos" [formGroup]="form">
          @if (elaboracaoNova) {
            <app-seletor
              class="campo"
              rotulo="NUT II"
              [obrigatorio]="true"
              [opcoes]="opcoesNutsII"
              [formControl]="formElaboracao.controls.nutIICodigo"
              [erro]="erroElaboracaoNova('nutIICodigo')"
            />
            <app-seletor
              class="campo"
              rotulo="Concelho"
              [obrigatorio]="true"
              [opcoes]="opcoesConcelhos"
              [formControl]="formElaboracao.controls.concelho"
              [ajuda]="formElaboracao.controls.concelho.disabled ? ajudaConcelhoSemNutII : null"
              [erro]="erroElaboracaoNova('concelho')"
            />
            <label class="campo">
              <span class="rotulo">Designação</span>
              <input
                type="text"
                name="designacao"
                [formControl]="formElaboracao.controls.designacao"
                [maxLength]="limiteDesignacao"
                [attr.aria-invalid]="erroElaboracaoNova('designacao') ? true : null"
                [attr.aria-describedby]="erroElaboracaoNova('designacao') ? 'erro-designacao' : null"
              />
              @if (erroElaboracaoNova('designacao'); as mensagem) {
                <p class="erro-campo" id="erro-designacao">{{ mensagem }}</p>
              }
            </label>
          }
          @if (pedeEntidadeInstrutora()) {
            <label class="campo">
              <span class="rotulo">Entidade instrutora</span>
              <input
                type="text"
                formControlName="entidadeInstrutora"
                [maxLength]="limiteEmitente"
                [attr.aria-required]="entidadeInstrutoraObrigatoria ? true : null"
                [attr.aria-invalid]="form.controls.entidadeInstrutora.hasError('servidor') ? true : null"
                [attr.aria-describedby]="descricaoCampo('entidadeInstrutora')"
              />
              @if (bloqueado('entidadeInstrutora')) {
                <p class="aviso-bloqueado" id="bloqueado-entidadeInstrutora">{{ mensagemBloqueado }}</p>
                <app-desbloquear-campo chave="entidadeInstrutora" rotulo="Entidade instrutora" />
              }
              @if (form.controls.entidadeInstrutora.getError('servidor'); as mensagem) {
                <p class="erro-campo" id="erro-entidadeInstrutora" role="alert">{{ mensagem }}</p>
              }
            </label>
          }
          @if (formulario === 'SUSPENSAO_CCDR') {
            <div class="campo entidade-emitente-lista">
              <app-seletor
                rotulo="Identificação da entidade emitente"
                placeholder="Escolha a CCDR ou Direção Regional"
                formControlName="entidadeEmitente"
                [obrigatorio]="true"
                [opcoes]="opcoesEmitentes()"
                [erro]="form.controls.entidadeEmitente.getError('servidor') ?? null"
                [ajuda]="ajudaEmitente()"
                [descritoPorAdicional]="descricaoBloqueio('entidadeEmitente')"
              />
              @if (bloqueado('entidadeEmitente')) {
                <p class="aviso-bloqueado" id="bloqueado-entidadeEmitente">{{ mensagemBloqueado }}</p>
                <app-desbloquear-campo chave="entidadeEmitente" rotulo="Identificação da entidade emitente" />
              }
            </div>
          } @else {
          <label class="campo">
            <span class="rotulo">Identificação da entidade emitente</span>
            <input type="text" formControlName="entidadeEmitente" [maxLength]="limiteEmitente" [attr.aria-required]="todosObrigatorios() ? true : null" [attr.aria-describedby]="descricaoCampo('entidadeEmitente')" />
            @if (bloqueado('entidadeEmitente')) {
              <p class="aviso-bloqueado" id="bloqueado-entidadeEmitente">{{ mensagemBloqueado }}</p>
              <app-desbloquear-campo chave="entidadeEmitente" rotulo="Identificação da entidade emitente" />
            }
            @if (form.controls.entidadeEmitente.getError('servidor'); as mensagem) {
              <p class="erro-campo" id="erro-entidadeEmitente" role="alert">{{ mensagem }}</p>
            }
          </label>
          }
          <label class="campo">
            <span class="rotulo">Contato para efeitos de tramitação processual</span>
            <input type="email" formControlName="contactoTramitacao" [maxLength]="limiteContacto" autocomplete="email" [attr.aria-required]="todosObrigatorios() ? true : null" [attr.aria-describedby]="descricaoCampo('contactoTramitacao')" />
            @if (bloqueado('contactoTramitacao')) {
              <p class="aviso-bloqueado" id="bloqueado-contactoTramitacao">{{ mensagemBloqueado }}</p>
              <app-desbloquear-campo chave="contactoTramitacao" rotulo="Contato para efeitos de tramitação processual" />
            }
            @if (form.controls.contactoTramitacao.getError('servidor'); as mensagem) {
              <p class="erro-campo" id="erro-contactoTramitacao" role="alert">{{ mensagem }}</p>
            } @else if (form.controls.contactoTramitacao.hasError('email') && form.controls.contactoTramitacao.touched) {
              <p class="erro-campo">Indique um email válido.</p>
            }
          </label>
          <label class="campo">
            <span class="rotulo">Contato para publicação</span>
            <input type="text" formControlName="contactoPublicacao" [maxLength]="limiteEmitente" [attr.aria-required]="todosObrigatorios() ? true : null" [attr.aria-describedby]="descricaoCampo('contactoPublicacao')" />
            @if (bloqueado('contactoPublicacao')) {
              <p class="aviso-bloqueado" id="bloqueado-contactoPublicacao">{{ mensagemBloqueado }}</p>
              <app-desbloquear-campo chave="contactoPublicacao" rotulo="Contato para publicação" />
            }
            @if (form.controls.contactoPublicacao.getError('servidor'); as mensagem) {
              <p class="erro-campo" id="erro-contactoPublicacao" role="alert">{{ mensagem }}</p>
            }
          </label>
          @if (mostraCamposSegundaSerie()) {
            <label class="campo">
              <span class="rotulo">Norma legal ou mera conveniência</span>
              <input type="text" formControlName="normaLegal" [maxLength]="limiteNormaLegal" [attr.aria-required]="todosObrigatorios() ? true : null" [attr.aria-describedby]="descricaoCampo('normaLegal')" />
              @if (bloqueado('normaLegal')) {
                <p class="aviso-bloqueado" id="bloqueado-normaLegal">{{ mensagemBloqueado }}</p>
                <app-desbloquear-campo chave="normaLegal" rotulo="Norma legal ou mera conveniência" />
              }
              @if (form.controls.normaLegal.getError('servidor'); as mensagem) {
                <p class="erro-campo" id="erro-normaLegal" role="alert">{{ mensagem }}</p>
              }
            </label>
          }
          @if (mostraLocalizacao()) {
            <fieldset class="campo campo-largo localizacao" formGroupName="localizacao" [attr.aria-describedby]="descricaoLocalizacao()">
              <!-- O mockup não mostra título no grupo; a legenda fica só para o leitor de ecrã, que anuncia a obrigatoriedade. -->
              <legend class="so-leitor">Localização{{ pedeLocalizacao === 'OBRIGATORIA' ? ' (obrigatório)' : '' }}</legend>
              <div class="localizacao-campos">
                <label class="campo">
                  <span class="rotulo">
                    Freguesia
                    @if (pedeLocalizacao === 'OBRIGATORIA') {
                      <span class="marca-obrigatoria" aria-hidden="true">*</span>
                    }
                  </span>
                  <select formControlName="freguesia" [attr.aria-describedby]="erroServidor('localizacao.freguesia') ? 'erro-localizacao-freguesia' : null">
                    <option value="">Selecione a freguesia</option>
                    @for (opcao of opcoesFreguesia(); track opcao.codigo) {
                      <option [value]="opcao.codigo">{{ opcao.descricao }}</option>
                    }
                  </select>
                  @if (erroServidor('localizacao.freguesia'); as mensagem) {
                    <p class="erro-campo" id="erro-localizacao-freguesia">{{ mensagem }}</p>
                  }
                </label>
                @if (mostraToponimo()) {
                  <label class="campo">
                    <span class="rotulo">Topónimo</span>
                    <input
                      type="text"
                      formControlName="toponimo"
                      list="opcoes-toponimo"
                      [maxLength]="limiteToponimo"
                      [attr.aria-describedby]="erroServidor('localizacao.toponimo') ? 'erro-localizacao-toponimo' : null"
                    />
                    <datalist id="opcoes-toponimo">
                      @for (opcao of opcoes()['TOPONIMO'] ?? []; track opcao.codigo) {
                        <option [value]="opcao.descricao"></option>
                      }
                    </datalist>
                    @if (erroServidor('localizacao.toponimo'); as mensagem) {
                      <p class="erro-campo" id="erro-localizacao-toponimo">{{ mensagem }}</p>
                    }
                  </label>
                }
              </div>
              <label class="campo-checkbox">
                <input
                  type="checkbox"
                  formControlName="aplicavelTodaArea"
                  [attr.aria-describedby]="erroServidor('localizacao.aplicavelTodaArea') ? 'erro-localizacao-aplicavelTodaArea' : null"
                />
                <span>{{ rotuloAplicavelTodaArea }}</span>
              </label>
              @if (erroServidor('localizacao.aplicavelTodaArea'); as mensagem) {
                <p class="erro-campo" id="erro-localizacao-aplicavelTodaArea">{{ mensagem }}</p>
              }
              @if (bloqueado('localizacao')) {
                <p class="aviso-bloqueado" id="bloqueado-localizacao">{{ mensagemBloqueado }}</p>
                <app-desbloquear-campo chave="localizacao" rotulo="Localização" />
              }
              @if (mensagemErro('localizacao'); as mensagem) {
                <p class="erro-campo" id="erro-localizacao" role="alert">{{ mensagem }}</p>
              }
            </fieldset>
          }
          @if (pedeMotivoAlteracao === 'COM_PERGUNTA') {
            <fieldset class="campo campo-largo conformacao" [attr.aria-describedby]="descricaoCampo('conformaHierarquiaSuperior')">
              <legend class="rotulo">
                {{ perguntaConformacao }}
                <span class="marca-obrigatoria" aria-hidden="true">*</span><span class="so-leitor">(obrigatório)</span>
              </legend>
              <div class="opcoes-geoconform">
                <label class="opcao-geoconform" [class.opcao-geoconform-escolhida]="form.controls.conformaHierarquiaSuperior.value === true">
                  <input type="radio" name="conformaHierarquiaSuperior" [value]="true" formControlName="conformaHierarquiaSuperior" />
                  <span>Sim</span>
                </label>
                <label class="opcao-geoconform" [class.opcao-geoconform-escolhida]="form.controls.conformaHierarquiaSuperior.value === false">
                  <input type="radio" name="conformaHierarquiaSuperior" [value]="false" formControlName="conformaHierarquiaSuperior" />
                  <span>Não</span>
                </label>
              </div>
              @if (bloqueado('conformaHierarquiaSuperior')) {
                <p class="aviso-bloqueado" id="bloqueado-conformaHierarquiaSuperior">{{ mensagemBloqueado }}</p>
                <app-desbloquear-campo chave="conformaHierarquiaSuperior" rotulo="Conformação com lei, programa ou plano" />
              }
              @if (mensagemErro('conformaHierarquiaSuperior'); as mensagem) {
                <p class="erro-campo" id="erro-conformaHierarquiaSuperior" role="alert">{{ mensagem }}</p>
              }
            </fieldset>
          }
          @if (mostraMotivos()) {
            <fieldset class="campo campo-largo motivos" [attr.aria-describedby]="descricaoCampo('motivosAlteracao')">
              <legend class="rotulo">
                Motivo da Alteração
                <span class="marca-obrigatoria" aria-hidden="true">*</span><span class="so-leitor">(obrigatório)</span>
              </legend>
              @for (grupo of gruposMotivos(); track grupo.tipo) {
                <fieldset class="subgrupo-motivos">
                  <legend class="rotulo-subgrupo">{{ grupo.rotulo }}</legend>
                  <div class="opcoes-checkbox">
                    @for (opcao of grupo.opcoes; track opcao.codigo) {
                      <label class="campo-checkbox">
                        <input
                          type="checkbox"
                          [checked]="motivoMarcado(grupo.tipo, opcao.codigo)"
                          [disabled]="form.controls.motivosAlteracao.disabled"
                          (change)="alternarMotivo(grupo.tipo, opcao.codigo)"
                        />
                        <span>{{ opcao.descricao }}</span>
                      </label>
                    }
                  </div>
                </fieldset>
              }
              @if (bloqueado('motivosAlteracao')) {
                <p class="aviso-bloqueado" id="bloqueado-motivosAlteracao">{{ mensagemBloqueado }}</p>
                <app-desbloquear-campo chave="motivosAlteracao" rotulo="Motivo da alteração" />
              }
              @if (mensagemErro('motivosAlteracao'); as mensagem) {
                <p class="erro-campo" id="erro-motivosAlteracao" role="alert">{{ mensagem }}</p>
              }
            </fieldset>
          }
          @if (mostraFundamentacaoRjigt()) {
            <fieldset class="campo campo-largo fundamentacao" [attr.aria-describedby]="descricaoCampo('fundamentacaoRjigt')">
              <legend class="rotulo">Fundamentação RJIGT</legend>
              <div class="opcoes-checkbox">
                @for (opcao of opcoesFundamentacaoRjigt; track opcao.codigo) {
                  <label class="campo-checkbox">
                    <input
                      type="checkbox"
                      [value]="opcao.codigo"
                      [checked]="fundamentacaoMarcada(opcao.codigo)"
                      [disabled]="form.controls.fundamentacaoRjigt.disabled"
                      (change)="alternarFundamentacao(opcao.codigo)"
                    />
                    <span>{{ opcao.descricao }}</span>
                  </label>
                }
              </div>
              @if (bloqueado('fundamentacaoRjigt')) {
                <p class="aviso-bloqueado" id="bloqueado-fundamentacaoRjigt">{{ mensagemBloqueado }}</p>
                <app-desbloquear-campo chave="fundamentacaoRjigt" rotulo="Fundamentação RJIGT" />
              }
              @if (form.controls.fundamentacaoRjigt.getError('servidor'); as mensagem) {
                <p class="erro-campo" id="erro-fundamentacaoRjigt" role="alert">{{ mensagem }}</p>
              }
            </fieldset>
          }
          @if (!escolheSerie()) {
            <dl class="contexto campo-largo">
              <div><dt>Parte do DR</dt><dd>{{ parteDr() }}</dd></div>
            </dl>
          } @else if (bloqueado('serieDr')) {
            <div class="campo serie-dr">
              <dl class="contexto">
                <div><dt>Série do Diário da República</dt><dd class="serie-dr-nome" aria-describedby="bloqueado-serieDr">{{ parteDr() }}</dd></div>
              </dl>
              <p class="aviso-bloqueado" id="bloqueado-serieDr">{{ mensagemBloqueado }}</p>
              <app-desbloquear-campo chave="serieDr" rotulo="Série do Diário da República" />
            </div>
          } @else {
            <app-seletor
              class="campo serie-dr"
              rotulo="Série do Diário da República"
              placeholder="Selecionar..."
              formControlName="serieDr"
              [obrigatorio]="true"
              [opcoes]="opcoesSerieDr"
              [erro]="form.controls.serieDr.getError('servidor') ?? null"
            />
          }
          @if (elaboracaoNova) {
            <app-seletor
              class="campo"
              rotulo="Tipo de ato"
              [opcoes]="opcoesTiposAto"
              [formControl]="formElaboracao.controls.tipoAtoId"
              [erro]="erroElaboracaoNova('tipoAtoId')"
            />
          } @else if (bloqueado('tipoAtoId')) {
            <div class="campo tipo-ato">
              <dl class="contexto">
                <div><dt>Tipo de ato</dt><dd class="tipo-ato-nome" aria-describedby="bloqueado-tipoAtoId">{{ nomeTipoAto() }}</dd></div>
              </dl>
              <p class="aviso-bloqueado" id="bloqueado-tipoAtoId">{{ mensagemBloqueado }}</p>
              <app-desbloquear-campo chave="tipoAtoId" rotulo="Tipo de ato" />
            </div>
          } @else {
            <app-seletor
              class="campo tipo-ato"
              rotulo="Tipo de ato"
              placeholder="Selecionar..."
              formControlName="tipoAtoId"
              [obrigatorio]="true"
              [opcoes]="opcoesTipoAto"
              [erro]="form.controls.tipoAtoId.getError('servidor') ?? null"
              [ajuda]="ajudaTipoAto()"
            />
          }
          @if (pedeDataAssinaturaNoCartao()) {
            <label class="campo">
              <span class="rotulo">Data de Assinatura do ato</span>
              <input type="date" class="data-assinatura" [formControl]="dataAssinatura" aria-required="true" [attr.aria-describedby]="descricaoDataAssinatura()" />
              @if (bloqueado('dataAssinatura')) {
                <p class="aviso-bloqueado" id="bloqueado-dataAssinatura">{{ mensagemBloqueado }}</p>
                <app-desbloquear-campo chave="dataAssinatura" rotulo="Data de Assinatura do ato" />
              }
              @if (mensagemErroDataAssinatura(); as mensagem) {
                <p class="erro-campo" id="erro-dataAssinatura" role="alert">{{ mensagem }}</p>
              }
            </label>
          }
          @if (pedePublicacaoNoCartao()) {
            <fieldset class="campo publicacao" aria-required="true" role="radiogroup" [attr.aria-describedby]="descricaoCampo('publicarSuplemento')">
              <legend class="rotulo">Publicação</legend>
              <div class="opcoes-radio">
                <label class="opcao-radio">
                  <input type="radio" formControlName="publicarSuplemento" [value]="false" />
                  <span>Diário</span>
                </label>
                <label class="opcao-radio">
                  <input type="radio" formControlName="publicarSuplemento" [value]="true" />
                  <span>Suplemento</span>
                </label>
              </div>
              @if (bloqueado('publicarSuplemento')) {
                <p class="aviso-bloqueado" id="bloqueado-publicarSuplemento">{{ mensagemBloqueado }}</p>
                <app-desbloquear-campo chave="publicarSuplemento" rotulo="Publicar suplemento" />
              }
              @if (form.controls.publicarSuplemento.getError('servidor'); as mensagem) {
                <p class="erro-campo" id="erro-publicarSuplemento" role="alert">{{ mensagem }}</p>
              }
            </fieldset>
            @if (form.controls.publicarSuplemento.value === true) {
              <label class="campo data-suplemento">
                <span class="rotulo">Data do suplemento</span>
                <input type="date" name="dataSuplemento" [formControl]="dataSuplemento" aria-required="true" [attr.aria-describedby]="descricaoDataSuplemento()" />
                @if (bloqueado('dataSuplemento')) {
                  <p class="aviso-bloqueado" id="bloqueado-dataSuplemento">{{ mensagemBloqueado }}</p>
                }
                @if (dataSuplemento.getError('servidor'); as mensagem) {
                  <p class="erro-campo" id="erro-dataSuplemento" role="alert">{{ mensagem }}</p>
                }
              </label>
            }
          }
          @if (formulario === 'SGIFR') {
            <dl class="contexto campo-largo servidao">
              <div><dt>Classe de servidão</dt><dd>{{ classeServidaoSgifr }}</dd></div>
              @if (redeUnica(); as rede) {
                <div><dt>Tipo de servidão</dt><dd>{{ rede.nome }}</dd></div>
              }
            </dl>
            @if (escolheRedes()) {
              <fieldset class="campo campo-largo redes-defesa" [attr.aria-describedby]="descricaoRedesDefesa()">
                <legend class="rotulo">Tipos de servidão</legend>
                <p class="ajuda-campo" id="ajuda-redesDefesa">{{ ajudaRedesDefesa }}</p>
                <div class="opcoes-checkbox">
                  @for (rede of redesDefesaDisponiveis; track rede.codigo) {
                    <label class="campo-checkbox">
                      <input
                        type="checkbox"
                        [value]="rede.codigo"
                        [checked]="redeMarcada(rede.codigo)"
                        [disabled]="form.controls.redesDefesa.disabled"
                        (change)="alternarRede(rede.codigo)"
                      />
                      <span>{{ rede.nome }} ({{ rede.codigo }})</span>
                    </label>
                  }
                </div>
                @if (form.controls.redesDefesa.getError('servidor'); as mensagem) {
                  <p class="erro-campo" id="erro-redesDefesa" role="alert">{{ mensagem }}</p>
                }
              </fieldset>
            }
            @if (pedeRegiao) {
              <app-seletor
                class="campo regiao"
                rotulo="Região"
                placeholder="Selecione a região"
                formControlName="regiao"
                [permitirVazio]="true"
                [opcoes]="opcoesSeletorRegiao"
                [erro]="form.controls.regiao.getError('servidor') ?? null"
              />
            }
            @if (pedeCimAm) {
              <app-seletor
                class="campo cim-am"
                rotulo="CIM / AM"
                placeholder="Selecione a CIM / AM"
                formControlName="cimAm"
                [permitirVazio]="true"
                [opcoes]="opcoesSeletorCimAm"
                [ajuda]="ajudaCimAm()"
                [erro]="form.controls.cimAm.getError('servidor') ?? null"
              />
            }
          }
          @if (formulario === 'REN') {
            @if (pedeDiplomaCorrigido) {
              <label class="campo campo-largo">
                <span class="rotulo">Diploma que corrige ou retifica</span>
                <input
                  type="text"
                  formControlName="diplomaCorrigido"
                  [maxLength]="limiteEmitente"
                  [attr.aria-invalid]="mensagemErro('diplomaCorrigido') ? true : null"
                  [attr.aria-describedby]="mensagemErro('diplomaCorrigido') ? 'erro-diplomaCorrigido' : null"
                />
                @if (mensagemErro('diplomaCorrigido'); as mensagem) {
                  <p class="erro-campo" id="erro-diplomaCorrigido" role="alert">{{ mensagem }}</p>
                }
              </label>
            }
            <app-seletor
              class="campo"
              rotulo="Região"
              placeholder="Selecione a região"
              [permitirVazio]="true"
              [opcoes]="opcoesRegiaoRen()"
              [formControl]="form.controls.regiaoCodigo"
              [erro]="mensagemErro('regiaoCodigo')"
            />
            <app-seletor
              class="campo"
              rotulo="Município"
              placeholder="Selecione o município"
              [permitirVazio]="true"
              [opcoes]="opcoesMunicipio()"
              [formControl]="form.controls.municipioCodigo"
              [ajuda]="form.controls.regiaoCodigo.value ? null : ajudaMunicipioSemRegiao"
              [erro]="mensagemErro('municipioCodigo')"
            />
            @if (mostraMotivoProcedimento()) {
              <app-seletor
                class="campo campo-largo motivo-procedimento"
                rotulo="Motivo do procedimento"
                placeholder="Selecione o motivo"
                [obrigatorio]="true"
                [opcoes]="opcoesMotivoProcedimento()"
                [formControl]="form.controls.motivoProcedimentoId"
                [erro]="mensagemErro('motivoProcedimentoId')"
              />
            }
          }
          @if (mostraCamposSegundaSerie() && tipoInformacaoFixa()) {
            <dl class="contexto campo-largo">
              <div><dt>Tipo de informação</dt><dd>Texto integral</dd></div>
            </dl>
          } @else if (mostraCamposSegundaSerie()) {
            <fieldset
              class="campo tipo-informacao"
              [attr.role]="todosObrigatorios() ? 'radiogroup' : null"
              [attr.aria-required]="todosObrigatorios() ? true : null"
              [attr.aria-describedby]="descricaoCampo('tipoInformacao')"
            >
              <legend class="rotulo">Tipo de informação</legend>
              <div class="opcoes-radio">
                <label class="opcao-radio">
                  <input type="radio" formControlName="tipoInformacao" value="TEXTO_INTEGRAL" />
                  <span>Texto integral</span>
                </label>
                <label class="opcao-radio">
                  <input type="radio" formControlName="tipoInformacao" value="EXTRATO" />
                  <span>Extrato</span>
                </label>
              </div>
              @if (bloqueado('tipoInformacao')) {
                <p class="aviso-bloqueado" id="bloqueado-tipoInformacao">{{ mensagemBloqueado }}</p>
                <app-desbloquear-campo chave="tipoInformacao" rotulo="Tipo de informação" />
              }
              @if (form.controls.tipoInformacao.getError('servidor'); as mensagem) {
                <p class="erro-campo" id="erro-tipoInformacao" role="alert">{{ mensagem }}</p>
              }
            </fieldset>
          }
          <label class="campo campo-largo">
            <span class="rotulo">Sumário conteúdo do ato <span class="marca-obrigatoria" aria-hidden="true">*</span></span>
            <textarea formControlName="sumario" [maxLength]="limiteSumario" placeholder="Resumo do conteúdo do ato a publicar (até {{ limiteSumario }} caracteres)" aria-required="true" [attr.aria-describedby]="descricaoCampo('sumario')"></textarea>
            @if (bloqueado('sumario')) {
              <p class="aviso-bloqueado" id="bloqueado-sumario">{{ mensagemBloqueado }}</p>
              <app-desbloquear-campo chave="sumario" rotulo="Sumário conteúdo do ato" />
            }
            @if (form.controls.sumario.getError('servidor'); as mensagem) {
              <p class="erro-campo" id="erro-sumario" role="alert">{{ mensagem }}</p>
            }
          </label>
          @if (mostraCheckboxSuplemento()) {
            <div class="campo campo-largo">
              <label class="campo-checkbox">
                <input type="checkbox" formControlName="publicarSuplemento" [attr.aria-describedby]="descricaoBloqueio('publicarSuplemento')" />
                <span>Publicar suplemento</span>
              </label>
              @if (bloqueado('publicarSuplemento')) {
                <p class="aviso-bloqueado" id="bloqueado-publicarSuplemento">{{ mensagemBloqueado }}</p>
                <app-desbloquear-campo chave="publicarSuplemento" rotulo="Publicar suplemento" />
              }
              @if (form.controls.publicarSuplemento.getError('servidor'); as mensagem) {
                <p class="erro-campo">{{ mensagem }}</p>
              }
            </div>
          }
          @if (pedeElementosAfetados) {
          <div class="campo-largo seccao">
          <fieldset class="campo elementos" formGroupName="elementosAfetados" [attr.aria-describedby]="descricaoElementos()">
            <legend class="rotulo">A alteração incide sobre que elementos fundamentais?</legend>
            <div class="opcoes-checkbox">
              @for (elemento of elementosAfetados; track elemento.valor) {
                <label class="campo-checkbox">
                  <input
                    type="checkbox"
                    [formControlName]="elemento.valor"
                    [attr.aria-describedby]="elemento.valor === 'PECAS_GRAFICAS' && pecasGraficasFixas() ? 'ajuda-pecasGraficas' : null"
                  />
                  <span>{{ elemento.rotulo }}</span>
                </label>
              }
            </div>
            @if (pecasGraficasFixas()) {
              <p class="ajuda-campo" id="ajuda-pecasGraficas">{{ mensagemPecasGraficasComGeoconform }}</p>
            }
            @if (bloqueado('elementosAfetados')) {
              <p class="aviso-bloqueado" id="bloqueado-elementosAfetados">{{ mensagemBloqueado }}</p>
              <app-desbloquear-campo chave="elementosAfetados" rotulo="Elementos fundamentais afetados" />
            }
            @if (mensagemErro('elementosAfetados'); as mensagem) {
              <p class="erro-campo" id="erro-elementosAfetados" role="alert">{{ mensagem }}</p>
            }
            @if (erroPlantaNovaSemPecasEscritas()) {
              <p class="erro-campo" id="erro-elementosAfetados-regulamento" role="alert">{{ mensagemPlantaNovaExigePecasEscritas }}</p>
            }
            @if (!perguntaPlantaNovaVisivel() && form.controls.introduzPlantaNova.getError('servidor'); as mensagem) {
              <p class="erro-campo" role="alert">{{ mensagem }}</p>
            }
          </fieldset>
          </div>
          }
          @if (perguntaPlantaNovaVisivel()) {
            <div class="campo-largo seccao">
            <fieldset class="campo planta-nova" [attr.aria-describedby]="descricaoPlantaNova()">
              <legend class="rotulo">A alteração introduz alguma planta nova?</legend>
              <div class="opcoes-geoconform">
                <label class="opcao-geoconform" [class.opcao-geoconform-escolhida]="form.controls.introduzPlantaNova.value === true">
                  <input type="radio" name="introduzPlantaNova" [value]="true" formControlName="introduzPlantaNova" />
                  <span>Sim</span>
                </label>
                <label class="opcao-geoconform" [class.opcao-geoconform-escolhida]="form.controls.introduzPlantaNova.value === false">
                  <input type="radio" name="introduzPlantaNova" [value]="false" formControlName="introduzPlantaNova" />
                  <span>Não</span>
                </label>
              </div>
              <p class="ajuda-campo" id="ajuda-introduzPlantaNova">{{ ajudaPlantaNova }}</p>
              @if (bloqueado('introduzPlantaNova')) {
                <p class="aviso-bloqueado" id="bloqueado-introduzPlantaNova">{{ mensagemBloqueado }}</p>
                <app-desbloquear-campo chave="introduzPlantaNova" rotulo="Planta nova" />
              }
              @if (mensagemErro('introduzPlantaNova'); as mensagem) {
                <p class="erro-campo" id="erro-introduzPlantaNova" role="alert">{{ mensagem }}</p>
              }
            </fieldset>
            </div>
          }
          @if (aceitaDinamicaAssociada && processoId) {
            <div class="campo-largo seccao associacao-igt">
              <h3 class="titulo-bloco">Associação a um IGT em curso</h3>
              <app-dinamica-associada
                formControlName="dinamicaAssociadaId"
                [processoId]="processoId"
                [dinamicaInicial]="dinamicaAssociada"
                [respostaInicial]="simultaneoComPmotGravado"
                [erro]="form.controls.dinamicaAssociadaId.getError('servidor') ?? null"
              />
              @if (bloqueado('dinamicaAssociadaId')) {
                <p class="aviso-bloqueado" id="bloqueado-dinamicaAssociadaId">{{ mensagemBloqueado }}</p>
                <app-desbloquear-campo chave="dinamicaAssociadaId" rotulo="Associação a um IGT em curso" />
              }
            </div>
          }
          @if (perguntaGeoconformVisivel()) {
            <div class="campo-largo seccao">
            <fieldset class="campo geoconform" [attr.aria-describedby]="mensagemGeoconform() ? 'erro-comGeoconform' : null">
              <legend class="rotulo">Vai fazer uso da plataforma Geoconform para a validação da informação e emissão das plantas?</legend>
              <div class="opcoes-geoconform">
                <label class="opcao-geoconform" [class.opcao-geoconform-escolhida]="form.controls.comGeoconform.value === true">
                  <input type="radio" name="comGeoconform" [value]="true" formControlName="comGeoconform" />
                  <span>Sim</span>
                </label>
                <label class="opcao-geoconform" [class.opcao-geoconform-escolhida]="form.controls.comGeoconform.value === false">
                  <input type="radio" name="comGeoconform" [value]="false" formControlName="comGeoconform" />
                  <span>Não</span>
                </label>
              </div>
              @if (mensagemGeoconform(); as mensagem) {
                <p class="erro-campo" id="erro-comGeoconform" role="alert">{{ mensagem }}</p>
              }
            </fieldset>
            </div>
          }
        </form>
        }
      </div>
    </section>
  `,
})
export class ProcessoFormularioInicialComponent implements OnChanges {
  private readonly fb = inject(FormBuilder);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly vista = inject(ChangeDetectorRef);
  private readonly listaValoresService = inject(ListaValoresService);
  private readonly referenciaSnitService = inject(ReferenciaSnitService);

  readonly mensagemPecasGraficasComGeoconform = MENSAGEM_PECAS_GRAFICAS_COM_GEOCONFORM;
  readonly mensagemPlantaNovaExigePecasEscritas = MENSAGEM_PLANTA_NOVA_EXIGE_PECAS_ESCRITAS;
  readonly ajudaPlantaNova = AJUDA_PLANTA_NOVA;
  readonly limiteSumario = 2000;
  readonly limiteNormaLegal = 500;
  readonly limiteContacto = 254;
  readonly limiteEmitente = 500;
  readonly limiteDesignacao = 255;
  readonly elementosAfetados = ELEMENTOS_AFETADOS;
  readonly mensagemBloqueado = MENSAGEM_CAMPO_BLOQUEADO;
  readonly limiteToponimo = 500;
  readonly rotuloAplicavelTodaArea = ROTULO_APLICAVEL_TODA_AREA;
  readonly perguntaConformacao = PERGUNTA_CONFORMACAO;
  readonly classeServidaoSgifr = CLASSE_SERVIDAO_SGIFR;
  readonly ajudaRedesDefesa = AJUDA_REDES_DEFESA;
  readonly opcoes = signal<Partial<Record<TipoListaFormulario, OpcaoLista[]>>>({});

  @Input() serieDr: string | null = null;
  /** `seriesDrEscolhiveis` do detalhe: com elas, a «Parte do DR» passa a seletor da série (restantes servidões). */
  @Input() seriesDrEscolhiveis: SerieDr[] | null | undefined = null;
  /** O autor escolheu outra série: o ecrã grava o rascunho e relê o detalhe, que traz os tipos de ato dela. */
  readonly serieEscolhida = output<SerieDr>();
  @Input() valoresIniciais: FormularioInicialValores | null = null;
  @Input() tipoInstrumento: string | null = null;
  /** Só os Programas pedem a entidade instrutora; nulo enquanto a categoria não é conhecida. */
  @Input() categoriaInstrumento: CategoriaInstrumento | null = null;
  /** Nos Programas, se a entidade instrutora é obrigatória; falso no PS. */
  @Input() entidadeInstrutoraObrigatoria = true;
  @Input() tipoProcedimento: string | null = null;
  /** Só no formulário da Ratificação, para a leitura. */
  @Input() modalidadeRatificacao: ModalidadeRatificacao | null | undefined = null;
  @Input() designacaoInstrumento: string | null = null;
  /** Só leitura: associar e desassociar faz-se no ecrã dos documentos. Nula enquanto o detalhe não foi lido. */
  @Input() instrumentosAbrangidos: InstrumentoAbrangido[] | null = null;
  @Input() territorio: TerritorioInstrumento | null = null;
  @Input() tipoAtoNome: string | null = null;
  /** As opções do «Tipo de ato» (`tiposAtoDisponiveis` do detalhe); o valor gravado vem em `valoresIniciais.tipoAtoId`. */
  @Input() tiposAtoDisponiveis: TipoAtoDisponivel[] = [];
  @Input() aCarregar = false;
  /** Mostra os valores gravados em texto, sem campos editáveis. */
  @Input() leitura = false;
  /** `camposBloqueados` do processo: os controlos destas chaves ficam desativados e continuam a contar em `dados()`. */
  @Input() camposBloqueados: string[] = [];
  /** O Procedimento pede a Fundamentação RJIGT; sem opções na lista o grupo não aparece. */
  @Input() pedeFundamentacaoRjigt = false;
  @Input() opcoesFundamentacaoRjigt: OpcaoLista[] = [];
  /** O Procedimento pergunta pelo GeoConform: a resposta grava-se com o formulário, na Dinâmica. */
  @Input() exigeOpcaoGeoconform = false;
  /** Configuração do Procedimento; nula enquanto não há detalhe, e então vale `exigeOpcaoGeoconform`. */
  @Input() perguntaGeoconform: PerguntaGeoconform | null = null;
  @Input() comGeoconform: boolean | null = null;
  /** Falso na Elaboração: um plano novo não altera elementos, e a pergunta não aparece nem se grava. */
  @Input() pedeElementosAfetados = true;
  /** Recusa da submissão por falta de resposta, que chega depois de o cartão ter sido recriado. */
  @Input() erroComGeoconform: string | null = null;
  /** O formulário do Procedimento: só o da Publicação da Alteração tem a pergunta da planta nova. */
  @Input() formulario: FormularioFixo | null = null;
  /** Do detalhe: o Procedimento não tem a Informação Base do Texto do Ato, por isso a data pede-se neste cartão. */
  @Input() dataAssinaturaNoFormulario = false;
  /** Erros 400 da submissão para campos deste cartão; chegam depois de o cartão ter sido recriado. */
  @Input() errosServidor: CampoErro[] = [];
  @Input() pedeLocalizacao: PedeLocalizacao = 'NAO';
  @Input() pedeMotivoAlteracao: PedeMotivoAlteracao = 'NAO';
  /** IGT novo de um tipo com Elaboração: NUT II, concelho, designação e tipo de ato escolhem-se aqui. */
  @Input() elaboracaoNova: ElaboracaoNova | null = null;
  /** Só na Delimitação e na Alteração da REN (o servidor decide): pergunta pela Dinâmica do IGT associada. */
  @Input() aceitaDinamicaAssociada = false;
  @Input() dinamicaAssociada: DinamicaAssociavel | null = null;
  /** Nulo enquanto o processo não existe; o bloco da associação precisa dele para pedir a lista. */
  @Input() processoId: string | null = null;
  /** Só no formulário `SGIFR`: as redes que o programa aceita; uma só (PRA) mostra-se sem escolha. */
  @Input() redesDefesaDisponiveis: RedeDefesa[] = [];
  /** Só no formulário `REN`: os motivos ativos; vazia, o campo não aparece nem é obrigatório (OI-246). */
  @Input() motivosProcedimentoDisponiveis: MotivoProcedimento[] = [];
  /** Só no formulário `REN`: a Dinâmica pede o «Diploma que corrige ou retifica». */
  @Input() pedeDiplomaCorrigido = false;
  /** Só no formulário `REN`: o tipo de informação imposto pela Dinâmica (a Delimitação publica o texto integral). */
  @Input() tipoInformacaoFixo: TipoInformacao | null = null;

  readonly ajudaMunicipioSemRegiao = AJUDA_MUNICIPIO_SEM_REGIAO;
  readonly opcoesRegiaoRen = signal<OpcaoSeletor[]>([]);
  readonly opcoesMunicipio = signal<OpcaoSeletor[]>([]);
  private regioesPedidas = false;
  private municipiosDaRegiao: string | null = null;
  /** Só no formulário `SGIFR`: o detalhe diz se a Região e a CIM / AM se pedem e dá as opções ativas. */
  @Input() pedeRegiao = false;
  @Input() opcoesRegiao: OpcaoLista[] = [];
  @Input() pedeCimAm = false;
  @Input() opcoesCimAm: OpcaoCimAm[] = [];

  opcoesSeletorRegiao: OpcaoSeletor[] = [];
  opcoesSeletorCimAm: OpcaoSeletor[] = [];
  readonly semRegiaoEscolhida = 'Escolha primeiro a Região.';

  opcoesNutsII: OpcaoSeletor[] = [];
  opcoesConcelhos: OpcaoSeletor[] = [];
  opcoesTiposAto: OpcaoSeletor[] = [];
  /** As do cabeçalho, com o tipo gravado mesmo que já não esteja na lista (o claim pode ter mudado). */
  opcoesTipoAto: OpcaoSeletor[] = [];
  readonly semTiposAto = 'Não há tipos de ato disponíveis para este procedimento e esta entidade.';
  readonly semSerieEscolhida = 'Escolha primeiro a série do Diário da República.';
  opcoesSerieDr: OpcaoSeletor[] = [];
  private serieConhecida: SerieDr | null = null;
  readonly ajudaConcelhoSemNutII = 'Escolha primeiro a NUT II.';
  private concelhosElaboracao: ConcelhoElaboracao[] = [];

  recolhido = false;

  simultaneoComPmotGravado: RespostaSimultaneoPmot | null = null;

  private readonly dinamicaAssociadaRef = viewChild(DinamicaAssociadaComponent);
  private readonly tiposPedidos = new Set<TipoListaFormulario>();
  private localizacaoGravada: LocalizacaoGravada | null = null;

  constructor() {
    this.formElaboracao.controls.nutIICodigo.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.filtrarConcelhosPelaNutII());
    this.form.controls.elementosAfetados.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.limparPerguntaEscondida());
    this.form.controls.localizacao.controls.aplicavelTodaArea.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.aplicarAplicavelTodaArea());
    this.form.controls.regiaoCodigo.valueChanges.pipe(takeUntilDestroyed()).subscribe((regiao) => this.carregarMunicipios(regiao));
    // Só uma escolha nova do autor: desativar e reativar o formulário também emite o valor que já lá está.
    this.form.controls.serieDr.valueChanges.pipe(takeUntilDestroyed()).subscribe((serie) => {
      if (serie && serie !== this.serieConhecida && this.escolheSerie()) {
        this.serieConhecida = serie;
        this.serieEscolhida.emit(serie);
      }
    });
    this.form.controls.regiao.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.filtrarCimAmPelaRegiao());
    this.form.controls.conformaHierarquiaSuperior.valueChanges.pipe(takeUntilDestroyed()).subscribe((resposta) => {
      if (resposta === false && this.form.controls.motivosAlteracao.value.length > 0) {
        this.form.controls.motivosAlteracao.setValue([]);
      }
    });
  }

  /** O título é o botão que recolhe o cartão: é ele que recebe o foco. */
  focarTitulo(): void {
    this.elemento.nativeElement.querySelector<HTMLElement>('.cabecalho .alternar')?.focus();
  }

  readonly form = this.fb.group({
    sumario: [''],
    normaLegal: [''],
    tipoInformacao: this.fb.control<TipoInformacao>('TEXTO_INTEGRAL'),
    publicarSuplemento: [false],
    contactoTramitacao: ['', [Validators.email]],
    entidadeInstrutora: [''],
    entidadeEmitente: [''],
    contactoPublicacao: [''],
    elementosAfetados: this.fb.nonNullable.group({ PECAS_ESCRITAS: false, PECAS_GRAFICAS: false, OUTROS: false }),
    comGeoconform: this.fb.control<boolean | null>(null),
    introduzPlantaNova: this.fb.control<boolean | null>(null),
    fundamentacaoRjigt: this.fb.nonNullable.control<string[]>([]),
    localizacao: this.fb.nonNullable.group({ aplicavelTodaArea: false, freguesia: '', toponimo: '' }),
    conformaHierarquiaSuperior: this.fb.control<boolean | null>(null),
    motivosAlteracao: this.fb.nonNullable.control<MotivoAlteracao[]>([]),
    dinamicaAssociadaId: this.fb.control<string | null>(null),
    tipoAtoId: this.fb.control<string | null>(null),
    serieDr: this.fb.control<SerieDr | null>(null),
    redesDefesa: this.fb.nonNullable.control<string[]>([]),
    motivoProcedimentoId: this.fb.control<string | null>(null),
    diplomaCorrigido: [''],
    regiaoCodigo: this.fb.control<string | null>(null),
    municipioCodigo: this.fb.control<string | null>(null),
    regiao: this.fb.control<string | null>(null),
    cimAm: this.fb.control<string | null>(null),
  });

  /** À parte de `form`: estas escolhas criam o Instrumento e não vão no `PUT` do formulário. */
  readonly formElaboracao = this.fb.group({
    nutIICodigo: this.fb.control<string | null>(null),
    concelho: this.fb.control<string | null>(null),
    designacao: [''],
    tipoAtoId: this.fb.control<string | null>(null),
  });

  /**
   * À parte de `form`: só a Ratificação e a Suspensão da CCDR a pedem neste cartão (nos outros formulários está no bloco
   * Informação Base).
   */
  readonly dataAssinatura = this.fb.nonNullable.control('');

  /**
   * À parte de `form`: só tem campo onde há «Publicação» (com Suplemento); nos outros formulários da 1.ª série é
   * reenviada como foi lida, porque o `PUT` substitui tudo e apagá-la-ia.
   */
  readonly dataSuplemento = this.fb.nonNullable.control('');

  /** Só na Suspensão da CCDR: a lista das CCDR e Direções Regionais, pelo nome (o valor gravado é o nome). */
  readonly opcoesEmitentes = signal<OpcaoSeletor[]>([]);
  private emitentes: EntidadeEmitente[] | null = null;
  private emitentesPedidas = false;
  /** O nome que o cartão escolheu por ser o da entidade da sessão; deixa de se anunciar quando o autor muda. */
  private emitentePreEscolhida: string | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if ('elaboracaoNova' in changes) {
      this.prepararElaboracaoNova(changes['elaboracaoNova'].previousValue ?? null);
    }
    if ('comGeoconform' in changes) {
      this.form.controls.comGeoconform.setValue(this.comGeoconform, { emitEvent: false });
    }
    if ('valoresIniciais' in changes) {
      const valores = this.valoresIniciais ?? {};
      this.form.patchValue({
        sumario: valores.sumario ?? '',
        normaLegal: valores.normaLegal ?? '',
        tipoInformacao: valores.tipoInformacao ?? 'TEXTO_INTEGRAL',
        publicarSuplemento: valores.publicarSuplemento ?? false,
        contactoTramitacao: valores.contactoTramitacao ?? '',
        entidadeInstrutora: valores.entidadeInstrutora ?? '',
        entidadeEmitente: valores.entidadeEmitente ?? '',
        contactoPublicacao: valores.contactoPublicacao ?? '',
        elementosAfetados: {
          PECAS_ESCRITAS: valores.elementosAfetados?.includes('PECAS_ESCRITAS') ?? false,
          PECAS_GRAFICAS: valores.elementosAfetados?.includes('PECAS_GRAFICAS') ?? false,
          OUTROS: valores.elementosAfetados?.includes('OUTROS') ?? false,
        },
        fundamentacaoRjigt: valores.fundamentacaoRjigt ?? [],
        redesDefesa: (valores.redesDefesa ?? []).map((rede) => (typeof rede === 'string' ? rede : rede.codigo)),
        diplomaCorrigido: valores.diplomaCorrigido ?? '',
      });
      const motivo = valores.motivoProcedimentoId ?? valores.motivoProcedimento?.id ?? null;
      this.form.controls.motivoProcedimentoId.setValue(motivo === null ? null : String(motivo), { emitEvent: false });
      this.form.controls.regiaoCodigo.setValue(valores.regiaoCodigo ?? null, { emitEvent: false });
      this.form.controls.municipioCodigo.setValue(valores.municipioCodigo ?? null, { emitEvent: false });
      this.form.controls.regiao.setValue(valores.regiao ?? null, { emitEvent: false });
      this.form.controls.cimAm.setValue(valores.cimAm ?? null, { emitEvent: false });
      this.dataAssinatura.setValue(valores.dataAssinatura ?? '', { emitEvent: false });
      this.dataSuplemento.setValue(valores.dataSuplemento ?? '', { emitEvent: false });
      this.form.controls.introduzPlantaNova.setValue(this.introduzPlantaNovaGravada(), { emitEvent: false });
      this.form.controls.dinamicaAssociadaId.setValue(valores.dinamicaAssociadaId ?? null, { emitEvent: false });
      this.form.controls.tipoAtoId.setValue(valores.tipoAtoId != null ? String(valores.tipoAtoId) : null, { emitEvent: false });
      this.simultaneoComPmotGravado =
        valores.simultaneoComPmot !== undefined ? valores.simultaneoComPmot : lerSimultaneoComPmot(valores.dadosFormulario ?? null);
      this.reporLocalizacaoEMotivos(valores);
    }
    if ('seriesDrEscolhiveis' in changes) {
      this.opcoesSerieDr = (this.seriesDrEscolhiveis ?? []).map((serie) => ({ valor: serie, rotulo: ROTULOS_SERIE_ESCOLHIVEL[serie] ?? serie }));
    }
    if ('valoresIniciais' in changes || 'seriesDrEscolhiveis' in changes) {
      this.serieConhecida = this.serieGravada(this.valoresIniciais?.serieDr ?? null);
      this.form.controls.serieDr.setValue(this.serieConhecida, { emitEvent: false });
    }
    if ('opcoesRegiao' in changes || 'opcoesCimAm' in changes || 'pedeRegiao' in changes || 'valoresIniciais' in changes) {
      this.opcoesSeletorRegiao = this.opcoesRegiao.map((opcao) => ({ valor: opcao.codigo, rotulo: opcao.descricao }));
      this.filtrarCimAmPelaRegiao();
    }
    if ('tiposAtoDisponiveis' in changes || 'valoresIniciais' in changes || 'tipoAtoNome' in changes) {
      this.prepararOpcoesTipoAto();
    }
    if (('pedeLocalizacao' in changes || 'pedeMotivoAlteracao' in changes) && !this.leitura) {
      this.carregarOpcoes();
    }
    if (this.formulario === 'REN' && ('formulario' in changes || 'valoresIniciais' in changes)) {
      this.carregarRegioes();
      this.carregarMunicipios(this.form.controls.regiaoCodigo.value, false);
    }
    if ('aCarregar' in changes) {
      if (this.aCarregar) {
        this.form.disable();
        this.dataAssinatura.disable({ emitEvent: false });
        this.dataSuplemento.disable({ emitEvent: false });
        this.formElaboracao.disable({ emitEvent: false });
      } else {
        this.reativar();
        this.reativarElaboracaoNova();
      }
    }
    if ('camposBloqueados' in changes) {
      this.aplicarBloqueios();
    }
    this.aplicarGeoconformFixo();
    this.aplicarAplicavelTodaArea();
    if ('errosServidor' in changes) {
      this.aplicarErrosServidor();
    }
    this.carregarEmitentes();
    this.preEscolherEmitente();
  }

  /** Lida uma vez, só na edição da Suspensão da CCDR; os serviços pedem-se só aqui, para os outros formulários não os exigirem. */
  private carregarEmitentes(): void {
    if (this.emitentesPedidas || this.leitura || this.formulario !== 'SUSPENSAO_CCDR') {
      return;
    }
    this.emitentesPedidas = true;
    this.injector
      .get(EntidadeService)
      .emitentes('CCDR_DR')
      .subscribe({
        next: (lista) => {
          this.emitentes = lista;
          this.opcoesEmitentes.set(lista.map((entidade) => ({ valor: entidade.nome, rotulo: entidade.nome })));
          this.preEscolherEmitente();
          this.vista.markForCheck();
        },
        // Sem a lista, o campo fica sem opções e a submissão recusa-o com a mensagem do servidor.
        error: () => undefined,
      });
  }

  /** Sem valor gravado nem bloqueio, a entidade da sessão quando é uma CCDR ou Direção Regional da lista. */
  private preEscolherEmitente(): void {
    const controlo = this.form.controls.entidadeEmitente;
    if (!this.emitentes || this.formulario !== 'SUSPENSAO_CCDR' || controlo.value || this.bloqueado('entidadeEmitente')) {
      return;
    }
    const sessao = this.injector.get(AuthService).entidade();
    const daSessao = sessao?.tipo_entidade === 'CCDR_DR' ? this.emitentes.find((entidade) => entidade.id === sessao.id) : undefined;
    if (daSessao) {
      controlo.setValue(daSessao.nome);
      this.emitentePreEscolhida = daSessao.nome;
    }
  }

  /** Anunciado pelo leitor de ecrã junto do campo, enquanto o valor é o pré-escolhido. */
  ajudaEmitente(): string | null {
    const valor = this.form.controls.entidadeEmitente.value;
    return this.emitentePreEscolhida && valor === this.emitentePreEscolhida
      ? 'Pré-escolhida a entidade da sessão; pode escolher outra da lista.'
      : null;
  }

  /** Formulários com uma secção própria por cima do cartão, que mostra o contexto do processo. */
  temSeccaoPropria(): boolean {
    return this.formulario === 'RATIFICACAO' || this.formulario === 'SUSPENSAO_CCDR' || this.formularioGoverno();
  }

  /** A Suspensão e as Medidas Preventivas do Governo, na Publicação e na Prorrogação. */
  formularioGoverno(): boolean {
    return this.formulario === 'SUSPENSAO_MP_GOVERNO' || this.formulario === 'PRORROGACAO_GOVERNO';
  }

  /** Na Suspensão da CCDR e nos formulários do Governo todos os campos do cartão são obrigatórios (EF v1.5 §3.1.2). */
  todosObrigatorios(): boolean {
    return this.formulario === 'SUSPENSAO_CCDR' || this.formularioGoverno();
  }

  /** Sem Informação Base, a data de assinatura do ato pede-se neste cartão. */
  pedeDataAssinaturaNoCartao(): boolean {
    return (
      this.dataAssinaturaNoFormulario ||
      this.formulario === 'RATIFICACAO' ||
      this.formulario === 'SUSPENSAO_CCDR' ||
      this.formularioGoverno()
    );
  }

  /** Em leitura, a data aparece junto da «Publicação» quando o formulário a tem; nos outros, aqui. */
  mostraDataAssinaturaNaLeitura(): boolean {
    return this.pedeDataAssinaturaNoCartao() && !this.pedePublicacaoNoCartao();
  }

  /** «Publicação» (Diário ou Suplemento) em botões de opção; a Prorrogação do Governo não a tem (EF v1.5 §3.1.2). */
  pedePublicacaoNoCartao(): boolean {
    return this.formulario === 'RATIFICACAO' || this.formulario === 'SUSPENSAO_MP_GOVERNO';
  }

  /** A caixa «Publicar suplemento» dos restantes formulários da 1.ª série. */
  mostraCheckboxSuplemento(): boolean {
    return this.mostraCamposPrimeiraSerie() && !this.pedePublicacaoNoCartao() && this.formulario !== 'PRORROGACAO_GOVERNO';
  }

  /** Com GeoConform a alteração afeta sempre as Peças Gráficas: fica marcada e fixa, e a pergunta da planta nova não se faz. */
  pecasGraficasFixas(): boolean {
    return this.formulario === 'PUBLICACAO_ALTERACAO' && this.comGeoconform === true;
  }

  /** Só com Peças Gráficas e sem GeoConform, como o servidor (BL da Publicação da Alteração). */
  perguntaPlantaNovaVisivel(): boolean {
    return (
      this.formulario === 'PUBLICACAO_ALTERACAO' &&
      this.comGeoconform !== true &&
      this.form.controls.elementosAfetados.getRawValue().PECAS_GRAFICAS
    );
  }

  /** Trava o «Seguinte» (`validarObrigatorios`), mas o rascunho grava-se na mesma. */
  erroPlantaNovaSemPecasEscritas(): boolean {
    return (
      this.perguntaPlantaNovaVisivel() &&
      this.form.controls.introduzPlantaNova.value === true &&
      !this.form.controls.elementosAfetados.getRawValue().PECAS_ESCRITAS
    );
  }

  descricaoElementos(): string | null {
    const ids = [
      this.descricaoBloqueio('elementosAfetados'),
      this.mensagemErro('elementosAfetados') ? 'erro-elementosAfetados' : null,
      this.erroPlantaNovaSemPecasEscritas() ? 'erro-elementosAfetados-regulamento' : null,
    ].filter(Boolean);
    return ids.length > 0 ? ids.join(' ') : null;
  }

  descricaoPlantaNova(): string {
    const ids = [
      'ajuda-introduzPlantaNova',
      this.descricaoBloqueio('introduzPlantaNova'),
      this.mensagemErro('introduzPlantaNova') ? 'erro-introduzPlantaNova' : null,
    ].filter(Boolean);
    return ids.join(' ');
  }

  rotuloPlantaNova(): string | null {
    const resposta = this.introduzPlantaNovaGravada();
    return resposta === null ? null : resposta ? 'Sim' : 'Não';
  }

  /** O primeiro controlo com mensagem do servidor recebe o foco, para o leitor de ecrã anunciar o erro. */
  focarPrimeiroCampoComErro(): void {
    const erro = this.elemento.nativeElement.querySelector<HTMLElement>('.erro-campo, .seletor-erro, app-dinamica-associada .erro');
    const campo = erro?.closest('fieldset, label, .campo');
    campo?.querySelector<HTMLElement>('input:not(:disabled), textarea:not(:disabled), select:not(:disabled)')?.focus();
  }

  private introduzPlantaNovaGravada(): boolean | null {
    const valores = this.valoresIniciais;
    if (!valores) {
      return null;
    }
    return valores.introduzPlantaNova !== undefined ? valores.introduzPlantaNova : lerIntroduzPlantaNova(valores.dadosFormulario ?? null);
  }

  /** Ao esconder-se, a pergunta deixa de ter resposta: o pedido leva nulo. */
  private limparPerguntaEscondida(): void {
    const resposta = this.form.controls.introduzPlantaNova;
    if (resposta.value !== null && !this.perguntaPlantaNovaVisivel()) {
      resposta.setValue(null, { emitEvent: false });
    }
  }

  /** Depois de qualquer `enable()`, que voltaria a ativar a opção fixa. */
  private aplicarGeoconformFixo(): void {
    const pecasGraficas = this.form.controls.elementosAfetados.controls.PECAS_GRAFICAS;
    if (this.pecasGraficasFixas()) {
      pecasGraficas.setValue(true, { emitEvent: false });
      pecasGraficas.disable({ emitEvent: false });
    } else if (pecasGraficas.disabled && !this.aCarregar && !this.bloqueado('elementosAfetados')) {
      pecasGraficas.enable({ emitEvent: false });
    }
    this.limparPerguntaEscondida();
  }

  /**
   * Depois da primeira renderização: os controlos do formulário revalidam-se ao ligar à vista e apagariam
   * estas mensagens. Chegam ao recriar o cartão, depois de a submissão ser recusada.
   */
  private aplicarErrosServidor(): void {
    if (this.errosServidor.length === 0) {
      return;
    }
    this.recolhido = false;
    afterNextRender(
      () => {
        let aplicado = false;
        for (const campo of this.errosServidor) {
          const controlo = this.controloDeData(campo.path) ?? this.form.get(caminhoDoControlo(campo.path));
          if (controlo) {
            controlo.setErrors({ servidor: campo.erro });
            aplicado = true;
          }
        }
        if (aplicado) {
          this.vista.detectChanges();
          this.focarPrimeiroCampoComErro();
        }
      },
      { injector: this.injector },
    );
  }

  /** As datas com controlo à parte; a do suplemento só onde o cartão a mostra. */
  private controloDeData(path: string) {
    if (path === 'dataAssinatura') {
      return this.dataAssinatura;
    }
    return path === 'dataSuplemento' && this.pedePublicacaoNoCartao() ? this.dataSuplemento : null;
  }

  descricaoDataSuplemento(): string | null {
    const ids = [this.descricaoBloqueio('dataSuplemento'), this.dataSuplemento.hasError('servidor') ? 'erro-dataSuplemento' : null]
      .filter(Boolean);
    return ids.length > 0 ? ids.join(' ') : null;
  }

  erroElaboracaoNova(campo: CampoElaboracaoNova): string | null {
    return this.formElaboracao.controls[campo].getError('servidor') ?? null;
  }

  /** `false` se algum erro não é destes campos: fica para quem chamou. */
  aplicarErrosElaboracaoNova(erro: ApiError): boolean {
    const campos = erro.campos ?? [];
    const controlos = this.formElaboracao.controls as Record<string, { setErrors(erros: object): void } | undefined>;
    const desteGrupo = campos.filter((campo) => controlos[campo.path]);
    desteGrupo.forEach((campo) => controlos[campo.path]!.setErrors({ servidor: campo.erro }));
    if (desteGrupo.length > 0) {
      this.recolhido = false;
    }
    return campos.length > 0 && desteGrupo.length === campos.length;
  }

  /** `null` quando falta alguma escolha; os campos em falta ficam com a mensagem. */
  dadosElaboracaoNova(): DadosElaboracaoNova | null {
    const valores = this.formElaboracao.getRawValue();
    const dados = {
      nutIICodigo: valores.nutIICodigo ?? '',
      concelho: valores.concelho ?? '',
      designacao: valores.designacao?.trim() ?? '',
      tipoAtoId: valores.tipoAtoId ? Number(valores.tipoAtoId) : 0,
    };
    const emFalta = (Object.keys(FALTA_ELABORACAO_NOVA) as CampoElaboracaoNova[]).filter((campo) => !dados[campo]);
    emFalta.forEach((campo) => this.formElaboracao.controls[campo].setErrors({ servidor: FALTA_ELABORACAO_NOVA[campo] }));
    if (emFalta.length > 0) {
      this.recolhido = false;
      return null;
    }
    return dados;
  }

  /** O concelho da câmara vem escolhido, e a NUT II com ele quando a réplica do SNIT a conhece; ambos podem mudar-se. */
  private prepararElaboracaoNova(anterior: ElaboracaoNova | null): void {
    const nova = this.elaboracaoNova;
    this.opcoesNutsII = (nova?.nutsII ?? []).map((item) => ({ valor: item.codigo, rotulo: item.nome ?? item.codigo }));
    this.concelhosElaboracao = nova?.concelhos ?? [];
    this.opcoesTiposAto = (nova?.procedimento.tiposAtoDisponiveis ?? []).map((tipo) => ({ valor: String(tipo.id), rotulo: tipo.nome }));
    if (nova && !anterior) {
      const concelhoEntidade = nova.concelhos.find((item) => item.codigo === nova.concelhoEntidade) ?? null;
      const nutIIDoConcelho = nova.nutsII.some((nut) => nut.codigo === concelhoEntidade?.nutIICodigo)
        ? (concelhoEntidade?.nutIICodigo ?? null)
        : null;
      this.formElaboracao.reset(
        { nutIICodigo: nutIIDoConcelho, concelho: concelhoEntidade?.codigo ?? null, designacao: '', tipoAtoId: null },
        { emitEvent: false },
      );
    }
    this.filtrarConcelhosPelaNutII();
  }

  /**
   * EF: nos municipais o concelho é uma lista «em função de NUTS» — só os da NUT II escolhida, mais os que a réplica
   * do SNIT não sabe de que NUT II são; sem NUT II, fica desativado. No intermunicipal a lista tem todos os concelhos.
   */
  private filtrarConcelhosPelaNutII(): void {
    const concelho = this.formElaboracao.controls.concelho;
    const nutII = this.formElaboracao.controls.nutIICodigo.value;
    const filtra = this.elaboracaoNova?.ambitoTerritorial !== 'INTERMUNICIPAL';
    const visiveis = filtra && nutII
      ? this.concelhosElaboracao.filter((item) => !item.nutIICodigo || item.nutIICodigo === nutII)
      : this.concelhosElaboracao;
    this.opcoesConcelhos = visiveis.map((item) => ({ valor: item.codigo, rotulo: item.nome ?? item.codigo }));
    if (!filtra || nutII) {
      concelho.enable({ emitEvent: false });
    } else {
      concelho.disable({ emitEvent: false });
    }
    if (filtra && nutII && concelho.value && !visiveis.some((item) => item.codigo === concelho.value)) {
      concelho.setValue(null);
    }
  }

  bloqueado(chave: string): boolean {
    return this.camposBloqueados.includes(chave);
  }

  private prepararOpcoesTipoAto(): void {
    const opcoes = this.tiposAtoDisponiveis.map((tipo) => ({ valor: String(tipo.id), rotulo: tipo.nome }));
    const gravado = this.valoresIniciais?.tipoAtoId;
    if (gravado != null && this.tipoAtoNome && !opcoes.some((opcao) => opcao.valor === String(gravado))) {
      opcoes.push({ valor: String(gravado), rotulo: this.tipoAtoNome });
    }
    this.opcoesTipoAto = opcoes;
  }

  /** O nome da opção escolhida ou, sem ela, o gravado. */
  nomeTipoAto(): string {
    const valor = this.form.controls.tipoAtoId.value;
    return this.opcoesTipoAto.find((opcao) => opcao.valor === valor)?.rotulo ?? (this.tipoAtoNome || '—');
  }

  descricaoBloqueio(chave: string): string | null {
    return this.bloqueado(chave) ? `bloqueado-${chave}` : null;
  }

  /** O aviso de bloqueio e a mensagem do servidor descrevem o campo para quem usa leitor de ecrã. */
  descricaoCampo(
    chave:
      | 'entidadeInstrutora'
      | 'entidadeEmitente'
      | 'contactoTramitacao'
      | 'contactoPublicacao'
      | 'normaLegal'
      | 'tipoInformacao'
      | 'sumario'
      | 'fundamentacaoRjigt'
      | 'conformaHierarquiaSuperior'
      | 'publicarSuplemento'
      | 'motivosAlteracao',
  ): string | null {
    const ids = [this.descricaoBloqueio(chave), this.mensagemErro(chave) ? `erro-${chave}` : null].filter(Boolean);
    return ids.length > 0 ? ids.join(' ') : null;
  }

  /** Com `SE_PECAS_GRAFICAS` segue a caixa das Peças gráficas antes de gravar; a submissão continua a usar o que ficou gravado. */
  perguntaGeoconformVisivel(): boolean {
    switch (this.perguntaGeoconform) {
      case 'OBRIGATORIA':
        return true;
      case 'SE_PECAS_GRAFICAS':
        return this.pedeElementosAfetados && this.form.controls.elementosAfetados.getRawValue().PECAS_GRAFICAS;
      default:
        return this.exigeOpcaoGeoconform;
    }
  }

  /** `false` com a pergunta visível e sem resposta: o erro fica junto do grupo, que recebe o foco. */
  validarOpcaoGeoconform(): boolean {
    if (!this.geoconformEmFalta()) {
      return true;
    }
    this.form.controls.comGeoconform.setErrors({ emFalta: MENSAGEM_GEOCONFORM_EM_FALTA });
    this.recolhido = false;
    this.vista.detectChanges();
    this.elemento.nativeElement.querySelector<HTMLElement>('.geoconform input:not(:disabled)')?.focus();
    return false;
  }

  /**
   * As regras da submissão para os campos que o Procedimento pede, aplicadas no «Seguinte»: `false` com algum em
   * falta, todos com a mensagem e o foco no primeiro. O rascunho grava-se sem elas; os campos bloqueados já passaram.
   */
  validarObrigatorios(): boolean {
    if (this.leitura) {
      return true;
    }
    const controlos = this.form.controls;
    const valores = this.form.getRawValue();
    const emFalta: [AbstractControl, string][] = [];
    if (
      this.pedeElementosAfetados &&
      !this.bloqueado('elementosAfetados') &&
      !ELEMENTOS_AFETADOS.some((elemento) => valores.elementosAfetados[elemento.valor])
    ) {
      emFalta.push([controlos.elementosAfetados, MENSAGEM_ELEMENTOS_EM_FALTA]);
    }
    if (
      this.pedeLocalizacao === 'OBRIGATORIA' &&
      this.mostraLocalizacao() &&
      !this.bloqueado('localizacao') &&
      !valores.localizacao.aplicavelTodaArea &&
      !valores.localizacao.freguesia
    ) {
      emFalta.push([controlos.localizacao, MENSAGEM_LOCALIZACAO_EM_FALTA]);
    }
    if (this.pedeMotivoAlteracao === 'COM_PERGUNTA' && !this.bloqueado('conformaHierarquiaSuperior') && valores.conformaHierarquiaSuperior === null) {
      emFalta.push([controlos.conformaHierarquiaSuperior, MENSAGEM_CONFORMACAO_EM_FALTA]);
    }
    if (this.mostraMotivos() && !this.bloqueado('motivosAlteracao') && valores.motivosAlteracao.length === 0) {
      emFalta.push([controlos.motivosAlteracao, MENSAGEM_MOTIVO_EM_FALTA]);
    }
    if (this.perguntaPlantaNovaVisivel() && !this.bloqueado('introduzPlantaNova') && valores.introduzPlantaNova === null) {
      emFalta.push([controlos.introduzPlantaNova, MENSAGEM_PLANTA_NOVA_EM_FALTA]);
    }
    if (this.geoconformEmFalta()) {
      emFalta.push([controlos.comGeoconform, MENSAGEM_GEOCONFORM_EM_FALTA]);
    }
    if (this.mostraMotivoProcedimento() && !valores.motivoProcedimentoId) {
      emFalta.push([controlos.motivoProcedimentoId, MENSAGEM_MOTIVO_PROCEDIMENTO_EM_FALTA]);
    }
    if (this.dataAssinaturaNoFormulario && !this.bloqueado('dataAssinatura') && !this.dataAssinatura.getRawValue()) {
      emFalta.push([this.dataAssinatura, MENSAGEM_DATA_ASSINATURA_EM_FALTA]);
    }
    if (emFalta.length === 0 && !this.erroPlantaNovaSemPecasEscritas()) {
      return true;
    }
    for (const [controlo, mensagem] of emFalta) {
      controlo.markAsTouched();
      controlo.setErrors({ emFalta: mensagem });
    }
    this.recolhido = false;
    this.vista.detectChanges();
    this.focarPrimeiroCampoComErro();
    return false;
  }

  /** Mensagem do servidor ou do «Seguinte»; uma alteração ao campo apaga-a. */
  mensagemErro(caminho: string): string | null {
    const controlo = this.form.get(caminho);
    return controlo?.getError('servidor') ?? controlo?.getError('emFalta') ?? null;
  }

  private geoconformEmFalta(): boolean {
    return this.perguntaGeoconformVisivel() && this.form.controls.comGeoconform.value === null;
  }

  mensagemGeoconform(): string | null {
    const resposta = this.form.controls.comGeoconform;
    return resposta.getError('servidor') ?? resposta.getError('emFalta') ?? (resposta.value === null ? this.erroComGeoconform : null);
  }

  parteDr(): string {
    return rotuloParteDr(this.serieDr);
  }

  /** Restantes servidões: a série escolhe-se no processo, antes do tipo de ato. */
  escolheSerie(): boolean {
    return (this.seriesDrEscolhiveis?.length ?? 0) > 0;
  }

  /** Sem série escolhida a lista dos tipos vem vazia: diz-se porquê, em vez de «não há tipos». */
  ajudaTipoAto(): string | null {
    if (this.opcoesTipoAto.length > 0) {
      return null;
    }
    return this.escolheSerie() && !this.form.controls.serieDr.value ? this.semSerieEscolhida : this.semTiposAto;
  }

  private serieGravada(valor: string | null): SerieDr | null {
    return valor && (this.seriesDrEscolhiveis ?? []).includes(valor as SerieDr) ? (valor as SerieDr) : null;
  }

  /**
   * Processo de uma Dinâmica sem nenhum IGT (Medidas Preventivas do município sem IGT): não há designação nem território
   * de Instrumento a mostrar, só «Sem IGT associado». Os formulários com secção própria mostram a Designação da Dinâmica.
   */
  semIgtAssociado(): boolean {
    return !this.temSeccaoPropria() && this.instrumentosAbrangidos?.length === 0;
  }

  rotuloTerritorio(itens: ItemTerritorio[] | undefined): string {
    return rotuloTerritorio(itens);
  }

  rotuloConcelhos(itens: ItemTerritorio[] | undefined): string {
    return rotuloConcelhos(itens);
  }

  rotuloTipoInformacao(): string {
    switch (this.valoresIniciais?.tipoInformacao) {
      case 'TEXTO_INTEGRAL':
        return 'Texto integral';
      case 'EXTRATO':
        return 'Extrato';
      default:
        return '—';
    }
  }

  rotuloElementosAfetados(): string {
    const marcados = this.valoresIniciais?.elementosAfetados ?? [];
    const rotulos = ELEMENTOS_AFETADOS.filter((elemento) => marcados.includes(elemento.valor)).map((e) => e.rotulo);
    return rotulos.length > 0 ? rotulos.join(', ') : '—';
  }

  mostraFundamentacaoRjigt(): boolean {
    return this.pedeFundamentacaoRjigt && this.opcoesFundamentacaoRjigt.length > 0;
  }

  /** O texto do ato é sempre integral no SGIFR e na Dinâmica da REN que o impõe: o servidor grava-o e recusa o extrato. */
  tipoInformacaoFixa(): boolean {
    return this.formulario === 'SGIFR' || (this.formulario === 'REN' && this.tipoInformacaoFixo === 'TEXTO_INTEGRAL');
  }

  /** Com a lista de motivos vazia o campo não aparece nem é obrigatório (OI-246, TR-256). */
  mostraMotivoProcedimento(): boolean {
    return this.formulario === 'REN' && this.motivosProcedimentoDisponiveis.length > 0;
  }

  opcoesMotivoProcedimento(): OpcaoSeletor[] {
    return this.motivosProcedimentoDisponiveis.map((motivo) => ({ valor: String(motivo.id), rotulo: motivo.descricao }));
  }

  nomeRegiao(codigo: string): string {
    return this.opcoesRegiaoRen().find((opcao) => opcao.valor === codigo)?.rotulo ?? codigo;
  }

  nomeMunicipio(codigo: string): string {
    return this.opcoesMunicipio().find((opcao) => opcao.valor === codigo)?.rotulo ?? codigo;
  }

  /** Só os campos que se veem: sem lista de motivos o pedido não leva o motivo; em branco apaga o gravado. */
  private dadosRen(valores: ReturnType<ProcessoFormularioInicialComponent['form']['getRawValue']>): DadosFormularioRen {
    if (this.formulario !== 'REN') {
      return {};
    }
    return {
      ...(this.mostraMotivoProcedimento() && valores.motivoProcedimentoId
        ? { motivoProcedimentoId: Number(valores.motivoProcedimentoId) }
        : {}),
      ...(this.pedeDiplomaCorrigido ? { diplomaCorrigido: valores.diplomaCorrigido?.trim() ?? '' } : {}),
      regiaoCodigo: valores.regiaoCodigo ?? '',
      municipioCodigo: valores.municipioCodigo ?? '',
    };
  }

  private carregarRegioes(): void {
    if (this.regioesPedidas) {
      return;
    }
    this.regioesPedidas = true;
    this.referenciaSnitService
      .nutsII()
      .pipe(catchError(() => of([])))
      .subscribe((regioes) => {
        this.opcoesRegiaoRen.set(regioes.map((regiao) => ({ valor: regiao.codigo, rotulo: regiao.nome })));
        this.vista.markForCheck();
      });
  }

  /**
   * O município só oferece os concelhos da região escolhida; sem região não tem opções. Ao mudar a região, um
   * município que deixou de lhe pertencer deixa de estar escolhido.
   */
  private carregarMunicipios(regiao: string | null, limparForaDaRegiao = true): void {
    const municipio = this.form.controls.municipioCodigo;
    if (!regiao) {
      this.municipiosDaRegiao = null;
      this.opcoesMunicipio.set([]);
      if (limparForaDaRegiao && municipio.value) {
        municipio.setValue(null);
      }
      return;
    }
    if (regiao === this.municipiosDaRegiao) {
      return;
    }
    this.municipiosDaRegiao = regiao;
    this.referenciaSnitService
      .concelhos(regiao)
      .pipe(catchError(() => of([])))
      .subscribe((concelhos) => {
        if (this.municipiosDaRegiao !== regiao) {
          return;
        }
        this.opcoesMunicipio.set(concelhos.map((concelho) => ({ valor: concelho.codigo, rotulo: concelho.nome })));
        if (limparForaDaRegiao && municipio.value && !concelhos.some((concelho) => concelho.codigo === municipio.value)) {
          municipio.setValue(null);
        }
        this.vista.markForCheck();
      });
  }

  /** O programa só aceita uma rede (PRA): mostra-se sem escolha, e o servidor grava-a. */
  redeUnica(): RedeDefesa | null {
    return this.formulario === 'SGIFR' && this.redesDefesaDisponiveis.length === 1 ? this.redesDefesaDisponiveis[0] : null;
  }

  escolheRedes(): boolean {
    return this.formulario === 'SGIFR' && this.redesDefesaDisponiveis.length > 1;
  }

  redeMarcada(codigo: string): boolean {
    return this.form.controls.redesDefesa.value.includes(codigo);
  }

  alternarRede(codigo: string): void {
    const controlo = this.form.controls.redesDefesa;
    const marcadas = controlo.value;
    controlo.setValue(marcadas.includes(codigo) ? marcadas.filter((c) => c !== codigo) : [...marcadas, codigo]);
    controlo.markAsDirty();
  }

  descricaoRedesDefesa(): string {
    return this.mensagemErro('redesDefesa') ? 'ajuda-redesDefesa erro-redesDefesa' : 'ajuda-redesDefesa';
  }

  /** Nome de cada rede gravada; um código sem nome mostra-se tal como foi gravado. */
  rotuloRedesDefesa(): string {
    const nomes = (this.valoresIniciais?.redesDefesa ?? []).map((rede) => {
      if (typeof rede !== 'string') {
        return rede.nome;
      }
      return this.redesDefesaDisponiveis.find((disponivel) => disponivel.codigo === rede)?.nome ?? rede;
    });
    return nomes.length > 0 ? nomes.join(', ') : '—';
  }

  /**
   * EF: na Delimitação do PSA a CIM / AM é uma lista «em função da região»; na Alteração, sem Região, é a completa.
   * Mudar a Região retira a CIM / AM escolhida que deixou de ser dela.
   */
  private filtrarCimAmPelaRegiao(): void {
    const regiao = this.form.controls.regiao.value;
    const visiveis = this.pedeRegiao ? this.opcoesCimAm.filter((opcao) => regiao && opcao.regiao === regiao) : this.opcoesCimAm;
    this.opcoesSeletorCimAm = visiveis.map((opcao) => ({ valor: opcao.codigo, rotulo: opcao.descricao }));
    const cimAm = this.form.controls.cimAm;
    if (this.pedeRegiao && cimAm.value && this.opcoesCimAm.length > 0 && !visiveis.some((opcao) => opcao.codigo === cimAm.value)) {
      cimAm.setValue(null);
    }
  }

  ajudaCimAm(): string | null {
    return this.pedeRegiao && !this.form.controls.regiao.value ? this.semRegiaoEscolhida : null;
  }

  /** A descrição da opção, ou a gravada (valor que o Master desativou), ou o código. */
  rotuloRegiao(): string {
    return this.rotuloOpcaoGravada(this.opcoesRegiao, this.valoresIniciais?.regiao, this.valoresIniciais?.regiaoDescricao);
  }

  rotuloCimAm(): string {
    return this.rotuloOpcaoGravada(this.opcoesCimAm, this.valoresIniciais?.cimAm, this.valoresIniciais?.cimAmDescricao);
  }

  private rotuloOpcaoGravada(opcoes: OpcaoLista[], codigo: string | null | undefined, descricao: string | null | undefined): string {
    if (!codigo) {
      return '—';
    }
    return opcoes.find((opcao) => opcao.codigo === codigo)?.descricao ?? descricao ?? codigo;
  }

  /** Pela ordem do catálogo e só com as redes que se veem, como a Fundamentação RJIGT. */
  private redesParaGravar(marcadas: string[]): string[] {
    return this.redesDefesaDisponiveis.map((rede) => rede.codigo).filter((codigo) => marcadas.includes(codigo));
  }

  fundamentacaoMarcada(codigo: string): boolean {
    return this.form.controls.fundamentacaoRjigt.value.includes(codigo);
  }

  alternarFundamentacao(codigo: string): void {
    const controlo = this.form.controls.fundamentacaoRjigt;
    const marcados = controlo.value;
    controlo.setValue(marcados.includes(codigo) ? marcados.filter((c) => c !== codigo) : [...marcados, codigo]);
    controlo.markAsDirty();
  }

  /** Um código que já não está nas opções (desativado) mostra-se tal como foi gravado. */
  rotuloFundamentacaoRjigt(): string {
    const gravados = this.valoresIniciais?.fundamentacaoRjigt ?? [];
    const rotulos = gravados.map((codigo) => this.opcoesFundamentacaoRjigt.find((o) => o.codigo === codigo)?.descricao ?? codigo);
    return rotulos.length > 0 ? rotulos.join(', ') : '—';
  }

  pedeEntidadeInstrutora(): boolean {
    return this.categoriaInstrumento === 'PROGRAMA';
  }

  /** A Ratificação e os formulários do Governo, na 1.ª série, pedem também a norma legal e o tipo de informação (EF v1.5 §3.1.2). */
  mostraCamposSegundaSerie(): boolean {
    return (
      this.serieDr === null ||
      this.serieDr === 'SEGUNDA_H' ||
      this.serieDr === 'SEGUNDA_C' ||
      this.formulario === 'RATIFICACAO' ||
      this.formularioGoverno()
    );
  }

  /** Na Ratificação, a modalidade escolhida no formulário; nos outros, o nome do Procedimento. */
  rotuloTipoProcedimento(): string {
    if (this.formulario === 'RATIFICACAO' && this.modalidadeRatificacao) {
      return ROTULO_MODALIDADE_RATIFICACAO[this.modalidadeRatificacao];
    }
    return this.tipoProcedimento || '—';
  }

  /** Sem Informação Base, a data deste cartão; nos outros, a do bloco Informação Base, que o ecrã passa. */
  dataAssinaturaPedido(gravada: string | null): string | null {
    return this.pedeDataAssinaturaNoCartao() ? this.dataAssinatura.getRawValue() || null : gravada;
  }

  mensagemErroDataAssinatura(): string | null {
    return this.dataAssinatura.getError('servidor') ?? this.dataAssinatura.getError('emFalta') ?? null;
  }

  descricaoDataAssinatura(): string | null {
    const ids = [this.descricaoBloqueio('dataAssinatura'), this.mensagemErroDataAssinatura() ? 'erro-dataAssinatura' : null]
      .filter(Boolean);
    return ids.length > 0 ? ids.join(' ') : null;
  }

  rotuloDataAssinatura(): string {
    const data = this.valoresIniciais?.dataAssinatura;
    return data ? data.split('-').reverse().join('/') : '—';
  }

  rotuloPublicacao(): string {
    const suplemento = this.valoresIniciais?.publicarSuplemento;
    return suplemento === true ? 'Suplemento' : suplemento === false ? 'Diário' : '—';
  }

  mostraCamposPrimeiraSerie(): boolean {
    return this.serieDr === null || this.serieDr === 'PRIMEIRA';
  }

  /** Campo vazio vale `null`; os que a série esconde também, para não gravar o que o utilizador não vê. */
  dados(): FormularioInicialDados {
    const valores = this.form.getRawValue();
    const segunda = this.mostraCamposSegundaSerie();
    // A Prorrogação do Governo não tem «Publicação»: o servidor grava Diário.
    const primeira = this.mostraCamposPrimeiraSerie() && this.formulario !== 'PRORROGACAO_GOVERNO';
    const suplemento = primeira ? (valores.publicarSuplemento ?? null) : null;
    return {
      sumario: valores.sumario || null,
      normaLegal: segunda ? valores.normaLegal || null : null,
      tipoInformacao: segunda ? (this.tipoInformacaoFixa() ? 'TEXTO_INTEGRAL' : valores.tipoInformacao || null) : null,
      publicarSuplemento: suplemento,
      // Sem campo fora da «Publicação», mas reenviada: o `PUT` substitui tudo e apagá-la-ia.
      dataSuplemento: primeira && (suplemento || !this.pedePublicacaoNoCartao()) ? this.dataSuplemento.getRawValue() || null : null,
      contactoTramitacao: valores.contactoTramitacao?.trim() || null,
      entidadeInstrutora: this.pedeEntidadeInstrutora() ? valores.entidadeInstrutora?.trim() || null : null,
      entidadeEmitente: valores.entidadeEmitente?.trim() || null,
      contactoPublicacao: valores.contactoPublicacao?.trim() || null,
      elementosAfetados: this.pedeElementosAfetados
        ? ELEMENTOS_AFETADOS.map((elemento) => elemento.valor).filter((valor) => valores.elementosAfetados[valor])
        : [],
      ...(this.perguntaGeoconformVisivel() ? { comGeoconform: valores.comGeoconform } : {}),
      ...(this.formulario === 'PUBLICACAO_ALTERACAO' ? { introduzPlantaNova: valores.introduzPlantaNova } : {}),
      fundamentacaoRjigt: this.fundamentacaoParaGravar(valores.fundamentacaoRjigt),
      localizacao: this.localizacaoParaGravar(valores.localizacao),
      conformaHierarquiaSuperior: this.conformacaoParaGravar(valores.conformaHierarquiaSuperior),
      motivosAlteracao: this.motivosParaGravar(valores.motivosAlteracao),
      // O servidor trata omitido como nulo; vai sempre, para não depender disso.
      dinamicaAssociadaId: this.aceitaDinamicaAssociada ? (valores.dinamicaAssociadaId ?? null) : null,
      ...this.tipoAtoParaGravar(valores.tipoAtoId),
      ...(this.escolheSerie() && valores.serieDr && !this.bloqueado('serieDr') ? { serieDr: valores.serieDr } : {}),
      ...(this.escolheRedes() ? { redesDefesa: this.redesParaGravar(valores.redesDefesa) } : {}),
      ...this.dadosRen(valores),
      // Só o que o detalhe pede: um campo que não se aplica é recusado pelo servidor.
      ...(this.formulario === 'SGIFR' && this.pedeRegiao ? { regiao: valores.regiao || null } : {}),
      ...(this.formulario === 'SGIFR' && this.pedeCimAm ? { cimAm: valores.cimAm || null } : {}),
    };
  }

  /** Omitido sem escolha ou bloqueado: o servidor mantém o gravado e não o compara. */
  private tipoAtoParaGravar(valor: string | null): { tipoAtoId?: number } {
    return valor && !this.bloqueado('tipoAtoId') && !this.elaboracaoNova ? { tipoAtoId: Number(valor) } : {};
  }

  /** A resposta à pergunta do PMOT; nula quando o processo não pergunta. */
  respostaSimultaneoPmot(): RespostaSimultaneoPmot | null {
    if (!this.aceitaDinamicaAssociada) {
      return null;
    }
    return this.dinamicaAssociadaRef()?.resposta() ?? this.simultaneoComPmotGravado;
  }

  /** Para juntar ao pedido de gravação: `dadosFormulario`, só quando o processo pergunta pelo PMOT. */
  dadosFormularioPedido(): { dadosFormulario?: { simultaneoComPmot: RespostaSimultaneoPmot } } {
    const resposta = this.respostaSimultaneoPmot();
    return resposta === null ? {} : { dadosFormulario: { simultaneoComPmot: resposta } };
  }

  /** Depois de uma recusa `DINAMICA_ASSOCIADA_INVALIDA` na gravação: a mensagem já está junto do seletor. */
  focarDinamicaAssociada(): void {
    this.recolhido = false;
    afterNextRender(() => this.dinamicaAssociadaRef()?.focar(), { injector: this.injector });
  }

  rotuloIgtAssociado(): string | null {
    const dinamica = this.dinamicaAssociada;
    if (!dinamica) {
      return null;
    }
    return `${dinamica.numeroDinamica} · ${dinamica.instrumento?.designacao ?? dinamica.descritivo}`;
  }

  erroServidor(caminho: string): string | null {
    return this.form.get(caminho)?.getError('servidor') ?? null;
  }

  descricaoLocalizacao(): string | null {
    const ids = [this.descricaoBloqueio('localizacao'), this.mensagemErro('localizacao') ? 'erro-localizacao' : null].filter(Boolean);
    return ids.length > 0 ? ids.join(' ') : null;
  }

  opcoesFreguesia(): OpcaoLista[] {
    return this.comGravada(this.opcoes()['FREGUESIA'] ?? [], this.localizacaoGravada?.freguesia, this.localizacaoGravada?.freguesiaDescricao);
  }

  mostraLocalizacao(): boolean {
    if (this.pedeLocalizacao !== 'OPCIONAL' && this.pedeLocalizacao !== 'OBRIGATORIA') {
      return false;
    }
    return (this.opcoes()['FREGUESIA'] ?? []).length > 0 || (this.bloqueado('localizacao') && this.localizacaoGravada !== null);
  }

  /** O topónimo é da lista ou em texto livre (EF §3.1.2): sem lista, continua editável como texto. */
  mostraToponimo(): boolean {
    const gravada = this.localizacaoGravada;
    const comToponimoGravado = gravada !== null && (gravada.toponimoCodigo !== null || gravada.toponimoTexto !== null);
    return !this.bloqueado('localizacao') || (this.opcoes()['TOPONIMO'] ?? []).length > 0 || comToponimoGravado;
  }

  pedeMotivos(): boolean {
    return (
      this.pedeMotivoAlteracao === 'OBRIGATORIO' ||
      (this.pedeMotivoAlteracao === 'COM_PERGUNTA' && this.form.controls.conformaHierarquiaSuperior.value === true)
    );
  }

  mostraMotivos(): boolean {
    return this.pedeMotivos() && this.gruposMotivos().length > 0;
  }

  gruposMotivos(): { tipo: TipoMotivoAlteracao; rotulo: string; opcoes: OpcaoLista[] }[] {
    const gravados = this.bloqueado('motivosAlteracao') ? (this.valoresIniciais?.motivosAlteracao ?? []) : [];
    return TIPOS_MOTIVO.map(({ tipo, rotulo }) => {
      let opcoes = this.opcoes()[tipo] ?? [];
      for (const motivo of gravados.filter((m) => m.tipo === tipo)) {
        opcoes = this.comGravada(opcoes, motivo.codigo, motivo.descricao ?? null);
      }
      return { tipo, rotulo, opcoes };
    }).filter((grupo) => grupo.opcoes.length > 0);
  }

  motivoMarcado(tipo: TipoMotivoAlteracao, codigo: string): boolean {
    return this.form.controls.motivosAlteracao.value.some((m) => m.tipo === tipo && m.codigo === codigo);
  }

  alternarMotivo(tipo: TipoMotivoAlteracao, codigo: string): void {
    const controlo = this.form.controls.motivosAlteracao;
    const marcados = controlo.value;
    controlo.setValue(
      this.motivoMarcado(tipo, codigo) ? marcados.filter((m) => m.tipo !== tipo || m.codigo !== codigo) : [...marcados, { tipo, codigo }],
    );
    controlo.markAsDirty();
  }

  rotuloSimNao(valor: boolean | null | undefined): string {
    return valor === true ? 'Sim' : valor === false ? 'Não' : '—';
  }

  leituraLocalizacao(): { freguesia: string; toponimo: string } | null {
    const gravada = this.localizacaoGravada;
    if ((this.pedeLocalizacao !== 'OPCIONAL' && this.pedeLocalizacao !== 'OBRIGATORIA') || gravada === null) {
      return null;
    }
    if (gravada.aplicavelTodaArea) {
      return { freguesia: ROTULO_APLICAVEL_TODA_AREA, toponimo: '—' };
    }
    return {
      freguesia: gravada.freguesiaDescricao ?? gravada.freguesia ?? '—',
      toponimo: gravada.toponimoDescricao ?? gravada.toponimoTexto ?? gravada.toponimoCodigo ?? '—',
    };
  }

  leituraMotivos(): string | null {
    const motivos = this.valoresIniciais?.motivosAlteracao ?? [];
    const partes = TIPOS_MOTIVO.map(({ tipo, rotulo }) => {
      const doTipo = motivos.filter((m) => m.tipo === tipo).map((m) => m.descricao ?? m.codigo);
      return doTipo.length > 0 ? `${rotulo}: ${doTipo.join(', ')}` : null;
    }).filter(Boolean);
    return partes.length > 0 ? partes.join('; ') : null;
  }

  private comGravada(opcoes: OpcaoLista[], codigo: string | null | undefined, descricao: string | null | undefined): OpcaoLista[] {
    if (!codigo || opcoes.some((opcao) => opcao.codigo === codigo)) {
      return opcoes;
    }
    return [...opcoes, { codigo, descricao: descricao ?? codigo }];
  }

  private carregarOpcoes(): void {
    const tipos: TipoListaFormulario[] = [];
    if (this.pedeLocalizacao === 'OPCIONAL' || this.pedeLocalizacao === 'OBRIGATORIA') {
      tipos.push('FREGUESIA', 'TOPONIMO');
    }
    if (this.pedeMotivoAlteracao === 'COM_PERGUNTA' || this.pedeMotivoAlteracao === 'OBRIGATORIO') {
      tipos.push(...TIPOS_MOTIVO.map((t) => t.tipo));
    }
    const novos = tipos.filter((tipo) => !this.tiposPedidos.has(tipo));
    if (novos.length === 0) {
      return;
    }
    novos.forEach((tipo) => this.tiposPedidos.add(tipo));
    forkJoin(novos.map((tipo) => this.listaValoresService.opcoes(tipo).pipe(catchError(() => of([] as OpcaoLista[]))))).subscribe(
      (listas) => {
        this.opcoes.update((atuais) => ({ ...atuais, ...Object.fromEntries(novos.map((tipo, i) => [tipo, listas[i]])) }));
        this.reporToponimoComDescricao();
        this.vista.markForCheck();
      },
    );
  }

  private reporLocalizacaoEMotivos(valores: FormularioInicialValores): void {
    const gravada = lerLocalizacao(valores.localizacao);
    this.localizacaoGravada = gravada;
    this.form.controls.localizacao.setValue(
      {
        aplicavelTodaArea: gravada?.aplicavelTodaArea === true,
        freguesia: gravada?.freguesia ?? '',
        toponimo: gravada?.toponimoTexto ?? this.descricaoDoToponimo(gravada) ?? '',
      },
      { emitEvent: false },
    );
    this.form.controls.conformaHierarquiaSuperior.setValue(valores.conformaHierarquiaSuperior ?? null, { emitEvent: false });
    this.form.controls.motivosAlteracao.setValue(
      (valores.motivosAlteracao ?? []).map(({ tipo, codigo }) => ({ tipo, codigo })),
      { emitEvent: false },
    );
  }

  private descricaoDoToponimo(gravada: LocalizacaoGravada | null): string | null {
    const codigo = gravada?.toponimoCodigo;
    if (!codigo) {
      return null;
    }
    return gravada.toponimoDescricao ?? (this.opcoes()['TOPONIMO'] ?? []).find((o) => o.codigo === codigo)?.descricao ?? codigo;
  }

  private reporToponimoComDescricao(): void {
    const toponimo = this.form.controls.localizacao.controls.toponimo;
    const codigo = this.localizacaoGravada?.toponimoCodigo;
    const descricao = this.descricaoDoToponimo(this.localizacaoGravada);
    if (codigo && toponimo.value === codigo && descricao) {
      toponimo.setValue(descricao, { emitEvent: false });
    }
  }

  private aplicarAplicavelTodaArea(): void {
    const { aplicavelTodaArea, freguesia, toponimo } = this.form.controls.localizacao.controls;
    if (aplicavelTodaArea.value) {
      freguesia.setValue('', { emitEvent: false });
      toponimo.setValue('', { emitEvent: false });
      freguesia.disable({ emitEvent: false });
      toponimo.disable({ emitEvent: false });
    } else if (!this.aCarregar && !this.bloqueado('localizacao')) {
      freguesia.enable({ emitEvent: false });
      toponimo.enable({ emitEvent: false });
    }
  }

  private localizacaoParaGravar(valor: { aplicavelTodaArea: boolean; freguesia: string; toponimo: string }): LocalizacaoPedido | null {
    if (this.bloqueado('localizacao')) {
      const gravada = this.localizacaoGravada;
      return gravada === null
        ? null
        : {
            aplicavelTodaArea: gravada.aplicavelTodaArea,
            freguesia: gravada.freguesia,
            toponimoCodigo: gravada.toponimoCodigo,
            toponimoTexto: gravada.toponimoTexto,
          };
    }
    if (!this.mostraLocalizacao()) {
      return null;
    }
    if (valor.aplicavelTodaArea) {
      return { aplicavelTodaArea: true, freguesia: null, toponimoCodigo: null, toponimoTexto: null };
    }
    const texto = this.mostraToponimo() ? valor.toponimo.trim() : '';
    const codigo = (this.opcoes()['TOPONIMO'] ?? []).find((opcao) => opcao.descricao === texto)?.codigo ?? null;
    const localizacao: LocalizacaoPedido = {
      aplicavelTodaArea: null,
      freguesia: valor.freguesia || null,
      toponimoCodigo: codigo,
      toponimoTexto: codigo ? null : texto || null,
    };
    return localizacao.freguesia || localizacao.toponimoCodigo || localizacao.toponimoTexto ? localizacao : null;
  }

  private conformacaoParaGravar(resposta: boolean | null): boolean | null {
    if (this.bloqueado('conformaHierarquiaSuperior')) {
      return this.valoresIniciais?.conformaHierarquiaSuperior ?? null;
    }
    return this.pedeMotivoAlteracao === 'COM_PERGUNTA' ? resposta : null;
  }

  private motivosParaGravar(marcados: MotivoAlteracao[]): MotivoAlteracao[] {
    if (this.bloqueado('motivosAlteracao')) {
      return (this.valoresIniciais?.motivosAlteracao ?? []).map(({ tipo, codigo }) => ({ tipo, codigo }));
    }
    if (!this.mostraMotivos()) {
      return [];
    }
    return this.gruposMotivos().flatMap((grupo) =>
      grupo.opcoes
        .filter((opcao) => marcados.some((m) => m.tipo === grupo.tipo && m.codigo === opcao.codigo))
        .map((opcao) => ({ tipo: grupo.tipo, codigo: opcao.codigo })),
    );
  }

  /**
   * Pela ordem das opções e só com as que se veem, para não reenviar um código desativado que já não se pode
   * desmarcar. Bloqueado, vai como foi gravado: o servidor recusa qualquer diferença.
   */
  private fundamentacaoParaGravar(marcados: string[]): string[] {
    if (this.bloqueado('fundamentacaoRjigt')) {
      return marcados;
    }
    if (!this.mostraFundamentacaoRjigt()) {
      return [];
    }
    return this.opcoesFundamentacaoRjigt.map((opcao) => opcao.codigo).filter((codigo) => marcados.includes(codigo));
  }

  private reativarElaboracaoNova(): void {
    const erros = Object.values(this.formElaboracao.controls).map((controlo) => [controlo, controlo.errors] as const);
    this.formElaboracao.enable({ emitEvent: false });
    this.filtrarConcelhosPelaNutII();
    erros.forEach(([controlo, valor]) => controlo.setErrors(valor));
  }

  /** `enable()` revalida e apagaria as mensagens do servidor que a gravação falhada acabou de pôr nos campos. */
  private reativar(): void {
    const erros = [...Object.values(this.form.controls), ...Object.values(this.form.controls.localizacao.controls)].map(
      (controlo) => [controlo, controlo.errors] as const,
    );
    this.form.enable();
    for (const data of [this.dataAssinatura, this.dataSuplemento]) {
      const erroData = data.errors;
      data.enable({ emitEvent: false });
      data.setErrors(erroData);
    }
    erros.forEach(([controlo, valor]) => controlo.setErrors(valor));
    if (erros.some(([, valor]) => valor)) {
      this.recolhido = false;
    }
    this.aplicarBloqueios(false);
  }

  /**
   * `reativarOsRestantes` só quando a lista muda: `enable()` revalida e apagaria as mensagens do servidor
   * que `reativar` acabou de repor, e depois de `form.enable()` já não há nada a reativar.
   */
  private aplicarBloqueios(reativarOsRestantes = true): void {
    for (const [chave, data] of [['dataAssinatura', this.dataAssinatura], ['dataSuplemento', this.dataSuplemento]] as const) {
      if (this.bloqueado(chave)) {
        data.disable({ emitEvent: false });
      } else if (reativarOsRestantes && !this.aCarregar) {
        data.enable({ emitEvent: false });
      }
    }
    for (const chave of CONTROLOS_BLOQUEAVEIS) {
      const controlo = this.form.controls[chave];
      if (this.bloqueado(chave)) {
        controlo.disable({ emitEvent: false });
      } else if (reativarOsRestantes && !this.aCarregar) {
        controlo.enable({ emitEvent: false });
      }
    }
  }
}
